package com.sosyaloyun.app.sikayet

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SikayetViewModelTest {
    private val api = SahteApiIstemcisi()

    @Test
    fun gunlukKaydiSikayetEdilirSonraEngellenir() = viewModelTesti {
        val vm = SikayetViewModel(api, SikayetHedefi("mehmet", gunlukKaydiId = "g1"))
        assertFalse((vm.ekran.value as SikayetEkrani.Form).gonderilebilir)

        vm.sebepSec(SikayetSebebi.TACIZ)
        vm.aciklamaDegisti("  Hakaret var  ")
        vm.gonder()
        advanceUntilIdle()
        assertEquals(listOf(SikayetIstegi(SikayetSebebi.TACIZ, gunlukKaydiId = "g1", aciklama = "Hakaret var")), api.sikayetler)
        assertEquals(SikayetEkrani.Gonderildi(), vm.ekran.value)

        vm.engelle()
        advanceUntilIdle()
        assertEquals(SikayetEkrani.Gonderildi(SikayetEkrani.Islem.ENGELLENDI), vm.ekran.value)
        assertEquals(listOf("engelle:mehmet"), api.engelIstekleri)
    }

    @Test
    fun profilSikayetiKullaniciAdiylaGiderHatadaFormKalir() = viewModelTesti {
        api.sikayetHatasi = IllegalStateException("bağlantı yok")
        val vm = SikayetViewModel(api, SikayetHedefi("mehmet"))
        vm.sebepSec(SikayetSebebi.SAHTE_HESAP)
        vm.gonder()
        advanceUntilIdle()
        assertEquals(SikayetEkrani.Form(SikayetSebebi.SAHTE_HESAP, hata = true), vm.ekran.value)

        api.sikayetHatasi = null
        vm.gonder()
        advanceUntilIdle()
        assertEquals(listOf(SikayetIstegi(SikayetSebebi.SAHTE_HESAP, kullaniciAdi = "mehmet")), api.sikayetler)
        vm.sessizeAl()
        advanceUntilIdle()
        assertEquals(SikayetEkrani.Gonderildi(SikayetEkrani.Islem.SESSIZE_ALINDI), vm.ekran.value)
    }
}
