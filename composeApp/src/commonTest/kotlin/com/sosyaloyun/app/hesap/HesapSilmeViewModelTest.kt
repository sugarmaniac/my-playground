package com.sosyaloyun.app.hesap

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HesapSilmeViewModelTest {
    private val api = SahteApiIstemcisi()
    private var silindi = 0

    private fun viewModel() = HesapSilmeViewModel(api, "ayse") { silindi++ }

    @Test
    fun kullaniciAdiYazilmadanSilinmez() = viewModelTesti {
        val vm = viewModel()
        vm.sil()
        vm.onayDegisti("evet")
        vm.sil()
        advanceUntilIdle()

        assertFalse(vm.ekran.value.silinebilir)
        assertEquals(emptyList(), api.silmeOnaylari)
        assertEquals(0, silindi)
    }

    @Test
    fun kullaniciAdiYazilincaSilinirVeOturumKapanir() = viewModelTesti {
        val vm = viewModel()
        vm.onayDegisti(" Ayse ")
        assertTrue(vm.ekran.value.silinebilir)

        vm.sil()
        advanceUntilIdle()

        assertEquals(listOf(" Ayse "), api.silmeOnaylari)
        assertEquals(1, silindi)
    }

    @Test
    fun silinemezseHataGosterilirOturumAcikKalir() = viewModelTesti {
        api.silmeHatasi = IllegalStateException("bağlantı yok")
        val vm = viewModel()
        vm.onayDegisti("ayse")

        vm.sil()
        advanceUntilIdle()

        assertTrue(vm.ekran.value.hata)
        assertTrue(vm.ekran.value.silinebilir)
        assertEquals(0, silindi)
    }
}
