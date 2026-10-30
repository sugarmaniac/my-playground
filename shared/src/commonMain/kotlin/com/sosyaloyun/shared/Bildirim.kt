package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BildirimTuru {
    @SerialName("yeni_takipci") YENI_TAKIPCI,
    @SerialName("arkadaslik_istegi") ARKADASLIK_ISTEGI,
    @SerialName("arkadaslik_kabulu") ARKADASLIK_KABULU,
    @SerialName("begeni") BEGENI,
    @SerialName("davet_kodu_kullanildi") DAVET_KODU_KULLANILDI,
}

/**
 * Uygulama içi bildirim. Beğeniler günlük kaydı başına gruplanır: [kisi] en son beğenen, [digerSayisi] diğerleri
 * ("Ayşe ve 4 kişi"). Grup, içindeki bildirimlerden biri okunmamışsa okunmamıştır.
 */
@Serializable
data class Bildirim(
    val id: String,
    val tur: BildirimTuru,
    val kisi: KullaniciOzeti,
    val zaman: Instant,
    val okundu: Boolean,
    val digerSayisi: Int = 0,
    val gunlukKaydiId: String? = null,
)

@Serializable
data class BildirimListesi(val bildirimler: List<Bildirim>, val okunmamisSayisi: Int)

@Serializable
data class OkunmamisBildirimSayisi(val sayi: Int)

/** Tür başına push tercihi; uygulama içi bildirimler her zaman oluşur. */
@Serializable
data class BildirimTercihleri(val push: Map<BildirimTuru, Boolean>)

@Serializable
enum class CihazPlatformu { @SerialName("android") ANDROID, @SerialName("ios") IOS }

/** `PUT /cihaz`: push token'ı ve sessiz saatler için cihazın IANA saat dilimi ("Europe/Istanbul"). */
@Serializable
data class CihazKaydiIstegi(val platform: CihazPlatformu, val token: String, val saatDilimi: String)
