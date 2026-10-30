package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get

/** Faz 3 (#55): istatistikler ve yıllık özet; web sitesindeki oturumsuz yıllık özet de fazla birlikte açılır. */
fun Route.istatistikRotalari(istatistik: IstatistikServisi, paylasim: Paylasim, kullanicilar: Kullanicilar, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_3) {
        get(ApiYollari.paylasilanYillikOzet("{ad}")) {
            val sahip = call.parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }
            // Paylaşım kuralları profil sayfasıyla aynı: gizli ya da yaptırımlı hesap paylaşılmaz.
            if (sahip == null || !paylasim.profilPaylasilabilir(sahip)) return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
            val ozet = istatistik.yillikOzet(sahip, istatistik.herkes, call.yil(istatistik) ?: return@get)
                ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
            call.respond(ozet)
        }
        oturumGerekli {
            get(ApiYollari.PROFILIM_ISTATISTIK) {
                val ben = call.oturumSahibi.kullaniciId
                call.respond(istatistik.istatistikler(ben, istatistik.gorus(ben, ben), call.yil(istatistik) ?: return@get))
            }
            get(ApiYollari.PROFILIM_YILLIK_OZET) {
                val ben = call.oturumSahibi.kullaniciId
                call.respond(istatistik.yillikOzet(ben, istatistik.gorus(ben, ben), call.yil(istatistik) ?: return@get)!!)
            }
            get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_ISTATISTIK)) {
                val sahip = call.parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }
                    ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
                val gorus = istatistik.gorus(call.oturumSahibi.kullaniciId, sahip)
                call.respond(istatistik.istatistikler(sahip, gorus, call.yil(istatistik) ?: return@get))
            }
            get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_YILLIK_OZET)) {
                val sahip = call.parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }
                    ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
                val gorus = istatistik.gorus(call.oturumSahibi.kullaniciId, sahip)
                val ozet = istatistik.yillikOzet(sahip, gorus, call.yil(istatistik) ?: return@get)
                    ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
                call.respond(ozet)
            }
        }
    }
}

/** `?yil=` yoksa bu yıl; geçersizse 400 yanıtı verip null döner. */
private suspend fun RoutingCall.yil(istatistik: IstatistikServisi): Int? {
    val ham = request.queryParameters["yil"] ?: return istatistik.buYil()
    val yil = ham.toIntOrNull()?.takeIf { it in 2000..istatistik.buYil() }
    if (yil == null) respond(HttpStatusCode.BadRequest)
    return yil
}
