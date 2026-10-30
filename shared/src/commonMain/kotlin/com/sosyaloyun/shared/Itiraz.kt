package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Moderasyon kararıyla kullanıcıya uygulanan yaptırım (#53). */
@Serializable
enum class YaptirimTuru {
    @SerialName("icerik_kaldirma") ICERIK_KALDIRMA,
    @SerialName("aski") ASKI,
    @SerialName("ban") BAN,
    @SerialName("uyari") UYARI,
    @SerialName("yorum_yasagi") YORUM_YASAGI,
}

@Serializable
enum class ItirazSonucu {
    @SerialName("bekliyor") BEKLIYOR,
    @SerialName("kabul_edildi") KABUL_EDILDI,
    @SerialName("reddedildi") REDDEDILDI,
}

/** [bitis]: askı ya da yorum yasağının bittiği an; diğer türlerde null. */
@Serializable
data class YaptirimOzeti(val tur: YaptirimTuru, val zaman: Instant, val bitis: Instant? = null)

/**
 * `GET /itiraz` (Faz 2, #53): kullanıcının son yaptırımı ve ona yaptığı itirazın sonucu. [itiraz] null ise henüz itiraz
 * edilmemiştir; her yaptırıma bir kez itiraz edilebilir.
 */
@Serializable
data class ItirazDurumu(val yaptirim: YaptirimOzeti? = null, val itiraz: ItirazSonucu? = null) {
    val itirazEdilebilir: Boolean get() = yaptirim != null && itiraz == null
}

@Serializable
data class ItirazIstegi(val metin: String)

/** Askıdaki ya da banlı hesabın giriş ekranından itirazı: [anahtar] reddedilen girişin hata yanıtından gelir. */
@Serializable
data class GirisItirazIstegi(val anahtar: String, val metin: String)

object ItirazKurallari {
    const val EN_FAZLA = 1000
}
