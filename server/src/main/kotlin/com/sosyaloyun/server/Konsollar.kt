package com.sosyaloyun.server

import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KonsolAgi
import com.sosyaloyun.shared.KonsolBaglantisi
import com.sosyaloyun.shared.KonsolIceAktarmaSonucu
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
import org.jetbrains.exposed.v1.core.isNull
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
 * Xbox ve PlayStation içe aktarma (Faz 3, #57). Oynanan oyunlar Steam'deki kurallarla durumsuz kütüphane girişi olur:
 * akışa düşmez, durum tahmin edilmez, mevcut girişler değişmez. Oyunlar katalogla adla eşlenir; eşleşmeyenler atlanır
 * ve sayılır. Bağlantı günde bir güncellenir (Faz 3 açıkken).
 */
class Konsollar(
    private val veritabani: Database,
    private val katalog: Katalog,
    kaynaklar: List<KonsolKaynagi>,
    private val fazlar: Fazlar,
    private val saat: Clock,
) {
    private val kaynaklar = kaynaklar.associateBy { it.ag }

    sealed interface Sonuc {
        data class Basarili(val sonuc: KonsolIceAktarmaSonucu) : Sonuc
        data class Hata(val kod: HataKodu) : Sonuc
    }

    /** Anahtarı olan ağlar ve kullanıcının bu ağlardaki bağlantıları. */
    suspend fun baglantilar(kullanici: UUID): List<KonsolBaglantisi> {
        val bagli = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KonsolBaglantisiTablosu.selectAll().where { KonsolBaglantisiTablosu.kullaniciId eq kullanici }
                    .associate { KonsolAgi.valueOf(it[KonsolBaglantisiTablosu.ag]) to it[KonsolBaglantisiTablosu.hesapAdi] }
            }
        }
        return KonsolAgi.entries.filter { it in kaynaklar }.map { KonsolBaglantisi(it, bagli[it]) }
    }

    suspend fun bagla(kullanici: UUID, ag: KonsolAgi, girdi: String): Sonuc {
        val kaynak = kaynaklar[ag] ?: return Sonuc.Hata(HataKodu.KONSOL_KULLANILAMIYOR)
        val ad = girdi.trim().takeIf { it.isNotEmpty() && it.length <= HESAP_ADI_EN_FAZLA } ?: return Sonuc.Hata(HataKodu.KONSOL_HESABI_BULUNAMADI)
        val (hesap, oyunlar) = try {
            val hesap = kaynak.hesapBul(ad) ?: return Sonuc.Hata(HataKodu.KONSOL_HESABI_BULUNAMADI)
            if (baskasinda(kullanici, ag, hesap.kimlik)) return Sonuc.Hata(HataKodu.KONSOL_HESABI_BASKA_KULLANICIDA)
            when (val sonuc = kaynak.oyunlar(hesap.kimlik)) {
                KonsolOyunlari.Gizli -> return Sonuc.Hata(HataKodu.KONSOL_PROFILI_GIZLI)
                is KonsolOyunlari.Acik -> hesap to sonuc.adlar
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("$ag isteği başarısız", e)
            return Sonuc.Hata(HataKodu.KONSOL_KULLANILAMIYOR)
        }
        val (eslesen, eslesmeyen) = eslestir(oyunlar)
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val simdi = simdi()
                KonsolBaglantisiTablosu.upsert {
                    it[kullaniciId] = kullanici
                    it[KonsolBaglantisiTablosu.ag] = ag.name
                    it[hesapKimligi] = hesap.kimlik
                    it[hesapAdi] = hesap.ad
                    it[baglanma] = simdi
                    it[sonGuncelleme] = simdi
                }
                iceAktar(kullanici, ag, eslesen, simdi)
            }
        }
        return Sonuc.Basarili(KonsolIceAktarmaSonucu(KonsolBaglantisi(ag, hesap.ad), eslesen.size, eslesmeyen))
    }

    /** Bağlantıyı kaldırır; içe aktarılan girişler kütüphanede kalır, artık güncellenmez. */
    suspend fun kaldir(kullanici: UUID, ag: KonsolAgi) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KonsolBaglantisiTablosu.deleteWhere { (KonsolBaglantisiTablosu.kullaniciId eq kullanici) and (KonsolBaglantisiTablosu.ag eq ag.name) }
            }
        }
    }

    /** Günde bir: Faz 3 açıkken bağlı hesapların yeni oyunları durumsuz eklenir. Biri başarısız olsa diğerleri sürer. */
    suspend fun hepsiniGuncelle() {
        if (!fazlar.acik(Faz.FAZ_3)) return
        val baglantilar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KonsolBaglantisiTablosu.selectAll().map {
                    Triple(it[KonsolBaglantisiTablosu.kullaniciId], KonsolAgi.valueOf(it[KonsolBaglantisiTablosu.ag]), it[KonsolBaglantisiTablosu.hesapKimligi])
                }
            }
        }
        for ((kullanici, ag, kimlik) in baglantilar) {
            val kaynak = kaynaklar[ag] ?: continue
            try {
                val oyunlar = kaynak.oyunlar(kimlik) as? KonsolOyunlari.Acik ?: continue
                val (eslesen, _) = eslestir(oyunlar.adlar)
                withContext(Dispatchers.IO) {
                    transaction(veritabani) {
                        val simdi = simdi()
                        iceAktar(kullanici, ag, eslesen, simdi)
                        KonsolBaglantisiTablosu.update({ (KonsolBaglantisiTablosu.kullaniciId eq kullanici) and (KonsolBaglantisiTablosu.ag eq ag.name) }) {
                            it[sonGuncelleme] = simdi
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("$ag oyunları güncellenemedi: $kullanici", e)
            }
        }
    }

    /** Katalogdaki ana oyunlarla adla eşleşenler ve eşleşmeyen farklı ad sayısı. */
    private suspend fun eslestir(adlar: List<String>): Pair<Set<UUID>, Int> {
        val farkli = adlar.map { it.replace(TICARI_ISARETLER, "") }.distinctBy(AramaMetni::duzenle)
        val eslesmeler = katalog.adlaEslesmeler(farkli)
        return farkli.mapNotNull { eslesmeler[AramaMetni.duzenle(it)] }.toSet() to farkli.count { AramaMetni.duzenle(it) !in eslesmeler }
    }

    /**
     * Girişi olmayan oyun durumsuz eklenir; mevcut girişin durumu, puanı ve diğer alanları değişmez, yalnızca ilk
     * içe aktarıldığı ağ işaretlenir (oynanmış sayılması için). Akış olayı yazılmaz.
     */
    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.iceAktar(kullanici: UUID, ag: KonsolAgi, oyunlar: Set<UUID>, simdi: OffsetDateTime) {
        val k = KutuphaneGirisiTablosu
        for (oyun in oyunlar) {
            k.insertIgnore {
                it[kullaniciId] = kullanici
                it[oyunId] = oyun
                it[guncellenme] = simdi
            }
            k.update({ (k.kullaniciId eq kullanici) and (k.oyunId eq oyun) and k.konsolAgi.isNull() }) { it[konsolAgi] = ag.name }
        }
    }

    private suspend fun baskasinda(kullanici: UUID, ag: KonsolAgi, kimlik: String): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            KonsolBaglantisiTablosu.selectAll().where {
                (KonsolBaglantisiTablosu.ag eq ag.name) and (KonsolBaglantisiTablosu.hesapKimligi eq kimlik) and (KonsolBaglantisiTablosu.kullaniciId neq kullanici)
            }.any()
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val HESAP_ADI_EN_FAZLA = 64
        val TICARI_ISARETLER = Regex("[™®©]")
        val log = LoggerFactory.getLogger(Konsollar::class.java)
    }
}

internal object KonsolBaglantisiTablosu : Table("konsol_baglantisi") {
    val kullaniciId = javaUUID("kullanici_id")
    val ag = text("ag")
    val hesapKimligi = text("hesap_kimligi")
    val hesapAdi = text("hesap_adi")
    val baglanma = timestampWithTimeZone("baglanma")
    val sonGuncelleme = timestampWithTimeZone("son_guncelleme")
    override val primaryKey = PrimaryKey(kullaniciId, ag)
}
