package com.sosyaloyun.app.itiraz

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.ItirazDurumu
import com.sosyaloyun.shared.ItirazSonucu
import com.sosyaloyun.shared.YaptirimOzeti
import com.sosyaloyun.shared.YaptirimTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ItirazViewModelTest {
    private val api = SahteApiIstemcisi()
    private val uyari = YaptirimOzeti(YaptirimTuru.UYARI, Instant.parse("2026-01-05T09:00:00Z"))

    @Test
    fun itirazBirKezGonderilirHatadaMetinKalir() = viewModelTesti {
        api.itirazDurumu = ItirazDurumu(uyari)
        val vm = ItirazViewModel(api)
        advanceUntilIdle()
        vm.metinDegisti("Yanlış anlaşıldı")

        api.itirazHatasi = IllegalStateException("bağlantı yok")
        vm.gonder()
        advanceUntilIdle()
        assertTrue(vm.ekran.value.hata)
        assertEquals("Yanlış anlaşıldı", vm.ekran.value.metin)

        api.itirazHatasi = null
        vm.gonder()
        advanceUntilIdle()
        assertEquals(ItirazSonucu.BEKLIYOR, vm.ekran.value.durum?.itiraz)
        assertFalse(vm.ekran.value.gonderilebilir)
        assertEquals(listOf("Yanlış anlaşıldı"), api.itirazlar)
    }

    @Test
    fun yuklenemezseBolumGosterilmez() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = ItirazViewModel(api)
        advanceUntilIdle()
        assertNull(vm.ekran.value.durum)
    }
}
