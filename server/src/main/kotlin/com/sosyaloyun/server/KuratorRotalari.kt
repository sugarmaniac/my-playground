package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Faz 2 (#50): küratörlük durumu, başvuru ve keşifteki küratör listeleri. Kararlar yönetim panelinde. */
fun Route.kuratorRotalari(kuratorler: Kuratorler, fazlar: Fazlar) {
    oturumGerekli {
        fazGerekli(fazlar, Faz.FAZ_2) {
            get(ApiYollari.KURATOR) { call.respond(kuratorler.durum(call.oturumSahibi.kullaniciId)) }
            post(ApiYollari.KURATOR_BASVURU) {
                if (kuratorler.basvur(call.oturumSahibi.kullaniciId)) {
                    call.respond(kuratorler.durum(call.oturumSahibi.kullaniciId))
                } else {
                    call.hata(HttpStatusCode.Conflict, HataKodu.KURATOR_BASVURUSU_GECERSIZ)
                }
            }
            get(ApiYollari.KURATOR_LISTELERI) {
                call.respond(kuratorler.listeler(call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
            }
        }
    }
}
