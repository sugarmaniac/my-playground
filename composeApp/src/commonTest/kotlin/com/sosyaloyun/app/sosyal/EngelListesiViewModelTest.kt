package com.sosyaloyun.app.sosyal

import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class EngelListesiViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun kisi(ad: String) = KullaniciOzeti(ad, ad, ad, Avatar.KEDI)

    @Test
    fun engelKaldirilinceKisiListedenDuser() = viewModelTesti {
        api.engelListeleri = mapOf(EngelListesiTuru.ENGELLENENLER to listOf(kisi("ayse"), kisi("mehmet")))
        val vm = EngelListesiViewModel(api, EngelListesiTuru.ENGELLENENLER)
        advanceUntilIdle()

        vm.kaldir("ayse")
        advanceUntilIdle()

        assertEquals(KullaniciListesiEkrani.Hazir(listOf(kisi("mehmet")), dahaVar = false), vm.ekran.value)
        assertEquals(listOf("engeliKaldir:ayse"), api.engelIstekleri)
    }

    @Test
    fun sessizdenCikarmaBasarisizsaKisiGeriGelir() = viewModelTesti {
        api.engelListeleri = mapOf(EngelListesiTuru.SESSIZE_ALINANLAR to listOf(kisi("ayse"), kisi("mehmet")))
        val vm = EngelListesiViewModel(api, EngelListesiTuru.SESSIZE_ALINANLAR)
        advanceUntilIdle()
        api.engelHatasi = IllegalStateException("bağlantı yok")

        vm.kaldir("mehmet")
        advanceUntilIdle()

        assertEquals(KullaniciListesiEkrani.Hazir(listOf(kisi("ayse"), kisi("mehmet")), dahaVar = false), vm.ekran.value)
    }
}
