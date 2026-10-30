package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/** Oyunun türü (IGDB "genre"). Katalogda IGDB'nin İngilizce adıyla saklanır; arayüz kendi diline çevirir. */
@Serializable
enum class Tur(val igdbAdi: String) {
    MACERA("Adventure"),
    ROL_YAPMA("Role-playing (RPG)"),
    NISANCI("Shooter"),
    STRATEJI("Strategy"),
    GERCEK_ZAMANLI_STRATEJI("Real Time Strategy (RTS)"),
    SIRA_TABANLI_STRATEJI("Turn-based strategy (TBS)"),
    TAKTIK("Tactical"),
    SIMULASYON("Simulator"),
    SPOR("Sport"),
    YARIS("Racing"),
    DOVUS("Fighting"),
    PLATFORM("Platform"),
    BULMACA("Puzzle"),
    KES_BICLE("Hack and slash/Beat 'em up"),
    ARCADE("Arcade"),
    BAGIMSIZ("Indie"),
    GORSEL_ROMAN("Visual Novel"),
    KART_VE_KUTU("Card & Board Game"),
    MOBA("MOBA"),
    MUZIK("Music"),
    TIKLA_VE_KESFET("Point-and-click"),
    BILGI_YARISMASI("Quiz/Trivia"),
    PINBALL("Pinball"),
    ;

    companion object {
        fun igdbAdindan(ad: String): Tur? = entries.firstOrNull { it.igdbAdi == ad }
    }
}

/** Keşifteki platform kısayolları, IGDB platform adlarıyla. */
val KESIF_PLATFORMLARI = listOf(
    "PC (Microsoft Windows)",
    "PlayStation 5",
    "PlayStation 4",
    "Xbox Series X|S",
    "Xbox One",
    "Nintendo Switch",
    "iOS",
    "Android",
)

/** Takip edilenler arasında bu hafta kaydedilen bir oyun ve onu kaydeden takip edilen kişi sayısı. */
@Serializable
data class PopulerOyun(val oyun: OyunOzeti, val kaydedenSayisi: Int)

/** `GET /kesif`: üç blok. */
@Serializable
data class Kesif(
    val takipEdilenlerde: List<PopulerOyun>,
    /** IGDB PopScore'a göre. */
    val trend: List<OyunOzeti>,
    val turler: List<Tur>,
    val platformlar: List<String>,
)
