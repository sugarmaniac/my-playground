package com.sosyaloyun.app.puan

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.HaftalikPuanDegisimi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import com.sosyaloyun.shared.PuanDegisimi
import com.sosyaloyun.shared.PuanGecmisi
import com.sosyaloyun.shared.PuanHaftasi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PuanGecmisiViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun hafta(gun: Int, puan: Double?) = PuanHaftasi(LocalDate(2026, 1, gun), puan, if (puan == null) 0 else 5)

    @Test
    fun ikiPuanliHaftaYoksaGrafikGosterilmez() = viewModelTesti {
        api.puanGecmisi = PuanGecmisi(listOf(hafta(5, null), hafta(12, 7.0)))
        val vm = PuanGecmisiViewModel(api, "o1")
        advanceUntilIdle()
        assertEquals(emptyList(), vm.haftalar.value)

        val gecmis = listOf(hafta(5, 6.0), hafta(12, null), hafta(19, 7.0))
        api.puanGecmisi = PuanGecmisi(gecmis)
        val vm2 = PuanGecmisiViewModel(api, "o1")
        advanceUntilIdle()
        assertEquals(gecmis, vm2.haftalar.value)
    }

    @Test
    fun degisimYuklenirHatadaBosKalir() = viewModelTesti {
        val oyun = OyunOzeti("o1", "Alfa", OyunTuru.ANA_OYUN, 2020, null, emptyList(), null)
        api.puanDegisimi = HaftalikPuanDegisimi(listOf(PuanDegisimi(oyun, 6.0, 8.0)), emptyList())
        val vm = PuanDegisimiViewModel(api)
        advanceUntilIdle()
        assertEquals(2.0, vm.degisim.value.artanlar.single().fark)

        api.hata = IllegalStateException("bağlantı yok")
        val vm2 = PuanDegisimiViewModel(api)
        advanceUntilIdle()
        assertEquals(HaftalikPuanDegisimi(emptyList(), emptyList()), vm2.degisim.value)
    }

    @Test
    fun farkYildizCinsindenYonluGosterilir() {
        assertEquals("▲ 1,0", farkMetni(2.0))
        assertEquals("▼ 0,3", farkMetni(-0.5))
    }
}
