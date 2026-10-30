package com.sosyaloyun.server

import com.sosyaloyun.shared.KonsolAgi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
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
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bir konsol ağının oyun geçmişi (#57). Her ağ bir adaptörün arkasındadır; sunucuda anahtar yoksa adaptör yoktur ve
 * ağ ayarlarda gösterilmez.
 */
interface KonsolKaynagi {
    val ag: KonsolAgi

    /** Kullanıcının yazdığı hesap adının (gamertag / PSN çevrimiçi kimliği) kalıcı kimliği ve görünen adı; yoksa null. */
    suspend fun hesapBul(ad: String): KonsolHesabi?

    /** Hesabın oynadığı oyunlar; oyun geçmişi gizliyse [KonsolOyunlari.Gizli]. */
    suspend fun oyunlar(kimlik: String): KonsolOyunlari
}

data class KonsolHesabi(val kimlik: String, val ad: String)

sealed interface KonsolOyunlari {
    data object Gizli : KonsolOyunlari
    data class Acik(val adlar: List<String>) : KonsolOyunlari
}

/** Xbox: OpenXBL (xbl.io); anahtar `OPENXBL_ANAHTARI`. */
class OpenXblKaynagi(private val anahtar: String, private val http: HttpClient = HttpClient(CIO)) : KonsolKaynagi {
    override val ag = KonsolAgi.XBOX
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun hesapBul(ad: String): KonsolHesabi? {
        val yanit = istek("search/${ad.encodeURLPathPart()}")
        if (yanit.status == HttpStatusCode.NotFound) return null
        check(yanit.status.isSuccess()) { "OpenXBL arama başarısız: ${yanit.status}" }
        val kisi = json.decodeFromString(AramaYaniti.serializer(), yanit.bodyAsText()).people.firstOrNull() ?: return null
        return KonsolHesabi(kisi.xuid, kisi.gamertag)
    }

    override suspend fun oyunlar(kimlik: String): KonsolOyunlari {
        val yanit = istek("player/titleHistory/$kimlik")
        // Oyun geçmişi gizli hesaplarda Xbox 403 döner.
        if (yanit.status == HttpStatusCode.Forbidden) return KonsolOyunlari.Gizli
        check(yanit.status.isSuccess()) { "OpenXBL oyun geçmişi başarısız: ${yanit.status}" }
        return KonsolOyunlari.Acik(json.decodeFromString(OyunGecmisi.serializer(), yanit.bodyAsText()).titles.map { it.name })
    }

    private suspend fun istek(yol: String): HttpResponse = http.get("https://xbl.io/api/v2/$yol") {
        header("X-Authorization", anahtar)
        header(HttpHeaders.Accept, "application/json")
    }

    @Serializable
    private data class AramaYaniti(val people: List<Kisi> = emptyList()) {
        @Serializable
        data class Kisi(val xuid: String, val gamertag: String)
    }

    @Serializable
    private data class OyunGecmisi(val titles: List<Baslik> = emptyList()) {
        @Serializable
        data class Baslik(val name: String)
    }

    companion object {
        fun ortamdan(): OpenXblKaynagi? = System.getenv("OPENXBL_ANAHTARI")?.takeIf { it.isNotBlank() }?.let(::OpenXblKaynagi)
    }
}

/**
 * PlayStation: ekibin PSN hesabının NPSSO token'ıyla (`PSN_NPSSO`) PlayStation'ın mobil uygulama uç noktaları. NPSSO
 * erişim token'ına çevrilir; token bir saat geçerlidir, süresi dolunca yenisi alınır.
 */
class PsnKaynagi(private val npsso: String, private val http: HttpClient = HttpClient(CIO)) : KonsolKaynagi {
    override val ag = KonsolAgi.PLAYSTATION
    private val json = Json { ignoreUnknownKeys = true }
    /** Yetkilendirme kodu yönlendirme adresinde döner; yönlendirme izlenmez. */
    private val yonlendirmesiz = http.config { followRedirects = false }
    private val tokenKilidi = Mutex()
    private var token: Pair<String, Long>? = null

    override suspend fun hesapBul(ad: String): KonsolHesabi? {
        val yanit = http.post("https://m.np.playstation.com/api/search/v1/universalSearch") {
            bearerAuth(erisimTokeni())
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AramaIstegi.serializer(), AramaIstegi(ad.trim())))
        }
        check(yanit.status.isSuccess()) { "PSN arama başarısız: ${yanit.status}" }
        val sonuc = json.decodeFromString(AramaYaniti.serializer(), yanit.bodyAsText())
        val hesap = sonuc.domainResponses.flatMap { it.results }.map { it.socialMetadata }
            .firstOrNull { it.onlineId.equals(ad.trim(), ignoreCase = true) } ?: return null
        return KonsolHesabi(hesap.accountId, hesap.onlineId)
    }

    override suspend fun oyunlar(kimlik: String): KonsolOyunlari {
        val adlar = mutableListOf<String>()
        var ofset = 0
        while (true) {
            val yanit = http.get("https://m.np.playstation.com/api/gamelist/v2/users/$kimlik/titles") {
                bearerAuth(erisimTokeni())
                parameter("categories", "ps4_game,ps5_native_game")
                parameter("limit", SAYFA)
                parameter("offset", ofset)
            }
            // Oyun geçmişi gizli hesaplarda PSN 403 döner.
            if (yanit.status == HttpStatusCode.Forbidden) return KonsolOyunlari.Gizli
            check(yanit.status.isSuccess()) { "PSN oyun listesi başarısız: ${yanit.status}" }
            val sayfa = json.decodeFromString(OyunListesi.serializer(), yanit.bodyAsText())
            adlar += sayfa.titles.map { it.name }
            ofset = sayfa.nextOffset ?: break
        }
        return KonsolOyunlari.Acik(adlar)
    }

    /** Aynı anda gelen istekler token'ı bir kez yeniler. */
    private suspend fun erisimTokeni(): String = tokenKilidi.withLock {
        token?.let { (deger, bitis) -> if (System.currentTimeMillis() < bitis) return@withLock deger }
        // NPSSO çerezli yetkilendirme isteği, yönlendirme adresinde bir kod döner; kod erişim token'ına çevrilir.
        val yetki = yonlendirmesiz.get("https://ca.account.sony.com/api/authz/v3/oauth/authorize") {
            header(HttpHeaders.Cookie, "npsso=$npsso")
            parameter("access_type", "offline")
            parameter("client_id", ISTEMCI)
            parameter("redirect_uri", YONLENDIRME)
            parameter("response_type", "code")
            parameter("scope", "psn:mobile.v2.core psn:clientapp")
        }
        val kod = yetki.headers[HttpHeaders.Location]?.let { Regex("[?&]code=([^&]+)").find(it)?.groupValues?.get(1) }
            ?: error("PSN NPSSO geçersiz ya da süresi dolmuş")
        val yanit = http.submitForm(
            "https://ca.account.sony.com/api/authz/v3/oauth/token",
            parameters {
                append("code", kod)
                append("redirect_uri", YONLENDIRME)
                append("grant_type", "authorization_code")
                append("token_format", "jwt")
            },
        ) { header(HttpHeaders.Authorization, "Basic $ISTEMCI_KIMLIK_BILGISI") }
        check(yanit.status.isSuccess()) { "PSN token alınamadı: ${yanit.status}" }
        val sonuc = json.decodeFromString(TokenYaniti.serializer(), yanit.bodyAsText())
        token = sonuc.accessToken to System.currentTimeMillis() + Duration.ofSeconds(sonuc.expiresIn - 60L).toMillis()
        sonuc.accessToken
    }

    @Serializable
    private data class TokenYaniti(@SerialName("access_token") val accessToken: String, @SerialName("expires_in") val expiresIn: Int)

    @Serializable
    private data class AramaIstegi(val searchTerm: String, val domainRequests: List<Alan> = listOf(Alan("SocialAllAccounts"))) {
        @Serializable
        data class Alan(val domain: String)
    }

    @Serializable
    private data class AramaYaniti(val domainResponses: List<Alan> = emptyList()) {
        @Serializable
        data class Alan(val results: List<Sonuc> = emptyList())

        @Serializable
        data class Sonuc(val socialMetadata: Hesap)

        @Serializable
        data class Hesap(val accountId: String, val onlineId: String)
    }

    @Serializable
    private data class OyunListesi(val titles: List<Baslik> = emptyList(), val nextOffset: Int? = null) {
        @Serializable
        data class Baslik(val name: String)
    }

    companion object {
        private const val SAYFA = 200

        /** PlayStation mobil uygulamasının herkese açık istemci kimliği ve kimlik bilgisi (gizli değildir). */
        private const val ISTEMCI = "09515159-7237-4370-9b40-3806e67c0891"
        private const val ISTEMCI_KIMLIK_BILGISI = "MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="
        private const val YONLENDIRME = "com.scee.psxandroid.scecompcall://redirect"

        fun ortamdan(): PsnKaynagi? = System.getenv("PSN_NPSSO")?.takeIf { it.isNotBlank() }?.let(::PsnKaynagi)
    }
}
