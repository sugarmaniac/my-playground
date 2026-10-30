package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import java.util.UUID

/** Web sitesinin paylaşım uç noktaları: oturum gerektirmez, salt okunurdur (ADR 0004). */
fun Route.paylasimRotalari(paylasim: Paylasim, kullanicilar: Kullanicilar, webDizini: File?) {
    get(ApiYollari.paylasilanKayit("{id}")) {
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val kayit = id?.let { paylasim.kayit(it) } ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.GUNLUK_KAYDI_BULUNAMADI)
        call.respond(kayit)
    }
    get(ApiYollari.paylasilanProfil("{ad}")) {
        val profil = call.parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }?.let { paylasim.profil(it) }
            ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
        call.respond(profil)
    }
    // Statik web sitesi; test ortamında aynı imajdan sunulur, böylece sayfalar API'ye aynı adresten ulaşır.
    if (webDizini != null) staticFiles("/", webDizini)
}
