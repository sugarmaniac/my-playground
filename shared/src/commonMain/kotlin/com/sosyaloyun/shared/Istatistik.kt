package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

@Serializable
data class DurumSayisi(val durum: Durum, val sayi: Int)

/** Tür (IGDB adı) ya da platform ve sayısı. */
@Serializable
data class AdSayisi(val ad: String, val sayi: Int)

/**
 * `GET /profilim/istatistik?yil=` ve `GET /kullanicilar/{ad}/istatistik?yil=` (Faz 3, #55). Başkasının istatistikleri
 * yalnızca izleyenin görebildiği kayıtlardan hesaplanır. [puanDagilimi] 10 eleman (1–10 puan), [aylaraGoreBitirilen]
 * 12 eleman (ocak–aralık, Türkiye saati, [yil] için).
 */
@Serializable
data class Istatistikler(
    val yil: Int,
    val durumlar: List<DurumSayisi>,
    val toplamSureDakika: Int,
    val puanDagilimi: List<Int>,
    val turler: List<AdSayisi>,
    val platformlar: List<AdSayisi>,
    val aylaraGoreBitirilen: List<Int>,
)

@Serializable
data class PuanliOyun(val oyun: OyunOzeti, val puan: Int)

/** Seçilen yılın (Türkiye saati) özeti; web sitesinde `yillik.html` ile paylaşılır (#55). */
@Serializable
data class YillikOzet(
    val yil: Int,
    val kullanici: KullaniciOzeti,
    val bitirilenler: List<OyunOzeti>,
    val toplamSureDakika: Int,
    val enYuksekPuanlilar: List<PuanliOyun>,
    val enCokTur: String?,
)
