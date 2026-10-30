package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Girişin yapılabildiği tek kimlik sağlayıcıları (ADR 0006). */
@Serializable
enum class KimlikSaglayici {
    @SerialName("apple")
    APPLE,

    @SerialName("google")
    GOOGLE,
}

/** `POST /oturum`: sağlayıcının cihazda verdiği kimlik token'ıyla giriş. */
@Serializable
data class GirisIstegi(
    val saglayici: KimlikSaglayici,
    val kimlikTokeni: String,
)

/** Giriş başarılıysa oturum token'ı; sonraki istekler `Authorization: Bearer <oturumTokeni>` taşır. */
@Serializable
data class GirisYaniti(
    val oturumTokeni: String,
    val kullaniciId: String,
    val yeniKullanici: Boolean,
)

/**
 * `GET /ben`: oturumun sahibi. Onboarding sırası buradan çıkar: önce davet kodu
 * ([davetKabulEdildi]), sonra kullanıcı adı ve görünen ad ([profil]).
 */
@Serializable
data class BenYaniti(
    val kullaniciId: String,
    val davetKabulEdildi: Boolean,
    val profil: KullaniciOzeti?,
)
