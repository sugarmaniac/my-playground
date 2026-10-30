package com.sosyaloyun.app.bildirim

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Bildirim
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BildirimViewModelTest {
    private val api = SahteApiIstemcisi()
    private val bildirim = Bildirim("b1", BildirimTuru.BEGENI, KullaniciOzeti("k", "ayse", "Ayşe", Avatar.KEDI), Instant.parse("2026-01-05T09:00:00Z"), okundu = false, digerSayisi = 4, gunlukKaydiId = "g1")

    @Test
    fun acilincaListeYuklenirVeOkunduSayilir() = viewModelTesti {
        api.bildirimListesi = BildirimListesi(listOf(bildirim), okunmamisSayisi = 1)
        val vm = BildirimlerViewModel(api)
        advanceUntilIdle()

        assertEquals(BildirimlerEkrani.Hazir(listOf(bildirim)), vm.ekran.value)
        assertEquals(listOf("okundu"), api.bildirimIstekleri)
    }

    @Test
    fun okunmamisYoksaOkunduIstegiGitmez() = viewModelTesti {
        api.bildirimListesi = BildirimListesi(listOf(bildirim.copy(okundu = true)), okunmamisSayisi = 0)
        BildirimlerViewModel(api)
        advanceUntilIdle()
        assertEquals(emptyList(), api.bildirimIstekleri)
    }

    @Test
    fun okunmamisSayisiYenilenir() = viewModelTesti {
        api.bildirimListesi = BildirimListesi(listOf(bildirim), okunmamisSayisi = 3)
        val vm = OkunmamisBildirimViewModel(api)
        vm.yenile()
        advanceUntilIdle()
        assertEquals(3, vm.sayi.value)
    }

    @Test
    fun tercihDegisikligiKaydedilirHatadaGeriAlinir() = viewModelTesti {
        val vm = BildirimTercihleriViewModel(api)
        advanceUntilIdle()

        vm.degistir(BildirimTuru.BEGENI, false)
        advanceUntilIdle()
        assertEquals(false, vm.tercihler.value!![BildirimTuru.BEGENI])
        assertEquals(listOf("tercih:{BEGENI=false}"), api.bildirimIstekleri)

        api.bildirimHatasi = IllegalStateException("bağlantı yok")
        vm.degistir(BildirimTuru.YENI_TAKIPCI, false)
        advanceUntilIdle()
        assertEquals(true, vm.tercihler.value!![BildirimTuru.YENI_TAKIPCI])
    }
}
