package com.sosyaloyun.app.oneri

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Oneri
import com.sosyaloyun.shared.OneriListesi
import com.sosyaloyun.shared.OneriNedeni
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class OnerilerViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun oneri(id: String) = Oneri(OyunOzeti(id, "Oyun $id", OyunTuru.ANA_OYUN, null, null, emptyList(), null), OneriNedeni.POPULER)

    @Test
    fun ilgilenmiyorumOneriyiKaldirirHatadaYerineGeriKoyar() = viewModelTesti {
        api.oneriListesi = OneriListesi(listOf(oneri("a"), oneri("b"), oneri("c")))
        val vm = OnerilerViewModel(api)
        advanceUntilIdle()

        vm.ilgilenmiyorum("b")
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), vm.oneriler.value.map { it.oyun.id })
        assertEquals(listOf("b"), api.ilgilenilmeyenler)

        api.ilgilenmiyorumHatasi = IllegalStateException("bağlantı yok")
        vm.ilgilenmiyorum("a")
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), vm.oneriler.value.map { it.oyun.id })
    }

    @Test
    fun yuklenemezseBolumBosKalir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = OnerilerViewModel(api)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.oneriler.value)
    }
}
