package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.HesapSilmeIstegi
import com.sosyaloyun.shared.KonsolAgi
import com.sosyaloyun.shared.KonsolBaglaIstegi
import com.sosyaloyun.shared.KonsolBaglantilari
import com.sosyaloyun.shared.KonsolBaglantisi
import com.sosyaloyun.shared.KonsolIceAktarmaSonucu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class KonsolTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 3) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        listOf("Halo Infinite", "Forza Horizon 5", "Starfield").forEachIndexed { i, ad ->
            katalogKaynagi.oyunlar += KaynakOyun(i + 1L, ad, KaynakTuru.ANA_OYUN)
        }
        katalogIceAktarici.tamIceAktar()
    }

    private suspend fun ApiTestOrtami.bagla(ad: String, hesap: String) = client.put(ApiYollari.konsol("xbox")) {
        bearerAuth(t(ad))
        json(KonsolBaglaIstegi.serializer(), KonsolBaglaIstegi(hesap))
    }

    /** Durumlu girişler ve durumsuz (içe aktarılmış) girişler birlikte. */
    private suspend fun ApiTestOrtami.kutuphane(ad: String) = listOf(null, "durumsuz").flatMap { durum ->
        client.get(ApiYollari.PROFILIM_OYUNLAR) {
            bearerAuth(t(ad))
            durum?.let { parameter("durum", it) }
        }.govde(KutuphaneSayfasi.serializer()).ogeler
    }.associate { it.oyun.ad to it.giris.durum }

    @Test
    fun iceAktarmaDurumsuzGirisOlusturAkisaDusmezMevcutGirisiBozmaz() = apiTesti {
        hazirla("ayse", "mehmet")
        val forza = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t("ayse"))
            parameter("q", "forza")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        client.put(ApiYollari.kutuphaneGirisi(forza)) {
            bearerAuth(t("ayse"))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(durum = Durum.BITIRDIM, puan = 9))
        }
        client.put(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(t("mehmet")) }
        val akisOnce = client.get(ApiYollari.AKIS) { bearerAuth(t("mehmet")) }.govde(AkisSayfasi.serializer()).ogeler.size
        xbox.hesaplar["AyseGamer"] = KonsolOyunlari.Acik(listOf("Halo Infinite™", "Forza Horizon 5", "Bilinmeyen Oyun", "Başka Bir Oyun"))

        // Testte yalnızca Xbox'ın anahtarı var.
        assertEquals(listOf(KonsolBaglantisi(KonsolAgi.XBOX)), client.get(ApiYollari.KONSOLLAR) { bearerAuth(t("ayse")) }.govde(KonsolBaglantilari.serializer()).baglantilar)
        val sonuc = bagla("ayse", "AyseGamer").govde(KonsolIceAktarmaSonucu.serializer())
        assertEquals(KonsolIceAktarmaSonucu(KonsolBaglantisi(KonsolAgi.XBOX, "AyseGamer"), iceAktarilanSayisi = 2, eslesmeyenSayisi = 2), sonuc)
        assertEquals(mapOf("Halo Infinite" to null, "Forza Horizon 5" to Durum.BITIRDIM), kutuphane("ayse"))
        assertEquals(akisOnce, client.get(ApiYollari.AKIS) { bearerAuth(t("mehmet")) }.govde(AkisSayfasi.serializer()).ogeler.size)
        assertEquals(KonsolBaglantisi(KonsolAgi.XBOX, "AyseGamer"), client.get(ApiYollari.KONSOLLAR) { bearerAuth(t("ayse")) }.govde(KonsolBaglantilari.serializer()).baglantilar.single())

        // Aynı hesap başka kullanıcıya bağlanamaz.
        assertEquals(HataKodu.KONSOL_HESABI_BASKA_KULLANICIDA, bagla("mehmet", "AyseGamer").govde(ApiHatasi.serializer()).kod)
    }

    @Test
    fun gizliProfilBilinmeyenHesapVeKaldirmaAnlasilirDoner() = apiTesti {
        hazirla("ayse")
        xbox.hesaplar["Gizli"] = KonsolOyunlari.Gizli
        assertEquals(HataKodu.KONSOL_PROFILI_GIZLI, bagla("ayse", "Gizli").govde(ApiHatasi.serializer()).kod)
        val yok = bagla("ayse", "Yok")
        assertEquals(HttpStatusCode.NotFound, yok.status)
        assertEquals(HataKodu.KONSOL_HESABI_BULUNAMADI, yok.govde(ApiHatasi.serializer()).kod)
        xbox.hata = IllegalStateException("xbl.io kapalı")
        assertEquals(HataKodu.KONSOL_KULLANILAMIYOR, bagla("ayse", "Gizli").govde(ApiHatasi.serializer()).kod)
        xbox.hata = null

        xbox.hesaplar["Acik"] = KonsolOyunlari.Acik(listOf("Starfield"))
        bagla("ayse", "Acik")
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.konsol("xbox")) { bearerAuth(t("ayse")) }.status)
        assertEquals(listOf(KonsolBaglantisi(KonsolAgi.XBOX)), client.get(ApiYollari.KONSOLLAR) { bearerAuth(t("ayse")) }.govde(KonsolBaglantilari.serializer()).baglantilar)
        // İçe aktarılan giriş kütüphanede kalır.
        assertTrue("Starfield" in kutuphane("ayse"))
    }

    @Test
    fun hesapSilinceBaglantilarSilinir() = apiTesti {
        hazirla("ayse")
        xbox.hesaplar["Acik"] = KonsolOyunlari.Acik(listOf("Starfield"))
        bagla("ayse", "Acik")
        client.delete(ApiYollari.BEN) {
            bearerAuth(t("ayse"))
            json(HesapSilmeIstegi.serializer(), HesapSilmeIstegi("ayse"))
        }
        assertEquals(0, transaction(veritabani) { KonsolBaglantisiTablosu.selectAll().count() })
    }

    @Test
    fun faz1ve2deKonsolUcNoktalariKapali() = apiTesti {
        hazirla("ayse", faz = 2)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.KONSOLLAR) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.OZELLIK_KAPALI, bagla("ayse", "x").govde(ApiHatasi.serializer()).kod)
    }
}
