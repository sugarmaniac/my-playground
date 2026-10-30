package com.sosyaloyun.app.hesap

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class HesapViewModelTest {
    private val api = SahteApiIstemcisi()
    private val profil = KullaniciOzeti("kullanici-1", "ayse", "Ayşe", Avatar.TILKI)

    @Test
    fun davetKoduGirilmemisseIlkAdimDavetKodudur() = viewModelTesti {
        val viewModel = HesapViewModel(api)
        assertEquals(HesapEkrani.Yukleniyor, viewModel.ekran.value)
        advanceUntilIdle()
        assertEquals(HesapEkrani.DavetKodu, viewModel.ekran.value)
    }

    @Test
    fun davettenSonraKullaniciAdiAdiminaGecilir() = viewModelTesti {
        val viewModel = HesapViewModel(api)
        advanceUntilIdle()

        api.davetKoduKullan("KOD")
        viewModel.yenile()
        advanceUntilIdle()

        assertEquals(HesapEkrani.Profil, viewModel.ekran.value)
    }

    @Test
    fun profilSecilmisAmaOnboardingBitmemisseOnboardingGosterilir() = viewModelTesti {
        api.benYaniti = api.benYaniti.copy(davetKabulEdildi = true, profil = profil)
        val viewModel = HesapViewModel(api)
        advanceUntilIdle()
        assertEquals(HesapEkrani.Onboarding, viewModel.ekran.value)
    }

    @Test
    fun onboardingBittiyseUygulamaHazirdir() = viewModelTesti {
        api.benYaniti = api.benYaniti.copy(davetKabulEdildi = true, profil = profil, onboardingTamamlandi = true)
        val viewModel = HesapViewModel(api)
        advanceUntilIdle()
        assertEquals(HesapEkrani.Hazir(profil), viewModel.ekran.value)
    }

    @Test
    fun oturumSunucudaKapanmissaOturumGecersizOlur() = viewModelTesti {
        api.hata = ApiHatasiIstisnasi(HataKodu.OTURUM_GEREKLI)
        val viewModel = HesapViewModel(api)
        advanceUntilIdle()
        assertEquals(HesapEkrani.OturumGecersiz, viewModel.ekran.value)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val viewModel = HesapViewModel(api)
        advanceUntilIdle()
        assertEquals(HesapEkrani.Hata, viewModel.ekran.value)
    }
}
