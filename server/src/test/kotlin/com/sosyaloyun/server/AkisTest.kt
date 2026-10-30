package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.YeniOlaySayisi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class AkisTest {
    private lateinit var izleyen: String
    private lateinit var ayse: String
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla() {
        izleyen = kayitliKullanici("izleyen").oturumTokeni
        ayse = kayitliKullanici("ayse").oturumTokeni
        katalogKaynagi.oyunlar += KaynakOyun(1, "Akış Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(izleyen)
            parameter("q", "akis")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        takipEt(izleyen, "ayse")
    }

    private suspend fun ApiTestOrtami.takipEt(token: String, ad: String) {
        client.post(ApiYollari.kullaniciSekmesi(ad, ApiYollari.TAKIP)) { bearerAuth(token) }
    }

    private suspend fun ApiTestOrtami.arkadasOl(a: String, aAdi: String, b: String, bAdi: String) {
        client.post(ApiYollari.kullaniciSekmesi(bAdi, ApiYollari.ARKADASLIK)) { bearerAuth(a) }
        client.post(ApiYollari.arkadaslikIstegiKabul(aAdi)) { bearerAuth(b) }
    }

    private suspend fun ApiTestOrtami.gunluk(token: String, istek: GunlukKaydiIstegi): GunlukKaydi {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(token)
            json(GunlukKaydiIstegi.serializer(), istek)
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.kutuphane(token: String, giris: KutuphaneGirisi) {
        saat.ilerlet(1.minutes)
        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(token)
            json(KutuphaneGirisi.serializer(), giris)
        }
    }

    private suspend fun ApiTestOrtami.akis(token: String = izleyen, sekme: String? = null, imlec: String? = null): AkisSayfasi =
        client.get(ApiYollari.AKIS) {
            bearerAuth(token)
            sekme?.let { parameter("sekme", it) }
            imlec?.let { parameter("imlec", it) }
        }.govde(AkisSayfasi.serializer())

    private suspend fun ApiTestOrtami.turler(token: String = izleyen, sekme: String? = null) = akis(token, sekme).ogeler.map { it.tur }

    @Test
    fun takipEdilenKisininGunlukKaydiAkisaDuser() = apiTesti {
        hazirla()
        val kayit = gunluk(ayse, GunlukKaydiIstegi(inceleme = "Harika"))

        val oge = akis().ogeler.single()

        assertEquals(AkisOlayTuru.GUNLUK_KAYDI, oge.tur)
        assertEquals("ayse", oge.kisi.kullaniciAdi)
        assertEquals("Akış Oyunu", oge.oyun?.ad)
        assertEquals(kayit, oge.gunlukKaydi)
        assertEquals(kayit.olusturulma, oge.zaman)
    }

    @Test
    fun sessizKayitAkisaDusmez() = apiTesti {
        hazirla()
        gunluk(ayse, GunlukKaydiIstegi(sessiz = true))

        assertEquals(emptyList(), akis().ogeler)
    }

    @Test
    fun yalnizcaOynuyorumaGecisAkisaDuser() = apiTesti {
        hazirla()
        kutuphane(ayse, KutuphaneGirisi(istekListesinde = true))
        kutuphane(ayse, KutuphaneGirisi(Durum.OYNUYORUM, istekListesinde = true))
        kutuphane(ayse, KutuphaneGirisi(Durum.OYNUYORUM, puan = 8))
        kutuphane(ayse, KutuphaneGirisi(Durum.BITIRDIM, puan = 9))

        val ogeler = akis().ogeler

        assertEquals(listOf(AkisOlayTuru.OYNAMAYA_BASLADI), ogeler.map { it.tur })
        assertEquals("Akış Oyunu", ogeler.single().oyun?.ad)
    }

    @Test
    fun arkadaslikKurulmasiAkisaDuserTakipDusmez() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        takipEt(ayse, "izleyen")
        arkadasOl(ayse, "ayse", mehmet, "mehmet")

        val oge = akis().ogeler.single()

        assertEquals(AkisOlayTuru.ARKADAS_OLDU, oge.tur)
        assertEquals(setOf("ayse", "mehmet"), setOf(oge.kisi.kullaniciAdi, oge.arkadas?.kullaniciAdi))
    }

    @Test
    fun takipEdilmeyeninVeKendiOlaylarimDusmez() = apiTesti {
        hazirla()
        val yabanci = kayitliKullanici("yabanci").oturumTokeni
        gunluk(yabanci, GunlukKaydiIstegi())
        gunluk(izleyen, GunlukKaydiIstegi())

        assertEquals(emptyList(), akis().ogeler)
    }

    @Test
    fun gorunurlukAkistaUygulanir() = apiTesti {
        hazirla()
        Gorunurluk.entries.forEach { gunluk(ayse, GunlukKaydiIstegi(inceleme = it.name, gorunurluk = it)) }

        assertEquals(setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER), akis().ogeler.mapNotNull { it.gunlukKaydi?.gorunurluk }.toSet())

        arkadasOl(izleyen, "izleyen", ayse, "ayse")
        assertEquals(
            setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER, Gorunurluk.ARKADASLAR),
            akis().ogeler.mapNotNull { it.gunlukKaydi?.gorunurluk }.toSet(),
        )
    }

    @Test
    fun arkadaslarSekmesiYalnizcaArkadaslarinOlaylariniGosterir() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        takipEt(izleyen, "mehmet")
        arkadasOl(izleyen, "izleyen", mehmet, "mehmet")
        gunluk(ayse, GunlukKaydiIstegi(inceleme = "Ayşe"))
        gunluk(mehmet, GunlukKaydiIstegi(inceleme = "Mehmet"))

        assertEquals(listOf("Mehmet", "Ayşe"), akis(sekme = "takip").ogeler.mapNotNull { it.gunlukKaydi?.inceleme })
        assertEquals(listOf("Mehmet"), akis(sekme = "arkadaslar").ogeler.mapNotNull { it.gunlukKaydi?.inceleme })
    }

    @Test
    fun akisYenidenEskiyeImlecleSayfalanir() = apiTesti {
        hazirla()
        repeat(25) { gunluk(ayse, GunlukKaydiIstegi(inceleme = "$it")) }

        val ilk = akis()
        gunluk(ayse, GunlukKaydiIstegi(inceleme = "araya giren"))
        val ikinci = akis(imlec = ilk.sonrakiImlec)

        assertEquals((24 downTo 5).map { "$it" }, ilk.ogeler.map { it.gunlukKaydi?.inceleme })
        assertEquals((4 downTo 0).map { "$it" }, ikinci.ogeler.map { it.gunlukKaydi?.inceleme })
        assertNull(ikinci.sonrakiImlec)
    }

    @Test
    fun yeniOlaySayisiUstUyariIcinSorulur() = apiTesti {
        hazirla()
        gunluk(ayse, GunlukKaydiIstegi())
        val enYeni = akis().enYeniImlec

        gunluk(ayse, GunlukKaydiIstegi())
        kutuphane(ayse, KutuphaneGirisi(Durum.OYNUYORUM))

        val yeni = client.get(ApiYollari.AKIS_YENI) {
            bearerAuth(izleyen)
            parameter("sonra", enYeni)
        }.govde(YeniOlaySayisi.serializer())
        assertEquals(YeniOlaySayisi(2), yeni)
    }

    @Test
    fun silinenKayitAkistanKalkarSessizeAlinanDaGizlenir() = apiTesti {
        hazirla()
        val silinecek = gunluk(ayse, GunlukKaydiIstegi(inceleme = "silinecek"))
        val sessizlesecek = gunluk(ayse, GunlukKaydiIstegi(inceleme = "sessizleşecek"))

        client.delete("${ApiYollari.GUNLUK}/${silinecek.id}") { bearerAuth(ayse) }
        client.put("${ApiYollari.GUNLUK}/${sessizlesecek.id}") {
            bearerAuth(ayse)
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "sessizleşecek", sessiz = true))
        }

        assertEquals(emptyList(), akis().ogeler)
    }

    @Test
    fun bosAkistaTakipOnerileriGelir() = apiTesti {
        val yeni = kayitliKullanici("yeni").oturumTokeni
        val populer = kayitliKullanici("populer").oturumTokeni
        val diger = kayitliKullanici("diger").oturumTokeni
        kayitliKullanici("sessiz")
        takipEt(diger, "populer")
        takipEt(populer, "diger")
        takipEt(yeni, "diger")

        val oneriler = client.get(ApiYollari.AKIS_ONERILER) { bearerAuth(yeni) }.govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

        assertEquals("populer", oneriler.first())
        assertEquals(false, "yeni" in oneriler)
        assertEquals(false, "diger" in oneriler)
    }

    @Test
    fun akisKartindanIstekListesineTekDokunuslaEklenir() = apiTesti {
        hazirla()
        kutuphane(izleyen, KutuphaneGirisi(Durum.BIRAKTIM, puan = 4))
        gunluk(ayse, GunlukKaydiIstegi())
        assertEquals(false, akis().ogeler.single().istekListemde)

        val yanit = client.post(ApiYollari.istekListesi(oyun)) { bearerAuth(izleyen) }

        assertEquals(HttpStatusCode.NoContent, yanit.status)
        assertEquals(true, akis().ogeler.single().istekListemde)
        val giris = client.get("${ApiYollari.OYUNLAR}/$oyun") { bearerAuth(izleyen) }.govde(OyunDetayi.serializer()).kutuphaneGirisi
        assertEquals(KutuphaneGirisi(Durum.BIRAKTIM, puan = 4, istekListesinde = true), giris)
    }

    @Test
    fun istekListesiEklemesiAkisaDusmez() = apiTesti {
        hazirla()
        client.post(ApiYollari.istekListesi(oyun)) { bearerAuth(ayse) }
        assertEquals(emptyList(), akis().ogeler)
    }
}
