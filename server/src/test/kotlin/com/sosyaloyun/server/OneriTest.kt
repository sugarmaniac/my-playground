package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OneriListesi
import com.sosyaloyun.shared.OneriNedeni
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneriTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private val oyunlar = mutableMapOf<String, String>()
    private val adlar = mutableMapOf<String, String>()

    /** Popülerlik sırası listedeki sıra; RPG'ler ve nişancılar. */
    private suspend fun ApiTestOrtami.hazirla(vararg kisiler: String, faz: Int = 3) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        kisiler.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        val katalog = listOf(
            "Ruh" to RPG, "Kale" to RPG, "Ejder" to RPG, "Buyu" to RPG,
            "Mermi" to NISANCI, "Namlu" to NISANCI, "Sniper" to NISANCI, "Tetik" to NISANCI,
        )
        katalog.forEachIndexed { i, (ad, tur) ->
            katalogKaynagi.oyunlar += KaynakOyun(i + 1L, "$ad Oyunu", KaynakTuru.ANA_OYUN, turler = listOf(tur), platformlar = listOf("PC (Microsoft Windows)"))
        }
        katalogIceAktarici.tamIceAktar()
        katalog.forEachIndexed { i, (ad, _) ->
            val id = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(t(kisiler.first()))
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
            oyunlar[ad] = id
            adlar[id] = ad
            veritabaniPopulerlik(id, 100 - i)
        }
    }

    private fun ApiTestOrtami.veritabaniPopulerlik(id: String, deger: Int) {
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(veritabani) {
            exec("UPDATE oyun SET populerlik = $deger WHERE id = '$id'")
        }
    }

    private suspend fun ApiTestOrtami.puanla(kisi: String, oyun: String, puan: Int) {
        client.put(ApiYollari.kutuphaneGirisi(oyunlar.getValue(oyun))) {
            bearerAuth(t(kisi))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(puan = puan))
        }
    }

    private suspend fun ApiTestOrtami.oneriler(kisi: String) =
        client.get(ApiYollari.ONERILER) { bearerAuth(t(kisi)) }.govde(OneriListesi.serializer()).oneriler

    @Test
    fun benzerKullanicilarVeTurlerdenOnerirKutuphanedekileriVeGizlenenleriAtlar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        // Ayşe ile Mehmet aynı zevkte; Zeynep tam tersi.
        listOf("Ruh" to 10, "Kale" to 9, "Mermi" to 2).forEach { (o, p) -> puanla("ayse", o, p); puanla("mehmet", o, p) }
        listOf("Ruh" to 1, "Kale" to 2, "Mermi" to 10).forEach { (o, p) -> puanla("zeynep", o, p) }
        puanla("mehmet", "Buyu", 9)
        puanla("zeynep", "Namlu", 10)

        val ilk = oneriler("ayse")
        val ilkOneri = ilk.first()
        assertEquals("Buyu" to OneriNedeni.BENZER_KULLANICILAR, adlar.getValue(ilkOneri.oyun.id) to ilkOneri.neden)
        // Sevdiği tür RPG: kalan RPG nedenle önerilir; kütüphanedekiler hiç önerilmez.
        assertTrue(ilk.any { adlar[it.oyun.id] == "Ejder" && it.neden == OneriNedeni.TUR && it.ayrinti == RPG })
        assertFalse(ilk.any { adlar[it.oyun.id] in setOf("Ruh", "Kale", "Mermi") })
        assertFalse(ilk.any { adlar[it.oyun.id] == "Namlu" && it.neden == OneriNedeni.BENZER_KULLANICILAR })

        assertEquals(HttpStatusCode.NoContent, client.put(ApiYollari.ilgilenmiyorum(oyunlar.getValue("Buyu"))) { bearerAuth(t("ayse")) }.status)
        assertFalse(oneriler("ayse").any { adlar[it.oyun.id] == "Buyu" })
        // Kalıcı: tekrar istendiğinde de yok.
        assertFalse(oneriler("ayse").any { adlar[it.oyun.id] == "Buyu" })
    }

    @Test
    fun yeniKullaniciyaPopulerOyunlarOnerilir() = apiTesti {
        hazirla("ayse")
        val liste = oneriler("ayse")
        assertEquals(listOf("Ruh", "Kale", "Ejder", "Buyu", "Mermi", "Namlu", "Sniper", "Tetik"), liste.map { adlar.getValue(it.oyun.id) })
        assertTrue(liste.all { it.neden == OneriNedeni.POPULER || it.neden == OneriNedeni.PLATFORM })
    }

    @Test
    fun faz1ve2deOnerilerKapali() = apiTesti {
        hazirla("ayse", faz = 2)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.ONERILER) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)
    }

    private companion object {
        const val RPG = "Role-playing (RPG)"
        const val NISANCI = "Shooter"
    }
}
