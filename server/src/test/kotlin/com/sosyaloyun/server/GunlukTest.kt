package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

class GunlukTest {
    private suspend fun ApiTestOrtami.oyun(ad: String): String {
        katalogKaynagi.oyunlar += KaynakOyun(katalogKaynagi.oyunlar.size + 1L, ad, KaynakTuru.ANA_OYUN, platformlar = listOf("PC", "PlayStation 5"))
        katalogIceAktarici.tamIceAktar()
        return client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(girisYap("katalogcu").oturumTokeni)
            parameter("q", ad)
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single { it.ad == ad }.id
    }

    private suspend fun ApiTestOrtami.gir(token: String, oyunId: String, istek: GunlukKaydiIstegi): HttpResponse =
        client.post(ApiYollari.oyunGunlugu(oyunId)) {
            bearerAuth(token)
            json(GunlukKaydiIstegi.serializer(), istek)
        }

    private suspend fun ApiTestOrtami.duzenle(token: String, kayitId: String, istek: GunlukKaydiIstegi): HttpResponse =
        client.put("${ApiYollari.GUNLUK}/$kayitId") {
            bearerAuth(token)
            json(GunlukKaydiIstegi.serializer(), istek)
        }

    private suspend fun ApiTestOrtami.puanVer(token: String, oyunId: String, giris: KutuphaneGirisi) {
        client.put(ApiYollari.kutuphaneGirisi(oyunId)) {
            bearerAuth(token)
            json(KutuphaneGirisi.serializer(), giris)
        }
    }

    private suspend fun ApiTestOrtami.detay(token: String, oyunId: String): OyunDetayi =
        client.get("${ApiYollari.OYUNLAR}/$oyunId") { bearerAuth(token) }.govde(OyunDetayi.serializer())

    private val tamIstek = GunlukKaydiIstegi(
        platform = "PlayStation 5",
        baslama = LocalDate(2026, 1, 2),
        bitis = LocalDate(2026, 1, 20),
        sureDakika = 25 * 60,
        inceleme = "Sonu çok etkileyiciydi.",
        spoiler = true,
        gorunurluk = Gorunurluk.ARKADASLAR,
        sessiz = true,
    )

    @Test
    fun gunlukKaydiButunAlanlariylaKaydedilirVePuanKopyalanir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Uzun Yolculuk")
        puanVer(token, oyunId, KutuphaneGirisi(Durum.BITIRDIM, puan = 8))

        val yanit = gir(token, oyunId, tamIstek)

        assertEquals(HttpStatusCode.Created, yanit.status)
        val kayit = yanit.govde(GunlukKaydi.serializer())
        assertEquals(oyunId, kayit.oyunId)
        assertEquals("PlayStation 5", kayit.platform)
        assertEquals(LocalDate(2026, 1, 2), kayit.baslama)
        assertEquals(LocalDate(2026, 1, 20), kayit.bitis)
        assertEquals(1500, kayit.sureDakika)
        assertEquals(8, kayit.puan)
        assertEquals("Sonu çok etkileyiciydi.", kayit.inceleme)
        assertEquals(true, kayit.spoiler)
        assertEquals(Gorunurluk.ARKADASLAR, kayit.gorunurluk)
        assertEquals(true, kayit.sessiz)
        assertEquals(saat.simdi, kayit.olusturulma)
        assertEquals(listOf(kayit), detay(token, oyunId).gunlukKayitlarim)
    }

    @Test
    fun ayniOyunIcinBirdenFazlaGunlukKaydiTutulurYeniOnce() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Tekrar Oynanan")

        val ilk = gir(token, oyunId, GunlukKaydiIstegi(inceleme = "İlk oynayış")).govde(GunlukKaydi.serializer())
        saat.ilerlet(24.hours)
        val ikinci = gir(token, oyunId, GunlukKaydiIstegi(inceleme = "İkinci oynayış")).govde(GunlukKaydi.serializer())

        assertEquals(listOf(ikinci.id, ilk.id), detay(token, oyunId).gunlukKayitlarim.map { it.id })
    }

    @Test
    fun puanKopyasiKutuphanePuaniDegisseDeKorunur() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Fikir Değişen")
        puanVer(token, oyunId, KutuphaneGirisi(puan = 6))
        val kayit = gir(token, oyunId, GunlukKaydiIstegi()).govde(GunlukKaydi.serializer())

        puanVer(token, oyunId, KutuphaneGirisi(puan = 10))
        val duzenlenmis = duzenle(token, kayit.id, GunlukKaydiIstegi(inceleme = "Düzenledim")).govde(GunlukKaydi.serializer())

        assertEquals(6, duzenlenmis.puan)
        assertEquals(6, detay(token, oyunId).gunlukKayitlarim.single().puan)
        assertEquals(10, detay(token, oyunId).kutuphaneGirisi?.puan)
    }

    @Test
    fun gunlukKaydiDuzenlenir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Düzenlenen")
        val kayit = gir(token, oyunId, GunlukKaydiIstegi(platform = "PC")).govde(GunlukKaydi.serializer())

        val yanit = duzenle(token, kayit.id, tamIstek)

        assertEquals(HttpStatusCode.OK, yanit.status)
        val duzenlenmis = detay(token, oyunId).gunlukKayitlarim.single()
        assertEquals(kayit.id, duzenlenmis.id)
        assertEquals(kayit.olusturulma, duzenlenmis.olusturulma)
        assertEquals(tamIstek.platform, duzenlenmis.platform)
        assertEquals(tamIstek.inceleme, duzenlenmis.inceleme)
        assertEquals(tamIstek.gorunurluk, duzenlenmis.gorunurluk)
    }

    @Test
    fun gunlukKaydiSilinir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Silinen Kayıt")
        val kayit = gir(token, oyunId, GunlukKaydiIstegi()).govde(GunlukKaydi.serializer())

        val yanit = client.delete("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NoContent, yanit.status)
        assertEquals(emptyList(), detay(token, oyunId).gunlukKayitlarim)
    }

    @Test
    fun baskasininGunlukKaydiDuzenlenemezVeSilinemez() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        val oyunId = oyun("Kişisel")
        val kayit = gir(ayse, oyunId, GunlukKaydiIstegi(inceleme = "Benim")).govde(GunlukKaydi.serializer())

        val duzenleme = duzenle(mehmet, kayit.id, GunlukKaydiIstegi(inceleme = "Değiştirdim"))
        val silme = client.delete("${ApiYollari.GUNLUK}/${kayit.id}") { bearerAuth(mehmet) }

        assertEquals(HttpStatusCode.NotFound, duzenleme.status)
        assertEquals(ApiHatasi(HataKodu.GUNLUK_KAYDI_BULUNAMADI), duzenleme.govde(ApiHatasi.serializer()))
        assertEquals(HttpStatusCode.NotFound, silme.status)
        assertEquals("Benim", detay(ayse, oyunId).gunlukKayitlarim.single().inceleme)
        assertEquals(emptyList(), detay(mehmet, oyunId).gunlukKayitlarim)
    }

    @Test
    fun toplamOyunSuresiGunlukKayitlarininToplamidir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Süreli Oyun")
        assertNull(detay(token, oyunId).toplamOyunSuresiDakika)

        gir(token, oyunId, GunlukKaydiIstegi(sureDakika = 90))
        val ikinci = gir(token, oyunId, GunlukKaydiIstegi(sureDakika = 600)).govde(GunlukKaydi.serializer())
        gir(token, oyunId, GunlukKaydiIstegi())
        assertEquals(690, detay(token, oyunId).toplamOyunSuresiDakika)

        duzenle(token, ikinci.id, GunlukKaydiIstegi(sureDakika = 30))
        assertEquals(120, detay(token, oyunId).toplamOyunSuresiDakika)
    }

    @Test
    fun sonSecilenPlatformSonrakiKayittaOnerilir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val ilkOyun = oyun("İlk Oyun")
        val ikinciOyun = oyun("İkinci Oyun")
        assertNull(detay(token, ikinciOyun).sonPlatform)

        gir(token, ilkOyun, GunlukKaydiIstegi(platform = "PlayStation 5"))

        assertEquals("PlayStation 5", detay(token, ikinciOyun).sonPlatform)
    }

    @Test
    fun gunlukKaydiDurumsuzKutuphaneGirisiOlusturur() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Kütüphanesiz")

        gir(token, oyunId, GunlukKaydiIstegi(inceleme = "Hızlı kayıt yapmadan"))

        assertEquals(KutuphaneGirisi(), detay(token, oyunId).kutuphaneGirisi)
    }

    @Test
    fun gunlukKaydiOlanGirisIsaretleriKaldirilsaDaKalir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Kalıcı Giriş")
        puanVer(token, oyunId, KutuphaneGirisi(Durum.OYNUYORUM))
        gir(token, oyunId, GunlukKaydiIstegi())

        puanVer(token, oyunId, KutuphaneGirisi())

        val detay = detay(token, oyunId)
        assertEquals(KutuphaneGirisi(), detay.kutuphaneGirisi)
        assertEquals(1, detay.gunlukKayitlarim.size)
    }

    @Test
    fun gecersizAlanlarReddedilir() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni
        val oyunId = oyun("Kurallı")

        suspend fun hata(istek: GunlukKaydiIstegi) = gir(token, oyunId, istek).govde(ApiHatasi.serializer()).kod

        assertEquals(HataKodu.TARIH_GECERSIZ, hata(GunlukKaydiIstegi(baslama = LocalDate(2026, 2, 1), bitis = LocalDate(2026, 1, 1))))
        assertEquals(HataKodu.SURE_GECERSIZ, hata(GunlukKaydiIstegi(sureDakika = -5)))
        assertEquals(HataKodu.INCELEME_COK_UZUN, hata(GunlukKaydiIstegi(inceleme = "a".repeat(10_001))))
        assertEquals(HataKodu.PLATFORM_GECERSIZ, hata(GunlukKaydiIstegi(platform = "  ")))
        assertEquals(emptyList(), detay(token, oyunId).gunlukKayitlarim)
    }

    @Test
    fun olmayanOyunaGunlukKaydiGirilemez() = apiTesti {
        val token = kayitliKullanici("ayse").oturumTokeni

        val yanit = gir(token, java.util.UUID.randomUUID().toString(), GunlukKaydiIstegi())

        assertEquals(HttpStatusCode.NotFound, yanit.status)
        assertEquals(ApiHatasi(HataKodu.OYUN_BULUNAMADI), yanit.govde(ApiHatasi.serializer()))
    }
}
