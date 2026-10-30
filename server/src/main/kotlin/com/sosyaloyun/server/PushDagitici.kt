package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.Faz
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Bekleyen bildirimlerin push'larını gönderir. Push şu durumlarda gitmez, bildirim yalnızca uygulama içinde kalır:
 * kullanıcı o tür için push'u kapatmış, kayıtlı cihazı yok, yerel saatiyle sessiz saatlerde, ya da bugün günlük sınıra
 * ulaşılmış. Sınırlar Ayar'dan gelir. Atlanan push sonradan gönderilmez.
 *
 * Faz 2'de (#52) acil olmayan türler ([PENCERELI]) yalnızca yerel saatle öğle ve akşam pencerelerinde gider; pencere
 * dışında oluşanlar bir sonraki pencereyi bekler ve orada kullanıcı başına tek bir özet push'ta toplanır.
 */
class PushDagitici(
    private val veritabani: Database,
    private val ayarlar: Ayarlar,
    private val gonderici: PushGonderici?,
    private val saat: Clock,
    private val fazlar: Fazlar,
) {
    suspend fun bekleyenleriGonder() {
        val kurallar = Kurallar(
            sinir = ayarlar.oku(Ayar.GunlukPushSiniri),
            sessizBaslangic = ayarlar.oku(Ayar.SessizSaatBaslangici),
            sessizBitis = ayarlar.oku(Ayar.SessizSaatBitisi),
            pencereler = if (fazlar.acik(Faz.FAZ_2)) {
                val sure = ayarlar.oku(Ayar.PushPenceresiSuresi)
                listOf(ayarlar.oku(Ayar.OglePushPenceresi), ayarlar.oku(Ayar.AksamPushPenceresi)).sorted().map { it to sure }
            } else {
                null
            },
        )
        val simdi = simdiUtc()
        val bekleyenler = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                BildirimTablosu.selectAll()
                    .where { (BildirimTablosu.pushDurumu eq PushDurumu.BEKLIYOR.name) and (BildirimTablosu.pushSonra.isNull() or (BildirimTablosu.pushSonra lessEq simdi)) }
                    .orderBy(BildirimTablosu.olusturulma to SortOrder.ASC)
                    .limit(PARTI)
                    .map { Bekleyen(it[BildirimTablosu.id], it[BildirimTablosu.alici], BildirimTuru.valueOf(it[BildirimTablosu.tur]), it[BildirimTablosu.aktor]) }
            }
        }
        val (pencereli, anlik) = bekleyenler.partition { kurallar.pencereler != null && it.tur in PENCERELI }
        // Bir bildirimin hatası diğerlerini durdurmaz; hatalı bildirim atlanır, yoksa her turda ilk sırada takılırdı.
        for (bildirim in anlik) {
            val durum = guvenle(listOf(bildirim)) { gonder(listOf(bildirim), kurallar) }
            isaretle(listOf(bildirim), durum)
        }
        for (grup in pencereli.groupBy { it.alici }.values) {
            val durum = guvenle(grup) { gonder(grup, kurallar) }
            isaretle(grup, durum)
        }
    }

    private suspend fun guvenle(grup: List<Bekleyen>, blok: suspend () -> Sonuc): Sonuc = try {
        blok()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        log.warn("Push hazırlanamadı: ${grup.map { it.id }}", e)
        Sonuc.Durum(PushDurumu.ATLANDI)
    }

    /** Tek bildirim ya da aynı alıcının pencereli bildirimleri; birden çoksa tek özet push gider. */
    private suspend fun gonder(grup: List<Bekleyen>, kurallar: Kurallar): Sonuc {
        val gonderici = gonderici ?: return Sonuc.Durum(PushDurumu.ATLANDI)
        val plan = withContext(Dispatchers.IO) { transaction(veritabani) { planla(grup, kurallar) } }
        val push = when (plan) {
            is Plan.Gonder -> plan
            is Plan.Bekle -> return Sonuc.Bekle(plan.zaman)
            Plan.Atla -> return Sonuc.Durum(PushDurumu.ATLANDI)
        }
        val gonderildi = push.tokenlar.count { token ->
            try {
                gonderici.gonder(token, BASLIK, push.metin)
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("Push gönderilemedi", e)
                false
            }
        }
        return if (gonderildi > 0) Sonuc.Gonderildi(push.dahil) else Sonuc.Durum(PushDurumu.ATLANDI)
    }

    private class Bekleyen(val id: UUID, val alici: UUID, val tur: BildirimTuru, val aktor: UUID?)

    /** Pencereler: (başlangıç saati, süre) çiftleri, yerel saatle; null ise Faz 1 davranışı (anında). */
    private class Kurallar(val sinir: Int, val sessizBaslangic: Int, val sessizBitis: Int, val pencereler: List<Pair<Int, Int>>?)

    private sealed interface Plan {
        /** [dahil]: tercihi açık olup push'a giren bildirimler; grubun kalanı atlanır. */
        class Gonder(val tokenlar: List<String>, val metin: String, val dahil: List<UUID>) : Plan
        class Bekle(val zaman: OffsetDateTime) : Plan
        data object Atla : Plan
    }

    private sealed interface Sonuc {
        class Durum(val durum: PushDurumu) : Sonuc
        class Gonderildi(val dahil: List<UUID>) : Sonuc
        class Bekle(val zaman: OffsetDateTime) : Sonuc
    }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.planla(grup: List<Bekleyen>, k: Kurallar): Plan {
        val alici = grup.first().alici
        val tercihler = pushTercihleri(alici)
        val acik = grup.filter { tercihler[it.tur] != false }
        if (acik.isEmpty()) return Plan.Atla
        val cihazlar = PushCihaziTablosu.selectAll().where { PushCihaziTablosu.kullaniciId eq alici }
            .orderBy(PushCihaziTablosu.guncellenme to SortOrder.DESC).toList()
        if (cihazlar.isEmpty()) return Plan.Atla
        val bolge = runCatching { ZoneId.of(cihazlar.first()[PushCihaziTablosu.saatDilimi]) }.getOrDefault(TURKIYE)
        val yerel = saat.now().toJavaInstant().atZone(bolge)
        val pencereli = k.pencereler != null && grup.all { it.tur in PENCERELI }
        if (pencereli && !pencerede(yerel.hour, k.pencereler!!)) {
            return Plan.Bekle(sonrakiPencere(yerel, k.pencereler).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
        }
        if (sessizMi(yerel.hour, k.sessizBaslangic, k.sessizBitis)) return Plan.Atla
        val gunBasi = yerel.toLocalDate().atStartOfDay(bolge).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
        val bugun = BildirimTablosu.selectAll().where {
            (BildirimTablosu.alici eq alici) and (BildirimTablosu.pushDurumu eq PushDurumu.GONDERILDI.name) and
                (BildirimTablosu.pushZamani greaterEq gunBasi)
        }.count()
        if (bugun >= k.sinir) return Plan.Atla
        val metin = acik.singleOrNull()?.let { b ->
            val aktor = b.aktor?.let { id -> KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq id }.single()[KullaniciTablosu.gorunenAd] }
            metin(b.tur, aktor)
        } ?: ozetMetni(acik.map { it.tur })
        return Plan.Gonder(cihazlar.map { it[PushCihaziTablosu.token] }, metin, acik.map { it.id })
    }

    /**
     * Gönderilen push'a girenlerin ilki gönderildi sayılır (günlük sınıra bir push olarak girer), diğerleri özete katılmış;
     * tercihi kapalı olanlar atlanır.
     */
    private suspend fun isaretle(grup: List<Bekleyen>, sonuc: Sonuc) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val idler = grup.map { it.id }
                fun durumla(hedef: List<UUID>, durum: PushDurumu) {
                    if (hedef.isNotEmpty()) BildirimTablosu.update({ BildirimTablosu.id inList hedef }) { it[pushDurumu] = durum.name }
                }
                when (sonuc) {
                    is Sonuc.Bekle -> BildirimTablosu.update({ BildirimTablosu.id inList idler }) { it[pushSonra] = sonuc.zaman }
                    is Sonuc.Durum -> durumla(idler, sonuc.durum)
                    is Sonuc.Gonderildi -> {
                        BildirimTablosu.update({ BildirimTablosu.id eq sonuc.dahil.first() }) {
                            it[pushDurumu] = PushDurumu.GONDERILDI.name
                            it[pushZamani] = simdiUtc()
                        }
                        durumla(sonuc.dahil.drop(1), PushDurumu.OZETLENDI)
                        durumla(idler - sonuc.dahil.toSet(), PushDurumu.ATLANDI)
                    }
                }
            }
        }
    }

    private fun simdiUtc(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val PARTI = 500
        const val BASLIK = "Sosyal Oyun"
        val log = LoggerFactory.getLogger(PushDagitici::class.java)

        /** Faz 2'de pencere bekleyen, acil olmayan türler (#52); diğerleri anında gider. */
        val PENCERELI = setOf(BildirimTuru.BEGENI, BildirimTuru.YORUM, BildirimTuru.YENI_TAKIPCI)

        fun pencerede(saat: Int, pencereler: List<Pair<Int, Int>>): Boolean =
            pencereler.any { (baslangic, sure) -> saat in baslangic until baslangic + sure }

        /** Pencere dışındaki yerel zamandan sonraki ilk pencerenin başlangıcı (bugün ya da yarın). */
        fun sonrakiPencere(yerel: ZonedDateTime, pencereler: List<Pair<Int, Int>>): ZonedDateTime =
            (0L..1L).flatMap { gun -> pencereler.map { (baslangic, _) -> yerel.toLocalDate().plusDays(gun).atTime(baslangic, 0).atZone(yerel.zone) } }
                .first { it.isAfter(yerel) }

        fun ozetMetni(turler: List<BildirimTuru>): String {
            val sayilar = turler.groupingBy { it }.eachCount()
            val parcalar = PENCERELI.mapNotNull { tur ->
                val sayi = sayilar[tur] ?: return@mapNotNull null
                when (tur) {
                    BildirimTuru.BEGENI -> "$sayi beğeni"
                    BildirimTuru.YORUM -> "$sayi yorum"
                    else -> "$sayi yeni takipçi"
                }
            }
            return "Yeni bildirimlerin var: ${parcalar.joinToString(", ")}."
        }

        /** Gece yarısını geçen aralık (23–9) da, gün içindeki aralık da desteklenir. */
        fun sessizMi(saat: Int, baslangic: Int, bitis: Int): Boolean =
            if (baslangic <= bitis) saat in baslangic until bitis else saat >= baslangic || saat < bitis

        /** Kullanıcılar Türkiye'de (ADR 0003); push metinleri Türkçe. Davet kodunu kullanan henüz ad seçmemiş olabilir. */
        fun metin(tur: BildirimTuru, aktor: String?): String {
            val kim = aktor ?: "Biri"
            return when (tur) {
                BildirimTuru.YENI_TAKIPCI -> "$kim seni takip etmeye başladı."
                BildirimTuru.ARKADASLIK_ISTEGI -> "$kim sana arkadaşlık isteği gönderdi."
                BildirimTuru.ARKADASLIK_KABULU -> "$kim arkadaşlık isteğini kabul etti."
                BildirimTuru.BEGENI -> "$kim günlük kaydını beğendi."
                BildirimTuru.DAVET_KODU_KULLANILDI -> "Davet kodun kullanıldı${aktor?.let { ": $it aramıza katıldı." } ?: "."}"
                BildirimTuru.SIKAYET_SONUCLANDI -> "Şikayetin incelendi. Bildirdiğin için teşekkürler."
                BildirimTuru.ICERIK_KALDIRILDI -> "Bir içeriğin topluluk kurallarına aykırı bulunduğu için kaldırıldı."
                BildirimTuru.YORUM -> "$kim günlük kaydına yorum yaptı."
                BildirimTuru.TAKIP_ISTEGI -> "$kim seni takip etmek istiyor."
                BildirimTuru.TAKIP_ISTEGI_KABULU -> "$kim takip isteğini kabul etti."
                BildirimTuru.KURATOR_ONAYLANDI -> "Küratör başvurun onaylandı. Listelerin artık keşifte."
                BildirimTuru.KURATOR_REDDEDILDI -> "Küratör başvurun bu sefer onaylanmadı."
                BildirimTuru.UYARI -> "Bir içeriğin topluluk kurallarına aykırı bulundu; bu bir uyarıdır."
                BildirimTuru.YORUM_YASAGI -> "Topluluk kuralları nedeniyle bir süre yorum yazamayacaksın."
                BildirimTuru.ITIRAZ_KABUL_EDILDI -> "İtirazın kabul edildi; yaptırım kaldırıldı."
                BildirimTuru.ITIRAZ_REDDEDILDI -> "İtirazın incelendi ve reddedildi."
            }
        }
    }
}
