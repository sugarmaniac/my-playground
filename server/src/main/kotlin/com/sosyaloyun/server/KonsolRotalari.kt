package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KonsolAgi
import com.sosyaloyun.shared.KonsolBaglaIstegi
import com.sosyaloyun.shared.KonsolBaglantilari
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

/** Faz 3 (#57): Xbox ve PlayStation bağlama, içe aktarma ve kaldırma. */
fun Route.konsolRotalari(konsollar: Konsollar, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_3) {
        oturumGerekli {
            get(ApiYollari.KONSOLLAR) { call.respond(KonsolBaglantilari(konsollar.baglantilar(call.oturumSahibi.kullaniciId))) }
            put(ApiYollari.konsol("{ag}")) {
                val ag = call.ag() ?: return@put call.respond(HttpStatusCode.NotFound)
                when (val sonuc = konsollar.bagla(call.oturumSahibi.kullaniciId, ag, call.receive<KonsolBaglaIstegi>().hesap)) {
                    is Konsollar.Sonuc.Basarili -> call.respond(sonuc.sonuc)
                    is Konsollar.Sonuc.Hata -> call.hata(
                        when (sonuc.kod) {
                            HataKodu.KONSOL_HESABI_BULUNAMADI -> HttpStatusCode.NotFound
                            HataKodu.KONSOL_KULLANILAMIYOR -> HttpStatusCode.ServiceUnavailable
                            else -> HttpStatusCode.Conflict
                        },
                        sonuc.kod,
                    )
                }
            }
            delete(ApiYollari.konsol("{ag}")) {
                val ag = call.ag() ?: return@delete call.respond(HttpStatusCode.NotFound)
                konsollar.kaldir(call.oturumSahibi.kullaniciId, ag)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun RoutingCall.ag(): KonsolAgi? = parameters["ag"]?.let { yol -> KonsolAgi.entries.firstOrNull { it.yol == yol } }
