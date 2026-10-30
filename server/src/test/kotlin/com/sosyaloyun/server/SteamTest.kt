package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.PopulerOyunlar
import com.sosyaloyun.shared.SteamBaglaIstegi
import com.sosyaloyun.shared.SteamBaglantisi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
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
import kotlin.test.assertTrue

class SteamTest {
    private lateinit var ayse: String
    private lateinit var mehmet: String
    private val oyunIdleri = mutableMapOf<String, String>()

    /** Katalog: her oyun bir Steam uygulamasına eşli; "Sürüm" aynı oyunun ikinci uygulaması. */
    private suspend fun ApiTestOrtami.hazirla() {
        ayse = kayitliKullanici("ayse").oturumTokeni
        mehmet = kayitliKullanici("mehmet").oturumTokeni
        katalogKaynagi.oyunlar += KaynakOyun(1, "Uzay Gemisi", KaynakTuru.ANA_OYUN, steamUygulamaIdleri = listOf(100, 101))
        katalogKaynagi.oyunlar += KaynakOyun(2, "Orman Macerası", KaynakTuru.ANA_OYUN, steamUygulamaIdleri = listOf(200))
        katalogKaynagi.oyunlar += KaynakOyun(3, "Hiç Açılmamış", KaynakTuru.ANA_OYUN, steamUygulamaIdleri = listOf(300))
        katalogKaynagi.oyunlar += KaynakOyun(4, "Konsol Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        listOf("Uzay Gemisi", "Orman Macerası", "Hiç Açılmamış", "Konsol Oyunu").forEach { ad ->
            oyunIdleri[ad] = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(ayse)
                parameter("q", ad)
            }.govde(com.sosyaloyun.shared.OyunAramaYaniti.serializer()).oyunlar.first { it.ad == ad }.id
        }
        steamKaynagi.ozelAdlar["ayseoyunda"] = AYSE_STEAM
        steamKaynagi.hesaplar[AYSE_STEAM] = listOf(
            SteamOyunu(100, 300),
            SteamOyunu(101, 60),
            SteamOyunu(200, 90),
            SteamOyunu(300, 0),
            SteamOyunu(999, 500),
        )
    }

    private suspend fun ApiTestOrtami.bagla(token: String, girdi: String): HttpResponse =
        client.put(ApiYollari.STEAM) {
            bearerAuth(token)
            json(SteamBaglaIstegi.serializer(), SteamBaglaIstegi(girdi))
        }

    private suspend fun ApiTestOrtami.detay(ad: String): OyunDetayi =
        client.get("${ApiYollari.OYUNLAR}/${oyunIdleri.getValue(ad)}") { bearerAuth(ayse) }.govde(OyunDetayi.serializer())

    private suspend fun ApiTestOrtami.durumsuzlar(): List<String> =
        client.get(ApiYollari.PROFILIM_OYUNLAR) {
            bearerAuth(ayse)
            parameter("durum", "durumsuz")
        }.govde(KutuphaneSayfasi.serializer()).ogeler.map { it.oyun.ad }

    @Test
    fun acilmisOyunlarDurumsuzGirisOlurAcilmamislarAlinmaz() = apiTesti {
        hazirla()

        val sonuc = bagla(ayse, "https://steamcommunity.com/id/ayseoyunda/").govde(SteamIceAktarmaSonucu.serializer())

        assertEquals(SteamBaglantisi(AYSE_STEAM), sonuc.baglanti)
        assertEquals(2, sonuc.iceAktarilanSayisi)
        assertEquals(listOf("Uzay Gemisi", "Orman Macerası"), sonuc.enCokOynananlar.map { it.oyun.ad })
        assertTrue(sonuc.enCokOynananlar.all { it.giris.durum == null })
        assertEquals(setOf("Uzay Gemisi", "Orman Macerası"), durumsuzlar().toSet())
        assertEquals(360, detay("Uzay Gemisi").toplamOyunSuresiDakika)
        assertNull(detay("Hiç Açılmamış").kutuphaneGirisi)
        assertEquals(SteamBaglantisi(AYSE_STEAM), client.get(ApiYollari.STEAM) { bearerAuth(ayse) }.govde(SteamBaglantisi.serializer()))
    }

    @Test
    fun iceAktarmaVeGunlukGuncellemeAkisaDusmez() = apiTesti {
        hazirla()
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(mehmet) }

        bagla(ayse, AYSE_STEAM)
        steamKaynagi.hesaplar[AYSE_STEAM] = listOf(SteamOyunu(100, 400), SteamOyunu(300, 15))
        steam.hepsiniGuncelle()

        assertEquals(emptyList(), client.get(ApiYollari.AKIS) { bearerAuth(mehmet) }.govde(AkisSayfasi.serializer()).ogeler)
        assertEquals(400, detay("Uzay Gemisi").toplamOyunSuresiDakika)
        assertEquals(setOf("Uzay Gemisi", "Orman Macerası", "Hiç Açılmamış"), durumsuzlar().toSet())
    }

    @Test
    fun toplamSureSteamSuresiArtiSteamDisiGunlukKayitlari() = apiTesti {
        hazirla()
        bagla(ayse, AYSE_STEAM)
        for ((platform, sure) in listOf("PC (Microsoft Windows)" to 45, "PlayStation 5" to 120, null to 30)) {
            client.post(ApiYollari.oyunGunlugu(oyunIdleri.getValue("Orman Macerası"))) {
                bearerAuth(ayse)
                json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(platform = platform, sureDakika = sure))
            }
        }

        assertEquals(90 + 120 + 30, detay("Orman Macerası").toplamOyunSuresiDakika)
    }

    @Test
    fun durumsuzGirisinDurumuSonradanSecilirVeBosaltilincaSilinmez() = apiTesti {
        hazirla()
        bagla(ayse, AYSE_STEAM)
        val oyun = oyunIdleri.getValue("Orman Macerası")

        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(ayse)
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(durum = Durum.BITIRDIM))
        }
        assertEquals(listOf("Uzay Gemisi"), durumsuzlar())

        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(ayse)
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi())
        }
        assertEquals(setOf("Uzay Gemisi", "Orman Macerası"), durumsuzlar().toSet())
    }

    @Test
    fun gizliProfilAciklayiciHataVerir() = apiTesti {
        hazirla()
        steamKaynagi.gizliHesaplar += AYSE_STEAM

        val yanit = bagla(ayse, "https://steamcommunity.com/profiles/$AYSE_STEAM")

        assertEquals(HttpStatusCode.Conflict, yanit.status)
        assertEquals(HataKodu.STEAM_PROFILI_GIZLI, yanit.govde(ApiHatasi.serializer()).kod)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.STEAM) { bearerAuth(ayse) }.status)
    }

    @Test
    fun bulunamayanVeBaskasinaBagliHesap() = apiTesti {
        hazirla()
        assertEquals(HataKodu.STEAM_HESABI_BULUNAMADI, bagla(ayse, "olmayanad").govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.STEAM_HESABI_BULUNAMADI, bagla(ayse, "bu bir profil değil!").govde(ApiHatasi.serializer()).kod)

        bagla(ayse, AYSE_STEAM)
        assertEquals(HataKodu.STEAM_HESABI_BASKA_KULLANICIDA, bagla(mehmet, AYSE_STEAM).govde(ApiHatasi.serializer()).kod)
    }

    @Test
    fun baglantiKaldirilincaGirislerKalirGuncellenmez() = apiTesti {
        hazirla()
        bagla(ayse, AYSE_STEAM)

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.STEAM) { bearerAuth(ayse) }.status)
        steamKaynagi.hesaplar[AYSE_STEAM] = listOf(SteamOyunu(100, 1000))
        steam.hepsiniGuncelle()

        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.STEAM) { bearerAuth(ayse) }.status)
        assertEquals(360, detay("Uzay Gemisi").toplamOyunSuresiDakika)
    }

    @Test
    fun onboardingSteamliVeSteamsizTamamlanir() = apiTesti {
        hazirla()
        katalogKaynagi.populerlikler += KaynakPopulerlik(4, 90.0)
        katalogKaynagi.populerlikler += KaynakPopulerlik(2, 50.0)
        katalogIceAktarici.populerlikleriGuncelle()
        suspend fun ben(token: String) = client.get(ApiYollari.BEN) { bearerAuth(token) }.govde(BenYaniti.serializer())
        suspend fun tamamla(token: String) = client.post(ApiYollari.ONBOARDING_TAMAMLA) { bearerAuth(token) }

        // Steam'li: bağlayınca tamamlanabilir.
        assertEquals(false, ben(ayse).onboardingTamamlandi)
        bagla(ayse, AYSE_STEAM)
        assertEquals(HttpStatusCode.NoContent, tamamla(ayse).status)
        assertEquals(true, ben(ayse).onboardingTamamlandi)

        // Steam'siz: popüler oyunlardan en az üçünün durumu seçilmeli.
        val populer = client.get(ApiYollari.POPULER_OYUNLAR) { bearerAuth(mehmet) }.govde(PopulerOyunlar.serializer()).oyunlar
        assertEquals(listOf("Konsol Oyunu", "Orman Macerası"), populer.take(2).map { it.ad })
        val eksik = tamamla(mehmet)
        assertEquals(HttpStatusCode.BadRequest, eksik.status)
        assertEquals(HataKodu.ONBOARDING_EKSIK, eksik.govde(ApiHatasi.serializer()).kod)
        populer.take(3).forEach { oyun ->
            client.put(ApiYollari.kutuphaneGirisi(oyun.id)) {
                bearerAuth(mehmet)
                json(KutuphaneGirisi.serializer(), KutuphaneGirisi(durum = Durum.BITIRDIM))
            }
        }
        assertEquals(HttpStatusCode.NoContent, tamamla(mehmet).status)
        assertEquals(true, ben(mehmet).onboardingTamamlandi)
    }

    private companion object {
        const val AYSE_STEAM = "76561197960287930"
    }
}
