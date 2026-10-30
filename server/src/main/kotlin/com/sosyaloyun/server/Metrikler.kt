package com.sosyaloyun.server

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Kimliği doğrulanmış her istekte kullanıcının o günü (Türkiye saati) aktif gün olarak yazılır; günde bir kez. */
class Aktiflik(private val veritabani: Database, private val saat: Clock) {
    private val sonYazilan = ConcurrentHashMap<UUID, LocalDate>()

    suspend fun kaydet(kullaniciId: UUID) {
        val bugun = saat.now().toJavaInstant().atZone(TURKIYE).toLocalDate()
        if (sonYazilan[kullaniciId] == bugun) return
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                AktifGunTablosu.insertIgnore {
                    it[AktifGunTablosu.kullaniciId] = kullaniciId
                    it[gun] = bugun
                }
            }
        }
        sonYazilan[kullaniciId] = bugun
    }
}

/**
 * Doğrulama eşiğinin metrikleri (ADR 0007), üçüncü taraf analitik olmadan kendi veritabanımızdan:
 * - [kayitliKullanici]: davet koduyla betaya katılmış hesaplar.
 * - [dorduncuHaftaAktiflik]: katılışının üzerinden en az 4 hafta geçenlerden 4. haftasında (katılıştan sonraki
 *   22.–28. günler) en az bir gün aktif olanların oranı; böyle kimse yoksa null.
 * - [kayitPerAktifKullanici]: son 7 günde girilen günlük kayıtlarının aynı 7 günde aktif olan kullanıcı sayısına oranı.
 */
data class DogrulamaMetrikleri(
    val kayitliKullanici: Int,
    val dorduncuHaftaKohortu: Int,
    val dorduncuHaftaAktif: Int,
    val haftalikAktifKullanici: Int,
    val haftalikGunlukKaydi: Int,
) {
    val dorduncuHaftaAktiflik: Double? get() = if (dorduncuHaftaKohortu == 0) null else dorduncuHaftaAktif.toDouble() / dorduncuHaftaKohortu
    val kayitPerAktifKullanici: Double? get() = if (haftalikAktifKullanici == 0) null else haftalikGunlukKaydi.toDouble() / haftalikAktifKullanici
}

class Metrikler(private val veritabani: Database, private val eposta: EpostaGonderici?, private val saat: Clock) {
    suspend fun hesapla(): DogrulamaMetrikleri = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val simdi = saat.now().toJavaInstant()
            val bugun = simdi.atZone(TURKIYE).toLocalDate()
            val katilislar = KullaniciTablosu.select(KullaniciTablosu.id, KullaniciTablosu.davetKabulZamani)
                .where { KullaniciTablosu.davetKabulZamani.isNotNull() }
                .associate { it[KullaniciTablosu.id] to it[KullaniciTablosu.davetKabulZamani]!!.toInstant().atZone(TURKIYE).toLocalDate() }
            // 4. haftası tamamlanmış olanlar: katılış günü + 28 gün bugüne ulaştı.
            val kohort = katilislar.filterValues { !it.plusDays(HAFTA_GUNU * 4).isAfter(bugun) }
            val enEski = kohort.values.minOrNull()
            val aktifGunler = if (enEski == null) emptyMap() else
                AktifGunTablosu.selectAll()
                    .where { (AktifGunTablosu.gun greaterEq enEski.plusDays(HAFTA_GUNU * 3 + 1)) and (AktifGunTablosu.kullaniciId inList kohort.keys) }
                    .groupBy({ it[AktifGunTablosu.kullaniciId] }, { it[AktifGunTablosu.gun] })
            val dorduncuHaftadaAktif = kohort.count { (id, katilis) ->
                val baslangic = katilis.plusDays(HAFTA_GUNU * 3 + 1)
                val bitis = katilis.plusDays(HAFTA_GUNU * 4)
                aktifGunler[id].orEmpty().any { !it.isBefore(baslangic) && !it.isAfter(bitis) }
            }
            val haftaBasi = bugun.minusDays(HAFTA_GUNU - 1)
            val haftalikAktif = AktifGunTablosu.select(AktifGunTablosu.kullaniciId)
                .where { AktifGunTablosu.gun greaterEq haftaBasi }
                .withDistinct()
                .count()
            val haftalikKayit = GunlukKaydiTablosu.selectAll()
                .where { GunlukKaydiTablosu.olusturulma greaterEq simdi.minusSeconds(HAFTA_GUNU * 24 * 3600).atOffset(ZoneOffset.UTC) }
                .count()
            DogrulamaMetrikleri(
                kayitliKullanici = katilislar.size,
                dorduncuHaftaKohortu = kohort.size,
                dorduncuHaftaAktif = dorduncuHaftadaAktif,
                haftalikAktifKullanici = haftalikAktif.toInt(),
                haftalikGunlukKaydi = haftalikKayit.toInt(),
            )
        }
    }

    /**
     * Bu haftanın (pazartesi başlar, Türkiye saati) raporu henüz gönderilmediyse metrikleri ekibe e-postayla gönderir.
     * Pazartesi [RAPOR_SAATI]'nden önce çalışırsa bir önceki haftaya bakar; yani rapor pazartesi sabahı gider, server o
     * an kapalıysa açılınca gider. E-posta adaptörü yoksa hiçbir şey yapmaz. Gönderildiyse true.
     */
    suspend fun haftalikRaporuGonder(): Boolean {
        val eposta = eposta ?: return false
        val simdi = saat.now().toJavaInstant().atZone(TURKIYE)
        val hafta = simdi.minusHours(RAPOR_SAATI.toLong()).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gonderildi = withContext(Dispatchers.IO) {
            transaction(veritabani) { MetrikRaporuTablosu.selectAll().where { MetrikRaporuTablosu.hafta eq hafta }.any() }
        }
        if (gonderildi) return false
        val metrikler = hesapla()
        eposta.gonder("Haftalık doğrulama metrikleri · ${hafta}", raporMetni(metrikler))
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                MetrikRaporuTablosu.insertIgnore {
                    it[MetrikRaporuTablosu.hafta] = hafta
                    it[gonderilme] = OffsetDateTime.ofInstant(simdi.toInstant(), ZoneOffset.UTC)
                }
            }
        }
        return true
    }

    companion object {
        private const val HAFTA_GUNU = 7L

        /** Rapor pazartesi bu saatten (Türkiye) sonra gider. */
        const val RAPOR_SAATI = 9

        /** Spec #1'deki doğrulama eşiği; yalnızca raporda hedef olarak gösterilir. */
        private const val HEDEF_KULLANICI = 300
        private const val HEDEF_AKTIFLIK = 0.25
        private const val HEDEF_KAYIT = 2.0

        fun raporMetni(m: DogrulamaMetrikleri): String {
            fun oran(d: Double?) = d?.let { "%" + String.format(Locale.forLanguageTag("tr"), "%.1f", it * 100) } ?: "henüz yok"
            fun ondalik(d: Double?) = d?.let { String.format(Locale.forLanguageTag("tr"), "%.2f", it) } ?: "henüz yok"
            return """
                |Kayıtlı kullanıcı: ${m.kayitliKullanici} (hedef: $HEDEF_KULLANICI)
                |4. haftada aktif kalma: ${oran(m.dorduncuHaftaAktiflik)} — ${m.dorduncuHaftaAktif}/${m.dorduncuHaftaKohortu} (hedef: ${oran(HEDEF_AKTIFLIK)})
                |Aktif kullanıcı başına haftalık günlük kaydı: ${ondalik(m.kayitPerAktifKullanici)} — ${m.haftalikGunlukKaydi} kayıt / ${m.haftalikAktifKullanici} aktif kullanıcı (hedef: ${ondalik(HEDEF_KAYIT)})
            """.trimMargin()
        }
    }
}

internal object AktifGunTablosu : Table("aktif_gun") {
    val kullaniciId = javaUUID("kullanici_id")
    val gun = date("gun")
    override val primaryKey = PrimaryKey(kullaniciId, gun)
}

internal object MetrikRaporuTablosu : Table("metrik_raporu") {
    val hafta = date("hafta")
    val gonderilme = timestampWithTimeZone("gonderilme")
    override val primaryKey = PrimaryKey(hafta)
}
