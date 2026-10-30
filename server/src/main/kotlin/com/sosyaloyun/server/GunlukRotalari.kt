package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukKurallari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.YorumIstegi
import com.sosyaloyun.shared.Faz
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

fun Route.gunlukRotalari(
    gunluk: Gunluk,
    katalog: Katalog,
    begeniler: Begeniler,
    sosyalGraf: SosyalGraf,
    kullanicilar: Kullanicilar,
    yorumlar: Yorumlar,
    fazlar: Fazlar,
) {
    oturumGerekli {
        fazGerekli(fazlar, Faz.FAZ_2) {
            get(ApiYollari.yorumlar("{id}")) {
                val erisim = call.erisim(gunluk, sosyalGraf) ?: return@get
                val liste = yorumlar.liste(UUID.fromString(erisim.kayit.id), erisim.sahip, call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"])
                call.respond(liste)
            }
            post(ApiYollari.yorumlar("{id}")) {
                val erisim = call.erisim(gunluk, sosyalGraf) ?: return@post
                when (val sonuc = yorumlar.yaz(call.oturumSahibi.kullaniciId, UUID.fromString(erisim.kayit.id), call.receive<YorumIstegi>().metin)) {
                    is Yorumlar.YazmaSonucu.Yazildi -> call.respond(HttpStatusCode.Created, sonuc.yorum)
                    Yorumlar.YazmaSonucu.Gecersiz -> call.hata(HttpStatusCode.BadRequest, HataKodu.YORUM_GECERSIZ)
                }
            }
            delete("${ApiYollari.YORUMLAR}/{id}") {
                val silindi = call.kimlik()?.let { yorumlar.sil(call.oturumSahibi.kullaniciId, it) } ?: false
                if (silindi) call.respond(HttpStatusCode.NoContent) else call.hata(HttpStatusCode.NotFound, HataKodu.YORUM_BULUNAMADI)
            }
        }
        get("${ApiYollari.GUNLUK}/{id}") {
            val erisim = call.erisim(gunluk, sosyalGraf) ?: return@get
            val oyunId = UUID.fromString(erisim.kayit.oyunId)
            val oyun = katalog.ozetleri(listOf(oyunId)).getValue(oyunId)
            // Sahibinin profili yoksa detay gösterilemez; kayıt yokmuş gibi davranılır.
            val sahip = kullanicilar.ben(erisim.sahip).profil
                ?: return@get call.hata(HttpStatusCode.NotFound, HataKodu.GUNLUK_KAYDI_BULUNAMADI)
            call.respond(GunlukKaydiDetayi(begeniler.ekle(call.oturumSahibi.kullaniciId, erisim.kayit), oyun, sahip))
        }
        put(ApiYollari.begeni("{id}")) {
            val erisim = call.erisim(gunluk, sosyalGraf) ?: return@put
            begeniler.begen(call.oturumSahibi.kullaniciId, UUID.fromString(erisim.kayit.id))
            call.respond(HttpStatusCode.NoContent)
        }
        delete(ApiYollari.begeni("{id}")) {
            val erisim = call.erisim(gunluk, sosyalGraf) ?: return@delete
            begeniler.geriAl(call.oturumSahibi.kullaniciId, UUID.fromString(erisim.kayit.id))
            call.respond(HttpStatusCode.NoContent)
        }
        get(ApiYollari.begenenler("{id}")) {
            val erisim = call.erisim(gunluk, sosyalGraf) ?: return@get
            call.respond(begeniler.begenenler(UUID.fromString(erisim.kayit.id), call.oturumSahibi.kullaniciId, call.request.queryParameters["imlec"]))
        }
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

/** Görme yetkisi olmayan kaydın varlığı da sızmaz: ikisi de 404. */
private suspend fun RoutingCall.erisim(gunluk: Gunluk, sosyalGraf: SosyalGraf): Gunluk.Erisim? {
    val erisim = kimlik()?.let { gunluk.gorebilecegiKayit(oturumSahibi.kullaniciId, it, sosyalGraf) }
    if (erisim == null) hata(HttpStatusCode.NotFound, HataKodu.GUNLUK_KAYDI_BULUNAMADI)
    return erisim
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
