package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneKurallari
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

fun Route.katalogRotalari(katalog: Katalog, kutuphane: Kutuphane, gunluk: Gunluk, begeniler: Begeniler, iceAktarici: KatalogIceAktarici?, webhookSirri: String?) {
    oturumGerekli {
        get(ApiYollari.OYUN_ARA) {
            call.respond(katalog.ara(call.request.queryParameters["q"].orEmpty(), call.request.queryParameters["imlec"]))
        }
        get("${ApiYollari.OYUNLAR}/{id}") {
            val id = call.oyunId()
            val detay = id?.let { katalog.detay(it) }
            if (detay == null) {
                call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                return@get
            }
            val kullaniciId = call.oturumSahibi.kullaniciId
            val oyunGunlugu = gunluk.oyunGunlugu(kullaniciId, id)
            call.respond(
                detay.copy(
                    kutuphaneGirisi = kutuphane.giris(kullaniciId, id),
                    gunlukKayitlarim = begeniler.ekle(kullaniciId, oyunGunlugu.kayitlar),
                    toplamOyunSuresiDakika = oyunGunlugu.toplamSureDakika,
                    sonPlatform = oyunGunlugu.sonPlatform,
                ),
            )
        }
        put(ApiYollari.kutuphaneGirisi("{id}")) {
            val id = call.oyunId()
            if (id == null || !katalog.varMi(id)) {
                call.hata(HttpStatusCode.NotFound, HataKodu.OYUN_BULUNAMADI)
                return@put
            }
            val giris = call.receive<KutuphaneGirisi>()
            KutuphaneKurallari.hatasi(giris)?.let {
                call.hata(HttpStatusCode.BadRequest, it)
                return@put
            }
            call.respond(kutuphane.kaydet(call.oturumSahibi.kullaniciId, id, giris) ?: KutuphaneGirisi())
        }
    }

    /** IGDB webhook'ları: gövdede yalnızca kimliğe güvenilir; kayıt kaynaktan yeniden okunur. */
    post("${ApiYollari.IGDB_WEBHOOK}/{olay}") {
        val gelenSir = call.request.headers["X-Secret"].orEmpty()
        if (webhookSirri.isNullOrEmpty() || !MessageDigest.isEqual(gelenSir.toByteArray(), webhookSirri.toByteArray())) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        if (iceAktarici == null) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@post
        }
        val kaynakId = Json.parseToJsonElement(call.receiveText()).jsonObject["id"]?.jsonPrimitive?.long
        if (kaynakId == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        when (call.parameters["olay"]) {
            "create", "update" -> iceAktarici.guncelle(kaynakId)
            "delete" -> iceAktarici.kaldir(kaynakId)
            else -> {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
        }
        call.respond(HttpStatusCode.OK)
    }
}

private fun RoutingCall.oyunId(): UUID? = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
