package com.sosyaloyun.app.kutuphane

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HizliKayitViewModelTest {
    private val api = SahteApiIstemcisi()
    private val kaydedilenler = mutableListOf<KutuphaneGirisi?>()
    private fun viewModel(baslangic: KutuphaneGirisi? = null) = HizliKayitViewModel(api, "oyun-1", baslangic) { kaydedilenler += it }

    @Test
    fun mevcutGirisleBaslar() = viewModelTesti {
        val giris = KutuphaneGirisi(Durum.OYNUYORUM, puan = 7, begendim = true)
        assertEquals(giris, viewModel(giris).ekran.value.giris)
        assertEquals(KutuphaneGirisi(), viewModel().ekran.value.giris)
    }

    @Test
    fun ayniDurumaTekrarDokununcaDurumKalkar() = viewModelTesti {
        val vm = viewModel()
        vm.durumSecildi(Durum.OYNUYORUM)
        assertEquals(Durum.OYNUYORUM, vm.ekran.value.giris.durum)
        vm.durumSecildi(Durum.OYNUYORUM)
        assertNull(vm.ekran.value.giris.durum)
    }

    @Test
    fun yuzdeYuzYalnizcaBitirdimdeSecilebilir() = viewModelTesti {
        val vm = viewModel()
        vm.durumSecildi(Durum.OYNUYORUM)
        assertFalse(vm.ekran.value.yuzdeYuzSecilebilir)
        vm.yuzdeYuzDegisti()
        assertFalse(vm.ekran.value.giris.yuzdeYuz)

        vm.durumSecildi(Durum.BITIRDIM)
        assertTrue(vm.ekran.value.yuzdeYuzSecilebilir)
        vm.yuzdeYuzDegisti()
        assertTrue(vm.ekran.value.giris.yuzdeYuz)
    }

    @Test
    fun bitirdimdenBaskaDurumaGecinceYuzdeYuzKalkar() = viewModelTesti {
        val vm = viewModel(KutuphaneGirisi(Durum.BITIRDIM, yuzdeYuz = true))
        vm.durumSecildi(Durum.BIRAKTIM)
        assertEquals(KutuphaneGirisi(Durum.BIRAKTIM, yuzdeYuz = false), vm.ekran.value.giris)
    }

    @Test
    fun yarimYildizliPuanVerilirAyniPuanaDokununcaKalkar() = viewModelTesti {
        val vm = viewModel()
        vm.puanSecildi(7)
        assertEquals(7, vm.ekran.value.giris.puan)
        vm.puanSecildi(7)
        assertNull(vm.ekran.value.giris.puan)
    }

    @Test
    fun biraktimVeIstekListesiBirlikteSecilebilir() = viewModelTesti {
        val vm = viewModel()
        vm.durumSecildi(Durum.BIRAKTIM)
        vm.istekListesiDegisti()
        vm.begendimDegisti()
        assertEquals(KutuphaneGirisi(Durum.BIRAKTIM, begendim = true, istekListesinde = true), vm.ekran.value.giris)
    }

    @Test
    fun kaydedilinceGirisGonderilirVeBildirilir() = viewModelTesti {
        val vm = viewModel()
        vm.durumSecildi(Durum.BITIRDIM)
        vm.puanSecildi(9)
        vm.yuzdeYuzDegisti()

        vm.kaydet()
        assertTrue(vm.ekran.value.kaydediliyor)
        advanceUntilIdle()

        val beklenen = KutuphaneGirisi(Durum.BITIRDIM, puan = 9, yuzdeYuz = true)
        assertEquals(listOf("oyun-1" to beklenen), api.kutuphaneKayitlari)
        assertEquals(listOf<KutuphaneGirisi?>(beklenen), kaydedilenler)
    }

    @Test
    fun hepsiTemizlenipKaydedilinceGirisKaldirilmisBildirilir() = viewModelTesti {
        val vm = viewModel(KutuphaneGirisi(Durum.OYNUYORUM))
        vm.durumSecildi(Durum.OYNUYORUM)
        vm.kaydet()
        advanceUntilIdle()
        assertEquals(listOf<KutuphaneGirisi?>(null), kaydedilenler)
    }

    @Test
    fun kaydedilemezseHataGosterilirVeFormKorunur() = viewModelTesti {
        api.kutuphaneHatasi = IllegalStateException("bağlantı yok")
        val vm = viewModel()
        vm.durumSecildi(Durum.OYNUYORUM)
        vm.kaydet()
        advanceUntilIdle()

        assertTrue(vm.ekran.value.hata)
        assertFalse(vm.ekran.value.kaydediliyor)
        assertEquals(Durum.OYNUYORUM, vm.ekran.value.giris.durum)
        assertEquals(emptyList(), kaydedilenler)
    }

    @Test
    fun sunucuReddederseDeHataGosterilir() = viewModelTesti {
        api.kutuphaneHatasi = ApiHatasiIstisnasi(HataKodu.YUZDE_YUZ_YALNIZCA_BITIRDIM)
        val vm = viewModel()
        vm.kaydet()
        advanceUntilIdle()
        assertTrue(vm.ekran.value.hata)
    }
}
