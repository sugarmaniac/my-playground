package com.sosyaloyun.server

import com.sosyaloyun.shared.Magaza
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** IGDB API'si (api.igdb.com/v4), Twitch istemci kimlik bilgileriyle; saniyede en fazla 4 istek (ADR 0001). */
class IgdbKatalogKaynagi(
    private val istemciKimligi: String,
    private val istemciSirri: String,
    private val http: HttpClient = HttpClient(CIO),
    private val simdi: () -> Long = System::currentTimeMillis,
) : KatalogKaynagi {
    private val json = Json { ignoreUnknownKeys = true }
    private val sira = Mutex()
    private var sonIstek = Long.MIN_VALUE / 2
    private var token: String? = null
    private var tokenBitisi = 0L

    override suspend fun oyunSayfasi(sonKaynakId: Long, adet: Int): List<KaynakOyun> =
        oyunSorgusu("where id > $sonKaynakId; sort id asc; limit $adet;")

    override suspend fun oyunlar(kaynakIdleri: List<Long>): List<KaynakOyun> =
        if (kaynakIdleri.isEmpty()) emptyList() else oyunSorgusu("where id = (${kaynakIdleri.joinToString(",")}); limit ${kaynakIdleri.size};")

    /** PopScore: IGDB'nin ziyaret sayısına dayalı popülerlik değeri (`popularity_type = 1`). */
    override suspend fun populerlikSayfasi(sonKaynakId: Long, adet: Int): List<KaynakPopulerlik> =
        sorgula(
            "popularity_primitives",
            "fields game_id,value; where popularity_type = 1 & game_id > $sonKaynakId; sort game_id asc; limit $adet;",
            ListSerializer(IgdbPopulerlik.serializer()),
        ).map { KaynakPopulerlik(it.gameId, it.value) }

    override suspend fun enPopulerler(adet: Int): List<KaynakPopulerlik> =
        (0 until adet step SAYFA_SINIRI).flatMap { ofset ->
            sorgula(
                "popularity_primitives",
                "fields game_id,value; where popularity_type = 1; sort value desc; limit ${minOf(SAYFA_SINIRI, adet - ofset)}; offset $ofset;",
                ListSerializer(IgdbPopulerlik.serializer()),
            ).map { KaynakPopulerlik(it.gameId, it.value) }
        }

    private suspend fun oyunSorgusu(kosul: String): List<KaynakOyun> =
        sorgula("games", "fields $OYUN_ALANLARI; $kosul", ListSerializer(IgdbOyun.serializer())).mapNotNull { it.kaynakOyun() }

    private suspend fun <T> sorgula(ucNokta: String, govde: String, cozumleyici: kotlinx.serialization.KSerializer<T>): T {
        var yanit = istekGonder(ucNokta, govde)
        if (yanit.status == HttpStatusCode.Unauthorized) {
            token = null
            yanit = istekGonder(ucNokta, govde)
        }
        check(yanit.status.isSuccess()) { "IGDB $ucNokta isteği başarısız: ${yanit.status} ${yanit.bodyAsText().take(300)}" }
        return json.decodeFromString(cozumleyici, yanit.bodyAsText())
    }

    private suspend fun istekGonder(ucNokta: String, govde: String): HttpResponse {
        val erisim = gecerliToken()
        sinirGozet()
        return http.post("https://api.igdb.com/v4/$ucNokta") {
            header("Client-ID", istemciKimligi)
            header(HttpHeaders.Authorization, "Bearer $erisim")
            contentType(ContentType.Text.Plain)
            setBody(govde)
        }
    }

    /** İstekler en az 250 ms arayla gider: IGDB saniyede 4 istekten fazlasını reddeder. */
    private suspend fun sinirGozet() {
        sira.withLock {
            val bekle = sonIstek + ISTEK_ARALIGI_MS - simdi()
            if (bekle > 0) delay(bekle)
            sonIstek = simdi()
        }
    }

    private suspend fun gecerliToken(): String {
        token?.takeIf { simdi() < tokenBitisi }?.let { return it }
        val yanit = http.post("https://id.twitch.tv/oauth2/token") {
            parameter("client_id", istemciKimligi)
            parameter("client_secret", istemciSirri)
            parameter("grant_type", "client_credentials")
        }
        check(yanit.status.isSuccess()) { "Twitch token alınamadı: ${yanit.status}" }
        val yeni = json.decodeFromString(TwitchToken.serializer(), yanit.bodyAsText())
        token = yeni.accessToken
        tokenBitisi = simdi() + (yeni.expiresIn - 60) * 1000
        return yeni.accessToken
    }

    companion object {
        private val log = LoggerFactory.getLogger(IgdbKatalogKaynagi::class.java)
        private const val ISTEK_ARALIGI_MS = 250L
        private const val OYUN_ALANLARI =
            "name,game_type,category,parent_game,version_parent,first_release_date,cover.image_id,platforms.name,summary," +
                "external_games.uid,external_games.category,external_games.external_game_source," +
                "slug,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher," +
                "websites.url,websites.type,websites.category"

        /** `IGDB_ISTEMCI_KIMLIGI` ve `IGDB_ISTEMCI_SIRRI` (Twitch geliştirici uygulaması); yoksa katalog içe aktarılmaz. */
        fun ortamdan(): IgdbKatalogKaynagi? {
            val kimlik = System.getenv("IGDB_ISTEMCI_KIMLIGI").orEmpty()
            val sir = System.getenv("IGDB_ISTEMCI_SIRRI").orEmpty()
            if (kimlik.isBlank() || sir.isBlank()) {
                log.warn("IGDB_ISTEMCI_KIMLIGI/IGDB_ISTEMCI_SIRRI tanımlı değil; oyun kataloğu içe aktarılmayacak.")
                return null
            }
            return IgdbKatalogKaynagi(kimlik, sir)
        }
    }
}

/** IGDB'nin harici oyun kaynakları arasında Steam'in kimliği. */
private const val IGDB_STEAM_KAYNAGI = 1

@Serializable
private class TwitchToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private class IgdbPopulerlik(@SerialName("game_id") val gameId: Long, val value: Double)

@Serializable
private class IgdbOyun(
    val id: Long,
    val name: String? = null,
    @SerialName("game_type") val gameType: Int? = null,
    /** `game_type`'tan önceki, kullanımdan kalkmış alan; aynı numaralandırma. */
    val category: Int? = null,
    @SerialName("parent_game") val parentGame: Long? = null,
    @SerialName("version_parent") val versionParent: Long? = null,
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    val cover: IgdbKapak? = null,
    val platforms: List<IgdbPlatform> = emptyList(),
    val summary: String? = null,
    @SerialName("external_games") val externalGames: List<IgdbHariciOyun> = emptyList(),
    val slug: String? = null,
    val genres: List<IgdbAdli> = emptyList(),
    @SerialName("involved_companies") val involvedCompanies: List<IgdbKatilanSirket> = emptyList(),
    val websites: List<IgdbWebSitesi> = emptyList(),
) {
    fun kaynakOyun(): KaynakOyun? {
        val ad = name ?: return null
        return KaynakOyun(
            kaynakId = id,
            ad = ad,
            tur = turu(gameType ?: category ?: 0),
            anaOyunKaynakId = parentGame,
            surumAnasiKaynakId = versionParent,
            cikisTarihi = firstReleaseDate?.let { LocalDate.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) },
            kapakKimligi = cover?.imageId,
            platformlar = platforms.mapNotNull { it.name },
            steamUygulamaIdleri = externalGames
                .filter { (it.externalGameSource ?: it.category) == IGDB_STEAM_KAYNAGI }
                .mapNotNull { it.uid?.toLongOrNull() },
            ozet = summary,
            slug = slug,
            gelistiriciler = involvedCompanies.filter { it.developer }.mapNotNull { it.company?.name },
            yayincilar = involvedCompanies.filter { it.publisher }.mapNotNull { it.company?.name },
            turler = genres.mapNotNull { it.name },
            magazaLinkleri = websites.mapNotNull { site ->
                val magaza = magazasi(site.type ?: site.category) ?: return@mapNotNull null
                site.url?.let { KaynakMagazaLinki(magaza, it) }
            },
        )
    }

    /** IGDB web sitesi türleri; sosyal medya ve wiki linkleri alınmaz. */
    private fun magazasi(tur: Int?): Magaza? = when (tur) {
        1 -> Magaza.RESMI_SITE
        10, 11 -> Magaza.APP_STORE
        12 -> Magaza.GOOGLE_PLAY
        13 -> Magaza.STEAM
        15 -> Magaza.ITCH
        16 -> Magaza.EPIC
        17 -> Magaza.GOG
        else -> null
    }

    private fun turu(igdbTuru: Int): KaynakTuru = when (igdbTuru) {
        0 -> KaynakTuru.ANA_OYUN
        1 -> KaynakTuru.DLC
        2 -> KaynakTuru.GENISLEME
        3 -> KaynakTuru.PAKET
        4 -> KaynakTuru.BAGIMSIZ_GENISLEME
        5 -> KaynakTuru.MOD
        6 -> KaynakTuru.BOLUM
        7 -> KaynakTuru.SEZON
        8 -> KaynakTuru.REMAKE
        9 -> KaynakTuru.REMASTER
        10 -> KaynakTuru.GENISLETILMIS_OYUN
        11 -> KaynakTuru.PORT
        12 -> KaynakTuru.CATAL
        13 -> KaynakTuru.PAKET
        14 -> KaynakTuru.GUNCELLEME
        else -> KaynakTuru.DIGER
    }
}

@Serializable
private class IgdbAdli(val name: String? = null)

@Serializable
private class IgdbKatilanSirket(val company: IgdbAdli? = null, val developer: Boolean = false, val publisher: Boolean = false)

@Serializable
private class IgdbWebSitesi(val url: String? = null, val type: Int? = null, val category: Int? = null)

@Serializable
private class IgdbKapak(@SerialName("image_id") val imageId: String? = null)

@Serializable
private class IgdbPlatform(val name: String? = null)

@Serializable
private class IgdbHariciOyun(
    val uid: String? = null,
    val category: Int? = null,
    @SerialName("external_game_source") val externalGameSource: Int? = null,
)

/** IGDB bir istekte en fazla 500 kayıt döner. */
private const val SAYFA_SINIRI = 500
