package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.shared.AnaOyunOzeti
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.Magaza
import com.sosyaloyun.shared.MagazaLinki
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunTuru
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OyunDetayiTest {
    private suspend fun ApiTestOrtami.token() = girisYap("okuyucu").oturumTokeni

    private suspend fun ApiTestOrtami.oyunId(ad: String): String =
        client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(token())
            parameter("q", ad)
        }.govde(OyunAramaYaniti.serializer()).oyunlar.first { it.ad == ad }.id

    private suspend fun ApiTestOrtami.detayIstegi(id: String): HttpResponse =
        client.get("${ApiYollari.OYUNLAR}/$id") { bearerAuth(token()) }

    private suspend fun ApiTestOrtami.detay(ad: String): OyunDetayi = detayIstegi(oyunId(ad)).govde(OyunDetayi.serializer())

    private suspend fun ApiTestOrtami.katalogKur() {
        katalogKaynagi.oyunlar += listOf(
            KaynakOyun(
                kaynakId = 100,
                ad = "Gece Avcısı",
                tur = KaynakTuru.ANA_OYUN,
                cikisTarihi = LocalDate.of(2020, 5, 1),
                platformlar = listOf("PC"),
                steamUygulamaIdleri = listOf(4242),
                ozet = "Karanlıkta geçen bir aksiyon oyunu.",
                slug = "gece-avcisi",
                gelistiriciler = listOf("Ay Stüdyosu"),
                yayincilar = listOf("Büyük Yayıncı", "Küçük Yayıncı"),
                turler = listOf("Adventure", "Role-playing (RPG)"),
                magazaLinkleri = listOf(
                    KaynakMagazaLinki(Magaza.RESMI_SITE, "https://geceavcisi.example"),
                    KaynakMagazaLinki(Magaza.GOG, "https://www.gog.com/game/gece_avcisi"),
                ),
            ),
            KaynakOyun(101, "Gece Avcısı: Şafak", KaynakTuru.GENISLEME, anaOyunKaynakId = 100, cikisTarihi = LocalDate.of(2022, 1, 1)),
            KaynakOyun(102, "Gece Avcısı: Kostüm Paketi", KaynakTuru.DLC, anaOyunKaynakId = 100, cikisTarihi = LocalDate.of(2021, 1, 1)),
            KaynakOyun(103, "Gece Avcısı", KaynakTuru.PORT, anaOyunKaynakId = 100, platformlar = listOf("PlayStation 5")),
            KaynakOyun(104, "Gece Avcısı: Silinmiş DLC", KaynakTuru.DLC, anaOyunKaynakId = 100),
        )
        katalogIceAktarici.tamIceAktar()
        katalogIceAktarici.kaldir(104)
    }

    @Test
    fun oyunDetayiGelistiriciYayinciTurVeMagazaLinkleriniGosterir() = apiTesti {
        katalogKur()

        val detay = detay("Gece Avcısı")

        assertEquals("Gece Avcısı", detay.oyun.ad)
        assertEquals(OyunTuru.ANA_OYUN, detay.oyun.tur)
        assertEquals(2020, detay.oyun.cikisYili)
        assertEquals(listOf("PC", "PlayStation 5"), detay.oyun.platformlar)
        assertEquals("Karanlıkta geçen bir aksiyon oyunu.", detay.aciklama)
        assertEquals(listOf("Ay Stüdyosu"), detay.gelistiriciler)
        assertEquals(listOf("Büyük Yayıncı", "Küçük Yayıncı"), detay.yayincilar)
        assertEquals(listOf("Adventure", "Role-playing (RPG)"), detay.turler)
        assertEquals(
            listOf(
                MagazaLinki(Magaza.RESMI_SITE, "https://geceavcisi.example"),
                MagazaLinki(Magaza.STEAM, "https://store.steampowered.com/app/4242"),
                MagazaLinki(Magaza.GOG, "https://www.gog.com/game/gece_avcisi"),
            ),
            detay.magazaLinkleri,
        )
        assertEquals("https://www.igdb.com/games/gece-avcisi", detay.kaynakUrl)
    }

    @Test
    fun anaOyunDetayiDlcVeGenislemeleriCikisSirasiylaGosterir() = apiTesti {
        katalogKur()

        val eklentiler = detay("Gece Avcısı").eklentiler

        assertEquals(listOf("Gece Avcısı: Kostüm Paketi", "Gece Avcısı: Şafak"), eklentiler.map { it.ad })
    }

    @Test
    fun genislemeDetayiAnaOyunaBaglanir() = apiTesti {
        katalogKur()
        val anaId = oyunId("Gece Avcısı")

        val genisleme = detay("Gece Avcısı: Şafak")

        assertEquals(AnaOyunOzeti(anaId, "Gece Avcısı"), genisleme.oyun.anaOyun)
        assertEquals(emptyList(), genisleme.eklentiler)
    }

    @Test
    fun olmayanOyunBulunamadiDoner() = apiTesti {
        val yanit = detayIstegi(UUID.randomUUID().toString())

        assertEquals(HttpStatusCode.NotFound, yanit.status)
        assertEquals(ApiHatasi(HataKodu.OYUN_BULUNAMADI), yanit.govde(ApiHatasi.serializer()))
        assertEquals(HttpStatusCode.NotFound, detayIstegi("uuid-degil").status)
    }

    @Test
    fun kaynaktaSilinenOyunBulunamadiDoner() = apiTesti {
        katalogKaynagi.oyunlar += KaynakOyun(1, "Gidecek Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        val id = oyunId("Gidecek Oyun")

        katalogIceAktarici.kaldir(1)

        assertEquals(HttpStatusCode.NotFound, detayIstegi(id).status)
    }

    @Test
    fun igdbSlugYoksaAtifIgdbAnaSayfasinaGider() = apiTesti {
        katalogKaynagi.oyunlar += KaynakOyun(1, "Slugsuz Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()

        assertEquals("https://www.igdb.com", detay("Slugsuz Oyun").kaynakUrl)
    }

    @Test
    fun detayOturumGerektirir() = apiTesti {
        assertEquals(HttpStatusCode.Unauthorized, client.get("${ApiYollari.OYUNLAR}/${UUID.randomUUID()}").status)
    }
}
