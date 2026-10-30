package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.GizlilikIstegi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KutuphaneSayfasi
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

class GizliProfilTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Gizli Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "gizli")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.gizliYap(ad: String, gizli: Boolean = true): HttpResponse =
        client.put(ApiYollari.GIZLILIK) {
            bearerAuth(t(ad))
            json(GizlilikIstegi.serializer(), GizlilikIstegi(gizli))
        }

    private suspend fun ApiTestOrtami.kayitGir(ad: String): GunlukKaydi {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "$ad inceledi"))
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.takipEt(a: String, b: String) =
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(t(a)) }

    private suspend fun ApiTestOrtami.profil(izleyen: String, ad: String) =
        client.get(ApiYollari.kullanici(ad)) { bearerAuth(t(izleyen)) }.govde(ProfilSayfasi.serializer())

    private suspend fun ApiTestOrtami.gunluk(izleyen: String, ad: String) =
        client.get(ApiYollari.kullaniciSekmesi(ad, ApiYollari.SEKME_GUNLUK)) { bearerAuth(t(izleyen)) }.govde(GunlukSayfasi.serializer()).ogeler

    private suspend fun ApiTestOrtami.bildirimler(ad: String) =
        client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t(ad)) }.govde(BildirimListesi.serializer()).bildirimler.map { it.tur to it.kisi?.kullaniciAdi }

    @Test
    fun gizliProfileTakipIstegeDonusurKabulEdilinceIcerikAcilir() = apiTesti {
        hazirla("ayse", "mehmet")
        assertEquals(HttpStatusCode.NoContent, gizliYap("ayse").status)
        val kayit = kayitGir("ayse")

        assertEquals(HttpStatusCode.Accepted, takipEt("mehmet", "ayse").status)
        val baslik = profil("mehmet", "ayse")
        assertEquals(true, baslik.gizli)
        assertEquals(true, baslik.takipIstegiGonderdim)
        assertEquals(true, baslik.icerikGizli)
        assertEquals(false, baslik.takipEdiyorum)
        assertEquals(emptyList(), gunluk("mehmet", "ayse"))
        assertEquals(HttpStatusCode.NotFound, client.get("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(t("mehmet")) }.status)
        assertEquals(listOf(BildirimTuru.TAKIP_ISTEGI to "mehmet"), bildirimler("ayse"))

        val istekler = client.get(ApiYollari.TAKIP_ISTEKLERI) { bearerAuth(t("ayse")) }.govde(KullaniciListesi.serializer())
        assertEquals(listOf("mehmet"), istekler.kullanicilar.map { it.kullaniciAdi })
        assertEquals(HttpStatusCode.NoContent, client.post(ApiYollari.takipIstegiKabul("mehmet")) { bearerAuth(t("ayse")) }.status)

        assertEquals(true, profil("mehmet", "ayse").takipEdiyorum)
        assertEquals(listOf(kayit.id), gunluk("mehmet", "ayse").map { it.kayit.id })
        assertEquals(listOf(BildirimTuru.TAKIP_ISTEGI_KABULU to "ayse"), bildirimler("mehmet"))
        // Kabul edilen isteğin bildirimi istekle birlikte kalkar.
        assertEquals(emptyList(), bildirimler("ayse"))
    }

    @Test
    fun reddedilenVeGeriAlinanIstekKalkar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        gizliYap("ayse")
        takipEt("mehmet", "ayse")
        takipEt("zeynep", "ayse")

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.takipIstegi("mehmet")) { bearerAuth(t("ayse")) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(t("zeynep")) }.status)

        assertEquals(emptyList(), client.get(ApiYollari.TAKIP_ISTEKLERI) { bearerAuth(t("ayse")) }.govde(KullaniciListesi.serializer()).kullanicilar)
        assertEquals(emptyList(), bildirimler("ayse"))
        val yok = client.post(ApiYollari.takipIstegiKabul("mehmet")) { bearerAuth(t("ayse")) }
        assertEquals(HataKodu.TAKIP_ISTEGI_YOK, yok.govde(ApiHatasi.serializer()).kod)
    }

    @Test
    fun gizliProfilinIcerigiTakipciOlmayanaHicbirYerdeGorunmez() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(t("ayse"))
            json(com.sosyaloyun.shared.KutuphaneGirisi.serializer(), com.sosyaloyun.shared.KutuphaneGirisi(puan = 8))
        }
        kayitGir("ayse")
        takipEt("zeynep", "ayse")
        gizliYap("ayse")

        assertEquals(emptyList(), client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_OYUNLAR)) { bearerAuth(t("mehmet")) }.govde(KutuphaneSayfasi.serializer()).ogeler)
        assertEquals(emptyList(), client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_TAKIPCILER)) { bearerAuth(t("mehmet")) }.govde(KullaniciListesi.serializer()).kullanicilar)
        assertEquals(emptyList(), client.get(ApiYollari.oyunIncelemeleri(oyun)) { bearerAuth(t("mehmet")) }.govde(IncelemeListesi.serializer()).incelemeler)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.paylasilanProfil("ayse")).status)

        // Gizli olmadan önce takip eden zeynep her şeyi görür.
        assertEquals(1, client.get(ApiYollari.oyunIncelemeleri(oyun)) { bearerAuth(t("zeynep")) }.govde(IncelemeListesi.serializer()).incelemeler.size)
        assertEquals(1, gunluk("zeynep", "ayse").size)
        assertEquals(1, client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_OYUNLAR)) { bearerAuth(t("zeynep")) }.govde(KutuphaneSayfasi.serializer()).ogeler.size)
    }

    @Test
    fun aciktaGeceneBekleyenIsteklerKabulEdilir() = apiTesti {
        hazirla("ayse", "mehmet")
        gizliYap("ayse")
        takipEt("mehmet", "ayse")

        gizliYap("ayse", gizli = false)

        assertEquals(true, profil("mehmet", "ayse").takipEdiyorum)
        assertEquals(false, profil("mehmet", "ayse").gizli)
        assertTrue(BildirimTuru.TAKIP_ISTEGI_KABULU to "ayse" in bildirimler("mehmet"))
    }

    @Test
    fun faz1deGizlilikYokSayilir() = apiTesti {
        hazirla("ayse", "mehmet", faz = 2)
        gizliYap("ayse")
        kayitGir("ayse")

        ayarlar.yaz(Ayar.AktifFaz, 1)
        assertEquals(HataKodu.OZELLIK_KAPALI, gizliYap("ayse", gizli = false).govde(ApiHatasi.serializer()).kod)
        assertEquals(false, profil("mehmet", "ayse").gizli)
        assertEquals(1, gunluk("mehmet", "ayse").size)
        // Faz 1'de takip doğrudan başlar.
        assertEquals(HttpStatusCode.NoContent, takipEt("mehmet", "ayse").status)
        assertEquals(true, profil("mehmet", "ayse").takipEdiyorum)
    }
}
