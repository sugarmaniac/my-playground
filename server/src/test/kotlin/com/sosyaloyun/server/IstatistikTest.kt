package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AdSayisi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.Istatistikler
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.YillikOzet
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class IstatistikTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private val oyunlar = mutableMapOf<String, String>()

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 3) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        listOf("Alfa" to listOf("Role-playing (RPG)", "Adventure"), "Beta" to listOf("Shooter"), "Gama" to listOf("Role-playing (RPG)"))
            .forEachIndexed { i, (ad, turler) ->
                katalogKaynagi.oyunlar += KaynakOyun(i + 1L, "$ad Oyunu", KaynakTuru.ANA_OYUN, platformlar = listOf(PC, PS5), turler = turler)
            }
        katalogIceAktarici.tamIceAktar()
        listOf("Alfa", "Beta", "Gama").forEach { ad ->
            oyunlar[ad] = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(t(adlar.first()))
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        }
    }

    private suspend fun ApiTestOrtami.kutuphane(ad: String, oyun: String, durum: Durum, puan: Int? = null) {
        client.put(ApiYollari.kutuphaneGirisi(oyunlar.getValue(oyun))) {
            bearerAuth(t(ad))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(durum = durum, puan = puan))
        }
    }

    private suspend fun ApiTestOrtami.kayit(ad: String, oyun: String, istek: GunlukKaydiIstegi) {
        client.post(ApiYollari.oyunGunlugu(oyunlar.getValue(oyun))) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), istek)
        }
    }

    private suspend fun ApiTestOrtami.istatistik(izleyen: String, ad: String?) =
        client.get(ad?.let { ApiYollari.kullaniciSekmesi(it, ApiYollari.SEKME_ISTATISTIK) } ?: ApiYollari.PROFILIM_ISTATISTIK) { bearerAuth(t(izleyen)) }
            .govde(Istatistikler.serializer())

    private suspend fun ApiTestOrtami.ornekVeri() {
        // Sahte saat: 2026-01-05 12:00 Türkiye.
        kutuphane("ayse", "Alfa", Durum.BITIRDIM, puan = 8)
        kutuphane("ayse", "Beta", Durum.OYNUYORUM, puan = 6)
        kayit("ayse", "Alfa", GunlukKaydiIstegi(platform = PC, sureDakika = 120, bitis = LocalDate(2026, 1, 4)))
        kayit("ayse", "Beta", GunlukKaydiIstegi(platform = PS5, sureDakika = 60, gorunurluk = Gorunurluk.TAKIPCILER))
    }

    @Test
    fun istatistiklerDogruHesaplanirGorunurlugeUyar() = apiTesti {
        hazirla("ayse", "mehmet")
        ornekVeri()

        val kendi = istatistik("ayse", null)
        assertEquals(2026, kendi.yil)
        assertEquals(mapOf(Durum.OYNUYORUM to 1, Durum.BITIRDIM to 1, Durum.BIRAKTIM to 0, Durum.BEKLEMEDE to 0), kendi.durumlar.associate { it.durum to it.sayi })
        assertEquals(180, kendi.toplamSureDakika)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 0, 1, 0, 0), kendi.puanDagilimi)
        assertEquals(listOf(AdSayisi("Adventure", 1), AdSayisi("Role-playing (RPG)", 1), AdSayisi("Shooter", 1)), kendi.turler)
        assertEquals(setOf(AdSayisi(PC, 1), AdSayisi(PS5, 1)), kendi.platformlar.toSet())
        assertEquals(1, kendi.aylaraGoreBitirilen[0])

        // Takipçi olmayan yalnızca Herkes görünürlüğündeki kaydı görür.
        val baskasi = istatistik("mehmet", "ayse")
        assertEquals(120, baskasi.toplamSureDakika)
        assertEquals(listOf(AdSayisi(PC, 1)), baskasi.platformlar)
    }

    @Test
    fun yillikOzetYilSinirlariniTurkiyeSaatineGoreAlirWebdePaylasilir() = apiTesti {
        hazirla("ayse")
        ornekVeri()
        // 2025-12-31 21:30 UTC = 2026-01-01 00:30 Türkiye: Gama 2026'da bitirilmiş sayılır.
        saat.simdi = Instant.parse("2025-12-31T21:30:00Z")
        kutuphane("ayse", "Gama", Durum.BITIRDIM, puan = 10)
        // Bitiş tarihi 2025'te olan kayıt 2025'e girer.
        kayit("ayse", "Beta", GunlukKaydiIstegi(platform = PS5, sureDakika = 30, bitis = LocalDate(2025, 12, 31)))
        saat.simdi = Instant.parse("2026-01-05T09:00:00Z")

        // Bitirme gününe göre sıralı.
        val ozet = client.get(ApiYollari.PROFILIM_YILLIK_OZET) { bearerAuth(t("ayse")) }.govde(YillikOzet.serializer())
        assertEquals(listOf("Gama Oyunu", "Alfa Oyunu"), ozet.bitirilenler.map { it.ad })
        assertEquals(listOf("Gama Oyunu" to 10, "Alfa Oyunu" to 8), ozet.enYuksekPuanlilar.map { it.oyun.ad to it.puan })
        assertEquals("Role-playing (RPG)", ozet.enCokTur)
        val gecenYil = client.get(ApiYollari.PROFILIM_YILLIK_OZET) {
            bearerAuth(t("ayse"))
            parameter("yil", 2025)
        }.govde(YillikOzet.serializer())
        assertEquals(listOf("Beta Oyunu"), gecenYil.bitirilenler.map { it.ad })

        // Web sitesindeki özet oturumsuzdur ve yalnızca Herkes görünürlüğündeki kayıtları sayar.
        val paylasilan = client.get(ApiYollari.paylasilanYillikOzet("ayse")).govde(YillikOzet.serializer())
        assertEquals(listOf("Gama Oyunu", "Alfa Oyunu"), paylasilan.bitirilenler.map { it.ad })
        assertEquals(120, paylasilan.toplamSureDakika)
    }

    @Test
    fun faz1ve2deUcNoktalarKapali() = apiTesti {
        hazirla("ayse", faz = 2)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.PROFILIM_ISTATISTIK) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.paylasilanYillikOzet("ayse")).govde(ApiHatasi.serializer()).kod)
        ayarlar.yaz(Ayar.AktifFaz, 1)
        assertEquals(HataKodu.OZELLIK_KAPALI, client.get(ApiYollari.PROFILIM_YILLIK_OZET) { bearerAuth(t("ayse")) }.govde(ApiHatasi.serializer()).kod)
    }

    private companion object {
        const val PC = "PC (Microsoft Windows)"
        const val PS5 = "PlayStation 5"
    }
}
