package com.sosyaloyun.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Steam Web API'si; gerçek anahtar yoksa sunucu Steam'siz çalışır (`STEAM_KULLANILAMIYOR`). */
interface SteamKaynagi {
    /** Özel profil adının (steamcommunity.com/id/<ad>) SteamID64'ü; yoksa null. */
    suspend fun ozelAdiCoz(ozelAd: String): String?

    /** Hesabın sahip olduğu oyunlar; profil ya da oyun ayrıntıları gizliyse [SteamOyunlari.Gizli]. */
    suspend fun oyunlar(steamId: String): SteamOyunlari
}

sealed interface SteamOyunlari {
    data object Gizli : SteamOyunlari
    data class Acik(val oyunlar: List<SteamOyunu>) : SteamOyunlari
}

/** [dakika]: Steam'in bildirdiği toplam oyun süresi; 0 ise oyun hiç açılmamıştır. */
data class SteamOyunu(val uygulamaId: Long, val dakika: Int)

/** Kullanıcının yazdığı: profil linki, SteamID64 ya da özel profil adı. */
internal sealed interface SteamGirdisi {
    data class Kimlik(val steamId: String) : SteamGirdisi
    data class OzelAd(val ad: String) : SteamGirdisi

    companion object {
        private val STEAM_ID = Regex("^7656119\\d{10}$")
        private val PROFIL_LINKI = Regex("steamcommunity\\.com/profiles/(\\d+)", RegexOption.IGNORE_CASE)
        private val OZEL_AD_LINKI = Regex("steamcommunity\\.com/id/([^/?#]+)", RegexOption.IGNORE_CASE)
        private val OZEL_AD = Regex("^[A-Za-z0-9_-]{2,32}$")

        fun coz(ham: String): SteamGirdisi? {
            val girdi = ham.trim()
            PROFIL_LINKI.find(girdi)?.let { return it.groupValues[1].takeIf(STEAM_ID::matches)?.let(::Kimlik) }
            OZEL_AD_LINKI.find(girdi)?.let { return OzelAd(it.groupValues[1]) }
            if (STEAM_ID.matches(girdi)) return Kimlik(girdi)
            return if (OZEL_AD.matches(girdi)) OzelAd(girdi) else null
        }
    }
}

/** api.steampowered.com; anahtar `STEAM_WEB_API_KEY` ortam değişkeninden. */
class SteamWebApiKaynagi(private val anahtar: String, private val http: HttpClient = HttpClient(CIO)) : SteamKaynagi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun ozelAdiCoz(ozelAd: String): String? {
        val yanit = istek("ISteamUser/ResolveVanityURL/v1/", "vanityurl" to ozelAd)
        val sonuc = json.decodeFromString(VanityYaniti.serializer(), yanit).response
        return sonuc.steamid.takeIf { sonuc.success == 1 }
    }

    override suspend fun oyunlar(steamId: String): SteamOyunlari {
        val yanit = istek(
            "IPlayerService/GetOwnedGames/v1/",
            "steamid" to steamId,
            "include_played_free_games" to "1",
        )
        // Gizli profilde (ya da gizli oyun ayrıntılarında) yanıt boş bir nesnedir; game_count hiç gelmez.
        val sonuc = json.decodeFromString(SahipOlunanOyunlarYaniti.serializer(), yanit).response
        if (sonuc.gameCount == null) return SteamOyunlari.Gizli
        return SteamOyunlari.Acik(sonuc.games.map { SteamOyunu(it.appid, it.playtimeForever) })
    }

    private suspend fun istek(yol: String, vararg parametreler: Pair<String, String>): String {
        val yanit = http.get("https://api.steampowered.com/$yol") {
            parameter("key", anahtar)
            parametreler.forEach { (ad, deger) -> parameter(ad, deger) }
        }
        check(yanit.status.isSuccess()) { "Steam $yol isteği başarısız: ${yanit.status}" }
        return yanit.bodyAsText()
    }

    @Serializable
    private data class VanityYaniti(val response: Sonuc) {
        @Serializable
        data class Sonuc(val success: Int, val steamid: String? = null)
    }

    @Serializable
    private data class SahipOlunanOyunlarYaniti(val response: Sonuc) {
        @Serializable
        data class Sonuc(@SerialName("game_count") val gameCount: Int? = null, val games: List<Oyun> = emptyList())

        @Serializable
        data class Oyun(val appid: Long, @SerialName("playtime_forever") val playtimeForever: Int = 0)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SteamWebApiKaynagi::class.java)

        fun ortamdan(): SteamWebApiKaynagi? {
            val anahtar = System.getenv("STEAM_WEB_API_KEY").orEmpty()
            if (anahtar.isBlank()) {
                log.warn("STEAM_WEB_API_KEY tanımlı değil; Steam bağlama kullanılamayacak.")
                return null
            }
            return SteamWebApiKaynagi(anahtar)
        }
    }
}
