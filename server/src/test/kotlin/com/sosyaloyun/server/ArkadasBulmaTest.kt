package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BulunanKisiler
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.RehberEslestirmeIstegi
import com.sosyaloyun.shared.Sha256
import com.sosyaloyun.shared.SteamBaglaIstegi
import com.sosyaloyun.shared.TelefonNumarasi
import com.sosyaloyun.shared.TelefonumIstegi
import com.sosyaloyun.shared.TopluTakipIstegi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArkadasBulmaTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
    }

    private suspend fun ApiTestOrtami.numaraKaydet(ad: String, numara: String) {
        client.put(ApiYollari.TELEFONUM) {
            bearerAuth(t(ad))
            json(TelefonumIstegi.serializer(), TelefonumIstegi(TelefonNumarasi.hash(numara)!!))
        }
    }

    private suspend fun ApiTestOrtami.rehber(ad: String, numaralar: List<String>): HttpResponse =
        client.post(ApiYollari.ARKADAS_BUL_REHBER) {
            bearerAuth(t(ad))
            json(RehberEslestirmeIstegi.serializer(), RehberEslestirmeIstegi(numaralar))
        }

    @Test
    fun telefonHashiCihazdaStandartSha256IleUretilir() {
        val beklenen = MessageDigest.getInstance("SHA-256").digest("+905321234567".toByteArray()).joinToString("") { "%02x".format(it) }
        listOf("+90 532 123 45 67", "0532 123 45 67", "5321234567", "0090 (532) 123-4567").forEach {
            assertEquals("+905321234567", TelefonNumarasi.normallestir(it))
            assertEquals(beklenen, TelefonNumarasi.hash(it))
        }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".toByteArray()))
        assertNull(TelefonNumarasi.hash("acil"))
    }

    @Test
    fun rehberYalnizcaHashlerleEslesirVeAcikNumaraReddedilir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali")
        numaraKaydet("mehmet", "0532 111 22 33")
        numaraKaydet("zeynep", "+90 533 444 55 66")
        numaraKaydet("ali", "0534 777 88 99")
        client.put(ApiYollari.kullaniciSekmesi("ali", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }

        val rehberim = listOf("5321112233", "05334445566", "05347778899", "05550000000").map { TelefonNumarasi.hash(it)!! }
        val bulunanlar = rehber("ayse", rehberim).govde(BulunanKisiler.serializer()).kisiler
        assertEquals(listOf("mehmet", "zeynep"), bulunanlar.map { it.kullanici.kullaniciAdi })

        val acik = rehber("ayse", listOf("+905321112233"))
        assertEquals(HttpStatusCode.BadRequest, acik.status)
        assertEquals(HataKodu.REHBER_GECERSIZ, acik.govde(ApiHatasi.serializer()).kod)
        val acikKayit = client.put(ApiYollari.TELEFONUM) {
            bearerAuth(t("ayse"))
            json(TelefonumIstegi.serializer(), TelefonumIstegi("05321112233"))
        }
        assertEquals(HttpStatusCode.BadRequest, acikKayit.status)
    }

    @Test
    fun steamArkadaslarindanUygulamayiKullananlarEslesir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        steamKaynagi.arkadasListeleri["76561197960000001"] = listOf("76561197960000002", "76561197960009999")
        for ((ad, id) in listOf("ayse" to "76561197960000001", "mehmet" to "76561197960000002", "zeynep" to "76561197960000003")) {
            client.put(ApiYollari.STEAM) {
                bearerAuth(t(ad))
                json(SteamBaglaIstegi.serializer(), SteamBaglaIstegi(id))
            }
        }

        val bulunanlar = client.get(ApiYollari.ARKADAS_BUL_STEAM) { bearerAuth(t("ayse")) }.govde(BulunanKisiler.serializer()).kisiler
        assertEquals(listOf("mehmet"), bulunanlar.map { it.kullanici.kullaniciAdi })

        val gizli = client.get(ApiYollari.ARKADAS_BUL_STEAM) { bearerAuth(t("mehmet")) }
        assertEquals(HataKodu.STEAM_PROFILI_GIZLI, gizli.govde(ApiHatasi.serializer()).kod)
    }

    @Test
    fun steamBagliDegilse404() = apiTesti {
        hazirla("ayse")
        val yanit = client.get(ApiYollari.ARKADAS_BUL_STEAM) { bearerAuth(t("ayse")) }
        assertEquals(HataKodu.STEAM_BAGLI_DEGIL, yanit.govde(ApiHatasi.serializer()).kod)
    }

    @Test
    fun topluTakipBulunanlariTakipEderGecersizleriAtlar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali")
        client.put(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.ENGEL)) { bearerAuth(t("ali")) }

        val yanit = client.post(ApiYollari.TOPLU_TAKIP) {
            bearerAuth(t("ayse"))
            json(TopluTakipIstegi.serializer(), TopluTakipIstegi(listOf("mehmet", "zeynep", "ali", "ayse", "olmayan")))
        }

        assertEquals(HttpStatusCode.NoContent, yanit.status)
        assertEquals(2, client.get(ApiYollari.PROFILIM) { bearerAuth(t("ayse")) }.govde(ProfilSayfasi.serializer()).takipEdilenSayisi)
        numaraKaydet("mehmet", "05321112233")
        val bulunan = rehber("ayse", listOf(TelefonNumarasi.hash("05321112233")!!)).govde(BulunanKisiler.serializer()).kisiler.single()
        assertEquals(true, bulunan.takipEdiyorum)
    }
}
