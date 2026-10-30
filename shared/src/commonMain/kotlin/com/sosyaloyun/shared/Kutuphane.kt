package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Kütüphane girişinin şu anki hali (CONTEXT.md: Durum); giriş durumsuz da olabilir. */
@Serializable
enum class Durum {
    @SerialName("oynuyorum") OYNUYORUM,
    @SerialName("bitirdim") BITIRDIM,
    @SerialName("biraktim") BIRAKTIM,
    @SerialName("beklemede") BEKLEMEDE,
}

/**
 * Kullanıcının bir oyunla ilişkisinin tek ve güncel kaydı (CONTEXT.md: Kütüphane girişi).
 * [puan] yarım yıldız cinsinden: 1 = yarım yıldız, 10 = beş yıldız.
 */
@Serializable
data class KutuphaneGirisi(
    val durum: Durum? = null,
    val puan: Int? = null,
    val begendim: Boolean = false,
    val istekListesinde: Boolean = false,
    /** %100 rozeti; yalnızca Bitirdim durumunda. */
    val yuzdeYuz: Boolean = false,
) {
    /** Hiçbir işaret yoksa giriş tutulmaz. */
    val bos: Boolean get() = durum == null && puan == null && !begendim && !istekListesinde && !yuzdeYuz
}

object KutuphaneKurallari {
    val PUAN_ARALIGI = 1..10

    fun hatasi(giris: KutuphaneGirisi): HataKodu? = when {
        giris.puan != null && giris.puan !in PUAN_ARALIGI -> HataKodu.PUAN_GECERSIZ
        giris.yuzdeYuz && giris.durum != Durum.BITIRDIM -> HataKodu.YUZDE_YUZ_YALNIZCA_BITIRDIM
        else -> null
    }
}
