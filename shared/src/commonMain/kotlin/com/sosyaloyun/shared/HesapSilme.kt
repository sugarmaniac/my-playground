package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/**
 * `DELETE /ben`: hesabı ve ona bağlı her şeyi anında ve kalıcı olarak siler (ADR 0008). Geri alınamayacağı için
 * [onay] kullanıcının elle yazdığı kullanıcı adıdır; henüz kullanıcı adı yoksa [HesapSilme.PROFILSIZ_ONAY].
 */
@Serializable
data class HesapSilmeIstegi(val onay: String)

object HesapSilme {
    const val PROFILSIZ_ONAY = "sil"

    /** Onay metni büyük/küçük harften ve baştaki/sondaki boşluktan bağımsızdır. */
    fun onaylandiMi(onay: String, kullaniciAdi: String?): Boolean =
        onay.trim().lowercase() == (kullaniciAdi ?: PROFILSIZ_ONAY).lowercase()
}
