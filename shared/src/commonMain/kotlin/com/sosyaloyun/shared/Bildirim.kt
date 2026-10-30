package com.sosyaloyun.shared

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BildirimTuru(
    /** Bu türün ait olduğu faz; faz kapalıyken bu türden bildirimler ve tercihleri görünmez (#46). */
    val faz: Faz = Faz.FAZ_1,
) {
    @SerialName("yeni_takipci") YENI_TAKIPCI,
    @SerialName("arkadaslik_istegi") ARKADASLIK_ISTEGI,
    @SerialName("arkadaslik_kabulu") ARKADASLIK_KABULU,
    @SerialName("begeni") BEGENI,
    @SerialName("davet_kodu_kullanildi") DAVET_KODU_KULLANILDI,

    /** Kalıp mesaj: şikayet edene, şikayetinin incelendiği. */
    @SerialName("sikayet_sonuclandi") SIKAYET_SONUCLANDI,

    /** Kalıp mesaj: içeriği kurallara aykırı bulunup kaldırılana. */
    @SerialName("icerik_kaldirildi") ICERIK_KALDIRILDI,

    /** Günlük kaydına yorum yapıldı (#47). */
    @SerialName("yorum") YORUM(Faz.FAZ_2),

    /** Gizli profile takip isteği geldi ve isteğin kabul edildi (#48). */
    @SerialName("takip_istegi") TAKIP_ISTEGI(Faz.FAZ_2),
    @SerialName("takip_istegi_kabulu") TAKIP_ISTEGI_KABULU(Faz.FAZ_2),

    /** Kalıp mesaj: küratör başvurusu sonuçlandı (#50). */
    @SerialName("kurator_onaylandi") KURATOR_ONAYLANDI(Faz.FAZ_2),
    @SerialName("kurator_reddedildi") KURATOR_REDDEDILDI(Faz.FAZ_2),

    /** Kalıp mesajlar (#53): uyarı, yorum yasağı ve itiraz sonucu. */
    @SerialName("uyari") UYARI(Faz.FAZ_2),
    @SerialName("yorum_yasagi") YORUM_YASAGI(Faz.FAZ_2),
    @SerialName("itiraz_kabul_edildi") ITIRAZ_KABUL_EDILDI(Faz.FAZ_2),
    @SerialName("itiraz_reddedildi") ITIRAZ_REDDEDILDI(Faz.FAZ_2),
}

/**
 * Uygulama içi bildirim. Beğeniler günlük kaydı başına gruplanır: [kisi] en son beğenen, [digerSayisi] diğerleri
 * ("Ayşe ve 4 kişi"). Grup, içindeki bildirimlerden biri okunmamışsa okunmamıştır.
 */
@Serializable
data class Bildirim(
    val id: String,
    val tur: BildirimTuru,
    /** Bildirimi doğuran kişi; moderasyon kalıp mesajlarında yok. */
    val kisi: KullaniciOzeti?,
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
