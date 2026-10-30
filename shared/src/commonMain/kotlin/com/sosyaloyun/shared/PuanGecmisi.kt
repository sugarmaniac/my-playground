package com.sosyaloyun.shared

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Bir haftanın (pazartesi, Türkiye saati) anlık görüntüsü; puan veren eşiğin altındaysa [puan] null. */
@Serializable
data class PuanHaftasi(val hafta: LocalDate, val puan: Double?, val puanVeren: Int)

/** `GET /oyunlar/{id}/puan-gecmisi` (Faz 2, #51): son 12 hafta, eskiden yeniye. */
@Serializable
data class PuanGecmisi(val haftalar: List<PuanHaftasi>)

@Serializable
data class PuanDegisimi(val oyun: OyunOzeti, val onceki: Double, val simdiki: Double) {
    val fark: Double get() = simdiki - onceki
}

/** `GET /kesif/puan-degisimi` (Faz 2, #51): son iki anlık görüntü arasında puanı en çok artan ve azalan oyunlar. */
@Serializable
data class HaftalikPuanDegisimi(val artanlar: List<PuanDegisimi>, val azalanlar: List<PuanDegisimi>)
