package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilSayfasi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EngellemeTest {
    private val tokenler = mutableMapOf<String, String>()
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Engel Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "engel")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private fun t(ad: String) = tokenler.getValue(ad)

    private suspend fun ApiTestOrtami.takipEt(a: String, b: String) =
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(t(a)) }

    private suspend fun ApiTestOrtami.arkadasOl(a: String, b: String) {
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.ARKADASLIK)) { bearerAuth(t(a)) }
        client.post(ApiYollari.arkadaslikIstegiKabul(a)) { bearerAuth(t(b)) }
    }

    private suspend fun ApiTestOrtami.engelle(a: String, b: String): HttpResponse =
        client.put(ApiYollari.kullaniciSekmesi(b, ApiYollari.ENGEL)) { bearerAuth(t(a)) }

    private suspend fun ApiTestOrtami.sessizeAl(a: String, b: String): HttpResponse =
        client.put(ApiYollari.kullaniciSekmesi(b, ApiYollari.SESSIZ)) { bearerAuth(t(a)) }

    private suspend fun ApiTestOrtami.gunlugeYaz(ad: String, inceleme: String = "İnceleme"): GunlukKaydi {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = inceleme))
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.profil(izleyen: String, ad: String): HttpResponse =
        client.get(ApiYollari.kullanici(ad)) { bearerAuth(t(izleyen)) }

    private suspend fun ApiTestOrtami.liste(izleyen: String, yol: String): List<String> =
        client.get(yol) { bearerAuth(t(izleyen)) }.govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

    private suspend fun ApiTestOrtami.akisKisileri(izleyen: String): List<String> =
        client.get(ApiYollari.AKIS) { bearerAuth(t(izleyen)) }.govde(AkisSayfasi.serializer()).ogeler.map { it.kisi.kullaniciAdi }

    @Test
    fun sessizeAlinanAkistanKalkarIliskiKorunur() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        arkadasOl("ayse", "mehmet")
        takipEt("ayse", "zeynep")
        gunlugeYaz("mehmet")
        gunlugeYaz("zeynep")

        assertEquals(HttpStatusCode.NoContent, sessizeAl("ayse", "mehmet").status)

        assertEquals(listOf("zeynep"), akisKisileri("ayse"))
        val mehmet = profil("ayse", "mehmet").govde(ProfilSayfasi.serializer())
        assertEquals(ArkadaslikDurumu.ARKADAS, mehmet.arkadaslik)
        assertEquals(true, mehmet.takipEdiyorum)
        assertEquals(true, mehmet.sessizeAldim)
        assertEquals(listOf("mehmet"), liste("ayse", ApiYollari.SESSIZE_ALINANLAR))

        client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.SESSIZ)) { bearerAuth(t("ayse")) }
        assertEquals(setOf("mehmet", "zeynep"), akisKisileri("ayse").toSet())
        assertEquals(emptyList(), liste("ayse", ApiYollari.SESSIZE_ALINANLAR))
    }

    @Test
    fun engellemeTakipArkadaslikVeBegenileriSiler() = apiTesti {
        hazirla("ayse", "mehmet")
        arkadasOl("ayse", "mehmet")
        val ayseninKaydi = gunlugeYaz("ayse")
        val mehmetinKaydi = gunlugeYaz("mehmet")
        client.put(ApiYollari.begeni(ayseninKaydi.id)) { bearerAuth(t("mehmet")) }
        client.put(ApiYollari.begeni(mehmetinKaydi.id)) { bearerAuth(t("ayse")) }

        assertEquals(HttpStatusCode.NoContent, engelle("ayse", "mehmet").status)

        val ayseninKendisi = client.get(ApiYollari.PROFILIM) { bearerAuth(t("ayse")) }.govde(ProfilSayfasi.serializer())
        assertEquals(0, ayseninKendisi.takipciSayisi)
        assertEquals(0, ayseninKendisi.takipEdilenSayisi)
        assertEquals(0, ayseninKendisi.arkadasSayisi)
        val detay = client.get("${ApiYollari.GUNLUK}/${ayseninKaydi.id}") { bearerAuth(t("ayse")) }.govde(GunlukKaydiDetayi.serializer())
        assertEquals(0, detay.kayit.begeniSayisi)
        assertEquals(listOf("mehmet"), liste("ayse", ApiYollari.ENGELLENENLER))

        engelle("ayse", "mehmet")
        assertEquals(listOf("mehmet"), liste("ayse", ApiYollari.ENGELLENENLER))
    }

    @Test
    fun engelliIkiTarafBirbiriniHicbirYerdeGoremez() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        takipEt("zeynep", "ayse")
        takipEt("zeynep", "mehmet")
        takipEt("ayse", "mehmet")
        val mehmetinKaydi = gunlugeYaz("mehmet", "Mehmet'in incelemesi")
        val ayseninKaydi = gunlugeYaz("ayse", "Ayşe'nin incelemesi")
        client.put(ApiYollari.begeni(mehmetinKaydi.id)) { bearerAuth(t("zeynep")) }
        client.put(ApiYollari.begeni(mehmetinKaydi.id)) { bearerAuth(t("ayse")) }
        client.put(ApiYollari.begeni(ayseninKaydi.id)) { bearerAuth(t("zeynep")) }

        engelle("mehmet", "ayse")

        for ((izleyen, diger, kayit) in listOf(Triple("ayse", "mehmet", mehmetinKaydi), Triple("mehmet", "ayse", ayseninKaydi))) {
            val profilYaniti = profil(izleyen, diger)
            assertEquals(HttpStatusCode.NotFound, profilYaniti.status)
            assertEquals(HataKodu.KULLANICI_BULUNAMADI, profilYaniti.govde(ApiHatasi.serializer()).kod)
            assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.kullaniciSekmesi(diger, ApiYollari.SEKME_GUNLUK)) { bearerAuth(t(izleyen)) }.status)
            assertEquals(HttpStatusCode.NotFound, takipEt(izleyen, diger).status)
            assertEquals(HttpStatusCode.NotFound, client.post(ApiYollari.kullaniciSekmesi(diger, ApiYollari.ARKADASLIK)) { bearerAuth(t(izleyen)) }.status)
            assertEquals(HttpStatusCode.NotFound, client.get("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(t(izleyen)) }.status)
            assertEquals(HttpStatusCode.NotFound, client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(t(izleyen)) }.status)

            val arama = client.get(ApiYollari.KULLANICI_ARA) {
                bearerAuth(t(izleyen))
                parameter("q", diger)
            }.govde(KullaniciListesi.serializer()).kullanicilar
            assertTrue(arama.none { it.kullaniciAdi == diger })
            assertTrue(diger !in akisKisileri(izleyen))
            assertTrue(diger !in liste(izleyen, ApiYollari.kullaniciSekmesi("zeynep", ApiYollari.SEKME_TAKIP_EDILENLER)))
            assertTrue(diger !in liste(izleyen, ApiYollari.AKIS_ONERILER))
            val incelemeler = client.get(ApiYollari.oyunIncelemeleri(oyun)) { bearerAuth(t(izleyen)) }.govde(IncelemeListesi.serializer())
            assertTrue(incelemeler.incelemeler.none { it.sahip.kullaniciAdi == diger })
        }
        // Üçüncü kişinin kaydını beğenenler listesinde de görünmezler.
        val zeyneplik = gunlugeYaz("zeynep")
        client.put(ApiYollari.begeni(zeyneplik.id)) { bearerAuth(t("ayse")) }
        assertEquals(emptyList(), liste("mehmet", ApiYollari.begenenler(zeyneplik.id)))
        assertEquals(listOf("ayse"), liste("zeynep", ApiYollari.begenenler(zeyneplik.id)))
    }

    @Test
    fun engelKaldirilincaYenidenGorunurAmaIliskilerGeriGelmez() = apiTesti {
        hazirla("ayse", "mehmet")
        takipEt("ayse", "mehmet")
        engelle("ayse", "mehmet")

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }.status)

        val mehmet = profil("ayse", "mehmet").govde(ProfilSayfasi.serializer())
        assertEquals(false, mehmet.takipEdiyorum)
        assertEquals(emptyList(), liste("ayse", ApiYollari.ENGELLENENLER))
    }

    @Test
    fun kendiniEngelleyemezVeSessizeAlamazsin() = apiTesti {
        hazirla("ayse")
        assertEquals(HataKodu.KENDINI_ENGELLEYEMEZSIN, engelle("ayse", "ayse").govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.KENDINI_SESSIZE_ALAMAZSIN, sessizeAl("ayse", "ayse").govde(ApiHatasi.serializer()).kod)
    }
}
