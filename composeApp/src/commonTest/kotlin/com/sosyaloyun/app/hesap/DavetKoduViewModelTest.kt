package com.sosyaloyun.app.hesap

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.HataKodu
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DavetKoduViewModelTest {
    private val api = SahteApiIstemcisi()
    private var hesapYenilendi = 0
    private fun viewModel() = DavetKoduViewModel(api) { hesapYenilendi++ }

    @Test
    fun bosKodGonderilemez() = viewModelTesti {
        val vm = viewModel()
        assertFalse(vm.ekran.value.gonderilebilir)
        vm.kodDegisti("  ")
        assertFalse(vm.ekran.value.gonderilebilir)
        vm.kodDegisti("abcd2345")
        assertTrue(vm.ekran.value.gonderilebilir)
    }

    @Test
    fun gecerliKodGonderilinceAdimTamamlanir() = viewModelTesti {
        val vm = viewModel()
        vm.kodDegisti("abcd2345")
        vm.gonder()
        advanceUntilIdle()

        assertEquals(listOf("abcd2345"), api.kullanilanDavetKodlari)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun gecersizVeKullanilmisKodlarAyriHatalarlaGosterilir() = viewModelTesti {
        val vm = viewModel()
        vm.kodDegisti("YOKBOYLE")

        api.davetKoduHatasi = ApiHatasiIstisnasi(HataKodu.DAVET_KODU_GECERSIZ)
        vm.gonder()
        advanceUntilIdle()
        assertEquals(DavetKoduHatasi.GECERSIZ, vm.ekran.value.hata)

        api.davetKoduHatasi = ApiHatasiIstisnasi(HataKodu.DAVET_KODU_KULLANILMIS)
        vm.gonder()
        advanceUntilIdle()
        assertEquals(DavetKoduHatasi.KULLANILMIS, vm.ekran.value.hata)
        assertEquals(0, hesapYenilendi)
    }

    @Test
    fun oturumDusmusseKodHatasiGosterilmezHesapYenilenir() = viewModelTesti {
        api.davetKoduHatasi = ApiHatasiIstisnasi(HataKodu.OTURUM_GEREKLI)
        val vm = viewModel()
        vm.kodDegisti("ABCD2345")
        vm.gonder()
        advanceUntilIdle()

        assertEquals(null, vm.ekran.value.hata)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun kodDegisinceHataTemizlenir() = viewModelTesti {
        api.davetKoduHatasi = ApiHatasiIstisnasi(HataKodu.DAVET_KODU_GECERSIZ)
        val vm = viewModel()
        vm.kodDegisti("YOKBOYLE")
        vm.gonder()
        advanceUntilIdle()

        vm.kodDegisti("YOKBOYLE2")

        assertEquals(null, vm.ekran.value.hata)
    }

    @Test
    fun servereUlasilamazsaBaglantiHatasiGosterilir() = viewModelTesti {
        api.davetKoduHatasi = IllegalStateException("bağlantı yok")
        val vm = viewModel()
        vm.kodDegisti("ABCD2345")
        vm.gonder()
        advanceUntilIdle()
        assertEquals(DavetKoduHatasi.BAGLANTI, vm.ekran.value.hata)
    }
}
