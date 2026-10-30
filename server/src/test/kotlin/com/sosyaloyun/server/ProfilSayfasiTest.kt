package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.VitrinIstegi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ProfilSayfasiTest {
    private lateinit var token: String

    private suspend fun ApiTestOrtami.hazirla(): ApiTestOrtami {
        token = kayitliKullanici("ayse").oturumTokeni
        return this
    }

    /** Katalogdaki oyunların aranarak bulunan kimlikleri, verilen sırayla. */
    private suspend fun ApiTestOrtami.oyunlar(vararg adlar: String): List<String> {
        adlar.forEach { katalogKaynagi.oyunlar += KaynakOyun(katalogKaynagi.oyunlar.size + 1L, it, KaynakTuru.ANA_OYUN) }
        katalogIceAktarici.tamIceAktar()
        return adlar.map { ad ->
            client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(token)
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.single { it.ad == ad }.id
        }
    }

    private suspend fun ApiTestOrtami.kutuphane(oyunId: String, giris: KutuphaneGirisi) {
        client.put(ApiYollari.kutuphaneGirisi(oyunId)) {
            bearerAuth(token)
            json(KutuphaneGirisi.serializer(), giris)
        }
    }

    private suspend fun ApiTestOrtami.gunluk(oyunId: String, istek: GunlukKaydiIstegi): GunlukKaydi =
        client.post(ApiYollari.oyunGunlugu(oyunId)) {
            bearerAuth(token)
            json(GunlukKaydiIstegi.serializer(), istek)
        }.govde(GunlukKaydi.serializer())

    private suspend fun ApiTestOrtami.profil(): ProfilSayfasi = client.get(ApiYollari.PROFILIM) { bearerAuth(token) }.govde(ProfilSayfasi.serializer())

    private suspend fun ApiTestOrtami.gunlukSekmesi(yol: String, imlec: String? = null): GunlukSayfasi =
        client.get(yol) {
            bearerAuth(token)
            imlec?.let { parameter("imlec", it) }
        }.govde(GunlukSayfasi.serializer())

    private suspend fun ApiTestOrtami.kutuphaneSekmesi(yol: String, durum: String? = null): KutuphaneSayfasi =
        client.get(yol) {
            bearerAuth(token)
            durum?.let { parameter("durum", it) }
        }.govde(KutuphaneSayfasi.serializer())

    private suspend fun ApiTestOrtami.vitrin(vararg oyunIdleri: String): HttpResponse =
        client.put(ApiYollari.PROFILIM_VITRIN) {
            bearerAuth(token)
            json(VitrinIstegi.serializer(), VitrinIstegi(oyunIdleri.toList()))
        }

    @Test
    fun profilBasligiKullaniciyiVeBiyografiyiGosterir() = apiTesti {
        hazirla()
        client.put(ApiYollari.PROFIL) {
            bearerAuth(token)
            json(ProfilIstegi.serializer(), ProfilIstegi("ayse", "Ayşe Yılmaz", Avatar.TILKI, biyografi = "  RPG sever.  "))
        }

        val profil = profil()

        assertEquals("ayse", profil.kullanici.kullaniciAdi)
        assertEquals(KullaniciOzeti(profil.kullanici.kullaniciId, "ayse", "Ayşe Yılmaz", Avatar.TILKI), profil.kullanici)
        assertEquals("RPG sever.", profil.biyografi)
        assertEquals(0, profil.buYilBitirilen)
        assertEquals(emptyList(), profil.vitrin)
    }

    @Test
    fun cokUzunBiyografiReddedilir() = apiTesti {
        hazirla()
        val yanit = client.put(ApiYollari.PROFIL) {
            bearerAuth(token)
            json(ProfilIstegi.serializer(), ProfilIstegi("ayse", "Ayşe", Avatar.TILKI, biyografi = "a".repeat(301)))
        }
        assertEquals(ApiHatasi(HataKodu.BIYOGRAFI_COK_UZUN), yanit.govde(ApiHatasi.serializer()))
    }

    @Test
    fun buYilBitirilenOyunlarSayilir() = apiTesti {
        hazirla()
        // Sahte saat 2026-01-05.
        val (a, b, c, d) = oyunlar("Birinci", "İkinci", "Üçüncü", "Dördüncü")
        kutuphane(a, KutuphaneGirisi(Durum.BITIRDIM))
        gunluk(b, GunlukKaydiIstegi(bitis = LocalDate(2026, 1, 2)))
        gunluk(b, GunlukKaydiIstegi(bitis = LocalDate(2026, 1, 3)))
        gunluk(c, GunlukKaydiIstegi(bitis = LocalDate(2025, 12, 30)))
        kutuphane(d, KutuphaneGirisi(Durum.OYNUYORUM))

        assertEquals(2, profil().buYilBitirilen)

        saat.simdi = Instant.parse("2027-01-01T08:00:00Z")
        assertEquals(0, profil().buYilBitirilen)
    }

    @Test
    fun vitrinSecilirVeDegistirilir() = apiTesti {
        hazirla()
        val (a, b, c, d, e) = oyunlar("Alfa", "Beta", "Gama", "Delta", "Epsilon")

        assertEquals(HttpStatusCode.OK, vitrin(c, a, b).status)
        assertEquals(listOf("Gama", "Alfa", "Beta"), profil().vitrin.map { it.ad })

        vitrin(d, e, a, b)
        assertEquals(listOf("Delta", "Epsilon", "Alfa", "Beta"), profil().vitrin.map { it.ad })

        vitrin()
        assertEquals(emptyList(), profil().vitrin)
    }

    @Test
    fun vitrinDorttenFazlaTekrarliYaDaOlmayanOyunAlmaz() = apiTesti {
        hazirla()
        val (a, b, c, d, e) = oyunlar("Alfa", "Beta", "Gama", "Delta", "Epsilon")
        vitrin(a)

        listOf(
            vitrin(a, b, c, d, e),
            vitrin(a, a),
            vitrin(a, UUID.randomUUID().toString()),
            vitrin("uuid-degil"),
        ).forEach { assertEquals(ApiHatasi(HataKodu.VITRIN_GECERSIZ), it.govde(ApiHatasi.serializer())) }
        assertEquals(listOf("Alfa"), profil().vitrin.map { it.ad })
    }

    @Test
    fun gunlukSekmesiKayitlariTarihSirasiylaGosterir() = apiTesti {
        hazirla()
        val (a, b) = oyunlar("Eski Oyun", "Yeni Oyun")
        gunluk(a, GunlukKaydiIstegi(bitis = LocalDate(2025, 6, 1), inceleme = "Eski"))
        saat.ilerlet(1.hours)
        gunluk(b, GunlukKaydiIstegi(bitis = LocalDate(2025, 12, 1)))
        saat.ilerlet(1.hours)
        gunluk(a, GunlukKaydiIstegi(baslama = LocalDate(2025, 8, 1)))

        val ogeler = gunlukSekmesi(ApiYollari.PROFILIM_GUNLUK).ogeler

        assertEquals(listOf("Yeni Oyun", "Eski Oyun", "Eski Oyun"), ogeler.map { it.oyun.ad })
        assertEquals(listOf(LocalDate(2025, 12, 1), null, LocalDate(2025, 6, 1)), ogeler.map { it.kayit.bitis })
    }

    @Test
    fun gunlukSekmesiImlecleSayfalanir() = apiTesti {
        hazirla()
        val (a) = oyunlar("Çok Oynanan")
        repeat(25) {
            gunluk(a, GunlukKaydiIstegi())
            saat.ilerlet(1.days)
        }

        val ilk = gunlukSekmesi(ApiYollari.PROFILIM_GUNLUK)
        val ikinci = gunlukSekmesi(ApiYollari.PROFILIM_GUNLUK, ilk.sonrakiImlec)

        assertEquals(20, ilk.ogeler.size)
        assertEquals(5, ikinci.ogeler.size)
        assertEquals(null, ikinci.sonrakiImlec)
        assertEquals(25, (ilk.ogeler + ikinci.ogeler).map { it.kayit.id }.toSet().size)
    }

    @Test
    fun incelemelerSekmesiYalnizcaIncelemeliKayitlariGosterir() = apiTesti {
        hazirla()
        val (a, b) = oyunlar("İncelenen", "İncelenmeyen")
        gunluk(a, GunlukKaydiIstegi(inceleme = "Çok iyi"))
        gunluk(b, GunlukKaydiIstegi(sureDakika = 60))

        val ogeler = gunlukSekmesi(ApiYollari.PROFILIM_INCELEMELER).ogeler

        assertEquals(listOf("Çok iyi"), ogeler.map { it.kayit.inceleme })
    }

    @Test
    fun oyunlarSekmesiDurumaGoreSuzulurVeYalnizcaIstekListesindekileriAlmaz() = apiTesti {
        hazirla()
        val (a, b, c, d) = oyunlar("Oynanan", "Bitirilen", "Bırakılan", "İstenen")
        kutuphane(a, KutuphaneGirisi(Durum.OYNUYORUM))
        saat.ilerlet(1.hours)
        kutuphane(b, KutuphaneGirisi(Durum.BITIRDIM, puan = 9))
        saat.ilerlet(1.hours)
        kutuphane(c, KutuphaneGirisi(Durum.BIRAKTIM, istekListesinde = true))
        kutuphane(d, KutuphaneGirisi(istekListesinde = true))

        assertEquals(listOf("Bırakılan", "Bitirilen", "Oynanan"), kutuphaneSekmesi(ApiYollari.PROFILIM_OYUNLAR).ogeler.map { it.oyun.ad })
        val bitirilenler = kutuphaneSekmesi(ApiYollari.PROFILIM_OYUNLAR, durum = "bitirdim").ogeler
        assertEquals(listOf("Bitirilen"), bitirilenler.map { it.oyun.ad })
        assertEquals(KutuphaneGirisi(Durum.BITIRDIM, puan = 9), bitirilenler.single().giris)
    }

    @Test
    fun durumsuzGirislerAyriSuzulebilir() = apiTesti {
        hazirla()
        val (a, b) = oyunlar("Durumlu", "Durumsuz")
        kutuphane(a, KutuphaneGirisi(Durum.OYNUYORUM))
        gunluk(b, GunlukKaydiIstegi())

        assertEquals(listOf("Durumsuz"), kutuphaneSekmesi(ApiYollari.PROFILIM_OYUNLAR, durum = "durumsuz").ogeler.map { it.oyun.ad })
    }

    @Test
    fun istekListesiSekmesiDurumdanBagimsizdir() = apiTesti {
        hazirla()
        val (a, b, c) = oyunlar("İstenen", "Bırakılıp İstenen", "Oynanan")
        kutuphane(a, KutuphaneGirisi(istekListesinde = true))
        saat.ilerlet(1.hours)
        kutuphane(b, KutuphaneGirisi(Durum.BIRAKTIM, istekListesinde = true))
        kutuphane(c, KutuphaneGirisi(Durum.OYNUYORUM))

        assertEquals(listOf("Bırakılıp İstenen", "İstenen"), kutuphaneSekmesi(ApiYollari.PROFILIM_ISTEK_LISTESI).ogeler.map { it.oyun.ad })
    }

    @Test
    fun sekmelerYalnizcaKendiVerileriniGosterir() = apiTesti {
        hazirla()
        val (a) = oyunlar("Ortak Oyun")
        kutuphane(a, KutuphaneGirisi(Durum.OYNUYORUM, istekListesinde = true))
        gunluk(a, GunlukKaydiIstegi(inceleme = "Benim incelemem"))
        token = kayitliKullanici("mehmet").oturumTokeni

        assertEquals(emptyList(), gunlukSekmesi(ApiYollari.PROFILIM_GUNLUK).ogeler)
        assertEquals(emptyList(), gunlukSekmesi(ApiYollari.PROFILIM_INCELEMELER).ogeler)
        assertEquals(emptyList(), kutuphaneSekmesi(ApiYollari.PROFILIM_OYUNLAR).ogeler)
        assertEquals(emptyList(), kutuphaneSekmesi(ApiYollari.PROFILIM_ISTEK_LISTESI).ogeler)
    }
}
