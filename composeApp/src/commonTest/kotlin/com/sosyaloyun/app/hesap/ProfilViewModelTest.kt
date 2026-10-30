package com.sosyaloyun.app.hesap

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.ProfilIstegi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilViewModelTest {
    private val api = SahteApiIstemcisi()
    private var hesapYenilendi = 0
    private fun viewModel() = ProfilViewModel(api) { hesapYenilendi++ }

    @Test
    fun duzenlemedeMevcutDegerlerleBaslarVeAdMusaitSayilir() = viewModelTesti {
        val vm = ProfilViewModel(api, KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.TILKI), "RPG sever") { hesapYenilendi++ }

        val durum = vm.ekran.value
        assertEquals("ayse", durum.kullaniciAdi)
        assertEquals(Musaitlik.MUSAIT, durum.musaitlik)
        assertEquals("Ayşe", durum.gorunenAd)
        assertEquals(Avatar.TILKI, durum.avatar)
        assertEquals("RPG sever", durum.biyografi)
        assertTrue(durum.duzenleme)
        assertTrue(durum.kaydedilebilir)
        assertEquals(emptyList(), api.musaitlikSorgulari)
    }

    @Test
    fun biyografiDegistirilipKaydedilir() = viewModelTesti {
        val vm = ProfilViewModel(api, KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.TILKI), null) { hesapYenilendi++ }
        vm.biyografiDegisti("  Yeni biyografi  ")
        vm.kaydet()
        advanceUntilIdle()

        assertEquals(listOf(ProfilIstegi("ayse", "Ayşe", Avatar.TILKI, biyografi = "Yeni biyografi")), api.kaydedilenProfiller)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun gecersizAdSunucuyaSorulmadanGosterilir() = viewModelTesti {
        val vm = viewModel()
        vm.kullaniciAdiDegisti("ayşe")
        advanceUntilIdle()

        assertEquals(Musaitlik.GECERSIZ, vm.ekran.value.musaitlik)
        assertEquals(emptyList(), api.musaitlikSorgulari)
    }

    @Test
    fun yazmaBitinceMusaitlikSorulurVeGosterilir() = viewModelTesti {
        api.alinmisAdlar = setOf("ali")
        val vm = viewModel()

        vm.kullaniciAdiDegisti("al")
        vm.kullaniciAdiDegisti("ali")
        assertEquals(Musaitlik.KONTROL_EDILIYOR, vm.ekran.value.musaitlik)
        advanceUntilIdle()

        assertEquals(Musaitlik.ALINMIS, vm.ekran.value.musaitlik)
        assertEquals(listOf("ali"), api.musaitlikSorgulari)

        vm.kullaniciAdiDegisti("@Veli")
        advanceTimeBy(100)
        assertEquals(emptyList<String>(), api.musaitlikSorgulari.drop(1))
        advanceUntilIdle()
        assertEquals(Musaitlik.MUSAIT, vm.ekran.value.musaitlik)
        assertEquals(listOf("ali", "veli"), api.musaitlikSorgulari)
    }

    @Test
    fun adMusaitVeGorunenAdDoluysaKaydedilebilir() = viewModelTesti {
        val vm = viewModel()
        vm.kullaniciAdiDegisti("ayse")
        advanceUntilIdle()
        assertFalse(vm.ekran.value.kaydedilebilir)

        vm.gorunenAdDegisti("Ayşe")
        assertTrue(vm.ekran.value.kaydedilebilir)
    }

    @Test
    fun kaydedilinceSecilenProfilGonderilirVeAdimTamamlanir() = viewModelTesti {
        val vm = viewModel()
        vm.kullaniciAdiDegisti("@Ayse")
        vm.gorunenAdDegisti("Ayşe Yılmaz")
        vm.avatarSecildi(Avatar.TILKI)
        advanceUntilIdle()

        vm.kaydet()
        advanceUntilIdle()

        assertEquals(listOf(ProfilIstegi("ayse", "Ayşe Yılmaz", Avatar.TILKI)), api.kaydedilenProfiller)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun kaydederkenOturumDusmusseHesapYenilenir() = viewModelTesti {
        val vm = viewModel()
        vm.kullaniciAdiDegisti("ayse")
        vm.gorunenAdDegisti("Ayşe")
        advanceUntilIdle()
        api.profilHatasi = ApiHatasiIstisnasi(HataKodu.OTURUM_GEREKLI)

        vm.kaydet()
        advanceUntilIdle()

        assertEquals(1, hesapYenilendi)
        assertFalse(vm.ekran.value.baglantiHatasi)
    }

    @Test
    fun kaydederkenAdAlinmissaMusaitlikAlinmisOlur() = viewModelTesti {
        val vm = viewModel()
        vm.kullaniciAdiDegisti("ayse")
        vm.gorunenAdDegisti("Ayşe")
        advanceUntilIdle()
        api.profilHatasi = ApiHatasiIstisnasi(HataKodu.KULLANICI_ADI_ALINMIS)

        vm.kaydet()
        advanceUntilIdle()

        assertEquals(Musaitlik.ALINMIS, vm.ekran.value.musaitlik)
        assertEquals(0, hesapYenilendi)
    }
}
