package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MetriklerTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private val baslangic = Instant.parse("2026-01-05T09:00:00Z")

    private fun ApiTestOrtami.gun(n: Int) {
        saat.simdi = baslangic + n.days
    }

    private suspend fun ApiTestOrtami.katil(ad: String) {
        tokenler[ad] = kayitliKullanici(ad).oturumTokeni
    }

    /** Oturumlu herhangi bir istek kullanıcıyı o gün aktif yapar. */
    private suspend fun ApiTestOrtami.uygulamayiAc(ad: String) {
        client.get(ApiYollari.BEN) { bearerAuth(t(ad)) }
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String) {
        val oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(ad))
            parameter("q", "metrik")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi())
        }
    }

    @Test
    fun ucMetrikAktifGunlerVeKayitlardanHesaplanir() = apiTesti {
        katalogKaynagi.oyunlar += KaynakOyun(1, "Metrik Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()

        gun(0)
        listOf("ayse", "mehmet", "zeynep").forEach { katil(it) }
        // Davet koduyla katılmamış hesap kayıtlı sayılmaz.
        girisYap("davetsiz")

        gun(15)
        uygulamayiAc("mehmet") // 3. hafta: 4. hafta aktifliğine sayılmaz.
        kayitGir("mehmet") // 7 günden eski: haftalık kayda sayılmaz.
        gun(20)
        katil("yeni") // 4. haftası henüz dolmadı: kohorta girmez.
        gun(23)
        uygulamayiAc("ayse") // 4. hafta (katılıştan sonraki 22.–28. gün).
        gun(25)
        kayitGir("yeni")
        gun(29)
        kayitGir("ayse")
        kayitGir("ayse")

        val m = metrikler.hesapla()
        assertEquals(4, m.kayitliKullanici)
        assertEquals(1 to 3, m.dorduncuHaftaAktif to m.dorduncuHaftaKohortu)
        // Son 7 gün: ayse ve yeni aktif, 3 kayıt.
        assertEquals(3 to 2, m.haftalikGunlukKaydi to m.haftalikAktifKullanici)
        assertEquals(1.5, m.kayitPerAktifKullanici)
    }

    @Test
    fun metrikVerisiYokkenOranlarBos() = apiTesti {
        val m = metrikler.hesapla()
        assertEquals(0, m.kayitliKullanici)
        assertEquals(null, m.dorduncuHaftaAktiflik)
        assertEquals(null, m.kayitPerAktifKullanici)
    }

    @Test
    fun haftalikRaporPazartesiSabahiBirKezEpostayaGider() = apiTesti {
        katil("ayse")
        // Pazartesi 08:00 Türkiye: bu haftanın raporu henüz gitmez, önceki haftanınki gider.
        saat.simdi = Instant.parse("2026-01-12T05:00:00Z")
        assertTrue(metrikler.haftalikRaporuGonder())
        assertTrue("2026-01-05" in eposta.gonderilenler.last().first)
        assertFalse(metrikler.haftalikRaporuGonder())

        saat.ilerlet(2.hours)
        assertTrue(metrikler.haftalikRaporuGonder())
        val (konu, metin) = eposta.gonderilenler.last()
        assertTrue("2026-01-12" in konu)
        assertTrue("Kayıtlı kullanıcı: 1" in metin)
        assertTrue("4. haftada aktif kalma: henüz yok" in metin)

        saat.ilerlet(3.days)
        assertFalse(metrikler.haftalikRaporuGonder())
        assertEquals(2, eposta.gonderilenler.size)
    }
}
