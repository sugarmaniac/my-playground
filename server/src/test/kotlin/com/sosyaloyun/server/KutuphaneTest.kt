package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KutuphaneTest {
    private suspend fun ApiTestOrtami.oyun(ad: String = "Kayıt Oyunu"): String {
        katalogKaynagi.oyunlar += KaynakOyun(katalogKaynagi.oyunlar.size + 1L, ad, KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        return client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(girisYap("katalogcu").oturumTokeni)
            parameter("q", ad)
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kaydet(token: String, oyunId: String, giris: KutuphaneGirisi): HttpResponse =
        client.put(ApiYollari.kutuphaneGirisi(oyunId)) {
            bearerAuth(token)
            json(KutuphaneGirisi.serializer(), giris)
        }

    private suspend fun ApiTestOrtami.detaydakiGiris(token: String, oyunId: String): KutuphaneGirisi? =
        client.get("${ApiYollari.OYUNLAR}/$oyunId") { bearerAuth(token) }.govde(OyunDetayi.serializer()).kutuphaneGirisi

    @Test
    fun durumPuanBegendimIstekListesiVeYuzdeYuzKaydedilirVeDetaydaGorunur() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()
        val giris = KutuphaneGirisi(Durum.BITIRDIM, puan = 9, begendim = true, istekListesinde = true, yuzdeYuz = true)

        val yanit = kaydet(token, oyunId, giris)

        assertEquals(HttpStatusCode.OK, yanit.status)
        assertEquals(giris, yanit.govde(KutuphaneGirisi.serializer()))
        assertEquals(giris, detaydakiGiris(token, oyunId))
    }

    @Test
    fun biraktimDurumundakiOyunAyniAndaIstekListesindeOlabilir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()
        val giris = KutuphaneGirisi(Durum.BIRAKTIM, istekListesinde = true)

        assertEquals(HttpStatusCode.OK, kaydet(token, oyunId, giris).status)
        assertEquals(giris, detaydakiGiris(token, oyunId))
    }

    @Test
    fun yuzdeYuzBitirdimDisindakiDurumlardaReddedilir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()

        listOf(null, Durum.OYNUYORUM, Durum.BIRAKTIM, Durum.BEKLEMEDE).forEach { durum ->
            val yanit = kaydet(token, oyunId, KutuphaneGirisi(durum, yuzdeYuz = true))
            assertEquals(HttpStatusCode.BadRequest, yanit.status, "durum: $durum")
            assertEquals(ApiHatasi(HataKodu.YUZDE_YUZ_YALNIZCA_BITIRDIM), yanit.govde(ApiHatasi.serializer()))
        }
        assertNull(detaydakiGiris(token, oyunId))
    }

    @Test
    fun ayniOyunIcinIkinciKayitAyniGirisiGunceller() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()

        kaydet(token, oyunId, KutuphaneGirisi(Durum.OYNUYORUM, puan = 6))
        kaydet(token, oyunId, KutuphaneGirisi(Durum.BITIRDIM, puan = 8))

        assertEquals(KutuphaneGirisi(Durum.BITIRDIM, puan = 8), detaydakiGiris(token, oyunId))
    }

    @Test
    fun yarimYildizAraligiDisindakiPuanReddedilir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()

        listOf(0, 11, -1).forEach { puan ->
            val yanit = kaydet(token, oyunId, KutuphaneGirisi(puan = puan))
            assertEquals(ApiHatasi(HataKodu.PUAN_GECERSIZ), yanit.govde(ApiHatasi.serializer()), "puan: $puan")
        }
        assertEquals(HttpStatusCode.OK, kaydet(token, oyunId, KutuphaneGirisi(puan = 1)).status)
    }

    @Test
    fun butunIsaretlerKaldirilincaGirisKalkar() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun()
        kaydet(token, oyunId, KutuphaneGirisi(Durum.OYNUYORUM))

        val yanit = kaydet(token, oyunId, KutuphaneGirisi())

        assertEquals(HttpStatusCode.OK, yanit.status)
        assertNull(detaydakiGiris(token, oyunId))
    }

    @Test
    fun herKullanicininGirisiKendineAittir() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        val oyunId = oyun()

        kaydet(ayse, oyunId, KutuphaneGirisi(Durum.OYNUYORUM))

        assertNull(detaydakiGiris(mehmet, oyunId))
        assertEquals(KutuphaneGirisi(Durum.OYNUYORUM), detaydakiGiris(ayse, oyunId))
    }

    @Test
    fun olmayanOyunIcinGirisOlusturulamaz() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni

        val yanit = kaydet(token, UUID.randomUUID().toString(), KutuphaneGirisi(Durum.OYNUYORUM))

        assertEquals(HttpStatusCode.NotFound, yanit.status)
        assertEquals(ApiHatasi(HataKodu.OYUN_BULUNAMADI), yanit.govde(ApiHatasi.serializer()))
    }
}
