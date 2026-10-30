package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.SikayetIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.sikayetRotalari(moderasyon: Moderasyon) {
    oturumGerekli {
        post(ApiYollari.SIKAYETLER) {
            val hata = moderasyon.sikayetEt(call.oturumSahibi.kullaniciId, call.receive<SikayetIstegi>())
            if (hata != null) call.hata(HttpStatusCode.BadRequest, hata) else call.respond(HttpStatusCode.NoContent)
        }
    }
}
