package com.sosyaloyun.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilKurallariTest {
    @Test
    fun kullaniciAdiKucukHarfeCevrilirVeBastakiAtAtilir() {
        assertEquals("oyuncu_42", ProfilKurallari.kullaniciAdiniDuzenle("  @Oyuncu_42 "))
    }

    @Test
    fun gecerliKullaniciAdlari() {
        listOf("ali", "oyuncu_42", "ayse.yilmaz", "a1_b2.c3", "x".repeat(20)).forEach {
            assertTrue(ProfilKurallari.kullaniciAdiGecerliMi(it), it)
        }
    }

    @Test
    fun gecersizKullaniciAdlari() {
        listOf("ab", "x".repeat(21), "ayşe", "çılgın", "boş luk", ".ali", "ali.", "a..b", "ali-veli", "ALİ").forEach {
            assertFalse(ProfilKurallari.kullaniciAdiGecerliMi(it), it)
        }
    }

    @Test
    fun gorunenAdBirIleKirkKarakterArasi() {
        assertTrue(ProfilKurallari.gorunenAdGecerliMi("Ayşe Yılmaz"))
        assertFalse(ProfilKurallari.gorunenAdGecerliMi("   "))
        assertFalse(ProfilKurallari.gorunenAdGecerliMi("x".repeat(41)))
    }

    @Test
    fun davetKoduBuyukHarfeCevrilirVeAyiricilarAtilir() {
        assertEquals("ABCD2345", DavetKoduKurallari.duzenle(" abcd-2345 "))
    }
}
