package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ürünün açık olan fazı (#45). Fazlar birikimlidir: Faz 3 açıkken Faz 2'nin özellikleri de açıktır. Ekip yönetim
 * panelinden değiştirir; varsayılan Faz 1 (yalnızca MVP).
 */
@Serializable
enum class Faz(val seviye: Int) {
    @SerialName("faz_1") FAZ_1(1),
    @SerialName("faz_2") FAZ_2(2),
    @SerialName("faz_3") FAZ_3(3);

    /** Bu faz açıkken [gereken] fazın özellikleri de açık mı. */
    fun kapsar(gereken: Faz): Boolean = seviye >= gereken.seviye

    companion object {
        fun seviyeden(seviye: Int): Faz = entries.firstOrNull { it.seviye == seviye } ?: FAZ_1
    }
}

/**
 * `GET /ozellikler`: oturum gerektirmez; web sitesi ve girişten önceki ekranlar kullanır. [webGiris] yalnızca Faz 3
 * açıkken ve sunucuda web istemci ayarları varken dolar; yoksa web sitesinde giriş düğmesi görünmez (#58).
 */
@Serializable
data class OzellikDurumu(val aktifFaz: Faz, val webGiris: WebGirisAyari? = null)

/** Web sitesindeki Google ve Apple girişinin istemci kimlikleri (gizli değildir); boş olan sağlayıcının düğmesi yoktur. */
@Serializable
data class WebGirisAyari(val googleIstemciKimligi: String? = null, val appleServisKimligi: String? = null)
