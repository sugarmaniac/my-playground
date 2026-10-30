package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SikayetSebebi {
    @SerialName("spam") SPAM,
    @SerialName("taciz") TACIZ,
    @SerialName("nefret_soylemi") NEFRET_SOYLEMI,
    @SerialName("uygunsuz_icerik") UYGUNSUZ_ICERIK,
    @SerialName("isaretsiz_spoiler") ISARETSIZ_SPOILER,
    @SerialName("sahte_hesap") SAHTE_HESAP,
    @SerialName("diger") DIGER,
}

/**
 * `POST /sikayetler`: bir günlük kaydını ([gunlukKaydiId]), bir yorumu ([yorumId], Faz 2) ya da profili
 * ([kullaniciAdi]) şikayet eder; yalnızca biri dolu. Şikayet anonimdir: içerik sahibi kimin şikayet ettiğini görmez.
 */
@Serializable
data class SikayetIstegi(
    val sebep: SikayetSebebi,
    val gunlukKaydiId: String? = null,
    val kullaniciAdi: String? = null,
    val aciklama: String? = null,
    val yorumId: String? = null,
)

object SikayetKurallari {
    const val ACIKLAMA_EN_FAZLA = 500
}
