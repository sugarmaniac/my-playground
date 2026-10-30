package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Önerinin nedeni (#56); istemci kendi dilinde cümleye çevirir. */
@Serializable
enum class OneriNedeni {
    /** Puanları benzeyen kullanıcılar yüksek puanladı. */
    @SerialName("benzer_kullanicilar") BENZER_KULLANICILAR,

    /** Sevdiğin bir türde; [Oneri.ayrinti] türün IGDB adı. */
    @SerialName("tur") TUR,

    /** Kullandığın bir platformda popüler; [Oneri.ayrinti] platform adı. */
    @SerialName("platform") PLATFORM,

    @SerialName("populer") POPULER,
}

@Serializable
data class Oneri(val oyun: OyunOzeti, val neden: OneriNedeni, val ayrinti: String? = null)

/** `GET /oneriler` (Faz 3, #56): kütüphanedeki ve "ilgilenmiyorum" denen oyunlar hiç önerilmez. */
@Serializable
data class OneriListesi(val oneriler: List<Oneri>)
