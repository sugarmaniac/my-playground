package com.sosyaloyun.app.sosyal

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class GelenIsteklerViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun kisi(ad: String) = KullaniciOzeti(ad, ad, ad, Avatar.KEDI)

    @Test
    fun kabulVeRetIstegiListedenKaldirir() = viewModelTesti {
        api.kullanicilar = listOf(kisi("ayse"), kisi("mehmet"), kisi("zeynep"))
        val vm = GelenIsteklerViewModel(api)
        advanceUntilIdle()

        vm.kabulEt("ayse")
        vm.reddet("zeynep")
        advanceUntilIdle()

        assertEquals(KullaniciListesiEkrani.Hazir(listOf(kisi("mehmet")), dahaVar = false), vm.ekran.value)
        assertEquals(listOf("kabul:ayse", "ret:zeynep"), api.takipIstekleri)
    }

    @Test
    fun islemBasarisizOlursaIstekListedeKalir() = viewModelTesti {
        api.kullanicilar = listOf(kisi("ayse"))
        val vm = GelenIsteklerViewModel(api)
        advanceUntilIdle()
        api.takipHatasi = IllegalStateException("bağlantı yok")

        vm.kabulEt("ayse")
        advanceUntilIdle()

        assertEquals(KullaniciListesiEkrani.Hazir(listOf(kisi("ayse")), dahaVar = false), vm.ekran.value)
    }
}
