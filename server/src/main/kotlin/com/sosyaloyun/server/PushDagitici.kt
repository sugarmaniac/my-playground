package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import java.time.ZoneId
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Bekleyen bildirimlerin push'larını gönderir. Push şu durumlarda gitmez, bildirim yalnızca uygulama içinde kalır:
 * kullanıcı o tür için push'u kapatmış, kayıtlı cihazı yok, yerel saatiyle sessiz saatlerde, ya da bugün günlük sınıra
 * ulaşılmış. Sınırlar Ayar'dan gelir. Atlanan push sonradan gönderilmez.
 */
class PushDagitici(
    private val veritabani: Database,
    private val ayarlar: Ayarlar,
    private val gonderici: PushGonderici?,
    private val saat: Clock,
) {
    suspend fun bekleyenleriGonder() {
        val sinir = ayarlar.oku(Ayar.GunlukPushSiniri)
        val sessizBaslangic = ayarlar.oku(Ayar.SessizSaatBaslangici)
        val sessizBitis = ayarlar.oku(Ayar.SessizSaatBitisi)
        val bekleyenler = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                BildirimTablosu.selectAll().where { BildirimTablosu.pushDurumu eq PushDurumu.BEKLIYOR.name }
                    .orderBy(BildirimTablosu.olusturulma to SortOrder.ASC)
                    .limit(PARTI)
                    .map { Bekleyen(it[BildirimTablosu.id], it[BildirimTablosu.alici], BildirimTuru.valueOf(it[BildirimTablosu.tur]), it[BildirimTablosu.aktor]) }
            }
        }
        // Bir bildirimin hatası diğerlerini durdurmaz; hatalı bildirim atlanır, yoksa her turda ilk sırada takılırdı.
        for (bildirim in bekleyenler) {
            val durum = try {
                gonder(bildirim, sinir, sessizBaslangic, sessizBitis)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("Push hazırlanamadı: ${bildirim.id}", e)
                PushDurumu.ATLANDI
            }
            isaretle(bildirim.id, durum)
        }
    }

    private suspend fun gonder(bildirim: Bekleyen, sinir: Int, sessizBaslangic: Int, sessizBitis: Int): PushDurumu {
        val gonderici = gonderici ?: return PushDurumu.ATLANDI
        val plan = withContext(Dispatchers.IO) { transaction(veritabani) { planla(bildirim, sinir, sessizBaslangic, sessizBitis) } }
            ?: return PushDurumu.ATLANDI
        val gonderildi = plan.tokenlar.count { token ->
            try {
                gonderici.gonder(token, BASLIK, plan.metin)
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("Push gönderilemedi", e)
                false
            }
        }
        return if (gonderildi > 0) PushDurumu.GONDERILDI else PushDurumu.ATLANDI
    }

    private class Bekleyen(val id: UUID, val alici: UUID, val tur: BildirimTuru, val aktor: UUID?)

    private class Plan(val tokenlar: List<String>, val metin: String)

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.planla(b: Bekleyen, sinir: Int, sessizBaslangic: Int, sessizBitis: Int): Plan? {
        if (pushTercihleri(b.alici)[b.tur] == false) return null
        val cihazlar = PushCihaziTablosu.selectAll().where { PushCihaziTablosu.kullaniciId eq b.alici }
            .orderBy(PushCihaziTablosu.guncellenme to SortOrder.DESC).toList()
        if (cihazlar.isEmpty()) return null
        val bolge = runCatching { ZoneId.of(cihazlar.first()[PushCihaziTablosu.saatDilimi]) }.getOrDefault(TURKIYE)
        val yerel = saat.now().toJavaInstant().atZone(bolge)
        if (sessizMi(yerel.hour, sessizBaslangic, sessizBitis)) return null
        val gunBasi = yerel.toLocalDate().atStartOfDay(bolge).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
        val bugun = BildirimTablosu.selectAll().where {
            (BildirimTablosu.alici eq b.alici) and (BildirimTablosu.pushDurumu eq PushDurumu.GONDERILDI.name) and
                (BildirimTablosu.pushZamani greaterEq gunBasi)
        }.count()
        if (bugun >= sinir) return null
        val aktor = b.aktor?.let { id -> KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq id }.single()[KullaniciTablosu.gorunenAd] }
        return Plan(cihazlar.map { it[PushCihaziTablosu.token] }, metin(b.tur, aktor))
    }

    private suspend fun isaretle(id: UUID, durum: PushDurumu) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                BildirimTablosu.update({ BildirimTablosu.id eq id }) {
                    it[pushDurumu] = durum.name
                    if (durum == PushDurumu.GONDERILDI) it[pushZamani] = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
                }
            }
        }
    }

    private companion object {
        const val PARTI = 500
        const val BASLIK = "Sosyal Oyun"
        val log = LoggerFactory.getLogger(PushDagitici::class.java)

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
            }
        }
    }
}
