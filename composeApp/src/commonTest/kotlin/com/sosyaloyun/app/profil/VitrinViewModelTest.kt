package com.sosyaloyun.app.profil

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VitrinViewModelTest {
    private val api = SahteApiIstemcisi()
    private var kaydedildi = 0
    private fun oyun(id: String) = OyunOzeti(id, "Oyun $id", OyunTuru.ANA_OYUN, null, null, emptyList(), null)
    private fun vm(vararg mevcut: String) = VitrinViewModel(api, mevcut.map(::oyun)) { kaydedildi++ }

    @Test
    fun enFazlaDortFarkliOyunEklenir() = viewModelTesti {
        val vm = vm("a")
        vm.ekle(oyun("a"))
        vm.ekle(oyun("b"))
        vm.ekle(oyun("c"))
        vm.ekle(oyun("d"))
        assertFalse(vm.ekran.value.eklenebilir)
        vm.ekle(oyun("e"))

        assertEquals(listOf("a", "b", "c", "d"), vm.ekran.value.oyunlar.map { it.id })
    }

    @Test
    fun oyunCikarilincaYerAcilir() = viewModelTesti {
        val vm = vm("a", "b", "c", "d")
        vm.cikar("b")
        assertTrue(vm.ekran.value.eklenebilir)
        assertEquals(listOf("a", "c", "d"), vm.ekran.value.oyunlar.map { it.id })
    }

    @Test
    fun kaydedilinceSiraylaGonderilir() = viewModelTesti {
        val vm = vm("a", "b")
        vm.cikar("a")
        vm.ekle(oyun("c"))
        vm.kaydet()
        advanceUntilIdle()

        assertEquals(listOf(listOf("b", "c")), api.kaydedilenVitrinler)
        assertEquals(1, kaydedildi)
    }

    @Test
    fun kaydedilemezseHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = vm("a")
        vm.kaydet()
        advanceUntilIdle()
        assertTrue(vm.ekran.value.hata)
        assertEquals(0, kaydedildi)
    }
}
