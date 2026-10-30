package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.GizlilikIstegi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnizlemeTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Önizleme Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "onizleme")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String, inceleme: String, gorunurluk: Gorunurluk = Gorunurluk.HERKES, spoiler: Boolean = false) =
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = inceleme, gorunurluk = gorunurluk, spoiler = spoiler))
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.sayfa(yol: String) = client.get(yol).bodyAsText()

    @Test
    fun herkeseAcikKayitIcerigeOzelVeKacislanmisEtiketlerleDoner() = apiTesti {
        hazirla("ayse")
        val kayit = kayitGir("ayse", "<script>alert(1)</script> & \"harika\"")
        val html = sayfa("/kayit.html?id=${kayit.id}")
        assertTrue("<title>Önizleme Oyunu · Ayse · Sosyal Oyun</title>" in html)
        assertTrue("<meta property=\"og:description\" content=\"&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;harika&quot;\">" in html)
        assertTrue("og:url\" content=\"http://localhost/kayit.html?id=${kayit.id}\"" in html)
        assertFalse("<script>alert" in html)
        assertFalse("noindex" in html)

        // Özel ve spoiler içerik: genel etiketler ya da metinsiz açıklama.
        val ozel = sayfa("/kayit.html?id=${kayitGir("ayse", "Gizli düşünceler", Gorunurluk.TAKIPCILER).id}")
        assertTrue("<title>Günlük kaydı · Sosyal Oyun</title>" in ozel && "noindex" in ozel && "og:title" !in ozel)
        val spoiler = sayfa("/kayit.html?id=${kayitGir("ayse", "Sonunda ölüyor", spoiler = true).id}")
        assertFalse("Sonunda ölüyor" in spoiler)
        assertTrue("Ayse, Önizleme Oyunu oyununu günlüğüne yazdı." in spoiler)
        assertTrue("noindex" in sayfa("/kayit.html?id=bozuk"))
    }

    @Test
    fun sitemapYalnizcaHerkeseAcikIcerigiListeler() = apiTesti {
        hazirla("ayse", "mehmet")
        val acik = kayitGir("ayse", "Açık")
        val ozel = kayitGir("ayse", "Özel", Gorunurluk.ARKADASLAR)
        val gizlininKaydi = kayitGir("mehmet", "Gizli profil")
        client.put(ApiYollari.GIZLILIK) {
            bearerAuth(t("mehmet"))
            json(GizlilikIstegi.serializer(), GizlilikIstegi(true))
        }
        val liste = client.post(ApiYollari.LISTELER) {
            bearerAuth(t("ayse"))
            json(ListeIstegi.serializer(), ListeIstegi("Herkese", gorunurluk = ListeGorunurlugu.HERKES))
        }.govde(Liste.serializer())
        val benimListem = client.post(ApiYollari.LISTELER) {
            bearerAuth(t("ayse"))
            json(ListeIstegi.serializer(), ListeIstegi("Bana", gorunurluk = ListeGorunurlugu.BEN))
        }.govde(Liste.serializer())

        val xml = sayfa("/sitemap.xml")
        assertTrue("http://localhost/kayit.html?id=${acik.id}" in xml)
        assertTrue("profil.html?ad=ayse" in xml && "liste.html?id=${liste.id}" in xml)
        assertFalse(ozel.id in xml || gizlininKaydi.id in xml || "ad=mehmet" in xml || benimListem.id in xml)
        assertTrue("Sitemap: http://localhost/sitemap.xml" in sayfa("/robots.txt"))
    }

    @Test
    fun faz1deSayfalarNoindexVeGenelEtiketli() = apiTesti {
        hazirla("ayse", faz = 1)
        val kayit = kayitGir("ayse", "Faz 1 incelemesi")
        val html = sayfa("/kayit.html?id=${kayit.id}")
        assertTrue("noindex" in html && "<title>Günlük kaydı · Sosyal Oyun</title>" in html && "og:" !in html)
        assertTrue("Disallow: /" in sayfa("/robots.txt"))
        val sitemap = client.get("/sitemap.xml")
        assertEquals(HttpStatusCode.NotFound, sitemap.status)
        assertEquals(HataKodu.OZELLIK_KAPALI, sitemap.govde(ApiHatasi.serializer()).kod)
    }
}
