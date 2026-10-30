package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bir günlük kaydını kimin görebileceği (CONTEXT.md: Görünürlük). */
@Serializable
enum class Gorunurluk {
    @SerialName("herkes") HERKES,
    @SerialName("takipciler") TAKIPCILER,
    @SerialName("arkadaslar") ARKADASLAR,
    @SerialName("ben") BEN,
}

/** Detaylı kayıt ekranının gönderdiği alanlar; puan buradan değil, kütüphane girişinden kopyalanır. */
@Serializable
data class GunlukKaydiIstegi(
    val platform: String? = null,
    val baslama: LocalDate? = null,
    val bitis: LocalDate? = null,
    val sureDakika: Int? = null,
    val inceleme: String? = null,
    val spoiler: Boolean = false,
    val gorunurluk: Gorunurluk = Gorunurluk.HERKES,
    /** Akışa düşmeden, sessizce girilen kayıt. */
    val sessiz: Boolean = false,
)

/** Bir oyunun belirli bir zamanda oynandığını anlatan tarihli olay (CONTEXT.md: Günlük kaydı). */
@Serializable
data class GunlukKaydi(
    val id: String,
    val oyunId: String,
    val platform: String?,
    val baslama: LocalDate?,
    val bitis: LocalDate?,
    val sureDakika: Int?,
    /** Kaydın girildiği andaki puanın kopyası; kütüphane girişinin puanı sonradan değişse de korunur. */
    val puan: Int?,
    val inceleme: String?,
    val spoiler: Boolean,
    val gorunurluk: Gorunurluk,
    val sessiz: Boolean,
    val olusturulma: Instant,
)

object GunlukKurallari {
    const val INCELEME_EN_FAZLA = 10_000
    const val PLATFORM_EN_FAZLA = 100
    /** 1000 saat. */
    const val SURE_EN_FAZLA_DAKIKA = 60_000

    fun hatasi(istek: GunlukKaydiIstegi): HataKodu? = when {
        istek.baslama != null && istek.bitis != null && istek.bitis < istek.baslama -> HataKodu.TARIH_GECERSIZ
        istek.sureDakika != null && istek.sureDakika !in 0..SURE_EN_FAZLA_DAKIKA -> HataKodu.SURE_GECERSIZ
        (istek.inceleme?.length ?: 0) > INCELEME_EN_FAZLA -> HataKodu.INCELEME_COK_UZUN
        istek.platform != null && (istek.platform.isBlank() || istek.platform.length > PLATFORM_EN_FAZLA) -> HataKodu.PLATFORM_GECERSIZ
        else -> null
    }
}
