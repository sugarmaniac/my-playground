package com.sosyaloyun.server

import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.GizlilikIstegi
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.VitrinIstegi
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

/** Kendi profilin (`/profilim`) ve başkasının profili (`/kullanicilar/{ad}`): aynı sekmeler, görünürlüğe göre süzülmüş. */
fun Route.profilSayfasiRotalari(profiller: Profiller, kullanicilar: Kullanicilar, sosyalGraf: SosyalGraf, fazlar: Fazlar) {
    oturumGerekli {
        fazGerekli(fazlar, Faz.FAZ_2) {
            put(ApiYollari.GIZLILIK) {
                sosyalGraf.gizliligiAyarla(call.oturumSahibi.kullaniciId, call.receive<GizlilikIstegi>().gizli)
                call.respond(HttpStatusCode.NoContent)
            }
            get(ApiYollari.TAKIP_ISTEKLERI) {
                call.respond(sosyalGraf.takipIstekleri(call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
            }
            post(ApiYollari.takipIstegiKabul("{ad}")) {
                val isteyen = call.adinSahibi(kullanicilar) ?: return@post
                if (sosyalGraf.takipIsteginiKabulEt(call.oturumSahibi.kullaniciId, isteyen)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.hata(HttpStatusCode.NotFound, HataKodu.TAKIP_ISTEGI_YOK)
                }
            }
            delete(ApiYollari.takipIstegi("{ad}")) {
                val isteyen = call.adinSahibi(kullanicilar) ?: return@delete
                if (sosyalGraf.takipIsteginiReddet(call.oturumSahibi.kullaniciId, isteyen)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.hata(HttpStatusCode.NotFound, HataKodu.TAKIP_ISTEGI_YOK)
                }
            }
        }

        get(ApiYollari.PROFILIM) {
            val sayfa = profiller.sayfa(call.oturumSahibi.kullaniciId)
            if (sayfa == null) call.hata(HttpStatusCode.Forbidden, HataKodu.DAVET_GEREKLI) else call.respond(sayfa)
        }
        put(ApiYollari.PROFILIM_VITRIN) {
            val istek = call.receive<VitrinIstegi>()
            if (profiller.vitriniKaydet(call.oturumSahibi.kullaniciId, istek.oyunIdleri)) {
                call.respond(profiller.sayfa(call.oturumSahibi.kullaniciId) ?: return@put call.hata(HttpStatusCode.Forbidden, HataKodu.DAVET_GEREKLI))
            } else {
                call.hata(HttpStatusCode.BadRequest, HataKodu.VITRIN_GECERSIZ)
            }
        }
        get(ApiYollari.PROFILIM_GUNLUK) { call.gunlukSekmesi(profiller, call.oturumSahibi.kullaniciId, yalnizcaIncelemeler = false) }
        get(ApiYollari.PROFILIM_INCELEMELER) { call.gunlukSekmesi(profiller, call.oturumSahibi.kullaniciId, yalnizcaIncelemeler = true) }
        get(ApiYollari.PROFILIM_OYUNLAR) { call.kutuphaneSekmesi(profiller, call.oturumSahibi.kullaniciId, istekListesi = false) }
        get(ApiYollari.PROFILIM_ISTEK_LISTESI) { call.kutuphaneSekmesi(profiller, call.oturumSahibi.kullaniciId, istekListesi = true) }

        get(ApiYollari.KULLANICI_ARA) {
            call.respond(kullanicilar.ara(call.request.queryParameters["q"].orEmpty(), call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }

        get(ApiYollari.kullanici("{ad}")) {
            val sahip = call.sahip(kullanicilar, sosyalGraf) ?: return@get
            call.respond(profiller.sayfa(sahip, izleyen = call.oturumSahibi.kullaniciId)!!)
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_GUNLUK)) {
            call.gunlukSekmesi(profiller, call.sahip(kullanicilar, sosyalGraf) ?: return@get, yalnizcaIncelemeler = false)
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_INCELEMELER)) {
            call.gunlukSekmesi(profiller, call.sahip(kullanicilar, sosyalGraf) ?: return@get, yalnizcaIncelemeler = true)
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_OYUNLAR)) {
            call.kutuphaneSekmesi(profiller, call.sahip(kullanicilar, sosyalGraf) ?: return@get, istekListesi = false, sosyalGraf)
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_ISTEK_LISTESI)) {
            call.kutuphaneSekmesi(profiller, call.sahip(kullanicilar, sosyalGraf) ?: return@get, istekListesi = true, sosyalGraf)
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_TAKIPCILER)) {
            val sahip = call.sahip(kullanicilar, sosyalGraf) ?: return@get
            if (sosyalGraf.icerikGizliMi(call.oturumSahibi.kullaniciId, sahip)) return@get call.respond(KullaniciListesi(emptyList(), null))
            call.respond(sosyalGraf.takipciler(sahip, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_TAKIP_EDILENLER)) {
            val sahip = call.sahip(kullanicilar, sosyalGraf) ?: return@get
            if (sosyalGraf.icerikGizliMi(call.oturumSahibi.kullaniciId, sahip)) return@get call.respond(KullaniciListesi(emptyList(), null))
            call.respond(sosyalGraf.takipEdilenler(sahip, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        post(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.TAKIP)) {
            val hedef = call.sahip(kullanicilar, sosyalGraf) ?: return@post
            if (hedef == call.oturumSahibi.kullaniciId) {
                call.hata(HttpStatusCode.BadRequest, HataKodu.KENDINI_TAKIP_EDEMEZSIN)
                return@post
            }
            // Gizli profile takip isteği gider: 202 (#48).
            when (sosyalGraf.takipEt(call.oturumSahibi.kullaniciId, hedef)) {
                SosyalGraf.TakipSonucu.TAKIP_EDILIYOR -> call.respond(HttpStatusCode.NoContent)
                SosyalGraf.TakipSonucu.ISTEK_GONDERILDI -> call.respond(HttpStatusCode.Accepted)
            }
        }
        delete(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.TAKIP)) {
            val hedef = call.sahip(kullanicilar, sosyalGraf) ?: return@delete
            if (sosyalGraf.birak(call.oturumSahibi.kullaniciId, hedef)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.hata(HttpStatusCode.Conflict, HataKodu.ARKADASKEN_TAKIPTEN_CIKILAMAZ)
            }
        }

        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_ARKADASLAR)) {
            val sahip = call.sahip(kullanicilar, sosyalGraf) ?: return@get
            if (sosyalGraf.icerikGizliMi(call.oturumSahibi.kullaniciId, sahip)) return@get call.respond(KullaniciListesi(emptyList(), null))
            call.respond(sosyalGraf.arkadaslar(sahip, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        get(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SEKME_ORTAK)) {
            val sahip = call.sahip(kullanicilar, sosyalGraf) ?: return@get
            if (!sosyalGraf.arkadasMi(call.oturumSahibi.kullaniciId, sahip)) {
                call.hata(HttpStatusCode.Forbidden, HataKodu.ARKADAS_DEGIL)
                return@get
            }
            call.respond(profiller.ortakOyunlar(sahip, izleyen = call.oturumSahibi.kullaniciId))
        }
        post(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.ARKADASLIK)) {
            val hedef = call.sahip(kullanicilar, sosyalGraf) ?: return@post
            if (hedef == call.oturumSahibi.kullaniciId) {
                call.hata(HttpStatusCode.BadRequest, HataKodu.KENDINE_ISTEK_GONDEREMEZSIN)
                return@post
            }
            sosyalGraf.arkadaslikIste(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        delete(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.ARKADASLIK)) {
            val hedef = call.sahip(kullanicilar, sosyalGraf) ?: return@delete
            sosyalGraf.arkadasligiBitir(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        put(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.ENGEL)) {
            val hedef = call.adinSahibi(kullanicilar) ?: return@put
            if (hedef == call.oturumSahibi.kullaniciId) return@put call.hata(HttpStatusCode.BadRequest, HataKodu.KENDINI_ENGELLEYEMEZSIN)
            sosyalGraf.engelle(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        delete(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.ENGEL)) {
            val hedef = call.adinSahibi(kullanicilar) ?: return@delete
            sosyalGraf.engeliKaldir(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        put(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SESSIZ)) {
            val hedef = call.sahip(kullanicilar, sosyalGraf) ?: return@put
            if (hedef == call.oturumSahibi.kullaniciId) return@put call.hata(HttpStatusCode.BadRequest, HataKodu.KENDINI_SESSIZE_ALAMAZSIN)
            sosyalGraf.sessizeAl(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        delete(ApiYollari.kullaniciSekmesi("{ad}", ApiYollari.SESSIZ)) {
            val hedef = call.adinSahibi(kullanicilar) ?: return@delete
            sosyalGraf.sessizdenCikar(call.oturumSahibi.kullaniciId, hedef)
            call.respond(HttpStatusCode.NoContent)
        }
        get(ApiYollari.ENGELLENENLER) {
            call.respond(sosyalGraf.engellediklerim(call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        get(ApiYollari.SESSIZE_ALINANLAR) {
            call.respond(sosyalGraf.sessizeAldiklarim(call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        get(ApiYollari.ARKADASLIK_ISTEKLERI) {
            call.respond(sosyalGraf.gelenIstekler(call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
        post(ApiYollari.arkadaslikIstegiKabul("{ad}")) {
            val gonderen = call.sahip(kullanicilar, sosyalGraf) ?: return@post
            if (sosyalGraf.kabulEt(call.oturumSahibi.kullaniciId, gonderen)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.hata(HttpStatusCode.NotFound, HataKodu.ARKADASLIK_ISTEGI_YOK)
            }
        }
        delete(ApiYollari.arkadaslikIstegi("{ad}")) {
            val gonderen = call.sahip(kullanicilar, sosyalGraf) ?: return@delete
            if (sosyalGraf.reddet(call.oturumSahibi.kullaniciId, gonderen)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.hata(HttpStatusCode.NotFound, HataKodu.ARKADASLIK_ISTEGI_YOK)
            }
        }
    }
}

/** Yoldaki kullanıcı adının sahibi; yoksa ya da aralarında engel varsa 404 yanıtını verip null döner. */
private suspend fun RoutingCall.sahip(kullanicilar: Kullanicilar, sosyalGraf: SosyalGraf): UUID? {
    val id = adinSahibi(kullanicilar) ?: return null
    if (sosyalGraf.engelliMi(oturumSahibi.kullaniciId, id)) {
        hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
        return null
    }
    return id
}

/** Engel denetimi olmadan: engellediğin kişi sana görünmez ama engelini kaldırabilmelisin. */
private suspend fun RoutingCall.adinSahibi(kullanicilar: Kullanicilar): UUID? {
    val id = parameters["ad"]?.let { kullanicilar.kullaniciAdindan(it) }
    if (id == null) hata(HttpStatusCode.NotFound, HataKodu.KULLANICI_BULUNAMADI)
    return id
}

private suspend fun RoutingCall.gunlukSekmesi(profiller: Profiller, sahip: UUID, yalnizcaIncelemeler: Boolean) {
    respond(profiller.gunluk(sahip, izleyen = oturumSahibi.kullaniciId, yalnizcaIncelemeler, request.queryParameters["imlec"]))
}

private suspend fun RoutingCall.kutuphaneSekmesi(profiller: Profiller, sahip: UUID, istekListesi: Boolean, sosyalGraf: SosyalGraf? = null) {
    // Gizli profilin takipçisi olmayana kütüphane boş görünür (#48).
    if (sosyalGraf != null && sosyalGraf.icerikGizliMi(oturumSahibi.kullaniciId, sahip)) return respond(KutuphaneSayfasi(emptyList(), null))
    val q = request.queryParameters
    respond(profiller.kutuphane(sahip, istekListesi, if (istekListesi) null else q["durum"], q["imlec"]))
}
