package com.sosyaloyun.server

import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DavetTest {
    private suspend fun com.sosyaloyun.server.test.ApiTestOrtami.koduKullan(token: String, kod: String) =
        client.post(ApiYollari.DAVET_KULLAN) {
            bearerAuth(token)
            json(DavetKoduKullanIstegi.serializer(), DavetKoduKullanIstegi(kod))
        }

    private suspend fun com.sosyaloyun.server.test.ApiTestOrtami.kodlarim(token: String) =
        client.get(ApiYollari.DAVET_KODLARIM) { bearerAuth(token) }.govde(DavetKodlarimYaniti.serializer()).kodlar

    @Test
    fun gecerliKodlaBetayaKatilinir() = apiTesti {
        val kod = kurucuKoduEkle("ABCD2345")
        val giris = girisYap()

        val yanit = koduKullan(giris.oturumTokeni, " abcd-2345 ")

        assertEquals(HttpStatusCode.NoContent, yanit.status)
        val ben = client.get(ApiYollari.BEN) { bearerAuth(giris.oturumTokeni) }.govde(BenYaniti.serializer())
        assertTrue(ben.davetKabulEdildi)
        assertEquals("ABCD2345", kod)
    }

    @Test
    fun olmayanKodGecersizHatasiylaReddedilir() = apiTesti {
        val giris = girisYap()

        val yanit = koduKullan(giris.oturumTokeni, "YOKBOYLE")

        assertEquals(HttpStatusCode.BadRequest, yanit.status)
        assertEquals(ApiHatasi(HataKodu.DAVET_KODU_GECERSIZ), yanit.govde(ApiHatasi.serializer()))
    }

    @Test
    fun kullanilmisKodIkinciKezKullanilamaz() = apiTesti {
        val kod = kurucuKoduEkle()
        val ilk = girisYap("ilk")
        val ikinci = girisYap("ikinci")
        koduKullan(ilk.oturumTokeni, kod)

        val yanit = koduKullan(ikinci.oturumTokeni, kod)

        assertEquals(HttpStatusCode.Conflict, yanit.status)
        assertEquals(ApiHatasi(HataKodu.DAVET_KODU_KULLANILMIS), yanit.govde(ApiHatasi.serializer()))
        assertFalse(client.get(ApiYollari.BEN) { bearerAuth(ikinci.oturumTokeni) }.govde(BenYaniti.serializer()).davetKabulEdildi)
    }

    @Test
    fun zatenKatilmisKullaniciBaskaKoduTuketmez() = apiTesti {
        val katilmis = davetliKullanici("katilmis")
        val kod = kurucuKoduEkle()

        assertEquals(HttpStatusCode.NoContent, koduKullan(katilmis.oturumTokeni, kod).status)

        val yeni = girisYap("yeni")
        assertEquals(HttpStatusCode.NoContent, koduKullan(yeni.oturumTokeni, kod).status)
    }

    @Test
    fun katilanKullaniciAyardakiSayidaKendiKodunuAlir() = apiTesti {
        ayarlar.yaz(Ayar.KisiBasiDavetKoduSayisi, 3)
        val giris = davetliKullanici()

        val kodlar = kodlarim(giris.oturumTokeni)

        assertEquals(3, kodlar.size)
        assertEquals(3, kodlar.map { it.kod }.toSet().size)
        assertTrue(kodlar.none { it.kullanildi })
    }

    @Test
    fun varsayilanKisiBasiKodSayisiBestir() = apiTesti {
        val giris = davetliKullanici()

        assertEquals(5, kodlarim(giris.oturumTokeni).size)
    }

    @Test
    fun kodlarimKiminKullandiginiGosterir() = apiTesti {
        val ayse = kayitliKullanici("ayse")
        val kod = kodlarim(ayse.oturumTokeni).first().kod
        val mehmetGiris = girisYap("mehmet")
        koduKullan(mehmetGiris.oturumTokeni, kod)
        client.put(ApiYollari.PROFIL) {
            bearerAuth(mehmetGiris.oturumTokeni)
            json(
                com.sosyaloyun.shared.ProfilIstegi.serializer(),
                com.sosyaloyun.shared.ProfilIstegi("mehmet", "Mehmet", Avatar.EJDERHA),
            )
        }

        val kullanilan = kodlarim(ayse.oturumTokeni).single { it.kod == kod }

        assertTrue(kullanilan.kullanildi)
        assertEquals(KullaniciOzeti(mehmetGiris.kullaniciId, "mehmet", "Mehmet", Avatar.EJDERHA), kullanilan.kullanan)
    }

    @Test
    fun davetsizKullaniciKodlarinaErisemez() = apiTesti {
        val giris = girisYap()

        val yanit = client.get(ApiYollari.DAVET_KODLARIM) { bearerAuth(giris.oturumTokeni) }

        assertEquals(HttpStatusCode.Forbidden, yanit.status)
        assertEquals(ApiHatasi(HataKodu.DAVET_GEREKLI), yanit.govde(ApiHatasi.serializer()))
    }
}
