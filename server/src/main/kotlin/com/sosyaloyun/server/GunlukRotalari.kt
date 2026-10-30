package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukKurallari
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Route.gunlukRotalari(gunluk: Gunluk, katalog: Katalog) {
    oturumGerekli {
        post(ApiYollari.oyunGunlugu("{id}")) {
            val oyunId = call.kimlik()
            if (oyunId == null || !katalog.varMi(oyunId)) {
                call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                return@post
            }
            val istek = call.gecerliIstek() ?: return@post
            call.respond(HttpStatusCode.Created, gunluk.gir(call.oturumSahibi.kullaniciId, oyunId, istek))
        }
        put("${ApiYollari.GUNLUK}/{id}") {
            val kayitId = call.kimlik()
            val istek = call.gecerliIstek() ?: return@put
            val kayit = kayitId?.let { gunluk.duzenle(call.oturumSahibi.kullaniciId, it, istek) }
            if (kayit == null) call.hata(HttpStatusCode.NotFound, HataKodu.GUNLUK_KAYDI_BULUNAMADI) else call.respond(kayit)
        }
        delete("${ApiYollari.GUNLUK}/{id}") {
            val silindi = call.kimlik()?.let { gunluk.sil(call.oturumSahibi.kullaniciId, it) } ?: false
            if (silindi) call.respond(HttpStatusCode.NoContent) else call.hata(HttpStatusCode.NotFound, HataKodu.GUNLUK_KAYDI_BULUNAMADI)
        }
    }
}

private fun RoutingCall.kimlik(): UUID? = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** İstek kurallara uymuyorsa 400 yanıtını verip null döner. */
private suspend fun RoutingCall.gecerliIstek(): GunlukKaydiIstegi? {
    val istek = receive<GunlukKaydiIstegi>()
    GunlukKurallari.hatasi(istek)?.let {
        hata(HttpStatusCode.BadRequest, it)
        return null
    }
    return istek
}
