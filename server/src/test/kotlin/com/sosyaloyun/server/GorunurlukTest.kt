package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlin.test.Test
import kotlin.test.assertEquals

/** Başkasının profilinde günlük kayıtlarının görünürlüğü (CONTEXT.md: Görünürlük). */
class GorunurlukTest {
    private lateinit var ayse: String

    /** Ayşe'nin her görünürlük seviyesinde bir incelemeli günlük kaydı ve kütüphanesi var. */
    private suspend fun ApiTestOrtami.hazirla() {
        ayse = kayitliKullanici("ayse").oturumTokeni
        katalogKaynagi.oyunlar += KaynakOyun(1, "Paylaşılan Oyun", KaynakTuru.ANA_OYUN)
        katalogKaynagi.oyunlar += KaynakOyun(2, "İstenen Oyun", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        val oyunlar = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(ayse)
            parameter("q", "oyun")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.associate { it.ad to it.id }
        client.put(ApiYollari.kutuphaneGirisi(oyunlar.getValue("Paylaşılan Oyun"))) {
            bearerAuth(ayse)
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(Durum.BITIRDIM))
        }
        client.put(ApiYollari.kutuphaneGirisi(oyunlar.getValue("İstenen Oyun"))) {
            bearerAuth(ayse)
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(istekListesinde = true))
        }
        Gorunurluk.entries.forEach { gorunurluk ->
            client.post(ApiYollari.oyunGunlugu(oyunlar.getValue("Paylaşılan Oyun"))) {
                bearerAuth(ayse)
                json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = gorunurluk.name, gorunurluk = gorunurluk))
            }
        }
    }

    private suspend fun ApiTestOrtami.gorunenler(token: String, sekme: String): Set<Gorunurluk> =
        client.get(ApiYollari.kullaniciSekmesi("ayse", sekme)) { bearerAuth(token) }
            .govde(GunlukSayfasi.serializer()).ogeler.map { it.kayit.gorunurluk }.toSet()

    @Test
    fun sahibiButunKayitlariniGorur() = apiTesti {
        hazirla()
        assertEquals(Gorunurluk.entries.toSet(), gorunenler(ayse, ApiYollari.SEKME_GUNLUK))
        assertEquals(Gorunurluk.entries.toSet(), gorunenler(ayse, ApiYollari.SEKME_INCELEMELER))
    }

    @Test
    fun takipEtmeyenYalnizcaHerkeseAcikKayitlariGorur() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni

        assertEquals(setOf(Gorunurluk.HERKES), gorunenler(mehmet, ApiYollari.SEKME_GUNLUK))
        assertEquals(setOf(Gorunurluk.HERKES), gorunenler(mehmet, ApiYollari.SEKME_INCELEMELER))
    }

    @Test
    fun takipciHerkeseVeTakipcilereAcikKayitlariGorur() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(mehmet) }

        assertEquals(setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER), gorunenler(mehmet, ApiYollari.SEKME_GUNLUK))
        assertEquals(setOf(Gorunurluk.HERKES, Gorunurluk.TAKIPCILER), gorunenler(mehmet, ApiYollari.SEKME_INCELEMELER))
    }

    @Test
    fun takipEdilenTakipEdeninTakipcilereAcikKaydiniGormez() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(ayse) }

        assertEquals(setOf(Gorunurluk.HERKES), gorunenler(mehmet, ApiYollari.SEKME_GUNLUK))
    }

    @Test
    fun kutuphaneVeIstekListesiProfilDuzeyindeHerkeseAciktir() = apiTesti {
        hazirla()
        val mehmet = kayitliKullanici("mehmet").oturumTokeni

        val oyunlar = client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_OYUNLAR)) { bearerAuth(mehmet) }
            .govde(KutuphaneSayfasi.serializer()).ogeler.map { it.oyun.ad }
        val istekListesi = client.get(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_ISTEK_LISTESI)) { bearerAuth(mehmet) }
            .govde(KutuphaneSayfasi.serializer()).ogeler.map { it.oyun.ad }

        assertEquals(listOf("Paylaşılan Oyun"), oyunlar)
        assertEquals(listOf("İstenen Oyun"), istekListesi)
    }
}
