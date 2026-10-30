package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.Tur
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class KesifTest {
    private val tokenler = mutableMapOf<String, String>()
    private val oyunlar = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Ejder Diyarı", KaynakTuru.ANA_OYUN, platformlar = listOf("PC (Microsoft Windows)"), turler = listOf("Role-playing (RPG)", "Adventure"))
        katalogKaynagi.oyunlar += KaynakOyun(2, "Hız Pisti", KaynakTuru.ANA_OYUN, platformlar = listOf("PlayStation 5"), turler = listOf("Racing"))
        katalogKaynagi.oyunlar += KaynakOyun(3, "Küçük Bulmaca", KaynakTuru.ANA_OYUN, platformlar = listOf("PC (Microsoft Windows)", "Nintendo Switch"), turler = listOf("Puzzle"))
        katalogKaynagi.oyunlar += KaynakOyun(4, "Ejder Ek Paketi", KaynakTuru.DLC, anaOyunKaynakId = 1, platformlar = listOf("PC (Microsoft Windows)"), turler = listOf("Role-playing (RPG)"))
        katalogKaynagi.populerlikler += listOf(KaynakPopulerlik(1, 10.0), KaynakPopulerlik(2, 30.0), KaynakPopulerlik(3, 20.0), KaynakPopulerlik(4, 99.0))
        katalogIceAktarici.tamIceAktar()
        katalogIceAktarici.populerlikleriGuncelle()
        listOf("Ejder Diyarı", "Hız Pisti", "Küçük Bulmaca").forEach { ad ->
            oyunlar[ad] = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(t(adlar.first()))
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.first { it.ad == ad }.id
        }
    }

    private suspend fun ApiTestOrtami.gunlugeYaz(ad: String, oyun: String, gorunurluk: Gorunurluk = Gorunurluk.HERKES, sessiz: Boolean = false) {
        client.post(ApiYollari.oyunGunlugu(oyunlar.getValue(oyun))) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(gorunurluk = gorunurluk, sessiz = sessiz))
        }
    }

    private suspend fun ApiTestOrtami.takipEt(a: String, b: String) {
        client.post(ApiYollari.kullaniciSekmesi(b, ApiYollari.TAKIP)) { bearerAuth(t(a)) }
    }

    private suspend fun ApiTestOrtami.kesif(ad: String): Kesif = client.get(ApiYollari.KESIF) { bearerAuth(t(ad)) }.govde(Kesif.serializer())

    @Test
    fun takipEdilenlerArasindaBuHaftaEnCokKaydedilenlerGorunurlugeUyar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep", "ali", "veli", "yabanci")
        listOf("mehmet", "zeynep", "ali", "veli").forEach { takipEt("ayse", it) }

        // Bir haftadan eski kayıt sayılmaz.
        gunlugeYaz("mehmet", "Küçük Bulmaca")
        saat.ilerlet(8.days)
        gunlugeYaz("mehmet", "Hız Pisti")
        gunlugeYaz("mehmet", "Hız Pisti")
        gunlugeYaz("zeynep", "Hız Pisti", sessiz = true)
        gunlugeYaz("ali", "Ejder Diyarı", Gorunurluk.TAKIPCILER)
        // Arkadaş olmayana Arkadaşlar ve kimseye Ben görünmez; takip edilmeyenin kaydı sayılmaz.
        gunlugeYaz("veli", "Ejder Diyarı", Gorunurluk.ARKADASLAR)
        gunlugeYaz("zeynep", "Küçük Bulmaca", Gorunurluk.BEN)
        gunlugeYaz("yabanci", "Küçük Bulmaca")
        saat.ilerlet(1.hours)

        val populer = kesif("ayse").takipEdilenlerde
        assertEquals(listOf("Hız Pisti" to 2, "Ejder Diyarı" to 1), populer.map { it.oyun.ad to it.kaydedenSayisi })

        // Sessize alınanın kayıtları sayılmaz.
        client.put(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.SESSIZ)) { bearerAuth(t("ayse")) }
        assertEquals(setOf("Ejder Diyarı" to 1, "Hız Pisti" to 1), kesif("ayse").takipEdilenlerde.map { it.oyun.ad to it.kaydedenSayisi }.toSet())
    }

    @Test
    fun trendPopScoreSirasiylaVeKisayollarListeler() = apiTesti {
        hazirla("ayse")

        val kesif = kesif("ayse")
        assertEquals(listOf("Hız Pisti", "Küçük Bulmaca", "Ejder Diyarı"), kesif.trend.map { it.ad })
        assertEquals(Tur.entries, kesif.turler)
        assertEquals(true, "PC (Microsoft Windows)" in kesif.platformlar)

        suspend fun liste(vararg parametre: Pair<String, String>) = client.get(ApiYollari.OYUN_LISTESI) {
            bearerAuth(t("ayse"))
            parametre.forEach { (ad, deger) -> parameter(ad, deger) }
        }.govde(OyunAramaYaniti.serializer()).oyunlar.map { it.ad }

        assertEquals(listOf("Ejder Diyarı"), liste("tur" to Tur.ROL_YAPMA.name))
        assertEquals(listOf("Küçük Bulmaca", "Ejder Diyarı"), liste("platform" to "PC (Microsoft Windows)"))
        assertEquals(listOf("Küçük Bulmaca"), liste("platform" to "Nintendo Switch"))
        assertEquals(emptyList(), liste("tur" to "OLMAYAN"))
    }
}
