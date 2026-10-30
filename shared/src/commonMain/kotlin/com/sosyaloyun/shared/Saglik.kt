package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Server'ın kullanıcıya gösterilebilecek genel durumu; metni istemci kendi dilinde seçer. */
@Serializable
enum class SunucuDurumu {
    @SerialName("ayakta")
    AYAKTA,

    @SerialName("bakimda")
    BAKIMDA,
}

/** `GET /saglik` yanıtı: server'ın durumu, konuştuğu API sürümü ve server saati. */
@Serializable
data class SaglikYaniti(
    val durum: SunucuDurumu,
    val apiSurumu: Int,
    val sunucuZamani: Instant,
) {
    companion object {
        const val API_SURUMU = 1
    }
}
