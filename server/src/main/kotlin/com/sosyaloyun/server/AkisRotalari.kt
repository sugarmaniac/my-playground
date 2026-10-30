package com.sosyaloyun.server

import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.YeniOlaySayisi
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.akisRotalari(akis: Akis, kutuphane: Kutuphane, katalog: Katalog) {
    oturumGerekli {
        get(ApiYollari.AKIS) {
            call.respond(akis.sayfa(call.oturumSahibi.kullaniciId, call.sekme(), call.request.queryParameters["imlec"]))
        }
        get(ApiYollari.AKIS_YENI) {
            call.respond(YeniOlaySayisi(akis.yeniSayisi(call.oturumSahibi.kullaniciId, call.sekme(), call.request.queryParameters["sonra"])))
        }
        get(ApiYollari.AKIS_ONERILER) {
            call.respond(akis.oneriler(call.oturumSahibi.kullaniciId))
        }
        post(ApiYollari.istekListesi("{id}")) {
            val oyunId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (oyunId == null || !katalog.varMi(oyunId)) {
                call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                return@post
            }
            kutuphane.istekListesineEkle(call.oturumSahibi.kullaniciId, oyunId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun RoutingCall.sekme(): AkisSekmesi =
    if (request.queryParameters["sekme"] == "arkadaslar") AkisSekmesi.ARKADASLAR else AkisSekmesi.TAKIP
