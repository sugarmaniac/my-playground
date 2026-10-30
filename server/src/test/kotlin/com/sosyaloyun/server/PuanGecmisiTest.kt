package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HaftalikPuanDegisimi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.PuanGecmisi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class PuanGecmisiTest {
    private val tokenler = mutableMapOf<String, String>()
    private val oyunlar = mutableMapOf<String, String>()

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        ayarlar.yaz(Ayar.ToplulukPuaniEsigi, 2)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        val adlarOyun = listOf("Alfa", "Beta", "Gama", "Delta")
        adlarOyun.forEachIndexed { i, ad -> katalogKaynagi.oyunlar += KaynakOyun(i + 1L, "$ad Oyunu", KaynakTuru.ANA_OYUN) }
        katalogIceAktarici.tamIceAktar()
        adlarOyun.forEach { ad ->
            oyunlar[ad] = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(tokenler.getValue(adlar.first()))
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        }
    }

    private suspend fun ApiTestOrtami.puanla(ad: String, oyun: String, puan: Int) {
        client.put(ApiYollari.kutuphaneGirisi(oyunlar.getValue(oyun))) {
            bearerAuth(tokenler.getValue(ad))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(puan = puan))
        }
    }

    private suspend fun ApiTestOrtami.gecmis(oyun: String) =
        client.get(ApiYollari.puanGecmisi(oyunlar.getValue(oyun))) { bearerAuth(tokenler.values.first()) }.govde(PuanGecmisi.serializer()).haftalar

    @Test
    fun anlikGoruntuDogruHaftayaBirKezYazilirFaz1deDe() = apiTesti {
        // Sahte saat pazartesi 2026-01-05 12:00 (Türkiye).
        hazirla("ayse", "mehmet", faz = 1)
        puanla("ayse", "Alfa", 8)
        puanla("mehmet", "Alfa", 6)
        assertTrue(puanGecmisleri.anlikGoruntuAl())
        assertFalse(puanGecmisleri.anlikGoruntuAl())

        puanla("ayse", "Alfa", 10)
        // Pazar 23:30 Türkiye hâlâ aynı hafta; pazartesi 00:30 yeni hafta.
        saat.simdi = Instant.parse("2026-01-11T20:30:00Z")
        assertFalse(puanGecmisleri.anlikGoruntuAl())
        saat.simdi = Instant.parse("2026-01-11T21:30:00Z")
        assertTrue(puanGecmisleri.anlikGoruntuAl())

        // Faz 1'de uç noktalar kapalı, ama görüntüler yazıldı.
        val kapali = client.get(ApiYollari.puanGecmisi(oyunlar.getValue("Alfa"))) { bearerAuth(tokenler.getValue("ayse")) }
        assertEquals(HataKodu.OZELLIK_KAPALI, kapali.govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.PUAN_DEGISIMI) { bearerAuth(tokenler.getValue("ayse")) }.govde(ApiHatasi.serializer()).kod)

        ayarlar.yaz(Ayar.AktifFaz, 2)
        val haftalar = gecmis("Alfa")
        assertEquals(12, haftalar.size)
        assertEquals(LocalDate(2025, 10, 27), haftalar.first().hafta)
        assertEquals(
            listOf(LocalDate(2026, 1, 5) to 7.0, LocalDate(2026, 1, 12) to 8.0),
            haftalar.takeLast(2).map { it.hafta to it.puan },
        )
        assertTrue(haftalar.dropLast(2).all { it.puan == null && it.puanVeren == 0 })
    }

    @Test
    fun esikAltiHaftaPuansizDoner() = apiTesti {
        hazirla("ayse", "mehmet")
        puanla("ayse", "Beta", 9)
        puanGecmisleri.anlikGoruntuAl()
        val son = gecmis("Beta").last()
        assertEquals(null, son.puan)
        assertEquals(1, son.puanVeren)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.puanGecmisi("00000000-0000-0000-0000-000000000000")) { bearerAuth(tokenler.getValue("ayse")) }.status)
    }

    @Test
    fun haftalikDegisimEnCokArtanVeAzalanlariDondurur() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        puanla("ayse", "Alfa", 6)
        puanla("mehmet", "Alfa", 6)
        puanla("ayse", "Beta", 9)
        puanla("mehmet", "Beta", 9)
        puanla("ayse", "Gama", 5)
        puanla("mehmet", "Gama", 5)
        puanla("zeynep", "Delta", 3)
        puanGecmisleri.anlikGoruntuAl()
        assertEquals(HaftalikPuanDegisimi(emptyList(), emptyList()), degisim())

        saat.ilerlet(7.days)
        puanla("ayse", "Alfa", 10)
        puanla("ayse", "Beta", 5)
        puanla("zeynep", "Delta", 10)
        puanGecmisleri.anlikGoruntuAl()

        val d = degisim()
        assertEquals(listOf("Alfa Oyunu" to 2.0), d.artanlar.map { it.oyun.ad to it.fark })
        assertEquals(listOf("Beta Oyunu" to -2.0), d.azalanlar.map { it.oyun.ad to it.fark })

        // Bu haftanın görüntüsü yoksa eski haftalar "bu hafta" diye gösterilmez.
        saat.ilerlet(14.days)
        assertEquals(HaftalikPuanDegisimi(emptyList(), emptyList()), degisim())
    }

    private suspend fun ApiTestOrtami.degisim() =
        client.get(ApiYollari.PUAN_DEGISIMI) { bearerAuth(tokenler.getValue("ayse")) }.govde(HaftalikPuanDegisimi.serializer())
}
