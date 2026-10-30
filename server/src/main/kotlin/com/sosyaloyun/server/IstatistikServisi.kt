package com.sosyaloyun.server

import com.sosyaloyun.shared.AdSayisi
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.DurumSayisi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.Istatistikler
import com.sosyaloyun.shared.PuanliOyun
import com.sosyaloyun.shared.YillikOzet
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * İstatistikler ve yıllık özet (Faz 3, #55). Başkasının istatistikleri yalnızca izleyenin görebildiği kayıtlardan
 * hesaplanır: günlük kayıtları görünürlüğe göre süzülür, kütüphane (durum, puan, Steam süresi) profil içeriğini
 * görebilen izleyene açıktır. Yıl sınırları Türkiye saatine göredir.
 */
class IstatistikServisi(
    private val veritabani: Database,
    private val katalog: Katalog,
    private val sosyalGraf: SosyalGraf,
    private val saat: Clock,
) {
    /** [gorunurluk] boşsa izleyen hiçbir içeriği göremez; [kutuphane] kütüphane girişlerini görebilir mi. */
    class Gorus(val gorunurluk: Set<Gorunurluk>, val kutuphane: Boolean)

    suspend fun gorus(izleyen: UUID, sahip: UUID): Gorus {
        val gorunurluk = sosyalGraf.gorebilecegi(izleyen, sahip)
        return Gorus(gorunurluk, gorunurluk.isNotEmpty())
    }

    /** Web sitesindeki oturumsuz özet: yalnızca Herkes görünürlüğündeki kayıtlar ve açık profilin kütüphanesi. */
    val herkes = Gorus(setOf(Gorunurluk.HERKES), kutuphane = true)

    fun buYil(): Int = saat.now().toJavaInstant().atZone(TURKIYE).year

    suspend fun istatistikler(sahip: UUID, gorus: Gorus, yil: Int): Istatistikler {
        val veri = veri(sahip, gorus)
        val turler = katalog.turleri(veri.kutuphane.filter { it.durum != null }.map { it.oyun })
        val bitirilen = bitirilenler(veri, yil)
        return Istatistikler(
            yil = yil,
            durumlar = Durum.entries.map { d -> DurumSayisi(d, veri.kutuphane.count { it.durum == d }) },
            toplamSureDakika = toplamSure(veri, null),
            puanDagilimi = (1..10).map { p -> veri.kutuphane.count { it.puan == p } },
            turler = enCok(turler.values.flatten()),
            platformlar = enCok(veri.kayitlar.mapNotNull { it.platform }),
            aylaraGoreBitirilen = (1..12).map { ay -> bitirilen.values.count { it.monthValue == ay } },
        )
    }

    suspend fun yillikOzet(sahip: UUID, gorus: Gorus, yil: Int): YillikOzet? {
        val kisi = withContext(Dispatchers.IO) {
            transaction(veritabani) { KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq sahip }.singleOrNull()?.kullaniciOzeti() }
        } ?: return null
        val veri = veri(sahip, gorus)
        val bitirilen = bitirilenler(veri, yil)
        val sirali = bitirilen.entries.sortedBy { it.value }.map { it.key }
        val ozetler = katalog.ozetleri(sirali)
        val puanlar = veri.kutuphane.associate { it.oyun to it.puan }
        val turler = katalog.turleri(sirali)
        return YillikOzet(
            yil = yil,
            kullanici = kisi,
            bitirilenler = sirali.mapNotNull(ozetler::get),
            toplamSureDakika = toplamSure(veri, yil),
            enYuksekPuanlilar = sirali.mapNotNull { id -> puanlar[id]?.let { p -> ozetler[id]?.let { PuanliOyun(it, p) } } }
                .sortedByDescending { it.puan }.take(EN_YUKSEK),
            enCokTur = enCok(turler.values.flatten()).firstOrNull()?.ad,
        )
    }

    private class Giris(val oyun: UUID, val durum: Durum?, val puan: Int?, val steamSure: Int?, val durumDegisme: LocalDate?)
    private class Kayit(val oyun: UUID, val platform: String?, val sure: Int?, val tarih: LocalDate, val bitis: LocalDate?)
    private class Veri(val kutuphane: List<Giris>, val kayitlar: List<Kayit>)

    private suspend fun veri(sahip: UUID, gorus: Gorus): Veri = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val k = KutuphaneGirisiTablosu
            val kutuphane = if (!gorus.kutuphane) emptyList() else k.selectAll().where { k.kullaniciId eq sahip }.map {
                Giris(it[k.oyunId], it[k.durum]?.let(Durum::valueOf), it[k.puan]?.toInt(), it[k.steamSureDakika], it[k.durumDegisme]?.atZoneSameInstant(TURKIYE)?.toLocalDate())
            }
            val g = GunlukKaydiTablosu
            val kayitlar = if (gorus.gorunurluk.isEmpty()) emptyList() else
                g.selectAll().where { (g.kullaniciId eq sahip) and (g.gorunurluk inList gorus.gorunurluk.map { it.name }) }
                    .map { Kayit(it[g.oyunId], it[g.platform], it[g.sureDakika], it[g.tarih], it[g.bitis]) }
            Veri(kutuphane, kayitlar)
        }
    }

    /** Yılda bitirilen oyunlar ve bitirme günü: günlük kaydındaki bitiş tarihi, yoksa Bitirdim'e geçtiği gün. */
    private fun bitirilenler(veri: Veri, yil: Int): Map<UUID, LocalDate> {
        val gunler = mutableMapOf<UUID, LocalDate>()
        veri.kayitlar.forEach { k -> k.bitis?.takeIf { it.year == yil }?.let { gun -> gunler.merge(k.oyun, gun, ::minOf) } }
        veri.kutuphane.filter { it.durum == Durum.BITIRDIM }.forEach { g ->
            g.durumDegisme?.takeIf { it.year == yil }?.let { gunler.putIfAbsent(g.oyun, it) }
        }
        return gunler
    }

    /**
     * Toplam süre: oyun başına Steam süresi ve Steam dışı platformlardaki kayıt süreleri (oyun detayındaki toplamla aynı
     * kural). [yil] verilirse yalnızca o yılın kayıtları sayılır; Steam süresi yıla bölünemediği için dahil edilmez.
     */
    private fun toplamSure(veri: Veri, yil: Int?): Int {
        val steam = if (yil == null) veri.kutuphane.associate { it.oyun to it.steamSure } else emptyMap()
        val kayitlar = veri.kayitlar.filter { yil == null || it.tarih.year == yil }
            .filter { steam[it.oyun] == null || !steamPlatformuMu(it.platform) }
        return steam.values.sumOf { it ?: 0 } + kayitlar.sumOf { it.sure ?: 0 }
    }

    private fun enCok(adlar: List<String>): List<AdSayisi> =
        adlar.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(EN_COK).map { AdSayisi(it.key, it.value) }

    private companion object {
        const val EN_COK = 5
        const val EN_YUKSEK = 5
    }
}
