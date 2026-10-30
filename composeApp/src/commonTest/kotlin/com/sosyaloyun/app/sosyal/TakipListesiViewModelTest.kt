package com.sosyaloyun.app.sosyal

import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class TakipListesiViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun kisi(ad: String) = KullaniciOzeti(ad, ad, ad, Avatar.KEDI)

    @Test
    fun takipcilerYuklenirVeSayfalanir() = viewModelTesti {
        api.kullanicilar = List(5) { kisi("k$it") }
        api.profilSayfaBoyu = 3
        val vm = TakipListesiViewModel(api, "ayse", KisiListesiTuru.TAKIPCILER)
        advanceUntilIdle()
        assertEquals(KullaniciListesiEkrani.Hazir(api.kullanicilar.take(3), dahaVar = true), vm.ekran.value)

        vm.dahaFazlaYukle()
        advanceUntilIdle()

        assertEquals(KullaniciListesiEkrani.Hazir(api.kullanicilar, dahaVar = false), vm.ekran.value)
        assertEquals(listOf("liste:ayse:TAKIPCILER:null", "liste:ayse:TAKIPCILER:3"), api.takipIstekleri)
    }

    @Test
    fun takipEdilenlerIstenir() = viewModelTesti {
        TakipListesiViewModel(api, "ayse", KisiListesiTuru.TAKIP_EDILENLER)
        advanceUntilIdle()
        assertEquals(listOf("liste:ayse:TAKIP_EDILENLER:null"), api.takipIstekleri)
    }

    @Test
    fun arkadaslarIstenir() = viewModelTesti {
        TakipListesiViewModel(api, "ayse", KisiListesiTuru.ARKADASLAR)
        advanceUntilIdle()
        assertEquals(listOf("liste:ayse:ARKADASLAR:null"), api.takipIstekleri)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = TakipListesiViewModel(api, "ayse", KisiListesiTuru.TAKIPCILER)
        advanceUntilIdle()
        assertEquals(KullaniciListesiEkrani.Hata, vm.ekran.value)
    }
}
