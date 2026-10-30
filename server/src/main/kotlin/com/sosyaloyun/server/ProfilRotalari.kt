package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciAdiMusaitlikYaniti
import com.sosyaloyun.shared.ProfilIstegi
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

suspend fun RoutingCall.hata(durum: HttpStatusCode, kod: HataKodu) = respond(durum, ApiHatasi(kod))

/** Onboarding'in davet kodu ve kullanıcı adı adımları. */
fun Route.profilRotalari(kullanicilar: Kullanicilar, davetKodlari: DavetKodlari, ayarlar: Ayarlar) {
    oturumGerekli {
        get(ApiYollari.BEN) {
            call.respond(kullanicilar.ben(call.oturumSahibi.kullaniciId))
        }

        post(ApiYollari.DAVET_KULLAN) {
            val istek = call.receive<DavetKoduKullanIstegi>()
            when (davetKodlari.kullan(call.oturumSahibi.kullaniciId, istek.kod)) {
                DavetKodlari.KullanmaSonucu.KATILDI,
                DavetKodlari.KullanmaSonucu.ZATEN_KATILMIS,
                -> call.respond(HttpStatusCode.NoContent)
                DavetKodlari.KullanmaSonucu.GECERSIZ -> call.hata(HttpStatusCode.BadRequest, HataKodu.DAVET_KODU_GECERSIZ)
                DavetKodlari.KullanmaSonucu.KULLANILMIS -> call.hata(HttpStatusCode.Conflict, HataKodu.DAVET_KODU_KULLANILMIS)
            }
        }

        get(ApiYollari.DAVET_KODLARIM) {
            val kullaniciId = call.oturumSahibi.kullaniciId
            if (!kullanicilar.davetKabulEdildiMi(kullaniciId)) {
                call.hata(HttpStatusCode.Forbidden, HataKodu.DAVET_GEREKLI)
                return@get
            }
            val kodlar = davetKodlari.kodlari(kullaniciId, ayarlar.oku(Ayar.KisiBasiDavetKoduSayisi))
            call.respond(DavetKodlarimYaniti(kodlar))
        }

        get(ApiYollari.KULLANICI_ADI_MUSAITLIK) {
            val (ad, durum) = kullanicilar.kullaniciAdiDurumu(call.request.queryParameters["ad"].orEmpty(), call.oturumSahibi.kullaniciId)
            call.respond(KullaniciAdiMusaitlikYaniti(ad, durum))
        }

        put(ApiYollari.PROFIL) {
            val kullaniciId = call.oturumSahibi.kullaniciId
            when (kullanicilar.profilKaydet(kullaniciId, call.receive<ProfilIstegi>())) {
                Kullanicilar.ProfilSonucu.KAYDEDILDI -> call.respond(kullanicilar.ben(kullaniciId))
                Kullanicilar.ProfilSonucu.DAVET_GEREKLI -> call.hata(HttpStatusCode.Forbidden, HataKodu.DAVET_GEREKLI)
                Kullanicilar.ProfilSonucu.KULLANICI_ADI_GECERSIZ -> call.hata(HttpStatusCode.BadRequest, HataKodu.KULLANICI_ADI_GECERSIZ)
                Kullanicilar.ProfilSonucu.GORUNEN_AD_GECERSIZ -> call.hata(HttpStatusCode.BadRequest, HataKodu.GORUNEN_AD_GECERSIZ)
                Kullanicilar.ProfilSonucu.BIYOGRAFI_COK_UZUN -> call.hata(HttpStatusCode.BadRequest, HataKodu.BIYOGRAFI_COK_UZUN)
                Kullanicilar.ProfilSonucu.KULLANICI_ADI_ALINMIS -> call.hata(HttpStatusCode.Conflict, HataKodu.KULLANICI_ADI_ALINMIS)
            }
        }
    }
}
