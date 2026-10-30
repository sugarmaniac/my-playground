package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.RehberEslestirmeIstegi
import com.sosyaloyun.shared.TelefonNumarasi
import com.sosyaloyun.shared.TelefonumIstegi
import com.sosyaloyun.shared.TopluTakipIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/** Rehber (yalnızca hash) ve Steam arkadaşlarından uygulama kullanıcılarını bulma, toplu takip. */
fun Route.arkadasBulmaRotalari(arkadasBulma: ArkadasBulma, kullanicilar: Kullanicilar, sosyalGraf: SosyalGraf) {
    oturumGerekli {
        put(ApiYollari.TELEFONUM) {
            val hash = call.receive<TelefonumIstegi>().hash
            if (!TelefonNumarasi.hashGecerliMi(hash)) return@put call.hata(HttpStatusCode.BadRequest, HataKodu.REHBER_GECERSIZ)
            arkadasBulma.telefonHashiniKaydet(call.oturumSahibi.kullaniciId, hash)
            call.respond(HttpStatusCode.NoContent)
        }
        delete(ApiYollari.TELEFONUM) {
            arkadasBulma.telefonHashiniKaydet(call.oturumSahibi.kullaniciId, null)
            call.respond(HttpStatusCode.NoContent)
        }
        post(ApiYollari.ARKADAS_BUL_REHBER) {
            val hashler = call.receive<RehberEslestirmeIstegi>().hashler
            if (hashler.size > TelefonNumarasi.EN_FAZLA_HASH || !hashler.all(TelefonNumarasi::hashGecerliMi)) {
                return@post call.hata(HttpStatusCode.BadRequest, HataKodu.REHBER_GECERSIZ)
            }
            call.respond(arkadasBulma.rehberden(call.oturumSahibi.kullaniciId, hashler))
        }
        get(ApiYollari.ARKADAS_BUL_STEAM) {
            when (val sonuc = arkadasBulma.steamden(call.oturumSahibi.kullaniciId)) {
                is ArkadasBulma.SteamSonucu.Bulunanlar -> call.respond(sonuc.kisiler)
                ArkadasBulma.SteamSonucu.BagliDegil -> call.hata(HttpStatusCode.NotFound, HataKodu.STEAM_BAGLI_DEGIL)
                ArkadasBulma.SteamSonucu.Gizli -> call.hata(HttpStatusCode.Conflict, HataKodu.STEAM_PROFILI_GIZLI)
                ArkadasBulma.SteamSonucu.Kullanilamiyor -> call.hata(HttpStatusCode.ServiceUnavailable, HataKodu.STEAM_KULLANILAMIYOR)
            }
        }
        /** Bulunamayan, kendisi ya da aralarında engel olan adlar sessizce atlanır. */
        post(ApiYollari.TOPLU_TAKIP) {
            val izleyen = call.oturumSahibi.kullaniciId
            for (ad in call.receive<TopluTakipIstegi>().kullaniciAdlari.distinct().take(TOPLU_TAKIP_SINIRI)) {
                val hedef = kullanicilar.kullaniciAdindan(ad) ?: continue
                if (hedef == izleyen || sosyalGraf.engelliMi(izleyen, hedef)) continue
                sosyalGraf.takipEt(izleyen, hedef)
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private const val TOPLU_TAKIP_SINIRI = 500
