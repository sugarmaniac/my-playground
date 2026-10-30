package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.TEST_WEBHOOK_SIRRI
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.shared.AnaOyunOzeti
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KatalogTest {
    private suspend fun ApiTestOrtami.ara(q: String, imlec: String? = null): OyunAramaYaniti {
        val token = girisYap("arayan").oturumTokeni
        return client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(token)
            parameter("q", q)
            imlec?.let { parameter("imlec", it) }
        }.govde(OyunAramaYaniti.serializer())
    }

    private suspend fun ApiTestOrtami.adlar(q: String) = ara(q).oyunlar.map { it.ad }

    private suspend fun ApiTestOrtami.webhook(olay: String, govde: String, sir: String = TEST_WEBHOOK_SIRRI) =
        client.post("${ApiYollari.IGDB_WEBHOOK}/$olay") {
            header("X-Secret", sir)
            contentType(ContentType.Application.Json)
            setBody(govde)
        }

    private fun ApiTestOrtami.oyunEkle(vararg oyunlar: KaynakOyun) {
        katalogKaynagi.oyunlar += oyunlar
    }

    @Test
    fun anaOyunDlcPortSurumVePaketlerDogruIceAlinir() = apiTesti {
        oyunEkle(
            KaynakOyun(100, "Kayıp Krallık", KaynakTuru.ANA_OYUN, cikisTarihi = LocalDate.of(2021, 3, 4), kapakKimligi = "co1abc", platformlar = listOf("PC")),
            KaynakOyun(101, "Kayıp Krallık: Buz Diyarı", KaynakTuru.DLC, anaOyunKaynakId = 100, platformlar = listOf("PC")),
            KaynakOyun(102, "Kayıp Krallık", KaynakTuru.PORT, anaOyunKaynakId = 100, platformlar = listOf("PlayStation 5")),
            KaynakOyun(103, "Kayıp Krallık: Altın Sürüm", KaynakTuru.ANA_OYUN, surumAnasiKaynakId = 100, platformlar = listOf("Xbox Series X|S")),
            KaynakOyun(104, "Kayıp Krallık Koleksiyonu", KaynakTuru.PAKET),
            KaynakOyun(105, "Kayıp Krallık Gerçekçi Grafik Modu", KaynakTuru.MOD),
            KaynakOyun(106, "Kayıp Krallık Remastered", KaynakTuru.REMASTER, anaOyunKaynakId = 100),
        )
        katalogIceAktarici.tamIceAktar()

        val sonuclar = ara("kayip krallik").oyunlar

        assertEquals(setOf("Kayıp Krallık", "Kayıp Krallık: Buz Diyarı", "Kayıp Krallık Remastered"), sonuclar.map { it.ad }.toSet())
        val ana = sonuclar.single { it.ad == "Kayıp Krallık" }
        assertEquals(OyunTuru.ANA_OYUN, ana.tur)
        assertEquals(2021, ana.cikisYili)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg", ana.kapakUrl)
        assertEquals(setOf("PC", "PlayStation 5", "Xbox Series X|S"), ana.platformlar.toSet())
        assertNull(ana.anaOyun)
        val dlc = sonuclar.single { it.ad == "Kayıp Krallık: Buz Diyarı" }
        assertEquals(OyunTuru.DLC, dlc.tur)
        assertEquals(AnaOyunOzeti(ana.id, "Kayıp Krallık"), dlc.anaOyun)
    }

    @Test
    fun dlcAnaOyundanOnceGelseBileBaglanir() = apiTesti {
        oyunEkle(
            KaynakOyun(10, "Genişleme", KaynakTuru.GENISLEME, anaOyunKaynakId = 20),
            KaynakOyun(20, "Temel Oyun", KaynakTuru.ANA_OYUN),
        )
        katalogIceAktarici.tamIceAktar()

        val genisleme = ara("genisleme").oyunlar.single()
        val temel = ara("temel oyun").oyunlar.single()
        assertEquals(OyunTuru.GENISLEME, genisleme.tur)
        assertEquals(AnaOyunOzeti(temel.id, "Temel Oyun"), genisleme.anaOyun)
    }

    @Test
    fun turkceKarakterliVeKaraktersizYazimlarAyniOyunuBulur() = apiTesti {
        oyunEkle(
            KaynakOyun(1, "Şövalye Çağı", KaynakTuru.ANA_OYUN),
            KaynakOyun(2, "Pokémon Kırmızı", KaynakTuru.ANA_OYUN),
            KaynakOyun(3, "İstanbul Gecesi", KaynakTuru.ANA_OYUN),
        )
        katalogIceAktarici.tamIceAktar()

        assertEquals(listOf("Şövalye Çağı"), adlar("sovalye cagi"))
        assertEquals(listOf("Şövalye Çağı"), adlar("ŞÖVALYE"))
        assertEquals(listOf("Şövalye Çağı"), adlar("çağı"))
        assertEquals(listOf("Pokémon Kırmızı"), adlar("pokemon kirmizi"))
        assertEquals(listOf("İstanbul Gecesi"), adlar("istanbul"))
        assertEquals(listOf("İstanbul Gecesi"), adlar("ISTANBUL"))
    }

    @Test
    fun butunKelimelerEslesmeliVeSiraOnemsiz() = apiTesti {
        oyunEkle(
            KaynakOyun(1, "Uzay Savaşları", KaynakTuru.ANA_OYUN),
            KaynakOyun(2, "Uzay Kaşifi", KaynakTuru.ANA_OYUN),
        )
        katalogIceAktarici.tamIceAktar()

        assertEquals(listOf("Uzay Savaşları"), adlar("savaslari uzay"))
    }

    @Test
    fun populerOyunlarOnceGelir() = apiTesti {
        oyunEkle(
            KaynakOyun(1, "Ejderha Avcısı", KaynakTuru.ANA_OYUN),
            KaynakOyun(2, "Ejderha Avcısı 2", KaynakTuru.ANA_OYUN),
            KaynakOyun(3, "Ejderha Avcısı 3", KaynakTuru.ANA_OYUN),
        )
        katalogKaynagi.populerlikler += listOf(KaynakPopulerlik(1, 0.2), KaynakPopulerlik(2, 0.9), KaynakPopulerlik(3, 0.5))
        katalogIceAktarici.tamIceAktar()
        katalogIceAktarici.populerlikleriGuncelle()

        assertEquals(listOf("Ejderha Avcısı 2", "Ejderha Avcısı 3", "Ejderha Avcısı"), adlar("ejderha"))
    }

    @Test
    fun sonuclarImlecleSayfalanir() = apiTesti {
        oyunEkle(*Array(25) { KaynakOyun(it + 1L, "Yarış ${it + 1}", KaynakTuru.ANA_OYUN) })
        katalogIceAktarici.tamIceAktar()

        val ilk = ara("yaris")
        val ikinci = ara("yaris", ilk.sonrakiImlec)

        assertEquals(20, ilk.oyunlar.size)
        assertEquals(5, ikinci.oyunlar.size)
        assertNull(ikinci.sonrakiImlec)
        assertEquals(25, (ilk.oyunlar + ikinci.oyunlar).map { it.id }.toSet().size)
    }

    @Test
    fun cokKisaAramaBosDoner() = apiTesti {
        oyunEkle(KaynakOyun(1, "Go", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()

        assertEquals(OyunAramaYaniti(emptyList(), null), ara("g"))
    }

    @Test
    fun kullaniciIstekleriKatalogKaynaginaGitmez() = apiTesti {
        oyunEkle(KaynakOyun(1, "Sessiz Tepe", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()
        val icerAlmaCagrilari = katalogKaynagi.cagriSayisi

        adlar("sessiz")
        adlar("hic olmayan oyun")

        assertEquals(icerAlmaCagrilari, katalogKaynagi.cagriSayisi)
    }

    @Test
    fun aramaOturumGerektirir() = apiTesti {
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiYollari.OYUN_ARA) { parameter("q", "oyun") }.status)
    }

    @Test
    fun webhookGuncellemesiDogruOyunaUygulanir() = apiTesti {
        oyunEkle(KaynakOyun(100, "Eski Ad", KaynakTuru.ANA_OYUN), KaynakOyun(200, "Başka Oyun", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()
        val onceki = ara("eski ad").oyunlar.single()

        katalogKaynagi.oyunlar.replaceAll {
            if (it.kaynakId == 100L) it.copy(ad = "Yeni Ad", platformlar = listOf("Nintendo Switch")) else it
        }

        val yanit = webhook("update", """{"id":100,"name":"Yeni Ad","game_type":0,"platforms":[130]}""")

        assertEquals(HttpStatusCode.OK, yanit.status)
        assertEquals(emptyList(), adlar("eski ad"))
        val guncel = ara("yeni ad").oyunlar.single()
        assertEquals(onceki.id, guncel.id)
        assertEquals(listOf("Nintendo Switch"), guncel.platformlar)
        assertEquals(listOf("Başka Oyun"), adlar("baska oyun"))
    }

    @Test
    fun webhookIleYeniOyunEklenir() = apiTesti {
        oyunEkle(KaynakOyun(300, "Taze Çıkan", KaynakTuru.REMAKE, cikisTarihi = LocalDate.of(2026, 1, 1)))

        webhook("create", """{"id":300,"name":"Taze Çıkan","game_type":8,"cover":555}""")

        val oyun = ara("taze cikan").oyunlar.single()
        assertEquals(OyunTuru.REMAKE, oyun.tur)
        assertEquals(2026, oyun.cikisYili)
    }

    @Test
    fun webhookSilmesiOyunuAramadanKaldirir() = apiTesti {
        oyunEkle(KaynakOyun(100, "Silinecek Oyun", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()

        webhook("delete", """{"id":100}""")

        assertEquals(emptyList(), adlar("silinecek"))
    }

    @Test
    fun portunSilinmesiAnaOyunuEtkilemez() = apiTesti {
        oyunEkle(
            KaynakOyun(100, "Kalıcı Oyun", KaynakTuru.ANA_OYUN),
            KaynakOyun(101, "Kalıcı Oyun", KaynakTuru.PORT, anaOyunKaynakId = 100, platformlar = listOf("PlayStation 4")),
        )
        katalogIceAktarici.tamIceAktar()

        webhook("delete", """{"id":101}""")

        assertEquals(listOf("Kalıcı Oyun"), adlar("kalici"))
    }

    @Test
    fun gecersizSirliWebhookReddedilir() = apiTesti {
        oyunEkle(KaynakOyun(100, "Korunan Oyun", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()

        val yanit = webhook("delete", """{"id":100}""", sir = "yanlis")

        assertEquals(HttpStatusCode.Unauthorized, yanit.status)
        assertEquals(listOf("Korunan Oyun"), adlar("korunan"))
    }

    @Test
    fun turuPaketeDonenOyunKatalogdanCikar() = apiTesti {
        oyunEkle(KaynakOyun(100, "Dönüşen Oyun", KaynakTuru.ANA_OYUN))
        katalogIceAktarici.tamIceAktar()

        katalogKaynagi.oyunlar.replaceAll { it.copy(tur = KaynakTuru.PAKET) }

        webhook("update", """{"id":100,"name":"Dönüşen Oyun","game_type":3}""")

        assertEquals(emptyList(), adlar("donusen"))
    }
}
