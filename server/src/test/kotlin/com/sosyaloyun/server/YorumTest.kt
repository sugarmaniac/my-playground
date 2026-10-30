package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.TEST_MODERASYON_SIFRESI
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import com.sosyaloyun.shared.Yorum
import com.sosyaloyun.shared.YorumIstegi
import com.sosyaloyun.shared.YorumListesi
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class YorumTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Yorum Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "yorum")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String, gorunurluk: Gorunurluk = Gorunurluk.HERKES): GunlukKaydi =
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "İnceleme", gorunurluk = gorunurluk))
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.yorumYaz(ad: String, kayit: GunlukKaydi, metin: String): HttpResponse {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.yorumlar(kayit.id)) {
            bearerAuth(t(ad))
            json(YorumIstegi.serializer(), YorumIstegi(metin))
        }
    }

    private suspend fun ApiTestOrtami.yorumlar(ad: String, kayit: GunlukKaydi): HttpResponse =
        client.get(ApiYollari.yorumlar(kayit.id)) { bearerAuth(t(ad)) }

    private suspend fun ApiTestOrtami.metinler(ad: String, kayit: GunlukKaydi) =
        yorumlar(ad, kayit).govde(YorumListesi.serializer()).yorumlar.map { it.kisi.kullaniciAdi to it.metin }

    private suspend fun ApiTestOrtami.bildirimler(ad: String) =
        client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t(ad)) }.govde(BildirimListesi.serializer()).bildirimler

    @Test
    fun yorumYazilirSirayalaListelenirSahibineBildirimGider() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        val kayit = kayitGir("ayse")

        val yorum = yorumYaz("mehmet", kayit, "  Harika bir oyun!  ").govde(Yorum.serializer())
        assertEquals("Harika bir oyun!", yorum.metin)
        yorumYaz("zeynep", kayit, "Katılıyorum")
        yorumYaz("ayse", kayit, "Teşekkürler")

        assertEquals(listOf("mehmet" to "Harika bir oyun!", "zeynep" to "Katılıyorum", "ayse" to "Teşekkürler"), metinler("zeynep", kayit))
        // Kendi yorumuna bildirim gitmez; iki yorumcu iki ayrı bildirim.
        assertEquals(listOf(BildirimTuru.YORUM to "zeynep", BildirimTuru.YORUM to "mehmet"), bildirimler("ayse").map { it.tur to it.kisi?.kullaniciAdi })
        assertEquals(kayit.id, bildirimler("ayse").first().gunlukKaydiId)

        assertEquals(HataKodu.YORUM_GECERSIZ, yorumYaz("mehmet", kayit, "   ").govde(ApiHatasi.serializer()).kod)
        assertEquals(HttpStatusCode.BadRequest, yorumYaz("mehmet", kayit, "x".repeat(501)).status)
    }

    @Test
    fun yalnizcaKaydiGorebilenYorumYazarVeGorur() = apiTesti {
        hazirla("ayse", "mehmet")
        val ozel = kayitGir("ayse", Gorunurluk.BEN)
        assertEquals(HttpStatusCode.NotFound, yorumYaz("mehmet", ozel, "Görmemem gerek").status)
        assertEquals(HttpStatusCode.NotFound, yorumlar("mehmet", ozel).status)
    }

    @Test
    fun yazarVeKayitSahibiSilerBaskasiSilemezBildirimKalkar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        val kayit = kayitGir("ayse")
        val mehmetin = yorumYaz("mehmet", kayit, "Birinci").govde(Yorum.serializer())
        val zeynebin = yorumYaz("zeynep", kayit, "İkinci").govde(Yorum.serializer())

        assertEquals(listOf(false, true), yorumlar("zeynep", kayit).govde(YorumListesi.serializer()).yorumlar.map { it.silebilirim })
        assertEquals(HttpStatusCode.NotFound, client.delete("${ApiYollari.YORUMLAR}/${mehmetin.id}") { bearerAuth(t("zeynep")) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("${ApiYollari.YORUMLAR}/${mehmetin.id}") { bearerAuth(t("mehmet")) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("${ApiYollari.YORUMLAR}/${zeynebin.id}") { bearerAuth(t("ayse")) }.status)

        assertEquals(emptyList(), metinler("ayse", kayit))
        assertEquals(emptyList(), bildirimler("ayse"))
    }

    @Test
    fun engelliVeSessizeAlinaninYorumlariGorunmez() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali")
        val kayit = kayitGir("ayse")
        yorumYaz("mehmet", kayit, "Mehmet'ten")
        yorumYaz("zeynep", kayit, "Zeynep'ten")
        client.put(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ali")) }
        client.put(ApiYollari.kullaniciSekmesi("zeynep", ApiYollari.SESSIZ)) { bearerAuth(t("ali")) }

        assertEquals(emptyList(), metinler("ali", kayit))
        assertEquals(2, metinler("ayse", kayit).size)
    }

    @Test
    fun faz1deYorumlarKapaliVeBildirimleriGizli() = apiTesti {
        hazirla("ayse", "mehmet", faz = 2)
        val kayit = kayitGir("ayse")
        yorumYaz("mehmet", kayit, "Faz 2'de yazıldı")
        assertTrue(BildirimTuru.YORUM in client.get(ApiYollari.BILDIRIM_TERCIHLERI) { bearerAuth(t("ayse")) }.govde(BildirimTercihleri.serializer()).push)

        ayarlar.yaz(Ayar.AktifFaz, 1)
        val kapali = yorumlar("ayse", kayit)
        assertEquals(HttpStatusCode.NotFound, kapali.status)
        assertEquals(HataKodu.OZELLIK_KAPALI, kapali.govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.OZELLIK_KAPALI, yorumYaz("mehmet", kayit, "Kapalı").govde(ApiHatasi.serializer()).kod)
        assertEquals(emptyList(), bildirimler("ayse"))
        assertFalse(BildirimTuru.YORUM in client.get(ApiYollari.BILDIRIM_TERCIHLERI) { bearerAuth(t("ayse")) }.govde(BildirimTercihleri.serializer()).push)

        // Faz yeniden açılınca veri olduğu gibi geri gelir.
        ayarlar.yaz(Ayar.AktifFaz, 3)
        assertEquals(listOf("mehmet" to "Faz 2'de yazıldı"), metinler("ayse", kayit))
    }

    @Test
    fun yorumSikayetEdilirVeIcerikKaldirilincaSilinir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        val kayit = kayitGir("ayse")
        val yorum = yorumYaz("mehmet", kayit, "Kaba yorum").govde(Yorum.serializer())

        val sikayet = client.post(ApiYollari.SIKAYETLER) {
            bearerAuth(t("zeynep"))
            json(SikayetIstegi.serializer(), SikayetIstegi(SikayetSebebi.TACIZ, yorumId = yorum.id))
        }
        assertEquals(HttpStatusCode.NoContent, sikayet.status)
        val panel = client.get(ApiYollari.YONETIM) { basicAuth("y", TEST_MODERASYON_SIFRESI) }.bodyAsText()
        assertTrue("Kaba yorum" in panel && "Yorum" in panel)

        val id = Regex("/yonetim/sikayetler/([0-9a-f-]{36})").find(panel)!!.groupValues[1]
        val anahtar = Regex("name=\"anahtar\" value=\"([^\"]+)\"").find(panel)!!.groupValues[1]
        client.submitForm("${ApiYollari.YONETIM}/sikayetler/$id", parameters {
            append("anahtar", anahtar)
            append("karar", SikayetDurumu.ICERIK_KALDIRILDI.name)
        }) { basicAuth("y", TEST_MODERASYON_SIFRESI) }

        assertEquals(emptyList(), metinler("ayse", kayit))
        // Kayıt yerinde kalır; yalnızca yorum kaldırılır ve yazarına kalıp mesaj gider.
        assertEquals(HttpStatusCode.OK, client.get("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(t("ayse")) }.status)
        assertEquals(listOf(BildirimTuru.ICERIK_KALDIRILDI), bildirimler("mehmet").map { it.tur })
    }
}
