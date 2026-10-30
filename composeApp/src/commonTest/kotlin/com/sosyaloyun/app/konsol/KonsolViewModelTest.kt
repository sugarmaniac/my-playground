package com.sosyaloyun.app.konsol

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KonsolAgi
import com.sosyaloyun.shared.KonsolBaglantilari
import com.sosyaloyun.shared.KonsolBaglantisi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KonsolViewModelTest {
    private val api = SahteApiIstemcisi()

    @Test
    fun baglamaSonucuGosterilirGizliProfilAnlasilirHataVerir() = viewModelTesti {
        api.konsolBaglantilari = KonsolBaglantilari(listOf(KonsolBaglantisi(KonsolAgi.XBOX)))
        val vm = KonsolViewModel(api)
        advanceUntilIdle()

        api.konsolHatasi = ApiHatasiIstisnasi(HataKodu.KONSOL_PROFILI_GIZLI)
        vm.girdiDegisti(KonsolAgi.XBOX, "AyseGamer")
        vm.bagla(KonsolAgi.XBOX)
        advanceUntilIdle()
        assertEquals(KonsolHatasi.GIZLI, vm.satirlar.value.single().hata)

        api.konsolHatasi = null
        vm.bagla(KonsolAgi.XBOX)
        advanceUntilIdle()
        val satir = vm.satirlar.value.single()
        assertEquals("AyseGamer", satir.baglanti.hesap)
        assertEquals(3 to 1, satir.sonuc?.let { it.iceAktarilanSayisi to it.eslesmeyenSayisi })

        vm.kaldir(KonsolAgi.XBOX)
        advanceUntilIdle()
        assertNull(vm.satirlar.value.single().baglanti.hesap)
        assertEquals(listOf("bagla:XBOX:AyseGamer", "kaldir:XBOX"), api.konsolIstekleri)
    }

    @Test
    fun anahtarliAgYoksaBolumBos() = viewModelTesti {
        val vm = KonsolViewModel(api)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.satirlar.value)
    }
}
