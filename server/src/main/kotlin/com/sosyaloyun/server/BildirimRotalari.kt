package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.CihazKaydiIstegi
import com.sosyaloyun.shared.OkunmamisBildirimSayisi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.bildirimRotalari(bildirimler: Bildirimler) {
    oturumGerekli {
        get(ApiYollari.BILDIRIMLER) { call.respond(bildirimler.liste(call.oturumSahibi.kullaniciId)) }
        get(ApiYollari.OKUNMAMIS_BILDIRIM_SAYISI) {
            call.respond(OkunmamisBildirimSayisi(bildirimler.okunmamisSayisi(call.oturumSahibi.kullaniciId)))
        }
        post(ApiYollari.BILDIRIMLER_OKUNDU) {
            bildirimler.okunduSay(call.oturumSahibi.kullaniciId)
            call.respond(HttpStatusCode.NoContent)
        }
        get(ApiYollari.BILDIRIM_TERCIHLERI) { call.respond(bildirimler.tercihler(call.oturumSahibi.kullaniciId)) }
        put(ApiYollari.BILDIRIM_TERCIHLERI) {
            bildirimler.tercihleriKaydet(call.oturumSahibi.kullaniciId, call.receive<BildirimTercihleri>())
            call.respond(bildirimler.tercihler(call.oturumSahibi.kullaniciId))
        }
        put(ApiYollari.CIHAZ) {
            bildirimler.cihazKaydet(call.oturumSahibi.kullaniciId, call.receive<CihazKaydiIstegi>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
