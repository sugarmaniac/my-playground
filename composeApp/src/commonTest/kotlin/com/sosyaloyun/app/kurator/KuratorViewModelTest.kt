package com.sosyaloyun.app.kurator

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.KuratorBasvuruDurumu
import com.sosyaloyun.shared.KuratorDurumu
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.KuratorSarti
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeOzeti
import com.sosyaloyun.shared.ListeSayfasi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KuratorViewModelTest {
    private val api = SahteApiIstemcisi()
    private val sartlar = listOf(KuratorSarti(KuratorSartTuru.INCELEME, 20, 25), KuratorSarti(KuratorSartTuru.TAKIPCI, 50, 60))

    @Test
    fun sartlarSaglanincaBasvuruBeklemeyeGecer() = viewModelTesti {
        api.kuratorDurumu = KuratorDurumu(false, null, sartlar)
        val vm = KuratorViewModel(api)
        advanceUntilIdle()
        assertTrue(vm.ekran.value.durum!!.basvurabilir)

        vm.basvur()
        vm.basvur()
        advanceUntilIdle()
        assertEquals(1, api.kuratorBasvurulari)
        assertEquals(KuratorBasvuruDurumu.BEKLIYOR, vm.ekran.value.durum?.basvuru)
    }

    @Test
    fun sartSaglanmazsaBasvuruGitmezHatadaUyariGosterilir() = viewModelTesti {
        api.kuratorDurumu = KuratorDurumu(false, null, listOf(KuratorSarti(KuratorSartTuru.TAKIPCI, 50, 3)))
        val vm = KuratorViewModel(api)
        advanceUntilIdle()
        vm.basvur()
        advanceUntilIdle()
        assertEquals(0, api.kuratorBasvurulari)

        api.kuratorDurumu = KuratorDurumu(false, KuratorBasvuruDurumu.REDDEDILDI, sartlar)
        val vm2 = KuratorViewModel(api)
        advanceUntilIdle()
        api.kuratorHatasi = IllegalStateException("bağlantı yok")
        vm2.basvur()
        advanceUntilIdle()
        assertTrue(vm2.ekran.value.hata)
        assertEquals(KuratorBasvuruDurumu.REDDEDILDI, vm2.ekran.value.durum?.basvuru)
    }

    @Test
    fun yuklenemezseBolumGosterilmez() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = KuratorViewModel(api)
        val listeler = KuratorListeleriViewModel(api)
        advanceUntilIdle()
        assertNull(vm.ekran.value.durum)
        assertEquals(emptyList(), listeler.listeler.value)
    }

    @Test
    fun kuratorListeleriYuklenir() = viewModelTesti {
        val liste = ListeOzeti("l1", KullaniciOzeti("k2", "mehmet", "Mehmet", Avatar.KUMANDA), "Seçkiler", ListeGorunurlugu.HERKES, 0, emptyList(), 0)
        api.kuratorListeSayfasi = ListeSayfasi(listOf(liste), null)
        val vm = KuratorListeleriViewModel(api)
        advanceUntilIdle()
        assertEquals(listOf(liste), vm.listeler.value)
    }
}
