package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.KuratorBasvuruDurumu
import com.sosyaloyun.shared.KuratorDurumu
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.KuratorSarti
import com.sosyaloyun.shared.ListeSayfasi
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Yönetim panelinde karar bekleyen küratör başvurusu. */
data class BekleyenKuratorBasvurusu(val id: UUID, val kullaniciAdi: String, val zaman: OffsetDateTime, val sartlar: List<KuratorSarti>)

/**
 * Küratörler (Faz 2, #50): ayarla belirlenen şartları sağlayan kullanıcı başvurur, ekip yönetim panelinden onaylar ya
 * da reddeder; sonuç kalıp mesajla bildirilir. Küratörlerin herkese açık listeleri keşifte gösterilir.
 */
class Kuratorler(
    private val veritabani: Database,
    private val ayarlar: Ayarlar,
    private val listeler: Listeler,
    private val saat: Clock,
) {
    suspend fun durum(kullanici: UUID): KuratorDurumu {
        val sartlar = sartlar(kullanici)
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val kurator = KullaniciTablosu.select(KullaniciTablosu.kurator).where { KullaniciTablosu.id eq kullanici }.single()[KullaniciTablosu.kurator]
                val son = KuratorBasvurusuTablosu.selectAll().where { KuratorBasvurusuTablosu.kullaniciId eq kullanici }
                    .orderBy(KuratorBasvurusuTablosu.olusturulma to SortOrder.DESC).limit(1).singleOrNull()
                val basvuru = when (son?.get(KuratorBasvurusuTablosu.durum)) {
                    BEKLIYOR -> KuratorBasvuruDurumu.BEKLIYOR
                    REDDEDILDI -> KuratorBasvuruDurumu.REDDEDILDI
                    else -> null
                }
                KuratorDurumu(kurator, basvuru, sartlar)
            }
        }
    }

    /** Şartlar sağlanmıyorsa, zaten küratörse ya da bekleyen başvurusu varsa (eşzamanlı istekler dahil) false. */
    suspend fun basvur(kullanici: UUID): Boolean {
        if (!durum(kullanici).basvurabilir) return false
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KuratorBasvurusuTablosu.insertIgnore {
                    it[id] = UUID.randomUUID()
                    it[kullaniciId] = kullanici
                    it[durum] = BEKLIYOR
                    it[olusturulma] = simdi()
                }.insertedCount > 0
            }
        }
    }

    suspend fun bekleyenler(): List<BekleyenKuratorBasvurusu> {
        val satirlar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KuratorBasvurusuTablosu.join(KullaniciTablosu, org.jetbrains.exposed.v1.core.JoinType.INNER, KuratorBasvurusuTablosu.kullaniciId, KullaniciTablosu.id)
                    .selectAll().where { KuratorBasvurusuTablosu.durum eq BEKLIYOR }
                    .orderBy(KuratorBasvurusuTablosu.olusturulma to SortOrder.ASC)
                    .map { Triple(it[KuratorBasvurusuTablosu.id], it[KullaniciTablosu.kullaniciAdi].orEmpty(), it[KuratorBasvurusuTablosu.olusturulma] to it[KuratorBasvurusuTablosu.kullaniciId]) }
            }
        }
        return satirlar.map { (id, ad, zamanVeKisi) -> BekleyenKuratorBasvurusu(id, ad, zamanVeKisi.first, sartlar(zamanVeKisi.second)) }
    }

    /**
     * Bekleyen başvuruya karar verir; onayda kullanıcı küratör olur. Şartlar yeniden denetlenmez: ekip panelde güncel
     * şartları görerek karar verir. Başvuru yoksa null; varsa başvuranın kullanıcı adı (denetim kaydı için).
     */
    suspend fun karar(basvuruId: UUID, onay: Boolean): String? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = KuratorBasvurusuTablosu.selectAll()
                .where { (KuratorBasvurusuTablosu.id eq basvuruId) and (KuratorBasvurusuTablosu.durum eq BEKLIYOR) }
                .singleOrNull() ?: return@transaction null
            val kullanici = satir[KuratorBasvurusuTablosu.kullaniciId]
            val simdi = simdi()
            KuratorBasvurusuTablosu.update({ KuratorBasvurusuTablosu.id eq basvuruId }) {
                it[durum] = if (onay) ONAYLANDI else REDDEDILDI
                it[kararZamani] = simdi
            }
            if (onay) KullaniciTablosu.update({ KullaniciTablosu.id eq kullanici }) { it[kurator] = true }
            with(BildirimOlaylari) { kalipMesaj(kullanici, if (onay) BildirimTuru.KURATOR_ONAYLANDI else BildirimTuru.KURATOR_REDDEDILDI, simdi) }
            KullaniciTablosu.select(KullaniciTablosu.kullaniciAdi).where { KullaniciTablosu.id eq kullanici }.single()[KullaniciTablosu.kullaniciAdi].orEmpty()
        }
    }

    /** Keşifteki "Küratörlerden listeler": küratörlerin herkese açık listeleri, en yeni önce. */
    suspend fun listeler(izleyen: UUID, imlec: String?): ListeSayfasi = listeler.kuratorListeleri(izleyen, imlec)

    private suspend fun sartlar(kullanici: UUID): List<KuratorSarti> {
        val enAzInceleme = ayarlar.oku(Ayar.KuratorEnAzInceleme)
        val enAzTakipci = ayarlar.oku(Ayar.KuratorEnAzTakipci)
        val enAzGun = ayarlar.oku(Ayar.KuratorEnAzHesapGunu)
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val inceleme = GunlukKaydiTablosu.selectAll()
                    .where { (GunlukKaydiTablosu.kullaniciId eq kullanici) and GunlukKaydiTablosu.inceleme.isNotNull() }.count().toInt()
                val takipci = TakipTablosu.selectAll().where { TakipTablosu.takipEdilen eq kullanici }.count().toInt()
                val katilis = KullaniciTablosu.select(KullaniciTablosu.davetKabulZamani).where { KullaniciTablosu.id eq kullanici }
                    .single()[KullaniciTablosu.davetKabulZamani]
                val gun = katilis?.let { Duration.between(it.toInstant(), saat.now().toJavaInstant()).toDays().toInt() } ?: 0
                listOf(
                    KuratorSarti(KuratorSartTuru.INCELEME, enAzInceleme, inceleme),
                    KuratorSarti(KuratorSartTuru.TAKIPCI, enAzTakipci, takipci),
                    KuratorSarti(KuratorSartTuru.HESAP_YASI_GUN, enAzGun, gun),
                )
            }
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val BEKLIYOR = "BEKLIYOR"
        const val ONAYLANDI = "ONAYLANDI"
        const val REDDEDILDI = "REDDEDILDI"
    }
}

internal object KuratorBasvurusuTablosu : Table("kurator_basvurusu") {
    val id = javaUUID("id")
    val kullaniciId = javaUUID("kullanici_id")
    val durum = text("durum")
    val olusturulma = timestampWithTimeZone("olusturulma")
    val kararZamani = timestampWithTimeZone("karar_zamani").nullable()
    override val primaryKey = PrimaryKey(id)
}
