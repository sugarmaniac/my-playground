package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import java.util.UUID

/** Faz 3 (#56): kişisel öneriler ve "ilgilenmiyorum". */
fun Route.oneriRotalari(oneriler: Oneriler, katalog: Katalog, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_3) {
        oturumGerekli {
            get(ApiYollari.ONERILER) { call.respond(oneriler.oneriler(call.oturumSahibi.kullaniciId)) }
            put(ApiYollari.ilgilenmiyorum("{id}")) {
                val oyun = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (oyun == null || !katalog.varMi(oyun)) return@put call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                oneriler.gizle(call.oturumSahibi.kullaniciId, oyun)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
