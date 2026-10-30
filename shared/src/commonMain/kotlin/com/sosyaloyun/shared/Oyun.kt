package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Kaydedilebilir oyun türleri (CONTEXT.md: Oyun). Port'lar ve sürümler ana oyunda birleşir, ayrı oyun değildir. */
@Serializable
enum class OyunTuru {
    @SerialName("ana_oyun") ANA_OYUN,
    @SerialName("remake") REMAKE,
    @SerialName("remaster") REMASTER,
    @SerialName("genisleme") GENISLEME,
    @SerialName("dlc") DLC,
}

/** DLC ve genişlemelerin bağlı olduğu ana oyun. */
@Serializable
data class AnaOyunOzeti(val id: String, val ad: String)

/** Arama sonuçlarında ve listelerde bir oyun. */
@Serializable
data class OyunOzeti(
    val id: String,
    val ad: String,
    val tur: OyunTuru,
    val cikisYili: Int?,
    val kapakUrl: String?,
    val platformlar: List<String>,
    val anaOyun: AnaOyunOzeti?,
)

/** `GET /oyunlar/ara?q=...&imlec=...`; `sonrakiImlec` null ise sonuçlar bitti. */
@Serializable
data class OyunAramaYaniti(
    val oyunlar: List<OyunOzeti>,
    val sonrakiImlec: String?,
)

@Serializable
enum class Magaza {
    @SerialName("resmi_site") RESMI_SITE,
    @SerialName("steam") STEAM,
    @SerialName("epic") EPIC,
    @SerialName("gog") GOG,
    @SerialName("itch") ITCH,
    @SerialName("app_store") APP_STORE,
    @SerialName("google_play") GOOGLE_PLAY,
}

@Serializable
data class MagazaLinki(val magaza: Magaza, val url: String)

/** `GET /oyunlar/{id}`: oyun detay ekranı. Veri IGDB'den; [kaynakUrl] atıf linkidir (ADR 0001). */
@Serializable
data class OyunDetayi(
    val oyun: OyunOzeti,
    val aciklama: String?,
    val gelistiriciler: List<String>,
    val yayincilar: List<String>,
    val turler: List<String>,
    val magazaLinkleri: List<MagazaLinki>,
    /** Ana oyunun DLC'leri ve genişlemeleri, çıkış sırasıyla. */
    val eklentiler: List<OyunOzeti>,
    val kaynakUrl: String,
    /** İsteyen kullanıcının bu oyun için kütüphane girişi; yoksa null. */
    val kutuphaneGirisi: KutuphaneGirisi? = null,
    /** İsteyen kullanıcının bu oyundaki günlük kayıtları, yeniden eskiye. */
    val gunlukKayitlarim: List<GunlukKaydi> = emptyList(),
    /** Günlük kayıtlarındaki sürelerin toplamı (dakika); süre girilmemişse null. */
    val toplamOyunSuresiDakika: Int? = null,
    /** Kullanıcının son günlük kaydında seçtiği platform; detaylı kayıt ekranı bunu önerir. */
    val sonPlatform: String? = null,
    val puanlar: OyunPuanlari = OyunPuanlari(),
)

/**
 * Oyun detayındaki puanlar, yarım yıldız cinsinden (1–10). Kütüphane girişlerindeki güncel puanlardan hesaplanır;
 * her kullanıcı tek oy verir ve görünürlük puanları etkilemez.
 */
@Serializable
data class OyunPuanlari(
    /** Topluluk puanı; puan veren sayısı eşiğin altındaysa null ("Henüz yeterli puan yok"). */
    val toplulukPuani: Double? = null,
    /** Puan veren arkadaşların ortalaması; hiç arkadaş puanlamamışsa null. */
    val arkadasOrtalamasi: Double? = null,
    /** Oyunu oynayan (durumu olan ya da günlük kaydı giren) arkadaş sayısı. */
    val oynayanArkadasSayisi: Int = 0,
)

/** Oyun detayındaki bir inceleme: metni olan bir günlük kaydı ve sahibi. */
@Serializable
data class OyunIncelemesi(val kayit: GunlukKaydi, val sahip: KullaniciOzeti, val arkadas: Boolean)

@Serializable
data class IncelemeListesi(val incelemeler: List<OyunIncelemesi>, val sonrakiImlec: String? = null)

object AramaKurallari {
    const val EN_AZ_KARAKTER = 2
}
