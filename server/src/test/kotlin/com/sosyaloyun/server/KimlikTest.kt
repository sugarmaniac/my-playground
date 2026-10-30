package com.sosyaloyun.server

import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KimlikSaglayici
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KimlikTest {
    @Test
    fun ilkGirisHesapOlustururSonrakiGirisAyniHesabiAcar() = apiTesti {
        val ilk = girisYap(haricKimlik = "apple-123", saglayici = KimlikSaglayici.APPLE)
        val ikinci = girisYap(haricKimlik = "apple-123", saglayici = KimlikSaglayici.APPLE)

        assertTrue(ilk.yeniKullanici)
        assertFalse(ikinci.yeniKullanici)
        assertEquals(ilk.kullaniciId, ikinci.kullaniciId)
        assertNotEquals(ilk.oturumTokeni, ikinci.oturumTokeni)
    }

    @Test
    fun ayniKimlikleEszamanliIlkGirislerTekHesapAcar() = apiTesti {
        val girisler = coroutineScope {
            List(5) { async { girisYap(haricKimlik = "cift-dokunma") } }.awaitAll()
        }

        assertEquals(1, girisler.map { it.kullaniciId }.toSet().size)
        assertEquals(1, girisler.count { it.yeniKullanici })
    }

    @Test
    fun appleVeGoogleKimlikleriAyriHesaplardir() = apiTesti {
        val apple = girisYap(haricKimlik = "ayni-kimlik", saglayici = KimlikSaglayici.APPLE)
        val google = girisYap(haricKimlik = "ayni-kimlik", saglayici = KimlikSaglayici.GOOGLE)

        assertTrue(google.yeniKullanici)
        assertNotEquals(apple.kullaniciId, google.kullaniciId)
    }

    @Test
    fun gecersizKimlikTokeniReddedilir() = apiTesti {
        val yanit = client.post(ApiYollari.OTURUM) {
            contentType(ContentType.Application.Json)
            setBody(ApiJson.encodeToString(GirisIstegi.serializer(), GirisIstegi(KimlikSaglayici.GOOGLE, "uydurma-token")))
        }

        assertEquals(HttpStatusCode.Unauthorized, yanit.status)
        assertEquals(ApiHatasi(HataKodu.KIMLIK_TOKENI_GECERSIZ), ApiJson.decodeFromString(ApiHatasi.serializer(), yanit.bodyAsText()))
    }

    @Test
    fun oturumTokeniKorumaliUcNoktayiAcar() = apiTesti {
        val giris = girisYap()

        val yanit = client.get(ApiYollari.BEN) { bearerAuth(giris.oturumTokeni) }

        assertEquals(HttpStatusCode.OK, yanit.status)
        assertEquals(BenYaniti(giris.kullaniciId, davetKabulEdildi = false, profil = null), ApiJson.decodeFromString(BenYaniti.serializer(), yanit.bodyAsText()))
    }

    @Test
    fun kimligiDogrulanmamisIstekKorumaliUcNoktadaReddedilir() = apiTesti {
        val tokensiz = client.get(ApiYollari.BEN)
        val uydurma = client.get(ApiYollari.BEN) { bearerAuth("uydurma-oturum") }

        assertEquals(HttpStatusCode.Unauthorized, tokensiz.status)
        assertEquals(ApiHatasi(HataKodu.OTURUM_GEREKLI), ApiJson.decodeFromString(ApiHatasi.serializer(), tokensiz.bodyAsText()))
        assertEquals(HttpStatusCode.Unauthorized, uydurma.status)
    }

    @Test
    fun cikisYapilincaOturumTokeniGecersizOlur() = apiTesti {
        val giris = girisYap()

        val cikis = client.delete(ApiYollari.OTURUM) { bearerAuth(giris.oturumTokeni) }
        val sonra = client.get(ApiYollari.BEN) { bearerAuth(giris.oturumTokeni) }

        assertEquals(HttpStatusCode.NoContent, cikis.status)
        assertEquals(HttpStatusCode.Unauthorized, sonra.status)
    }
}
