package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.GirisItirazIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.ItirazIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Faz 2 (#53): itirazlar. Giriş ekranından itiraz oturumsuzdur; reddedilen girişin anahtarıyla yapılır. */
fun Route.itirazRotalari(itirazlar: Itirazlar, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_2) {
        post(ApiYollari.ITIRAZ_GIRIS) {
            val istek = call.receive<GirisItirazIstegi>()
            call.sonuc(itirazlar.girisIleItirazEt(istek.anahtar, istek.metin))
        }
        oturumGerekli {
            get(ApiYollari.ITIRAZ) { call.respond(itirazlar.durum(call.oturumSahibi.kullaniciId)) }
            post(ApiYollari.ITIRAZ) {
                val ben = call.oturumSahibi.kullaniciId
                when (itirazlar.itirazEt(ben, call.receive<ItirazIstegi>().metin)) {
                    Itirazlar.Sonuc.Tamam -> call.respond(HttpStatusCode.Created, itirazlar.durum(ben))
                    Itirazlar.Sonuc.Gecersiz -> call.hata(HttpStatusCode.BadRequest, HataKodu.ITIRAZ_GECERSIZ)
                }
            }
        }
    }
}

private suspend fun RoutingCall.sonuc(sonuc: Itirazlar.Sonuc) = when (sonuc) {
    Itirazlar.Sonuc.Tamam -> respond(HttpStatusCode.NoContent)
    Itirazlar.Sonuc.Gecersiz -> hata(HttpStatusCode.BadRequest, HataKodu.ITIRAZ_GECERSIZ)
}
