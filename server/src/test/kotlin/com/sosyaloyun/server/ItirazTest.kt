package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.TEST_MODERASYON_SIFRESI
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.GirisItirazIstegi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.ItirazDurumu
import com.sosyaloyun.shared.ItirazIstegi
import com.sosyaloyun.shared.ItirazSonucu
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import com.sosyaloyun.shared.YaptirimTuru
import com.sosyaloyun.shared.YorumIstegi
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ItirazTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "İtiraz Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "itiraz")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String): GunlukKaydi =
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "$ad yazdı"))
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.sikayetEt(ad: String, kayit: GunlukKaydi) {
        client.post(ApiYollari.SIKAYETLER) {
            bearerAuth(t(ad))
            json(SikayetIstegi.serializer(), SikayetIstegi(SikayetSebebi.TACIZ, gunlukKaydiId = kayit.id))
        }
    }

    private suspend fun ApiTestOrtami.sayfa(): String =
        client.get(ApiYollari.YONETIM) { basicAuth("deniz", TEST_MODERASYON_SIFRESI) }.bodyAsText()

    /** Paneldeki ilk [yol] formuna kararı gönderir. */
    private suspend fun ApiTestOrtami.panelKarari(yol: String, karar: String): HttpResponse {
        val html = sayfa()
        val id = Regex("/yonetim/$yol/([0-9a-f-]{36})").find(html)!!.groupValues[1]
        val anahtar = Regex("name=\"anahtar\" value=\"([^\"]+)\"").find(html)!!.groupValues[1]
        return client.submitForm("${ApiYollari.YONETIM}/$yol/$id", parameters {
            append("anahtar", anahtar)
            append("karar", karar)
        }) { basicAuth("deniz", TEST_MODERASYON_SIFRESI) }
    }

    private suspend fun ApiTestOrtami.itirazDurumu(ad: String) =
        client.get(ApiYollari.ITIRAZ) { bearerAuth(t(ad)) }.govde(ItirazDurumu.serializer())

    private suspend fun ApiTestOrtami.itirazEt(ad: String, metin: String) =
        client.post(ApiYollari.ITIRAZ) {
            bearerAuth(t(ad))
            json(ItirazIstegi.serializer(), ItirazIstegi(metin))
        }

    private suspend fun ApiTestOrtami.bildirimler(ad: String) =
        client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t(ad)) }.govde(BildirimListesi.serializer()).bildirimler.map { it.tur }

    private suspend fun ApiTestOrtami.girisDene(ad: String) = client.post(ApiYollari.OTURUM) {
        json(GirisIstegi.serializer(), GirisIstegi(KimlikSaglayici.APPLE, kimlikDogrulayici.tokenUret(KimlikSaglayici.APPLE, ad)))
    }

    private suspend fun ApiTestOrtami.yorumYaz(ad: String, kayit: GunlukKaydi) = client.post(ApiYollari.yorumlar(kayit.id)) {
        bearerAuth(t(ad))
        json(YorumIstegi.serializer(), YorumIstegi("Merhaba"))
    }

    @Test
    fun yorumYasagiItirazKabulEdilinceKalkarItirazBirKezYapilir() = apiTesti {
        hazirla("ayse", "mehmet")
        val kayit = kayitGir("ayse")
        sikayetEt("ayse", kayitGir("mehmet"))
        panelKarari("sikayetler", SikayetDurumu.YORUM_YASAKLANDI.name)

        val yasak = yorumYaz("mehmet", kayit)
        assertEquals(HttpStatusCode.Forbidden, yasak.status)
        assertEquals(HataKodu.YORUM_YASAGI, yasak.govde(ApiHatasi.serializer()).kod)
        assertTrue(BildirimTuru.YORUM_YASAGI in bildirimler("mehmet"))
        val durum = itirazDurumu("mehmet")
        assertEquals(YaptirimTuru.YORUM_YASAGI, durum.yaptirim?.tur)
        assertTrue(durum.itirazEdilebilir)

        assertEquals(HttpStatusCode.Created, itirazEt("mehmet", "Yanlış anlaşıldı").status)
        assertEquals(HataKodu.ITIRAZ_GECERSIZ, itirazEt("mehmet", "Bir daha").govde(ApiHatasi.serializer()).kod)
        assertEquals(ItirazSonucu.BEKLIYOR, itirazDurumu("mehmet").itiraz)
        assertTrue("Yanlış anlaşıldı" in sayfa())

        panelKarari("itirazlar", "KABUL")
        assertEquals(HttpStatusCode.Created, yorumYaz("mehmet", kayit).status)
        assertTrue(BildirimTuru.ITIRAZ_KABUL_EDILDI in bildirimler("mehmet"))
        // Kalkan yaptırım artık itiraz edilecek yaptırım değil.
        assertEquals(ItirazDurumu(), itirazDurumu("mehmet"))

        // Denetim kaydı her kararı kimin verdiğiyle tutar.
        val html = sayfa()
        assertTrue("Şikayet: Yorum yasağı" in html && "İtiraz kabulü" in html && "<td>deniz</td>" in html)
    }

    @Test
    fun askidakiHesapGirisEkranindanItirazEderRetteYaptirimSurer() = apiTesti {
        hazirla("ayse", "mehmet")
        sikayetEt("ayse", kayitGir("mehmet"))
        panelKarari("sikayetler", SikayetDurumu.ASKIYA_ALINDI.name)

        val hata = girisDene("mehmet").govde(ApiHatasi.serializer())
        assertEquals(HataKodu.HESAP_ASKIDA, hata.kod)
        val anahtar = assertNotNull(hata.itirazAnahtari)
        assertEquals(HttpStatusCode.BadRequest, client.post(ApiYollari.ITIRAZ_GIRIS) {
            json(GirisItirazIstegi.serializer(), GirisItirazIstegi("yanlis", "Haksızlık"))
        }.status)
        assertEquals(HttpStatusCode.NoContent, client.post(ApiYollari.ITIRAZ_GIRIS) {
            json(GirisItirazIstegi.serializer(), GirisItirazIstegi(anahtar, "Haksızlık"))
        }.status)

        panelKarari("itirazlar", "RED")
        assertEquals(HataKodu.HESAP_ASKIDA, girisDene("mehmet").govde(ApiHatasi.serializer()).kod)
        assertFalse("Haksızlık" in sayfa())
    }

    @Test
    fun uyariKalipMesajGonderirAskiItirazlaKalkar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        sikayetEt("ayse", kayitGir("mehmet"))
        panelKarari("sikayetler", SikayetDurumu.UYARILDI.name)
        assertEquals(listOf(BildirimTuru.UYARI), bildirimler("mehmet"))
        assertEquals(YaptirimTuru.UYARI, itirazDurumu("mehmet").yaptirim?.tur)

        sikayetEt("ayse", kayitGir("zeynep"))
        panelKarari("sikayetler", SikayetDurumu.ASKIYA_ALINDI.name)
        val anahtar = girisDene("zeynep").govde(ApiHatasi.serializer()).itirazAnahtari!!
        client.post(ApiYollari.ITIRAZ_GIRIS) { json(GirisItirazIstegi.serializer(), GirisItirazIstegi(anahtar, "Lütfen")) }
        panelKarari("itirazlar", "KABUL")
        assertEquals(HttpStatusCode.OK, girisDene("zeynep").status)
    }

    @Test
    fun faz1deItirazKapaliPanelFaz2BolumleriniGostermezDenetimYineTutulur() = apiTesti {
        hazirla("ayse", "mehmet", faz = 1)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.ITIRAZ) { bearerAuth(t("mehmet")) }.govde(ApiHatasi.serializer()).kod)
        sikayetEt("ayse", kayitGir("mehmet"))
        val html = sayfa()
        assertFalse("Uyar<" in html || "Denetim kaydı" in html || "İtirazlar" in html)
        // Faz 1'de Faz 2 kararı reddedilir.
        assertEquals(HttpStatusCode.BadRequest, panelKarari("sikayetler", SikayetDurumu.UYARILDI.name).status)
        panelKarari("sikayetler", SikayetDurumu.ASKIYA_ALINDI.name)
        assertNull(girisDene("mehmet").govde(ApiHatasi.serializer()).itirazAnahtari)

        ayarlar.yaz(Ayar.AktifFaz, 2)
        assertTrue("Şikayet: Hesabı askıya al" in sayfa())
    }
}
