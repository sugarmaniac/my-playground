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
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KuratorBasvuruDurumu
import com.sosyaloyun.shared.KuratorDurumu
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.ListeSayfasi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilSayfasi
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class KuratorTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        ayarlar.yaz(Ayar.KuratorEnAzInceleme, 2)
        ayarlar.yaz(Ayar.KuratorEnAzTakipci, 1)
        ayarlar.yaz(Ayar.KuratorEnAzHesapGunu, 10)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Küratör Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "kurator")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.durum(ad: String) =
        client.get(ApiYollari.KURATOR) { bearerAuth(t(ad)) }.govde(KuratorDurumu.serializer())

    private suspend fun ApiTestOrtami.inceleme(ad: String) = client.post(ApiYollari.oyunGunlugu(oyun)) {
        bearerAuth(t(ad))
        json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "İnceleme"))
    }

    private suspend fun ApiTestOrtami.panelKarari(karar: String) {
        val panel = client.get(ApiYollari.YONETIM) { basicAuth("y", TEST_MODERASYON_SIFRESI) }.bodyAsText()
        val id = Regex("/yonetim/kurator/([0-9a-f-]{36})").find(panel)!!.groupValues[1]
        val anahtar = Regex("name=\"anahtar\" value=\"([^\"]+)\"").find(panel)!!.groupValues[1]
        client.submitForm("${ApiYollari.YONETIM}/kurator/$id", parameters {
            append("anahtar", anahtar)
            append("karar", karar)
        }) { basicAuth("y", TEST_MODERASYON_SIFRESI) }
    }

    @Test
    fun sartlarAyardanGelirEksikOlanBasvuramaz() = apiTesti {
        hazirla("ayse", "mehmet")
        val ilk = durum("ayse")
        assertEquals(listOf(KuratorSartTuru.INCELEME to (0 to 2), KuratorSartTuru.TAKIPCI to (0 to 1), KuratorSartTuru.HESAP_YASI_GUN to (0 to 10)),
            ilk.sartlar.map { it.tur to (it.mevcut to it.gereken) })
        assertFalse(ilk.basvurabilir)
        assertEquals(HataKodu.KURATOR_BASVURUSU_GECERSIZ, client.post(ApiYollari.KURATOR_BASVURU) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)

        inceleme("ayse")
        inceleme("ayse")
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(t("mehmet")) }
        saat.ilerlet(10.days)
        assertTrue(durum("ayse").sartlar.all { it.saglandi })
        val basvuru = client.post(ApiYollari.KURATOR_BASVURU) { bearerAuth(t("ayse")) }.govde(KuratorDurumu.serializer())
        assertEquals(KuratorBasvuruDurumu.BEKLIYOR, basvuru.basvuru)
        // Bekleyen başvuru varken ikincisi yapılamaz.
        assertEquals(HttpStatusCode.Conflict, client.post(ApiYollari.KURATOR_BASVURU) { bearerAuth(t("ayse")) }.status)
    }

    @Test
    fun panelOnayiKuratorYaparRozetVeListelerKesifteGorunur() = apiTesti {
        hazirla("ayse", "mehmet")
        ayarlar.yaz(Ayar.KuratorEnAzInceleme, 0)
        ayarlar.yaz(Ayar.KuratorEnAzTakipci, 0)
        ayarlar.yaz(Ayar.KuratorEnAzHesapGunu, 0)
        client.post(ApiYollari.LISTELER) {
            bearerAuth(t("ayse"))
            json(ListeIstegi.serializer(), ListeIstegi("Küratör seçkisi", oyunIdleri = listOf(oyun)))
        }
        client.post(ApiYollari.LISTELER) {
            bearerAuth(t("mehmet"))
            json(ListeIstegi.serializer(), ListeIstegi("Sıradan liste", oyunIdleri = listOf(oyun)))
        }
        assertEquals(emptyList(), client.get(ApiYollari.KURATOR_LISTELERI) { bearerAuth(t("mehmet")) }.govde(ListeSayfasi.serializer()).listeler)

        client.post(ApiYollari.KURATOR_BASVURU) { bearerAuth(t("ayse")) }
        panelKarari("ONAYLA")

        assertTrue(durum("ayse").kurator)
        assertTrue(client.get(ApiYollari.kullanici("ayse")) { bearerAuth(t("mehmet")) }.govde(ProfilSayfasi.serializer()).kurator)
        assertEquals(listOf("Küratör seçkisi"), client.get(ApiYollari.KURATOR_LISTELERI) { bearerAuth(t("mehmet")) }.govde(ListeSayfasi.serializer()).listeler.map { it.baslik })
        val bildirimler = client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t("ayse")) }.govde(BildirimListesi.serializer()).bildirimler
        assertEquals(listOf(BildirimTuru.KURATOR_ONAYLANDI), bildirimler.map { it.tur })
    }

    @Test
    fun reddedilenBasvuranaMesajGiderTekrarBasvurabilir() = apiTesti {
        hazirla("ayse")
        ayarlar.yaz(Ayar.KuratorEnAzInceleme, 0)
        ayarlar.yaz(Ayar.KuratorEnAzTakipci, 0)
        ayarlar.yaz(Ayar.KuratorEnAzHesapGunu, 0)
        client.post(ApiYollari.KURATOR_BASVURU) { bearerAuth(t("ayse")) }
        panelKarari("REDDET")

        val sonra = durum("ayse")
        assertEquals(KuratorBasvuruDurumu.REDDEDILDI, sonra.basvuru)
        assertFalse(sonra.kurator)
        assertTrue(sonra.basvurabilir)
        val bildirimler = client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t("ayse")) }.govde(BildirimListesi.serializer()).bildirimler
        assertEquals(listOf(BildirimTuru.KURATOR_REDDEDILDI), bildirimler.map { it.tur })
    }

    @Test
    fun faz1deKuratorlukKapali() = apiTesti {
        hazirla("ayse", faz = 1)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.KURATOR) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)
        assertFalse("Küratör başvuruları" in client.get(ApiYollari.YONETIM) { basicAuth("y", TEST_MODERASYON_SIFRESI) }.bodyAsText())
        assertFalse(client.get(ApiYollari.PROFILIM) { bearerAuth(t("ayse")) }.govde(ProfilSayfasi.serializer()).kurator)
    }
}
