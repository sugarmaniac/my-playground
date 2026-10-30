package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilSayfasi
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

class ArkadaslikTest {
    private lateinit var ayse: String
    private lateinit var mehmet: String

    private suspend fun ApiTestOrtami.hazirla() {
        ayse = kayitliKullanici("ayse").oturumTokeni
        mehmet = kayitliKullanici("mehmet").oturumTokeni
    }

    private suspend fun ApiTestOrtami.istekGonder(token: String, ad: String): HttpResponse =
        client.post(ApiYollari.kullaniciSekmesi(ad, ApiYollari.ARKADASLIK)) { bearerAuth(token) }

    private suspend fun ApiTestOrtami.kabulEt(token: String, ad: String): HttpResponse =
        client.post(ApiYollari.arkadaslikIstegiKabul(ad)) { bearerAuth(token) }

    private suspend fun ApiTestOrtami.arkadasOl() {
        istekGonder(ayse, "mehmet")
        kabulEt(mehmet, "ayse")
    }

    private suspend fun ApiTestOrtami.profil(token: String, ad: String): ProfilSayfasi =
        client.get(ApiYollari.kullanici(ad)) { bearerAuth(token) }.govde(ProfilSayfasi.serializer())

    private suspend fun ApiTestOrtami.liste(token: String, yol: String): List<String> =
        client.get(yol) { bearerAuth(token) }.govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

    @Test
    fun istekGonderilirKabulEdilirVeArkadasListesiSayaciDogruOlur() = apiTesti {
        hazirla()

        assertEquals(HttpStatusCode.NoContent, istekGonder(ayse, "mehmet").status)
        assertEquals(ArkadaslikDurumu.ISTEK_GONDERILDI, profil(ayse, "mehmet").arkadaslik)
        assertEquals(ArkadaslikDurumu.ISTEK_ALINDI, profil(mehmet, "ayse").arkadaslik)
        assertEquals(listOf("ayse"), liste(mehmet, ApiYollari.ARKADASLIK_ISTEKLERI))

        assertEquals(HttpStatusCode.NoContent, kabulEt(mehmet, "ayse").status)

        assertEquals(ArkadaslikDurumu.ARKADAS, profil(ayse, "mehmet").arkadaslik)
        assertEquals(1, profil(ayse, "mehmet").arkadasSayisi)
        assertEquals(1, client.get(ApiYollari.PROFILIM) { bearerAuth(ayse) }.govde(ProfilSayfasi.serializer()).arkadasSayisi)
        assertEquals(listOf("mehmet"), liste(ayse, ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_ARKADASLAR)))
        assertEquals(emptyList(), liste(mehmet, ApiYollari.ARKADASLIK_ISTEKLERI))
    }

    @Test
    fun istekReddedilinceArkadaslikOlusmaz() = apiTesti {
        hazirla()
        istekGonder(ayse, "mehmet")

        val ret = client.delete(ApiYollari.arkadaslikIstegi("ayse")) { bearerAuth(mehmet) }

        assertEquals(HttpStatusCode.NoContent, ret.status)
        assertEquals(ArkadaslikDurumu.YOK, profil(ayse, "mehmet").arkadaslik)
        assertEquals(emptyList(), liste(mehmet, ApiYollari.ARKADASLIK_ISTEKLERI))
    }

    @Test
    fun karsilikliIstekArkadasYapar() = apiTesti {
        hazirla()
        istekGonder(ayse, "mehmet")

        istekGonder(mehmet, "ayse")

        assertEquals(ArkadaslikDurumu.ARKADAS, profil(ayse, "mehmet").arkadaslik)
    }

    @Test
    fun olmayanIstekKabulEdilemezVeKendineIstekGonderilemez() = apiTesti {
        hazirla()

        assertEquals(ApiHatasi(HataKodu.ARKADASLIK_ISTEGI_YOK), kabulEt(mehmet, "ayse").govde(ApiHatasi.serializer()))
        assertEquals(ApiHatasi(HataKodu.KENDINE_ISTEK_GONDEREMEZSIN), istekGonder(ayse, "ayse").govde(ApiHatasi.serializer()))
    }

    @Test
    fun arkadaslikIkiYonluTakipOlusturur() = apiTesti {
        hazirla()

        arkadasOl()

        assertEquals(true, profil(ayse, "mehmet").takipEdiyorum)
        assertEquals(true, profil(mehmet, "ayse").takipEdiyorum)
    }

    @Test
    fun arkadaskenTakiptenCikilamaz() = apiTesti {
        hazirla()
        arkadasOl()

        val yanit = client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(ayse) }

        assertEquals(HttpStatusCode.Conflict, yanit.status)
        assertEquals(ApiHatasi(HataKodu.ARKADASKEN_TAKIPTEN_CIKILAMAZ), yanit.govde(ApiHatasi.serializer()))
        assertEquals(true, profil(ayse, "mehmet").takipEdiyorum)
    }

    @Test
    fun arkadaslikBitinceTakiplerKorunur() = apiTesti {
        hazirla()
        arkadasOl()

        val bitir = client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ARKADASLIK)) { bearerAuth(ayse) }

        assertEquals(HttpStatusCode.NoContent, bitir.status)
        assertEquals(ArkadaslikDurumu.YOK, profil(ayse, "mehmet").arkadaslik)
        assertEquals(0, profil(ayse, "mehmet").arkadasSayisi)
        assertEquals(true, profil(ayse, "mehmet").takipEdiyorum)
        assertEquals(true, profil(mehmet, "ayse").takipEdiyorum)
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(ayse) }.status)
    }

    @Test
    fun gonderilenIstekGeriAlinabilir() = apiTesti {
        hazirla()
        istekGonder(ayse, "mehmet")

        client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ARKADASLIK)) { bearerAuth(ayse) }

        assertEquals(emptyList(), liste(mehmet, ApiYollari.ARKADASLIK_ISTEKLERI))
    }

    @Test
    fun arkadaslarGorunurluguYalnizcaArkadaslaraAcilir() = apiTesti {
        hazirla()
        val zeynep = kayitliKullanici("zeynep").oturumTokeni
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(zeynep) }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Ortak Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        val oyunId = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(ayse)
            parameter("q", "ortak")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        Gorunurluk.entries.forEach { gorunurluk ->
            client.post(ApiYollari.oyunGunlugu(oyunId)) {
                bearerAuth(ayse)
                json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(gorunurluk = gorunurluk))
            }
        }
        arkadasOl()

        suspend fun gorunenler(token: String) = client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_GUNLUK)) { bearerAuth(token) }
            .govde(GunlukSayfasi.serializer()).ogeler.map { it.kayit.gorunurluk }.toSet()

        assertEquals(setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER, Gorunurluk.ARKADASLAR), gorunenler(mehmet))
        assertEquals(setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER), gorunenler(zeynep))
    }

    @Test
    fun arkadasProfilindeOrtakOyunlarVeIstekListesiEslesmeleriGorunur() = apiTesti {
        hazirla()
        val adlar = listOf("İkimiz de", "Yalnız Ayşe", "Ayşe bitirdi Mehmet istiyor", "Mehmet istiyor Ayşe oynuyor", "Yalnız istek")
        adlar.forEachIndexed { i, ad -> katalogKaynagi.oyunlar += KaynakOyun(i + 1L, ad, KaynakTuru.ANA_OYUN) }
        katalogIceAktarici.tamIceAktar()
        val idler = adlar.associateWith { ad ->
            client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(ayse)
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.first { it.ad == ad }.id
        }
        suspend fun kaydet(token: String, ad: String, giris: KutuphaneGirisi) = client.put(ApiYollari.kutuphaneGirisi(idler.getValue(ad))) {
            bearerAuth(token)
            json(KutuphaneGirisi.serializer(), giris)
        }
        kaydet(ayse, "İkimiz de", KutuphaneGirisi(Durum.OYNUYORUM))
        kaydet(mehmet, "İkimiz de", KutuphaneGirisi(Durum.BITIRDIM))
        kaydet(ayse, "Yalnız Ayşe", KutuphaneGirisi(Durum.BITIRDIM))
        kaydet(ayse, "Ayşe bitirdi Mehmet istiyor", KutuphaneGirisi(Durum.BITIRDIM))
        kaydet(mehmet, "Ayşe bitirdi Mehmet istiyor", KutuphaneGirisi(istekListesinde = true))
        kaydet(ayse, "Mehmet istiyor Ayşe oynuyor", KutuphaneGirisi(Durum.OYNUYORUM))
        kaydet(mehmet, "Mehmet istiyor Ayşe oynuyor", KutuphaneGirisi(istekListesinde = true))
        kaydet(ayse, "Yalnız istek", KutuphaneGirisi(istekListesinde = true))
        kaydet(mehmet, "Yalnız istek", KutuphaneGirisi(istekListesinde = true))

        val arkadasDegil = client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_ORTAK)) { bearerAuth(mehmet) }
        assertEquals(ApiHatasi(HataKodu.ARKADAS_DEGIL), arkadasDegil.govde(ApiHatasi.serializer()))

        arkadasOl()
        val ortak = client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_ORTAK)) { bearerAuth(mehmet) }.govde(OrtakOyunlar.serializer())

        assertEquals(listOf("İkimiz de"), ortak.ortakOyunlar.map { it.ad })
        assertEquals(listOf("Ayşe bitirdi Mehmet istiyor"), ortak.istekListesiEslesmeleri.map { it.ad })
    }
}
