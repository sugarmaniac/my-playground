package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import java.util.UUID

/** Web sitesinin paylaşım uç noktaları: oturum gerektirmez, salt okunurdur (ADR 0004). */
fun Route.paylasimRotalari(paylasim: Paylasim, kullanicilar: Kullanicilar, onizlemeler: Onizlemeler, webDizini: File?) {
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
    if (webDizini != null) {
        // Faz 2'de paylaşım sayfaları içeriğe özel önizleme etiketleriyle sunulur (#54); aksi halde statik dosya.
        for (sayfa in PaylasimSayfasi.entries) {
            get("/${sayfa.dosya}") {
                val html = Onizlemeler.sayfaDosyasi(webDizini, sayfa) ?: return@get call.respond(HttpStatusCode.NotFound)
                val deger = call.request.queryParameters[sayfa.parametre]
                val onizleme = if (onizlemeler.acik()) onizlemeler.onizleme(sayfa, deger) else null
                val adres = "${call.webKoku()}/${sayfa.dosya}?${sayfa.parametre}=${deger.orEmpty().encodeURLParameter()}"
                call.respondText(onizleme?.let { onizlemeler.etiketle(html, it, adres) } ?: html, ContentType.Text.Html)
            }
        }
        get("/robots.txt") {
            val metin = if (onizlemeler.acik()) "User-agent: *\nAllow: /\nSitemap: ${call.webKoku()}/sitemap.xml\n" else "User-agent: *\nDisallow: /\n"
            call.respondText(metin, ContentType.Text.Plain)
        }
        get("/sitemap.xml") {
            if (!onizlemeler.acik()) return@get call.hata(HttpStatusCode.NotFound, HataKodu.OZELLIK_KAPALI)
            val kok = call.webKoku()
            val xml = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                onizlemeler.sitemapYollari().forEach { append("  <url><loc>${"$kok/$it".html()}</loc></url>\n") }
                append("</urlset>\n")
            }
            call.respondText(xml, ContentType.Application.Xml)
        }
        staticFiles("/", webDizini)
    }
}

/** Sitenin dışarıdan görünen kökü; Railway gibi bir vekilin ardında `X-Forwarded-*` başlıklarına göre. */
private fun RoutingCall.webKoku(): String {
    val sema = request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim() ?: request.origin.scheme
    val host = request.headers["X-Forwarded-Host"]?.substringBefore(',')?.trim() ?: request.host().let { h ->
        val port = request.port()
        if (port == 80 || port == 443) h else "$h:$port"
    }
    return "$sema://$host"
}
