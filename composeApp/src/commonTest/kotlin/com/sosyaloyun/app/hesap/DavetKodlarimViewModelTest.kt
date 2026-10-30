package com.sosyaloyun.app.hesap

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.KullaniciOzeti
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class DavetKodlarimViewModelTest {
    private val api = SahteApiIstemcisi()

    @Test
    fun kodlarVeKullananlarGosterilir() = viewModelTesti {
        val mehmet = KullaniciOzeti("k2", "mehmet", "Mehmet", Avatar.EJDERHA)
        api.davetKodlari = listOf(
            DavetKodu("AAAA2222", kullanan = mehmet, kullanildi = true),
            DavetKodu("BBBB3333", kullanan = null, kullanildi = false),
        )

        val vm = DavetKodlarimViewModel(api)
        advanceUntilIdle()

        assertEquals(DavetKodlarimEkrani.Hazir(api.davetKodlari), vm.ekran.value)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = DavetKodlarimViewModel(api)
        advanceUntilIdle()
        assertEquals(DavetKodlarimEkrani.Hata, vm.ekran.value)
    }
}
