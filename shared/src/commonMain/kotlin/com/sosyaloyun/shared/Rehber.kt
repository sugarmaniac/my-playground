package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/**
 * Rehber eşleştirmesi için telefon numarası: cihazda normalleştirilip SHA-256 ile hash'lenir; backend'e yalnızca hash
 * gider ve yalnızca hash saklanır (ADR 0003).
 */
object TelefonNumarasi {
    /** Türkiye numaraları için E.164 biçimi ("+905321234567"); numara değilse null. */
    fun normallestir(ham: String): String? {
        val arti = ham.trimStart().startsWith("+")
        var rakamlar = ham.filter { it.isDigit() }
        when {
            arti -> Unit
            rakamlar.startsWith("00") -> rakamlar = rakamlar.drop(2)
            rakamlar.length == 11 && rakamlar.startsWith("0") -> rakamlar = "90" + rakamlar.drop(1)
            rakamlar.length == 10 && rakamlar.startsWith("5") -> rakamlar = "90$rakamlar"
        }
        return if (rakamlar.length in 8..15) "+$rakamlar" else null
    }

    /** Normalleştirilmiş numaranın SHA-256'sı, küçük harf onaltılık; numara değilse null. */
    fun hash(ham: String): String? = normallestir(ham)?.let { Sha256.hex(it.encodeToByteArray()) }

    private val HASH = Regex("^[0-9a-f]{64}$")

    fun hashGecerliMi(hash: String): Boolean = HASH.matches(hash)

    /** Tek istekte gönderilebilecek en fazla rehber hash'i. */
    const val EN_FAZLA_HASH = 5000
}

/** `PUT /telefonum`: arkadaşların seni rehberden bulabilsin diye kendi numaranın hash'i. */
@Serializable
data class TelefonumIstegi(val hash: String)

/** `POST /arkadas-bul/rehber`: rehberdeki numaraların hash'leri. */
@Serializable
data class RehberEslestirmeIstegi(val hashler: List<String>)

@Serializable
data class BulunanKisi(val kullanici: KullaniciOzeti, val takipEdiyorum: Boolean)

@Serializable
data class BulunanKisiler(val kisiler: List<BulunanKisi>)

/** `POST /takip/toplu`: bulunan kişilerin hepsini birden takip et. */
@Serializable
data class TopluTakipIstegi(val kullaniciAdlari: List<String>)
