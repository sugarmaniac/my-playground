package com.sosyaloyun.server

import com.sosyaloyun.server.test.SahteKatalogKaynagi
import com.sosyaloyun.server.test.apiTesti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KatalogIceAktarmaTest {
    /** [hataKimligi]'nden sonraki sayfayı isterken patlayan kaynak: server'ın içe aktarma ortasında yeniden başlaması. */
    private class YarimKalanKaynak(private val ic: SahteKatalogKaynagi, var hataKimligi: Long?) : KatalogKaynagi by ic {
        override suspend fun oyunSayfasi(sonKaynakId: Long, adet: Int): List<KaynakOyun> {
            if (hataKimligi != null && sonKaynakId >= hataKimligi!!) error("server yeniden başladı")
            return ic.oyunSayfasi(sonKaynakId, adet)
        }
    }

    @Test
    fun populerOyunlarOnceGelirYarimKalanIceAktarmaKaldigiYerdenSurer() = apiTesti {
        // 1200 oyun (üç sayfa); en popüler oyun son sayfada.
        (1L..1200L).forEach { katalogKaynagi.oyunlar += KaynakOyun(it, "Oyun $it", KaynakTuru.ANA_OYUN) }
        katalogKaynagi.populerlikler += KaynakPopulerlik(1150, 99.0)
        val kaynak = YarimKalanKaynak(katalogKaynagi, hataKimligi = 500)
        val katalog = Katalog(veritabani)
        val iceAktarici = KatalogIceAktarici(kaynak, katalog, ayarlar)

        assertFailsWith<IllegalStateException> { iceAktarici.tamIceAktar() }
        // İlk sayfa ve en popüler oyun (son sayfadan) içeride; konum kaydedildi.
        assertEquals(listOf("Oyun 1150"), katalog.populerler(1).map { it.ad })
        assertEquals(500L, ayarlar.oku(Ayar.KatalogIceAktarmaKonumu))

        val okunanSayfalar = mutableListOf<Long>()
        kaynak.hataKimligi = null
        val izleyen = object : KatalogKaynagi by kaynak {
            override suspend fun oyunSayfasi(sonKaynakId: Long, adet: Int): List<KaynakOyun> {
                okunanSayfalar += sonKaynakId
                return kaynak.oyunSayfasi(sonKaynakId, adet)
            }
        }
        KatalogIceAktarici(izleyen, katalog, ayarlar).tamIceAktar()
        // Baştan değil, 500'den devam etti; bitince konum sıfırlandı.
        assertEquals(listOf(500L, 1000L, 1200L), okunanSayfalar)
        assertEquals(0L, ayarlar.oku(Ayar.KatalogIceAktarmaKonumu))
        assertEquals(1200, katalog.populerler(2000).size)
    }
}
