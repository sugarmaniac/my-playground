package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Faz 3 (#57): oyunları içe aktarılabilen konsol ağları. */
@Serializable
enum class KonsolAgi(val yol: String) {
    @SerialName("xbox") XBOX("xbox"),
    @SerialName("playstation") PLAYSTATION("playstation"),
}

/** [hesap] bağlıysa hesabın görünen adı (gamertag / PSN çevrimiçi kimliği), bağlı değilse null. */
@Serializable
data class KonsolBaglantisi(val ag: KonsolAgi, val hesap: String? = null)

/** `GET /konsollar`: yalnızca sunucuda anahtarı olan ağlar döner; anahtarı olmayan ağ ayarlarda gösterilmez. */
@Serializable
data class KonsolBaglantilari(val baglantilar: List<KonsolBaglantisi>)

/** Kullanıcının yazdığı gamertag ya da PSN çevrimiçi kimliği. */
@Serializable
data class KonsolBaglaIstegi(val hesap: String)

/** Oyunlar Steam'deki kurallarla durumsuz kütüphane girişi olur; katalogla eşleşmeyenler atlanır ve sayılır. */
@Serializable
data class KonsolIceAktarmaSonucu(val baglanti: KonsolBaglantisi, val iceAktarilanSayisi: Int, val eslesmeyenSayisi: Int)
