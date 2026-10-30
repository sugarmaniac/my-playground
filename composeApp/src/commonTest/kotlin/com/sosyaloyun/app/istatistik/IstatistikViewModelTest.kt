package com.sosyaloyun.app.istatistik

import com.sosyaloyun.app.profil.ProfilSayfasiViewModel
import com.sosyaloyun.app.profil.ProfilSekmesi
import com.sosyaloyun.app.profil.SekmeIcerigi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.YillikOzet
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IstatistikViewModelTest {
    private val api = SahteApiIstemcisi()

    @Test
    fun istatistikSekmesiBaskasininIstatistikleriniYukler() = viewModelTesti {
        api.istatistik = api.istatistik.copy(toplamSureDakika = 600)
        val vm = ProfilSayfasiViewModel(api, "mehmet", webAdresi = "https://ornek.app")
        advanceUntilIdle()
        vm.sekmeSecildi(ProfilSekmesi.ISTATISTIK)
        advanceUntilIdle()
        assertEquals(600, (vm.ekran.value.icerik as SekmeIcerigi.Istatistik).istatistikler.toplamSureDakika)
        assertTrue("istatistik:mehmet:null" in api.istatistikIstekleri)
    }

    @Test
    fun yillikOzetYuklenirPaylasimLinkiUretilir() = viewModelTesti {
        val ayse = KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.KUMANDA)
        api.yillikOzetler[2025] = YillikOzet(2025, ayse, emptyList(), 120, emptyList(), "Shooter")
        val vm = YillikOzetViewModel(api, null, 2025, webAdresi = "https://ornek.app")
        advanceUntilIdle()
        val hazir = vm.ekran.value as YillikOzetEkrani.Hazir
        assertEquals("https://ornek.app/yillik.html?ad=ayse&yil=2025", hazir.paylasimLinki)
        assertEquals(listOf("yillik:null:2025"), api.istatistikIstekleri)
    }

    @Test
    fun yillikOzetYuklenemezseHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = YillikOzetViewModel(api, "mehmet", 2026, webAdresi = "https://ornek.app")
        advanceUntilIdle()
        assertEquals(YillikOzetEkrani.Hata, vm.ekran.value)
    }
}
