package com.sosyaloyun.app.arama

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class AramaViewModelTest {
    private val api = SahteApiIstemcisi()

    private fun oyun(ad: String) = OyunOzeti(ad, ad, OyunTuru.ANA_OYUN, 2020, null, listOf("PC"), null)

    @Test
    fun ikiKarakterdenKisaSorguAranmaz() = viewModelTesti {
        val vm = AramaViewModel(api)
        vm.sorguDegisti("z")
        advanceUntilIdle()

        assertEquals(AramaSonucu.Bos, vm.ekran.value.sonuc)
        assertEquals(emptyList(), api.aramalar)
    }

    @Test
    fun yazmaDuruncaYalnizcaSonSorguAranir() = viewModelTesti {
        api.katalog = listOf(oyun("Zelda"), oyun("Zork"))
        val vm = AramaViewModel(api)

        vm.sorguDegisti("ze")
        advanceTimeBy(100)
        vm.sorguDegisti("zel")
        assertEquals(AramaSonucu.Yukleniyor, vm.ekran.value.sonuc)
        advanceUntilIdle()

        assertEquals(listOf<Pair<String, String?>>("zel" to null), api.aramalar)
        assertEquals(AramaSonucu.Oyunlar(listOf(oyun("Zelda")), dahaVar = false), vm.ekran.value.sonuc)
    }

    @Test
    fun eslesmeYoksaSonucYokGosterilir() = viewModelTesti {
        val vm = AramaViewModel(api)
        vm.sorguDegisti("bulunmaz")
        advanceUntilIdle()
        assertEquals<AramaSonucu>(AramaSonucu.SonucYok, vm.ekran.value.sonuc)
    }

    @Test
    fun dahaFazlaSonucSonrakiSayfayiEkler() = viewModelTesti {
        api.katalog = List(5) { oyun("Yarış $it") }
        api.aramaSayfasi = 3
        val vm = AramaViewModel(api)
        vm.sorguDegisti("yarış")
        advanceUntilIdle()
        assertEquals(AramaSonucu.Oyunlar(api.katalog.take(3), dahaVar = true), vm.ekran.value.sonuc)

        vm.dahaFazlaYukle()
        advanceUntilIdle()

        assertEquals(AramaSonucu.Oyunlar(api.katalog, dahaVar = false), vm.ekran.value.sonuc)
        assertEquals(listOf<Pair<String, String?>>("yarış" to null, "yarış" to "3"), api.aramalar)
    }

    @Test
    fun kullaniciAramasinaGecilinceKullanicilarAranir() = viewModelTesti {
        api.kullanicilar = listOf(KullaniciOzeti("k1", "mehmet", "Mehmet", Avatar.KEDI), KullaniciOzeti("k2", "ayse", "Ayşe", Avatar.TILKI))
        val vm = AramaViewModel(api)
        vm.sorguDegisti("meh")
        advanceUntilIdle()

        vm.turSecildi(AramaTuru.KULLANICI)
        advanceUntilIdle()

        assertEquals(AramaTuru.KULLANICI, vm.ekran.value.tur)
        assertEquals(listOf("meh"), api.kullaniciAramalari)
        assertEquals(AramaSonucu.Kullanicilar(listOf(api.kullanicilar[0])), vm.ekran.value.sonuc)

        vm.sorguDegisti("ays")
        advanceUntilIdle()
        assertEquals(AramaSonucu.Kullanicilar(listOf(api.kullanicilar[1])), vm.ekran.value.sonuc)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = AramaViewModel(api)
        vm.sorguDegisti("zelda")
        advanceUntilIdle()
        assertEquals(AramaSonucu.Hata, vm.ekran.value.sonuc)
    }
}
