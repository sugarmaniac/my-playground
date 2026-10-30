package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciAdiDurumu
import com.sosyaloyun.shared.KullaniciAdiMusaitlikYaniti
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.ProfilIstegi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfilTest {
    private suspend fun ApiTestOrtami.musaitlik(token: String, ad: String) =
        client.get(ApiYollari.KULLANICI_ADI_MUSAITLIK) {
            bearerAuth(token)
            parameter("ad", ad)
        }.govde(KullaniciAdiMusaitlikYaniti.serializer())

    private suspend fun ApiTestOrtami.profilKaydet(token: String, istek: ProfilIstegi) =
        client.put(ApiYollari.PROFIL) {
            bearerAuth(token)
            json(ProfilIstegi.serializer(), istek)
        }

    @Test
    fun kullaniciAdiMusaitligiYazarkenSorulabilir() = apiTesti {
        kayitliKullanici("ali")
        val giris = davetliKullanici("yeni")

        assertEquals(KullaniciAdiMusaitlikYaniti("veli", KullaniciAdiDurumu.MUSAIT), musaitlik(giris.oturumTokeni, "veli"))
        assertEquals(KullaniciAdiMusaitlikYaniti("ali", KullaniciAdiDurumu.ALINMIS), musaitlik(giris.oturumTokeni, "@Ali"))
        assertEquals(KullaniciAdiMusaitlikYaniti("ab", KullaniciAdiDurumu.GECERSIZ), musaitlik(giris.oturumTokeni, "ab"))
    }

    @Test
    fun profilKaydedilinceBenYanitindaGorunur() = apiTesti {
        val giris = davetliKullanici()

        val yanit = profilKaydet(giris.oturumTokeni, ProfilIstegi("  Oyuncu_42 ", "  Ayşe Yılmaz ", Avatar.TILKI))

        assertEquals(HttpStatusCode.OK, yanit.status)
        val beklenen = KullaniciOzeti(giris.kullaniciId, "oyuncu_42", "Ayşe Yılmaz", Avatar.TILKI)
        assertEquals(BenYaniti(giris.kullaniciId, davetKabulEdildi = true, profil = beklenen), yanit.govde(BenYaniti.serializer()))
        assertEquals(beklenen, client.get(ApiYollari.BEN) { bearerAuth(giris.oturumTokeni) }.govde(BenYaniti.serializer()).profil)
    }

    @Test
    fun baskasininKullaniciAdiAlinamaz() = apiTesti {
        kayitliKullanici("ali")
        val giris = davetliKullanici("yeni")

        val yanit = profilKaydet(giris.oturumTokeni, ProfilIstegi("ALI", "Ali", Avatar.KEDI))

        assertEquals(HttpStatusCode.Conflict, yanit.status)
        assertEquals(ApiHatasi(HataKodu.KULLANICI_ADI_ALINMIS), yanit.govde(ApiHatasi.serializer()))
    }

    @Test
    fun kullaniciKendiAdiniKoruyarakProfiliniDegistirebilir() = apiTesti {
        val ali = kayitliKullanici("ali")

        assertEquals(KullaniciAdiDurumu.MUSAIT, musaitlik(ali.oturumTokeni, "ali").durum)
        assertEquals(HttpStatusCode.OK, profilKaydet(ali.oturumTokeni, ProfilIstegi("ali", "Ali Veli", Avatar.ROBOT)).status)
    }

    @Test
    fun gecersizKullaniciAdiVeGorunenAdReddedilir() = apiTesti {
        val giris = davetliKullanici()

        val ad = profilKaydet(giris.oturumTokeni, ProfilIstegi("ayşe", "Ayşe", Avatar.KEDI))
        val gorunen = profilKaydet(giris.oturumTokeni, ProfilIstegi("ayse", "   ", Avatar.KEDI))

        assertEquals(HttpStatusCode.BadRequest, ad.status)
        assertEquals(ApiHatasi(HataKodu.KULLANICI_ADI_GECERSIZ), ad.govde(ApiHatasi.serializer()))
        assertEquals(HttpStatusCode.BadRequest, gorunen.status)
        assertEquals(ApiHatasi(HataKodu.GORUNEN_AD_GECERSIZ), gorunen.govde(ApiHatasi.serializer()))
    }

    @Test
    fun davetKoduOlmadanProfilOlusturulamaz() = apiTesti {
        val giris = girisYap()

        val yanit = profilKaydet(giris.oturumTokeni, ProfilIstegi("ayse", "Ayşe", Avatar.KEDI))

        assertEquals(HttpStatusCode.Forbidden, yanit.status)
        assertEquals(ApiHatasi(HataKodu.DAVET_GEREKLI), yanit.govde(ApiHatasi.serializer()))
    }
}
