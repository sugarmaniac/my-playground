package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.GirisYaniti
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import java.util.UUID

private const val OTURUM = "oturum"

/** Kimliği doğrulanmış isteğin sahibi. */
data class OturumSahibi(val kullaniciId: UUID, val token: String)

fun Application.oturumDogrulamasiKur(oturumlar: Oturumlar, aktiflik: Aktiflik) {
    install(Authentication) {
        register(OturumDogrulamasi(oturumlar, aktiflik))
    }
}

/** `Authorization: Bearer <oturum token'ı>`; geçersizse 401 ve `oturum_gerekli` gövdesi. */
private class OturumDogrulamasi(private val oturumlar: Oturumlar, private val aktiflik: Aktiflik) :
    AuthenticationProvider(object : AuthenticationProvider.Config(OTURUM) {}) {
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val baslik = context.call.request.headers[HttpHeaders.Authorization]
        val token = baslik?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
        val sahibi = token?.let { oturumlar.sahibi(it) }?.let { OturumSahibi(it, token) }
        if (sahibi != null) {
            // Doğrulama metrikleri için kullanıcının bugün aktif olduğu yazılır (günde bir kez).
            aktiflik.kaydet(sahibi.kullaniciId)
            context.principal(sahibi)
            return
        }
        context.challenge(OTURUM, AuthenticationFailedCause.InvalidCredentials) { meydanOkuma, call ->
            call.respond(HttpStatusCode.Unauthorized, ApiHatasi(HataKodu.OTURUM_GEREKLI))
            meydanOkuma.complete()
        }
    }
}

/** İçindeki uç noktalar geçerli bir oturum token'ı ister; yoksa 401 `oturum_gerekli` döner. */
fun Route.oturumGerekli(govde: Route.() -> Unit) {
    authenticate(OTURUM, strategy = AuthenticationStrategy.Required, build = govde)
}

val RoutingCall.oturumSahibi: OturumSahibi
    get() = principal<OturumSahibi>()!!

fun Route.kimlikRotalari(
    kimlikDogrulayici: KimlikDogrulayici,
    kullanicilar: Kullanicilar,
    oturumlar: Oturumlar,
    moderasyon: Moderasyon,
    itirazlar: Itirazlar,
    fazlar: Fazlar,
) {
    post(ApiYollari.OTURUM) {
        val istek = call.receive<GirisIstegi>()
        val kimlik = kimlikDogrulayici.dogrula(istek.saglayici, istek.kimlikTokeni)
        if (kimlik == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiHatasi(HataKodu.KIMLIK_TOKENI_GECERSIZ))
            return@post
        }
        val kullanici = kullanicilar.bulVeyaOlustur(istek.saglayici, kimlik.haricKimlik)
        // Askıdaki ya da banlı hesap nedeniyle birlikte reddedilir; Faz 2'de giriş ekranından itiraz anahtarı da verilir (#53).
        val yaptirim = moderasyon.yaptirim(kullanici.kullaniciId)
        if (yaptirim != null) {
            val anahtar = if (fazlar.acik(Faz.FAZ_2)) itirazlar.girisAnahtari(kullanici.kullaniciId) else null
            val hata = when (yaptirim) {
                is Yaptirim.Askida -> ApiHatasi(HataKodu.HESAP_ASKIDA, yaptirim.bitis.toInstant().toString(), anahtar)
                Yaptirim.Banli -> ApiHatasi(HataKodu.HESAP_BANLI, itirazAnahtari = anahtar)
            }
            return@post call.respond(HttpStatusCode.Forbidden, hata)
        }
        call.respond(
            GirisYaniti(
                oturumTokeni = oturumlar.ac(kullanici.kullaniciId),
                kullaniciId = kullanici.kullaniciId.toString(),
                yeniKullanici = kullanici.yeni,
            ),
        )
    }

    oturumGerekli {
        delete(ApiYollari.OTURUM) {
            oturumlar.kapat(call.oturumSahibi.token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
