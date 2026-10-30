package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.CihazKaydiIstegi
import com.sosyaloyun.shared.CihazPlatformu
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.OkunmamisBildirimSayisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.ProfilIstegi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlin.test.Test
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BildirimTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
    }

    private suspend fun ApiTestOrtami.bildirimler(ad: String): BildirimListesi =
        client.get(ApiYollari.BILDIRIMLER) { bearerAuth(t(ad)) }.govde(BildirimListesi.serializer())

    private suspend fun ApiTestOrtami.takipEt(a: String, b: String) {
        saat.ilerlet(1.minutes)
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(t(a)) }
    }

    private suspend fun ApiTestOrtami.takibiBirak(a: String, b: String) {
        client.delete(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(t(a)) }
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String): GunlukKaydi {
        if (katalogKaynagi.oyunlar.isEmpty()) {
            katalogKaynagi.oyunlar += KaynakOyun(1, "Bildirim Oyunu", KaynakTuru.ANA_OYUN)
            katalogIceAktarici.tamIceAktar()
        }
        val oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(ad))
            parameter("q", "bildirim")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi())
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.begen(ad: String, kayit: GunlukKaydi) {
        saat.ilerlet(1.minutes)
        client.put(ApiYollari.begeni(kayit.id)) { bearerAuth(t(ad)) }
    }

    private suspend fun ApiTestOrtami.cihazKaydet(ad: String, saatDilimi: String = "Europe/Istanbul") {
        client.put(ApiYollari.CIHAZ) {
            bearerAuth(t(ad))
            json(CihazKaydiIstegi.serializer(), CihazKaydiIstegi(CihazPlatformu.ANDROID, "token-$ad", saatDilimi))
        }
    }

    @Test
    fun dortTurDogruOlaylardaOlusur() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")

        takipEt("ayse", "mehmet")
        saat.ilerlet(1.minutes)
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ARKADASLIK)) { bearerAuth(t("zeynep")) }
        saat.ilerlet(1.minutes)
        client.post(ApiYollari.arkadaslikIstegiKabul("zeynep")) { bearerAuth(t("mehmet")) }
        begen("ayse", kayitGir("mehmet"))

        // Davet kodu: mehmet'in kodunu yeni biri kullanıyor; adı seçince bildirimde görünür.
        val kod = client.get(ApiYollari.DAVET_KODLARIM) { bearerAuth(t("mehmet")) }.govde(DavetKodlarimYaniti.serializer()).kodlar.first().kod
        val yeni = girisYap("yeni-kisi")
        saat.ilerlet(1.minutes)
        client.post(ApiYollari.DAVET_KULLAN) {
            bearerAuth(yeni.oturumTokeni)
            json(DavetKoduKullanIstegi.serializer(), DavetKoduKullanIstegi(kod))
        }
        client.put(ApiYollari.PROFIL) {
            bearerAuth(yeni.oturumTokeni)
            json(ProfilIstegi.serializer(), ProfilIstegi("yenikisi", "Yeni", Avatar.KUMANDA))
        }

        assertEquals(
            listOf(
                BildirimTuru.DAVET_KODU_KULLANILDI to "yenikisi",
                BildirimTuru.BEGENI to "ayse",
                BildirimTuru.ARKADASLIK_ISTEGI to "zeynep",
                BildirimTuru.YENI_TAKIPCI to "ayse",
            ),
            bildirimler("mehmet").bildirimler.map { it.tur to it.kisi?.kullaniciAdi },
        )
        // Kabul eden tarafın arkadaşlıkla gelen takibi "yeni takipçi" düşürmez.
        assertEquals(listOf(BildirimTuru.ARKADASLIK_KABULU to "mehmet"), bildirimler("zeynep").bildirimler.map { it.tur to it.kisi?.kullaniciAdi })
    }

    @Test
    fun begenilerGruplanirOkunmamisSayilirGeriAlinincaSilinir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali")
        val kayit = kayitGir("ayse")
        begen("mehmet", kayit)
        begen("zeynep", kayit)
        begen("ali", kayit)
        begen("ayse", kayit)

        val grup = bildirimler("ayse").bildirimler.single()
        assertEquals(BildirimTuru.BEGENI, grup.tur)
        assertEquals("ali", grup.kisi?.kullaniciAdi)
        assertEquals(2, grup.digerSayisi)
        assertEquals(kayit.id, grup.gunlukKaydiId)
        assertEquals(1, client.get(ApiYollari.OKUNMAMIS_BILDIRIM_SAYISI) { bearerAuth(t("ayse")) }.govde(OkunmamisBildirimSayisi.serializer()).sayi)

        client.post(ApiYollari.BILDIRIMLER_OKUNDU) { bearerAuth(t("ayse")) }
        assertEquals(0, bildirimler("ayse").okunmamisSayisi)

        client.delete(ApiYollari.begeni(kayit.id)) { bearerAuth(t("ali")) }
        val sonra = bildirimler("ayse").bildirimler.single()
        assertEquals("zeynep", sonra.kisi?.kullaniciAdi)
        assertEquals(1, sonra.digerSayisi)

        takipEt("mehmet", "ayse")
        takibiBirak("mehmet", "ayse")
        assertEquals(listOf(BildirimTuru.BEGENI), bildirimler("ayse").bildirimler.map { it.tur })
    }

    @Test
    fun gunlukPushSiniriAyardanGelirVeErtesiGunSifirlanir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali", "veli")
        ayarlar.yaz(Ayar.GunlukPushSiniri, 2)
        cihazKaydet("ayse")
        // 12:00 Türkiye saati.
        saat.simdi = Instant.parse("2026-01-05T09:00:00Z")

        listOf("mehmet", "zeynep", "ali").forEach { takipEt(it, "ayse") }
        pushDagitici.bekleyenleriGonder()
        assertEquals(2, pushGonderici.gonderilenler.size)
        assertEquals("token-ayse" to "Mehmet seni takip etmeye başladı.", pushGonderici.gonderilenler.first())

        // Sınıra takılan push sonradan gönderilmez, ama uygulama içinde durur.
        saat.ilerlet(1.days)
        takipEt("veli", "ayse")
        pushDagitici.bekleyenleriGonder()
        assertEquals(3, pushGonderici.gonderilenler.size)
        assertEquals(4, bildirimler("ayse").bildirimler.size)
    }

    @Test
    fun sessizSaatlerdeKullanicininYerelSaatineGorePushGitmez() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        cihazKaydet("ayse", saatDilimi = "Europe/Istanbul")
        cihazKaydet("zeynep", saatDilimi = "America/New_York")

        // 21:00 UTC: Türkiye'de gece 00:00 (sessiz), New York'ta 16:00.
        saat.simdi = Instant.parse("2026-01-05T21:00:00Z")
        takipEt("mehmet", "ayse")
        takipEt("mehmet", "zeynep")
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("token-zeynep"), pushGonderici.gonderilenler.map { it.first })

        // Sessiz saatler Ayar'dan: sabah 10'a kadar uzatılınca 09:30'da da gitmez.
        ayarlar.yaz(Ayar.SessizSaatBitisi, 10)
        saat.simdi = Instant.parse("2026-01-06T06:30:00Z")
        takibiBirak("mehmet", "ayse")
        takipEt("mehmet", "ayse")
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("token-zeynep"), pushGonderici.gonderilenler.map { it.first })
        saat.ilerlet(1.hours)
        takibiBirak("mehmet", "ayse")
        takipEt("mehmet", "ayse")
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("token-zeynep", "token-ayse"), pushGonderici.gonderilenler.map { it.first })
    }

    @Test
    fun turBazindaPushTercihiUygulanir() = apiTesti {
        hazirla("ayse", "mehmet")
        cihazKaydet("ayse")
        val tercihler = client.put(ApiYollari.BILDIRIM_TERCIHLERI) {
            bearerAuth(t("ayse"))
            json(BildirimTercihleri.serializer(), BildirimTercihleri(mapOf(BildirimTuru.BEGENI to false)))
        }.govde(BildirimTercihleri.serializer())
        assertEquals(false, tercihler.push[BildirimTuru.BEGENI])
        assertEquals(true, tercihler.push[BildirimTuru.YENI_TAKIPCI])

        begen("mehmet", kayitGir("ayse"))
        takipEt("mehmet", "ayse")
        pushDagitici.bekleyenleriGonder()

        assertEquals(listOf("Mehmet seni takip etmeye başladı."), pushGonderici.gonderilenler.map { it.second })
        assertEquals(2, bildirimler("ayse").bildirimler.size)
    }

    @Test
    fun engellenincePushVeBildirimlerKalkar() = apiTesti {
        hazirla("ayse", "mehmet")
        takipEt("mehmet", "ayse")
        client.put(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }
        assertEquals(emptyList(), bildirimler("ayse").bildirimler)
    }

    @Test
    fun faz2dePencereDisindakiAcilOlmayanlarPenceredeTekOzetOlarakGider() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        ayarlar.yaz(Ayar.AktifFaz, 2)
        cihazKaydet("ayse")
        // 09:00 Türkiye: sessiz saatler bitti, öğle penceresi (12–14) henüz açılmadı.
        saat.simdi = Instant.parse("2026-01-05T06:00:00Z")
        val kayit = kayitGir("ayse")
        takipEt("mehmet", "ayse")
        takipEt("zeynep", "ayse")
        begen("mehmet", kayit)
        pushDagitici.bekleyenleriGonder()
        assertEquals(emptyList(), pushGonderici.gonderilenler)
        // Uygulama içinde hemen görünür.
        assertEquals(3, bildirimler("ayse").bildirimler.size)

        saat.simdi = Instant.parse("2026-01-05T09:05:00Z")
        pushDagitici.bekleyenleriGonder()
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("token-ayse" to "Yeni bildirimlerin var: 1 beğeni, 2 yeni takipçi."), pushGonderici.gonderilenler)
    }

    @Test
    fun faz2deOzeteYalnizcaPushuAcikTurlerGirer() = apiTesti {
        hazirla("ayse", "mehmet")
        ayarlar.yaz(Ayar.AktifFaz, 2)
        cihazKaydet("ayse")
        client.put(ApiYollari.BILDIRIM_TERCIHLERI) {
            bearerAuth(t("ayse"))
            json(BildirimTercihleri.serializer(), BildirimTercihleri(mapOf(BildirimTuru.BEGENI to false)))
        }
        saat.simdi = Instant.parse("2026-01-05T06:00:00Z")
        begen("mehmet", kayitGir("ayse"))
        takipEt("mehmet", "ayse")
        saat.simdi = Instant.parse("2026-01-05T09:05:00Z")
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("Mehmet seni takip etmeye başladı."), pushGonderici.gonderilenler.map { it.second })
        // Atlanan beğeni günlük sınıra sayılmaz: yalnızca takip "gönderildi" olarak kalır.
        val durumlar = org.jetbrains.exposed.v1.jdbc.transactions.transaction(veritabani) {
            BildirimTablosu.selectAll().map { it[BildirimTablosu.tur] to it[BildirimTablosu.pushDurumu] }.toSet()
        }
        assertEquals(setOf("BEGENI" to "ATLANDI", "YENI_TAKIPCI" to "GONDERILDI"), durumlar)
    }

    @Test
    fun faz2deAcilTurlerPencereyiBeklemezPencereSaatleriAyardanGelir() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        ayarlar.yaz(Ayar.AktifFaz, 2)
        cihazKaydet("ayse")
        saat.simdi = Instant.parse("2026-01-05T06:30:00Z")
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.ARKADASLIK)) { bearerAuth(t("zeynep")) }
        pushDagitici.bekleyenleriGonder()
        assertEquals(listOf("Zeynep sana arkadaşlık isteği gönderdi."), pushGonderici.gonderilenler.map { it.second })

        // Öğle penceresi 10'a çekilince 09:30'daki takip 10:00'da, tek bildirimse kendi metniyle gider.
        ayarlar.yaz(Ayar.OglePushPenceresi, 10)
        takipEt("mehmet", "ayse")
        pushDagitici.bekleyenleriGonder()
        assertEquals(1, pushGonderici.gonderilenler.size)
        saat.simdi = Instant.parse("2026-01-05T07:00:00Z")
        pushDagitici.bekleyenleriGonder()
        assertEquals("Mehmet seni takip etmeye başladı.", pushGonderici.gonderilenler.last().second)
    }
}
