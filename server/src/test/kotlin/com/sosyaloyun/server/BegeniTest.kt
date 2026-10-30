package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class BegeniTest {
    private lateinit var ayse: String
    private lateinit var mehmet: String
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla() {
        ayse = kayitliKullanici("ayse").oturumTokeni
        mehmet = kayitliKullanici("mehmet").oturumTokeni
        katalogKaynagi.oyunlar += KaynakOyun(1, "Beğenilen Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(ayse)
            parameter("q", "begenilen")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayit(gorunurluk: Gorunurluk = Gorunurluk.HERKES): GunlukKaydi =
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(ayse)
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "İnceleme", gorunurluk = gorunurluk))
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.detay(token: String, id: String) =
        client.get("${ApiYollari.GUNLUK}/$id") { bearerAuth(token) }

    @Test
    fun begeniVeGeriAlmaSayaciGunceller() = apiTesti {
        hazirla()
        val kayit = kayit()

        assertEquals(HttpStatusCode.NoContent, client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }.status)
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(ayse) }

        val mehmetIcin = detay(mehmet, kayit.id).govde(GunlukKaydiDetayi.serializer())
        assertEquals(2, mehmetIcin.kayit.begeniSayisi)
        assertEquals(true, mehmetIcin.kayit.begendim)
        assertEquals("ayse", mehmetIcin.sahip.kullaniciAdi)
        assertEquals("Beğenilen Oyun", mehmetIcin.oyun.ad)

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }.status)
        val sonra = detay(mehmet, kayit.id).govde(GunlukKaydiDetayi.serializer()).kayit
        assertEquals(1, sonra.begeniSayisi)
        assertEquals(false, sonra.begendim)
    }

    @Test
    fun begenenlerListelenir() = apiTesti {
        hazirla()
        val zeynep = kayitliKullanici("zeynep").oturumTokeni
        val kayit = kayit()
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(zeynep) }

        val begenenler = client.get(ApiYollari.begenenler(kayit.id)) { bearerAuth(ayse) }.govde(KullaniciListesi.serializer())

        assertEquals(setOf("mehmet", "zeynep"), begenenler.kullanicilar.map { it.kullaniciAdi }.toSet())
    }

    @Test
    fun gormeYetkisiOlmayanKayitBegenilemezVeGorulemez() = apiTesti {
        hazirla()
        val takipcilere = kayit(Gorunurluk.TAKIPCILER)
        val yalnizBen = kayit(Gorunurluk.BEN)

        listOf(takipcilere, yalnizBen).forEach { kayit ->
            val begeni = client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }
            assertEquals(HttpStatusCode.NotFound, begeni.status)
            assertEquals(ApiHatasi(HataKodu.GUNLUK_KAYDI_BULUNAMADI), begeni.govde(ApiHatasi.serializer()))
            assertEquals(HttpStatusCode.NotFound, detay(mehmet, kayit.id).status)
            assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.begenenler(kayit.id)) { bearerAuth(mehmet) }.status)
        }

        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(mehmet) }
        assertEquals(HttpStatusCode.NoContent, client.put(ApiYollari.begeni(takipcilere.id)) { bearerAuth(mehmet) }.status)
        assertEquals(HttpStatusCode.NotFound, client.put(ApiYollari.begeni(yalnizBen.id)) { bearerAuth(mehmet) }.status)
        assertEquals(HttpStatusCode.OK, detay(ayse, yalnizBen.id).status)
    }

    @Test
    fun begeniSayisiAkistaVeProfildeGorunur() = apiTesti {
        hazirla()
        val kayit = kayit()
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(mehmet) }
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }

        val akistaki = client.get(ApiYollari.AKIS) { bearerAuth(mehmet) }.govde(AkisSayfasi.serializer()).ogeler.single().gunlukKaydi!!
        val profildeki = client.get(ApiYollari.PROFILIM_GUNLUK) { bearerAuth(ayse) }.govde(GunlukSayfasi.serializer()).ogeler.single().kayit

        assertEquals(1 to true, akistaki.begeniSayisi to akistaki.begendim)
        assertEquals(1 to false, profildeki.begeniSayisi to profildeki.begendim)
    }

    @Test
    fun begeniAkisaDusmez() = apiTesti {
        hazirla()
        val kayit = kayit()
        val zeynep = kayitliKullanici("zeynep").oturumTokeni
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(zeynep) }

        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(mehmet) }

        assertEquals(emptyList(), client.get(ApiYollari.AKIS) { bearerAuth(zeynep) }.govde(AkisSayfasi.serializer()).ogeler)
    }
}
