package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/** `POST /davet/kullan`: kapalı betaya katılmak için tek kullanımlık davet kodu. */
@Serializable
data class DavetKoduKullanIstegi(val kod: String)

/** Kullanıcının kendi davet kodlarından biri; kullanıldıysa kimin kullandığı. */
@Serializable
data class DavetKodu(
    val kod: String,
    val kullanan: KullaniciOzeti?,
    val kullanildi: Boolean,
)

@Serializable
data class DavetKodlarimYaniti(val kodlar: List<DavetKodu>)

object DavetKoduKurallari {
    /** Kodlar büyük harfle saklanır; kullanıcı küçük harf veya boşlukla yazsa da eşleşir. */
    fun duzenle(kod: String): String = kod.trim().uppercase().replace(" ", "").replace("-", "")
}
