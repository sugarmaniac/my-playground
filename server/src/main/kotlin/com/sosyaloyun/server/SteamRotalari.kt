package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.OnboardingKurallari
import com.sosyaloyun.shared.PopulerOyunlar
import com.sosyaloyun.shared.SteamBaglaIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/** Steam bağlama ve onboarding'in son iki adımı (Steam, ilk durumlar). */
fun Route.steamRotalari(steam: Steam, katalog: Katalog, kutuphane: Kutuphane, kullanicilar: Kullanicilar) {
    oturumGerekli {
        get(ApiYollari.STEAM) {
            val baglanti = steam.baglanti(call.oturumSahibi.kullaniciId)
            if (baglanti == null) call.hata(HttpStatusCode.NotFound, HataKodu.STEAM_BAGLI_DEGIL) else call.respond(baglanti)
        }
        put(ApiYollari.STEAM) {
            when (val sonuc = steam.bagla(call.oturumSahibi.kullaniciId, call.receive<SteamBaglaIstegi>().girdi)) {
                is Steam.Sonuc.Basarili -> call.respond(sonuc.sonuc)
                is Steam.Sonuc.Hata -> call.hata(
                    when (sonuc.kod) {
                        HataKodu.STEAM_HESABI_BULUNAMADI -> HttpStatusCode.NotFound
                        HataKodu.STEAM_KULLANILAMIYOR -> HttpStatusCode.ServiceUnavailable
                        else -> HttpStatusCode.Conflict
                    },
                    sonuc.kod,
                )
            }
        }
        delete(ApiYollari.STEAM) {
            steam.kaldir(call.oturumSahibi.kullaniciId)
            call.respond(HttpStatusCode.NoContent)
        }
        get(ApiYollari.POPULER_OYUNLAR) {
            call.respond(PopulerOyunlar(katalog.populerler(POPULER_SAYISI)))
        }
        post(ApiYollari.ONBOARDING_TAMAMLA) {
            val kullaniciId = call.oturumSahibi.kullaniciId
            // Steam bağlamayan kullanıcı en az birkaç oyunun durumunu seçmiş olmalı; akış ve öneriler buna dayanır.
            if (steam.baglanti(kullaniciId) == null && kutuphane.durumluSayisi(kullaniciId) < OnboardingKurallari.EN_AZ_OYUN) {
                call.hata(HttpStatusCode.BadRequest, HataKodu.ONBOARDING_EKSIK)
                return@post
            }
            kullanicilar.onboardingiTamamla(kullaniciId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private const val POPULER_SAYISI = 30
