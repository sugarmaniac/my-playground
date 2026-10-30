package com.sosyaloyun.server

import com.sosyaloyun.server.test.TEST_MODERASYON_SIFRESI
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.OzellikDurumu
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FazTest {
    private lateinit var fazlar: Fazlar

    /** Gerçek Faz 2/3 uç noktaları gelene kadar denetimi örnek uç noktalarla dener. */
    private val ornekRotalar: Route.() -> Unit = {
        fazGerekli(fazlar, Faz.FAZ_2) { get("/deneme/faz-2") { call.respondText("faz 2") } }
        fazGerekli(fazlar, Faz.FAZ_3) { get("/deneme/faz-3") { call.respondText("faz 3") } }
    }

    private suspend fun com.sosyaloyun.server.test.ApiTestOrtami.fazSec(faz: Int, anahtar: String? = null): HttpResponse {
        val sayfa = client.get(ApiYollari.YONETIM) { basicAuth("yonetici", TEST_MODERASYON_SIFRESI) }.bodyAsText()
        val formAnahtari = anahtar ?: Regex("name=\"anahtar\" value=\"([^\"]+)\"").find(sayfa)!!.groupValues[1]
        return client.submitForm("${ApiYollari.YONETIM}/faz", parameters {
            append("anahtar", formAnahtari)
            append("faz", faz.toString())
        }) { basicAuth("yonetici", TEST_MODERASYON_SIFRESI) }
    }

    private suspend fun com.sosyaloyun.server.test.ApiTestOrtami.aktifFaz() =
        client.get(ApiYollari.OZELLIKLER).govde(OzellikDurumu.serializer()).aktifFaz

    @Test
    fun fazlarBirikimliAcilirVeKapaliFazinUcNoktasiReddedilir() = apiTesti(ekRotalar = { ornekRotalar() }) {
        fazlar = Fazlar(ayarlar)
        assertEquals(Faz.FAZ_1, aktifFaz())
        listOf("/deneme/faz-2", "/deneme/faz-3").forEach {
            val yanit = client.get(it)
            assertEquals(HttpStatusCode.NotFound, yanit.status)
            assertEquals(HataKodu.OZELLIK_KAPALI, yanit.govde(ApiHatasi.serializer()).kod)
        }

        fazSec(3)
        assertEquals(Faz.FAZ_3, aktifFaz())
        assertEquals("faz 2", client.get("/deneme/faz-2").bodyAsText())
        assertEquals("faz 3", client.get("/deneme/faz-3").bodyAsText())

        // Faz 2'ye dönünce Faz 3 kapanır, Faz 2 açık kalır.
        fazSec(2)
        assertEquals(HttpStatusCode.OK, client.get("/deneme/faz-2").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/deneme/faz-3").status)
    }

    @Test
    fun benYanitiAktifFaziTasir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        assertEquals(Faz.FAZ_1, client.get(ApiYollari.BEN) { bearerAuth(token) }.govde(BenYaniti.serializer()).aktifFaz)
        ayarlar.yaz(Ayar.AktifFaz, 2)
        assertEquals(Faz.FAZ_2, client.get(ApiYollari.BEN) { bearerAuth(token) }.govde(BenYaniti.serializer()).aktifFaz)
    }

    @Test
    fun fazYalnizcaSifreVeFormAnahtariylaDegisir() = apiTesti {
        val sifresiz = client.submitForm("${ApiYollari.YONETIM}/faz", parameters { append("faz", "3") })
        assertEquals(HttpStatusCode.Unauthorized, sifresiz.status)
        assertEquals(HttpStatusCode.Forbidden, fazSec(3, anahtar = "sahte").status)
        assertEquals(HttpStatusCode.BadRequest, fazSec(7).status)
        assertEquals(Faz.FAZ_1, aktifFaz())

        // Panelde faz seçici var; eski moderasyon adresi panele yönlenir.
        val panel = client.get(ApiYollari.YONETIM) { basicAuth("yonetici", TEST_MODERASYON_SIFRESI) }
        assertTrue("name=\"faz\"" in panel.bodyAsText())
        val eski = client.get(ApiYollari.MODERASYON)
        assertTrue(eski.status == HttpStatusCode.Found || eski.call.request.url.encodedPath == ApiYollari.YONETIM)
    }

    @Test
    fun webGirisAyarlariYalnizcaFaz3teDoner() = apiTesti {
        ayarlar.yaz(Ayar.AktifFaz, 2)
        assertEquals(null, client.get(ApiYollari.OZELLIKLER).govde(OzellikDurumu.serializer()).webGiris)
        ayarlar.yaz(Ayar.AktifFaz, 3)
        assertEquals(
            com.sosyaloyun.shared.WebGirisAyari("google-web-istemcisi", "com.sosyaloyun.web"),
            client.get(ApiYollari.OZELLIKLER).govde(OzellikDurumu.serializer()).webGiris,
        )
    }
}
