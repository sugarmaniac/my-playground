package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Günlük kaydına yazılan yorum (Faz 2, #47). [benim]: isteyen yorumun yazarı. [silebilirim]: isteyen yazar ya da
 * kaydın sahibi.
 */
@Serializable
data class Yorum(
    val id: String,
    val kisi: KullaniciOzeti,
    val metin: String,
    val zaman: Instant,
    val silebilirim: Boolean,
    val benim: Boolean = false,
)

/** Eskiden yeniye; [sonrakiImlec] daha yeni yorumların sayfası. */
@Serializable
data class YorumListesi(val yorumlar: List<Yorum>, val sonrakiImlec: String?)

/** `POST /gunluk/{id}/yorumlar`. */
@Serializable
data class YorumIstegi(val metin: String)

object YorumKurallari {
    const val EN_FAZLA = 500
}
