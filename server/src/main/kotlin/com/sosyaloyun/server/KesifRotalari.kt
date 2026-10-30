package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Tur
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.kesifRotalari(kesif: Kesif, katalog: Katalog) {
    oturumGerekli {
        get(ApiYollari.KESIF) { call.respond(kesif.kesif(call.oturumSahibi.kullaniciId)) }
        get(ApiYollari.OYUN_LISTESI) {
            val q = call.request.queryParameters
            val tur = q["tur"]?.let { ad -> Tur.entries.firstOrNull { it.name == ad } }
            call.respond(katalog.listele(tur, q["platform"]?.takeIf { it.isNotBlank() }, q["imlec"]))
        }
    }
}
