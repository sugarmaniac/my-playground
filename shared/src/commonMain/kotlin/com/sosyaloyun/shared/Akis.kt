package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Akışa düşen olaylar (CONTEXT.md: Akış); takipler, puanlar, istek listesi ve içe aktarmalar düşmez. */
@Serializable
enum class AkisOlayTuru {
    @SerialName("gunluk_kaydi") GUNLUK_KAYDI,
    @SerialName("oynamaya_basladi") OYNAMAYA_BASLADI,
    @SerialName("arkadas_oldu") ARKADAS_OLDU,
}

@Serializable
enum class AkisSekmesi {
    @SerialName("takip") TAKIP,
    @SerialName("arkadaslar") ARKADASLAR,
}

/** Akıştaki bir kart. Günlük kaydında [oyun] ve [gunlukKaydi], oynamaya başladıda [oyun], arkadaş olduda [arkadas] dolu. */
@Serializable
data class AkisOgesi(
    val id: String,
    val tur: AkisOlayTuru,
    val kisi: KullaniciOzeti,
    val zaman: Instant,
    val oyun: OyunOzeti? = null,
    val gunlukKaydi: GunlukKaydi? = null,
    val arkadas: KullaniciOzeti? = null,
    /** Karttaki oyun izleyenin istek listesinde mi; karttan tek dokunuşla eklenebilir. */
    val istekListemde: Boolean = false,
)

/** `GET /akis`: en yeniden eskiye; [enYeniImlec] yeni olay uyarısı için `/akis/yeni?sonra=` ile kullanılır. */
@Serializable
data class AkisSayfasi(
    val ogeler: List<AkisOgesi>,
    val sonrakiImlec: String?,
    val enYeniImlec: String?,
)

@Serializable
data class YeniOlaySayisi(val sayi: Int)
