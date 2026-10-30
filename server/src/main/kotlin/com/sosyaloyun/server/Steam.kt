package com.sosyaloyun.server

import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.SteamBaglantisi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Steam bağlantısı ve içe aktarma. En az bir kez açılmış oyunlar durumsuz kütüphane girişi olur ve Steam süreleri saklanır;
 * durum tahmin edilmez, içe aktarma ve süre güncellemeleri akışa düşmez. [kaynak] null ise Steam kullanılamaz.
 */
class Steam(
    private val veritabani: Database,
    private val katalog: Katalog,
    private val kutuphane: Kutuphane,
    private val kaynak: SteamKaynagi?,
    private val saat: Clock,
) {
    sealed interface Sonuc {
        data class Basarili(val sonuc: SteamIceAktarmaSonucu) : Sonuc
        data class Hata(val kod: HataKodu) : Sonuc
    }

    suspend fun bagla(kullaniciId: UUID, girdi: String): Sonuc {
        val kaynak = kaynak ?: return Sonuc.Hata(HataKodu.STEAM_KULLANILAMIYOR)
        val cozulen = SteamGirdisi.coz(girdi) ?: return Sonuc.Hata(HataKodu.STEAM_HESABI_BULUNAMADI)
        val oyunlar = try {
            val steamId = when (cozulen) {
                is SteamGirdisi.Kimlik -> cozulen.steamId
                is SteamGirdisi.OzelAd -> kaynak.ozelAdiCoz(cozulen.ad) ?: return Sonuc.Hata(HataKodu.STEAM_HESABI_BULUNAMADI)
            }
            if (baskasinda(kullaniciId, steamId)) return Sonuc.Hata(HataKodu.STEAM_HESABI_BASKA_KULLANICIDA)
            when (val sonuc = kaynak.oyunlar(steamId)) {
                SteamOyunlari.Gizli -> return Sonuc.Hata(HataKodu.STEAM_PROFILI_GIZLI)
                is SteamOyunlari.Acik -> steamId to sonuc.oyunlar
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("Steam isteği başarısız", e)
            return Sonuc.Hata(HataKodu.STEAM_KULLANILAMIYOR)
        }
        val (steamId, liste) = oyunlar
        val sureler = oyunSureleri(liste)
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val simdi = simdi()
                SteamBaglantisiTablosu.upsert {
                    it[SteamBaglantisiTablosu.kullaniciId] = kullaniciId
                    it[SteamBaglantisiTablosu.steamId] = steamId
                    it[baglanma] = simdi
                    it[sonGuncelleme] = simdi
                }
                iceAktar(kullaniciId, sureler, simdi)
            }
        }
        val enCok = sureler.entries.sortedByDescending { it.value }.take(EN_COK_OYNANAN).map { it.key }
        val ozetler = katalog.ozetleri(enCok)
        return Sonuc.Basarili(
            SteamIceAktarmaSonucu(
                baglanti = SteamBaglantisi(steamId),
                iceAktarilanSayisi = sureler.size,
                enCokOynananlar = enCok.mapNotNull { oyunId ->
                    val giris = kutuphane.giris(kullaniciId, oyunId) ?: return@mapNotNull null
                    ozetler[oyunId]?.let { KutuphaneOgesi(it, giris) }
                },
            ),
        )
    }

    suspend fun baglanti(kullaniciId: UUID): SteamBaglantisi? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            SteamBaglantisiTablosu.selectAll().where { SteamBaglantisiTablosu.kullaniciId eq kullaniciId }
                .singleOrNull()?.let { SteamBaglantisi(it[SteamBaglantisiTablosu.steamId]) }
        }
    }

    /** Bağlantıyı kaldırır; içe aktarılan girişler ve son Steam süreleri kütüphanede kalır, artık güncellenmez. */
    suspend fun kaldir(kullaniciId: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) { SteamBaglantisiTablosu.deleteWhere { SteamBaglantisiTablosu.kullaniciId eq kullaniciId } }
        }
    }

    /** Günde bir: bağlı her hesabın süreleri güncellenir, yeni açılan oyunlar durumsuz eklenir. Biri başarısız olsa diğerleri sürer. */
    suspend fun hepsiniGuncelle() {
        val kaynak = kaynak ?: return
        val baglantilar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                SteamBaglantisiTablosu.selectAll().map { it[SteamBaglantisiTablosu.kullaniciId] to it[SteamBaglantisiTablosu.steamId] }
            }
        }
        for ((kullaniciId, steamId) in baglantilar) {
            try {
                val oyunlar = kaynak.oyunlar(steamId) as? SteamOyunlari.Acik ?: continue
                val sureler = oyunSureleri(oyunlar.oyunlar)
                withContext(Dispatchers.IO) {
                    transaction(veritabani) {
                        val simdi = simdi()
                        iceAktar(kullaniciId, sureler, simdi)
                        SteamBaglantisiTablosu.update({ SteamBaglantisiTablosu.kullaniciId eq kullaniciId }) { it[sonGuncelleme] = simdi }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("Steam süreleri güncellenemedi: $kullaniciId", e)
            }
        }
    }

    /** Açılmış oyunların katalogdaki karşılıkları; aynı oyuna eşlenen uygulamaların (ör. sürümler) süreleri toplanır. */
    private suspend fun oyunSureleri(oyunlar: List<SteamOyunu>): Map<UUID, Int> {
        val acilmis = oyunlar.filter { it.dakika > 0 }
        val eslesmeler = katalog.steamEslesmeleri(acilmis.map { it.uygulamaId })
        return acilmis.mapNotNull { oyun -> eslesmeler[oyun.uygulamaId]?.let { it to oyun.dakika } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
    }

    /** Girişi olmayan oyun durumsuz eklenir; mevcut girişin yalnızca Steam süresi değişir. Akış olayı yazılmaz. */
    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.iceAktar(kullaniciId: UUID, sureler: Map<UUID, Int>, simdi: OffsetDateTime) {
        val k = KutuphaneGirisiTablosu
        for ((oyunId, dakika) in sureler) {
            k.insertIgnore {
                it[k.kullaniciId] = kullaniciId
                it[k.oyunId] = oyunId
                it[guncellenme] = simdi
            }
            k.update({ (k.kullaniciId eq kullaniciId) and (k.oyunId eq oyunId) }) { it[steamSureDakika] = dakika }
        }
    }

    private suspend fun baskasinda(kullaniciId: UUID, steamId: String): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            SteamBaglantisiTablosu.selectAll()
                .where { (SteamBaglantisiTablosu.steamId eq steamId) and (SteamBaglantisiTablosu.kullaniciId neq kullaniciId) }
                .any()
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val EN_COK_OYNANAN = 10
        val log = LoggerFactory.getLogger(Steam::class.java)
    }
}

internal object SteamBaglantisiTablosu : Table("steam_baglantisi") {
    val kullaniciId = javaUUID("kullanici_id")
    val steamId = text("steam_id")
    val baglanma = timestampWithTimeZone("baglanma")
    val sonGuncelleme = timestampWithTimeZone("son_guncelleme")
    override val primaryKey = PrimaryKey(kullaniciId)
}
