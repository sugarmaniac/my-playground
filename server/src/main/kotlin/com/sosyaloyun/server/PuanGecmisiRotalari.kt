package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Faz 2 (#51): puan geçmişi ve haftalık puan değişimi. Anlık görüntüler fazdan bağımsız zamanlayıcıyla alınır. */
fun Route.puanGecmisiRotalari(puanGecmisleri: PuanGecmisleri, katalog: Katalog, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_2) {
        oturumGerekli {
            get(ApiYollari.puanGecmisi("{id}")) {
                val oyunId = call.kimlik()
                if (oyunId == null || !katalog.varMi(oyunId)) return@get call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                call.respond(puanGecmisleri.gecmis(oyunId))
            }
            get(ApiYollari.PUAN_DEGISIMI) { call.respond(puanGecmisleri.haftalikDegisim()) }
        }
    }
}
