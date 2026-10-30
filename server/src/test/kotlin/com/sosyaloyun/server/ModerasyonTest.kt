package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.TEST_MODERASYON_SIFRESI
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
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
import kotlin.time.Duration.Companion.days

class ModerasyonTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Moderasyon Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "moderasyon")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String, inceleme: String): GunlukKaydi =
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = inceleme))
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.sikayetEt(ad: String, istek: SikayetIstegi): HttpResponse =
        client.post(ApiYollari.SIKAYETLER) {
            bearerAuth(t(ad))
            json(SikayetIstegi.serializer(), istek)
        }

    private suspend fun ApiTestOrtami.sayfa(sifre: String? = TEST_MODERASYON_SIFRESI): HttpResponse =
        client.get(ApiYollari.MODERASYON) { sifre?.let { basicAuth("moderator", it) } }

    /** Sayfadaki ilk açık şikayete kararı verir; sayfanın formundaki anahtarı kullanır. */
    private suspend fun ApiTestOrtami.karar(karar: SikayetDurumu, anahtar: String? = null): HttpResponse {
        val html = sayfa().bodyAsText()
        val id = Regex("/moderasyon/sikayetler/([0-9a-f-]{36})").find(html)!!.groupValues[1]
        val formAnahtari = anahtar ?: Regex("name=\"anahtar\" value=\"([^\"]+)\"").find(html)!!.groupValues[1]
        return client.submitForm(
            "${ApiYollari.MODERASYON}/sikayetler/$id",
            parameters {
                append("anahtar", formAnahtari)
                append("karar", karar.name)
            },
        ) { basicAuth("moderator", TEST_MODERASYON_SIFRESI) }
    }

    private suspend fun ApiTestOrtami.bildirimler(ad: String) =
        client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t(ad)) }.govde(BildirimListesi.serializer()).bildirimler

    @Test
    fun sikayetSabitSebepleOlusurEpostaGiderSikayetEdenGorunmez() = apiTesti {
        hazirla("ayse", "mehmet")
        val kayit = kayitGir("mehmet", "Kaba bir inceleme")

        assertEquals(HttpStatusCode.NoContent, sikayetEt("ayse", SikayetIstegi(SikayetSebebi.TACIZ, gunlukKaydiId = kayit.id, aciklama = "Hakaret var")).status)
        // Aynı şikayet ikinci kez açılmaz; kendini ya da hem kayıt hem profil şikayet edemezsin.
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.TACIZ, gunlukKaydiId = kayit.id))
        assertEquals(HataKodu.SIKAYET_GECERSIZ, sikayetEt("mehmet", SikayetIstegi(SikayetSebebi.SPAM, kullaniciAdi = "mehmet")).govde(ApiHatasi.serializer()).kod)
        assertEquals(HttpStatusCode.BadRequest, sikayetEt("ayse", SikayetIstegi(SikayetSebebi.SPAM, gunlukKaydiId = kayit.id, kullaniciAdi = "mehmet")).status)

        assertEquals(1, eposta.gonderilenler.size)
        assertTrue("Kaba bir inceleme" in eposta.gonderilenler.single().second)
        val html = sayfa().bodyAsText()
        assertEquals(1, Regex("<article>").findAll(html).count())
        assertTrue("Hakaret var" in html && "@ayse" in html)
        // İçerik sahibine şikayet edenden hiçbir iz düşmez.
        assertEquals(emptyList(), bildirimler("mehmet"))
    }

    @Test
    fun moderasyonSayfasiSifresizAcilmazVeFormAnahtarsizKararVerilmez() = apiTesti {
        hazirla("ayse", "mehmet")
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.SPAM, kullaniciAdi = "mehmet"))

        assertEquals(HttpStatusCode.Unauthorized, sayfa(sifre = null).status)
        assertEquals(HttpStatusCode.Unauthorized, sayfa(sifre = "yanlis").status)
        assertEquals(HttpStatusCode.Forbidden, karar(SikayetDurumu.BANLANDI, anahtar = "sahte").status)
        assertEquals(HttpStatusCode.OK, client.get(ApiYollari.kullanici("mehmet")) { bearerAuth(t("ayse")) }.status)
    }

    @Test
    fun icerikKaldirilincaHicbirUcNoktadaDonmezVeIkiTarafaMesajGider() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(t("ayse")) }
        val kayit = kayitGir("mehmet", "Kaba bir inceleme")
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.TACIZ, gunlukKaydiId = kayit.id))
        // Aynı kaydı başkası da şikayet etti: tek kararla ikisi de kapanır, profil şikayetine dönüşmez.
        sikayetEt("zeynep", SikayetIstegi(SikayetSebebi.SPAM, gunlukKaydiId = kayit.id))

        karar(SikayetDurumu.ICERIK_KALDIRILDI)

        assertEquals(HttpStatusCode.NotFound, client.get("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(t("ayse")) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(t("mehmet")) }.status)
        assertEquals(emptyList(), client.get(ApiYollari.AKIS) { bearerAuth(t("ayse")) }.govde(AkisSayfasi.serializer()).ogeler)
        assertEquals(emptyList(), client.get(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.SEKME_GUNLUK)) { bearerAuth(t("ayse")) }.govde(GunlukSayfasi.serializer()).ogeler)
        assertEquals(emptyList(), client.get(ApiYollari.PROFILIM_GUNLUK) { bearerAuth(t("mehmet")) }.govde(GunlukSayfasi.serializer()).ogeler)
        assertEquals(emptyList(), client.get(ApiYollari.oyunIncelemeleri(oyun)) { bearerAuth(t("ayse")) }.govde(IncelemeListesi.serializer()).incelemeler)

        assertEquals(listOf(BildirimTuru.ICERIK_KALDIRILDI, BildirimTuru.YENI_TAKIPCI), bildirimler("mehmet").map { it.tur })
        assertEquals(listOf(BildirimTuru.SIKAYET_SONUCLANDI), bildirimler("ayse").map { it.tur })
        assertEquals(listOf(BildirimTuru.SIKAYET_SONUCLANDI), bildirimler("zeynep").map { it.tur })
        assertFalse("<article>" in sayfa().bodyAsText())
    }

    @Test
    fun profilIcerigiKaldirilincaBiyografiVeGorunenAdSifirlanir() = apiTesti {
        hazirla("ayse", "mehmet")
        client.put(ApiYollari.PROFIL) {
            bearerAuth(t("mehmet"))
            json(ProfilIstegi.serializer(), ProfilIstegi("mehmet", "Kaba Ad", com.sosyaloyun.shared.Avatar.KUMANDA, biyografi = "Kaba biyografi"))
        }
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.UYGUNSUZ_ICERIK, kullaniciAdi = "mehmet"))

        karar(SikayetDurumu.ICERIK_KALDIRILDI)

        val profil = client.get(ApiYollari.kullanici("mehmet")) { bearerAuth(t("ayse")) }.govde(ProfilSayfasi.serializer())
        assertEquals("mehmet", profil.kullanici.gorunenAd)
        assertEquals(null, profil.biyografi)
    }

    @Test
    fun askiyaAlinanHesapOturumuKapanirVeGiristeNedeniyleReddedilir() = apiTesti {
        hazirla("ayse", "mehmet")
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.SPAM, kullaniciAdi = "mehmet"))

        karar(SikayetDurumu.ASKIYA_ALINDI)

        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiYollari.PROFILIM) { bearerAuth(t("mehmet")) }.status)
        val giris = client.post(ApiYollari.OTURUM) {
            json(GirisIstegi.serializer(), GirisIstegi(KimlikSaglayici.APPLE, kimlikDogrulayici.tokenUret(KimlikSaglayici.APPLE, "mehmet")))
        }
        assertEquals(HttpStatusCode.Forbidden, giris.status)
        val hata = giris.govde(ApiHatasi.serializer())
        assertEquals(HataKodu.HESAP_ASKIDA, hata.kod)
        assertEquals((saat.simdi + 7.days).toString(), hata.ayrinti)
        assertEquals(listOf(BildirimTuru.SIKAYET_SONUCLANDI), bildirimler("ayse").map { it.tur })

        // Askı bitince giriş açılır.
        saat.ilerlet(8.days)
        assertEquals(HttpStatusCode.OK, girisYap("mehmet").let { client.get(ApiYollari.PROFILIM) { bearerAuth(it.oturumTokeni) }.status })
    }

    @Test
    fun banliHesapGiristeReddedilirReddedilenSikayetYaptirimUygulamaz() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.SPAM, kullaniciAdi = "zeynep"))
        karar(SikayetDurumu.REDDEDILDI)
        assertEquals(HttpStatusCode.OK, client.get(ApiYollari.PROFILIM) { bearerAuth(t("zeynep")) }.status)
        assertEquals(listOf(BildirimTuru.SIKAYET_SONUCLANDI), bildirimler("ayse").map { it.tur })

        sikayetEt("ayse", SikayetIstegi(SikayetSebebi.SAHTE_HESAP, kullaniciAdi = "mehmet"))
        sikayetEt("zeynep", SikayetIstegi(SikayetSebebi.SAHTE_HESAP, kullaniciAdi = "mehmet"))
        karar(SikayetDurumu.BANLANDI)

        val giris = client.post(ApiYollari.OTURUM) {
            json(GirisIstegi.serializer(), GirisIstegi(KimlikSaglayici.APPLE, kimlikDogrulayici.tokenUret(KimlikSaglayici.APPLE, "mehmet")))
        }
        assertEquals(HataKodu.HESAP_BANLI, giris.govde(ApiHatasi.serializer()).kod)
        // Aynı hesaba açılmış diğer şikayet de kapanır ve iki şikayet edene de mesaj gider.
        assertFalse("<article>" in sayfa().bodyAsText())
        assertEquals(listOf(BildirimTuru.SIKAYET_SONUCLANDI), bildirimler("zeynep").map { it.tur })
    }
}
