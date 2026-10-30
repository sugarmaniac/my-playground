package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Küratör olmanın şartları (Faz 2, #50); eşikler sunucudaki ayarlardan gelir. */
@Serializable
enum class KuratorSartTuru {
    @SerialName("inceleme") INCELEME,
    @SerialName("takipci") TAKIPCI,
    @SerialName("hesap_yasi_gun") HESAP_YASI_GUN,
}

@Serializable
data class KuratorSarti(val tur: KuratorSartTuru, val gereken: Int, val mevcut: Int) {
    val saglandi: Boolean get() = mevcut >= gereken
}

@Serializable
enum class KuratorBasvuruDurumu { @SerialName("bekliyor") BEKLIYOR, @SerialName("reddedildi") REDDEDILDI }

/** `GET /kurator`: kişinin küratörlük durumu; [basvuru] son başvurunun durumu (onaylanınca [kurator] true olur). */
@Serializable
data class KuratorDurumu(
    val kurator: Boolean,
    val basvuru: KuratorBasvuruDurumu?,
    val sartlar: List<KuratorSarti>,
) {
    val basvurabilir: Boolean get() = !kurator && basvuru != KuratorBasvuruDurumu.BEKLIYOR && sartlar.all { it.saglandi }
}
