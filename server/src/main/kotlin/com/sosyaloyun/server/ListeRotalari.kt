package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.ListeIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

/** Faz 2 (#49): listeler. Oturumsuz paylaşım uç noktası da fazla birlikte açılır. */
fun Route.listeRotalari(listeler: Listeler, kullanicilar: Kullanicilar, fazlar: Fazlar) {
    fazGerekli(fazlar, Faz.FAZ_2) {
        get(ApiYollari.paylasilanListe("{id}")) {
            val liste = call.kimlik()?.let { listeler.getir(it, izleyen = null) } ?: return@get call.bulunamadi()
            call.respond(liste)
        }
        oturumGerekli {
            post(ApiYollari.LISTELER) { call.sonuc(listeler.olustur(call.oturumSahibi.kullaniciId, call.receive<ListeIstegi>()), HttpStatusCode.Created) }
            get(ApiYollari.liste("{id}")) {
                val liste = call.kimlik()?.let { listeler.getir(it, call.oturumSahibi.kullaniciId) } ?: return@get call.bulunamadi()
                call.respond(liste)
            }
            put(ApiYollari.liste("{id}")) {
                val id = call.kimlik() ?: return@put call.bulunamadi()
                call.sonuc(listeler.guncelle(call.oturumSahibi.kullaniciId, id, call.receive<ListeIstegi>()), HttpStatusCode.OK)
            }
            delete(ApiYollari.liste("{id}")) {
                val silindi = call.kimlik()?.let { listeler.sil(call.oturumSahibi.kullaniciId, it) } ?: false
                if (silindi) call.respond(HttpStatusCode.NoContent) else call.bulunamadi()
            }
            put(ApiYollari.listeBegenisi("{id}")) {
                val tamam = call.kimlik()?.let { listeler.begen(call.oturumSahibi.kullaniciId, it) } ?: false
                if (tamam) call.respond(HttpStatusCode.NoContent) else call.bulunamadi()
            }
            delete(ApiYollari.listeBegenisi("{id}")) {
                val tamam = call.kimlik()?.let { listeler.begeniyiGeriAl(call.oturumSahibi.kullaniciId, it) } ?: false
                if (tamam) call.respond(HttpStatusCode.NoContent) else call.bulunamadi()
            }
            get(ApiYollari.PROFILIM_LISTELER) {
                val ben = call.oturumSahibi.kullaniciId
                call.respond(listeler.kullanicininListeleri(ben, ben, call.request.queryParameters["imlec"]))
            }
            get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_LISTELER)) {
                val sahip = call.parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }
                    ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
                call.respond(listeler.kullanicininListeleri(sahip, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
            }
            get(ApiYollari.oyunListeleri("{id}")) {
                val oyunId = call.kimlik() ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                call.respond(listeler.oyununListeleri(oyunId, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
            }
        }
    }
}

private suspend fun RoutingCall.sonuc(sonuc: Listeler.Sonuc, basari: HttpStatusCode) = when (sonuc) {
    is Listeler.Sonuc.Tamam -> respond(basari, sonuc.liste)
    Listeler.Sonuc.Gecersiz -> hata(HttpStatusCode.BadRequest, HataKodu.LISTE_GECERSIZ)
    Listeler.Sonuc.Bulunamadi -> bulunamadi()
}

private suspend fun RoutingCall.bulunamadi() = hata(HttpStatusCode.NotFound, HataKodu.LISTE_BULUNAMADI)

private fun RoutingCall.kimlik(): UUID? = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
