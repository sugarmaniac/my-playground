package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunPuanlari
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class ToplulukTest {
    private lateinit var oyun: String
    private val tokenler = mutableMapOf<String, String>()

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Topluluk Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(tokenler.getValue(adlar.first()))
            parameter("q", "topluluk")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.puanla(ad: String, puan: Int?, durum: Durum? = null) {
        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(tokenler.getValue(ad))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(durum = durum, puan = puan))
        }
    }

    /** Saat her kayıtta ilerler; incelemeler yeniden eskiye sıralanır. */
    private suspend fun ApiTestOrtami.gunlugeYaz(ad: String, inceleme: String?, gorunurluk: Gorunurluk = Gorunurluk.HERKES) {
        saat.ilerlet(1.minutes)
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(tokenler.getValue(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = inceleme, gorunurluk = gorunurluk))
        }
    }

    private suspend fun ApiTestOrtami.arkadasOl(a: String, b: String) {
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.ARKADASLIK)) { bearerAuth(tokenler.getValue(a)) }
        client.post(ApiYollari.arkadaslikIstegiKabul(a)) { bearerAuth(tokenler.getValue(b)) }
    }

    private suspend fun ApiTestOrtami.takipEt(a: String, b: String) {
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(tokenler.getValue(a)) }
    }

    private suspend fun ApiTestOrtami.puanlar(ad: String): OyunPuanlari =
        client.get("${ApiYollari.OYUNLAR}/$oyun") { bearerAuth(tokenler.getValue(ad)) }.govde(OyunDetayi.serializer()).puanlar

    private suspend fun ApiTestOrtami.incelemeler(ad: String, imlec: String? = null): IncelemeListesi =
        client.get(ApiYollari.oyunIncelemeleri(oyun)) {
            bearerAuth(tokenler.getValue(ad))
            imlec?.let { parameter("imlec", it) }
        }.govde(IncelemeListesi.serializer())

    @Test
    fun toplulukPuaniEsikAltindaDonmezVeEsikAyardanGelir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        puanla("ayse", 10)
        puanla("mehmet", 6)
        puanla("zeynep", null, Durum.OYNUYORUM)

        assertNull(puanlar("ayse").toplulukPuani)

        ayarlar.yaz(Ayar.ToplulukPuaniEsigi, 2)
        assertEquals(8.0, puanlar("ayse").toplulukPuani)
    }

    @Test
    fun herKullanicininGuncelPuaniBirKezSayilir() = apiTesti {
        hazirla("ayse", "mehmet")
        ayarlar.yaz(Ayar.ToplulukPuaniEsigi, 1)
        puanla("ayse", 2)
        gunlugeYaz("ayse", null)
        puanla("ayse", 4)
        gunlugeYaz("ayse", null)
        puanla("ayse", 9)
        puanla("mehmet", 5)

        assertEquals(7.0, puanlar("mehmet").toplulukPuani)
    }

    @Test
    fun arkadasOrtalamasiVeOynayanArkadasSayisi() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali", "veli")
        arkadasOl("ayse", "mehmet")
        arkadasOl("ayse", "zeynep")
        arkadasOl("ayse", "ali")
        puanla("mehmet", 8, Durum.BITIRDIM)
        puanla("zeynep", null, Durum.OYNUYORUM)
        gunlugeYaz("ali", "Güzel")
        puanla("veli", 2, Durum.BITIRDIM)

        val ayseninki = puanlar("ayse")
        assertEquals(8.0, ayseninki.arkadasOrtalamasi)
        assertEquals(3, ayseninki.oynayanArkadasSayisi)

        val velininki = puanlar("veli")
        assertNull(velininki.arkadasOrtalamasi)
        assertEquals(0, velininki.oynayanArkadasSayisi)
    }

    @Test
    fun incelemelerOnceArkadaslariGosterirVeGorunurlugeUyar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali", "veli")
        arkadasOl("ayse", "mehmet")
        gunlugeYaz("zeynep", "Herkese açık")
        gunlugeYaz("mehmet", "Arkadaşa")
        gunlugeYaz("ali", "Takipçilere", Gorunurluk.TAKIPCILER)
        gunlugeYaz("veli", "Yalnız arkadaşlara", Gorunurluk.ARKADASLAR)
        gunlugeYaz("zeynep", "Kendime", Gorunurluk.BEN)
        gunlugeYaz("veli", null)
        gunlugeYaz("ayse", "Benim incelemem")

        assertEquals(listOf("Arkadaşa", "Herkese açık"), incelemeler("ayse").incelemeler.map { it.kayit.inceleme })
        assertEquals(listOf(true, false), incelemeler("ayse").incelemeler.map { it.arkadas })

        takipEt("ayse", "ali")
        assertEquals(listOf("Arkadaşa", "Takipçilere", "Herkese açık"), incelemeler("ayse").incelemeler.map { it.kayit.inceleme })
    }

    @Test
    fun incelemelerSayfalanir() = apiTesti {
        hazirla("ayse", "mehmet")
        repeat(25) { gunlugeYaz("mehmet", "İnceleme $it") }

        val ilk = incelemeler("ayse")
        assertEquals(20, ilk.incelemeler.size)
        val ikinci = incelemeler("ayse", ilk.sonrakiImlec)
        assertEquals(5, ikinci.incelemeler.size)
        assertNull(ikinci.sonrakiImlec)
        assertEquals(25, (ilk.incelemeler + ikinci.incelemeler).map { it.kayit.id }.toSet().size)
    }

    @Test
    fun olmayanOyununIncelemeleri404() = apiTesti {
        hazirla("ayse")
        val yanit = client.get(ApiYollari.oyunIncelemeleri("00000000-0000-0000-0000-000000000000")) { bearerAuth(tokenler.getValue("ayse")) }
        assertEquals(HttpStatusCode.NotFound, yanit.status)
        assertEquals(HataKodu.OYUN_BULUNAMADI, yanit.govde(ApiHatasi.serializer()).kod)
    }
}
