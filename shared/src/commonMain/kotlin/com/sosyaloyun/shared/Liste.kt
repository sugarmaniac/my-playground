package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Liste görünürlüğü (Faz 2, #49): herkese açık ya da yalnızca sahibine. */
@Serializable
enum class ListeGorunurlugu { @SerialName("herkes") HERKES, @SerialName("ben") BEN }

/** `POST /listeler` ve `PUT /listeler/{id}`: oyunlar verilen sırayla kaydedilir; liste baştan yazılır. */
@Serializable
data class ListeIstegi(
    val baslik: String,
    val aciklama: String? = null,
    val gorunurluk: ListeGorunurlugu = ListeGorunurlugu.HERKES,
    val oyunIdleri: List<String> = emptyList(),
)

/** Listenin tamamı; [benim]: isteyen listenin sahibi. */
@Serializable
data class Liste(
    val id: String,
    val sahip: KullaniciOzeti,
    val baslik: String,
    val aciklama: String?,
    val gorunurluk: ListeGorunurlugu,
    val oyunlar: List<OyunOzeti>,
    val begeniSayisi: Int,
    val begendim: Boolean,
    val benim: Boolean,
    val guncellenme: Instant,
)

/** Profilde, oyun detayında ve keşifte listelenen özet: ilk dört oyunun kapağıyla. */
@Serializable
data class ListeOzeti(
    val id: String,
    val sahip: KullaniciOzeti,
    val baslik: String,
    val gorunurluk: ListeGorunurlugu,
    val oyunSayisi: Int,
    val onIzleme: List<OyunOzeti>,
    val begeniSayisi: Int,
)

@Serializable
data class ListeSayfasi(val listeler: List<ListeOzeti>, val sonrakiImlec: String?)

object ListeKurallari {
    const val BASLIK_EN_FAZLA = 100
    const val ACIKLAMA_EN_FAZLA = 1000
    const val OYUN_EN_FAZLA = 100
}
