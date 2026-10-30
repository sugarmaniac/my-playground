package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.Tur
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.kesifRotalari(kesif: KesifServisi, katalog: Katalog) {
    oturumGerekli {
        get(ApiYollari.KESIF) { call.respond(kesif.kesif(call.oturumSahibi.kullaniciId)) }
        get(ApiYollari.OYUN_LISTESI) {
            val q = call.request.queryParameters
            val turAdi = q["tur"]
            val tur = turAdi?.let { ad -> Tur.entries.firstOrNull { it.name == ad } }
            // Bilinmeyen tür bütün kataloğu değil, boş liste döndürür.
            if (turAdi != null && tur == null) return@get call.respond(OyunAramaYaniti(emptyList(), null))
            call.respond(katalog.listele(tur, q["platform"]?.takeIf { it.isNotBlank() }, q["imlec"]))
        }
    }
}
