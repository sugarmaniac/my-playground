package com.sosyaloyun.app.gizlilik

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GizlilikViewModelTest {
    private val api = SahteApiIstemcisi()
    private val mehmet = KullaniciOzeti("k2", "mehmet", "Mehmet", Avatar.KUMANDA)
    private val zeynep = KullaniciOzeti("k3", "zeynep", "Zeynep", Avatar.TILKI)

    @Test
    fun gizlilikVeIsteklerYuklenirKabulVeRetIstegiKaldirir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(gizli = true)
        api.gelenTakipIstekleri = mutableListOf(mehmet, zeynep)
        val vm = GizlilikViewModel(api)
        advanceUntilIdle()
        assertEquals(GizlilikEkrani(yuklendi = true, gizli = true, istekler = listOf(mehmet, zeynep)), vm.ekran.value)

        vm.kabulEt("mehmet")
        vm.reddet("zeynep")
        advanceUntilIdle()
        assertEquals(emptyList(), vm.ekran.value.istekler)
        assertEquals(listOf("kabul:mehmet", "reddet:zeynep"), api.gizlilikIstekleri)
    }

    @Test
    fun acigaGecinceIsteklerTemizlenirHatadaGeriAlinir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(gizli = true)
        api.gelenTakipIstekleri = mutableListOf(mehmet)
        val vm = GizlilikViewModel(api)
        advanceUntilIdle()

        api.gizlilikHatasi = IllegalStateException("bağlantı yok")
        vm.gizliDegistir(false)
        advanceUntilIdle()
        assertEquals(true, vm.ekran.value.gizli)
        assertEquals(listOf(mehmet), vm.ekran.value.istekler)
        assertTrue(vm.ekran.value.hata)

        api.gizlilikHatasi = null
        vm.gizliDegistir(false)
        advanceUntilIdle()
        assertEquals(GizlilikEkrani(yuklendi = true, gizli = false), vm.ekran.value)
        assertEquals(listOf("gizli:false"), api.gizlilikIstekleri)
    }

    @Test
    fun basarisizIstekYalnizcaKendiKisisiniGeriGetirir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(gizli = true)
        api.gelenTakipIstekleri = mutableListOf(mehmet, zeynep)
        val vm = GizlilikViewModel(api)
        advanceUntilIdle()

        api.basarisizTakipIstekleri += "mehmet"
        vm.kabulEt("mehmet")
        vm.reddet("zeynep")
        advanceUntilIdle()

        assertEquals(listOf(mehmet), vm.ekran.value.istekler)
        assertTrue(vm.ekran.value.hata)
    }

    @Test
    fun ilkYuklemeHatasindaTekrarDenenebilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = GizlilikViewModel(api)
        advanceUntilIdle()
        assertEquals(GizlilikEkrani(hata = true), vm.ekran.value)

        api.hata = null
        vm.yenile()
        advanceUntilIdle()
        assertTrue(vm.ekran.value.yuklendi)
    }
}
