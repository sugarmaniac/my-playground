package com.sosyaloyun.app.liste

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListeViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun oyun(id: String) = OyunOzeti(id, "Oyun $id", OyunTuru.ANA_OYUN, null, null, emptyList(), null)

    @Test
    fun detayYuklenirBegeniIyimserVePaylasimYalnizcaHerkeseAcikta() = viewModelTesti {
        api.listeler["l1"] = api.ornekListe("l1", ListeIstegi("Açık", oyunIdleri = listOf("a")), begeniSayisi = 2, benim = false)
        api.listeler["l2"] = api.ornekListe("l2", ListeIstegi("Kapalı", gorunurluk = ListeGorunurlugu.BEN))
        val acik = ListeDetayViewModel(api, "l1", webAdresi = "https://ornek.test")
        val kapali = ListeDetayViewModel(api, "l2", webAdresi = "https://ornek.test")
        advanceUntilIdle()

        val hazir = acik.ekran.value as ListeDetayEkrani.Hazir
        assertEquals("https://ornek.test/liste.html?id=l1", hazir.paylasimLinki)
        assertNull((kapali.ekran.value as ListeDetayEkrani.Hazir).paylasimLinki)

        acik.begeniDegistir()
        assertEquals(3 to true, (acik.ekran.value as ListeDetayEkrani.Hazir).liste.let { it.begeniSayisi to it.begendim })
        advanceUntilIdle()
        assertEquals(listOf("begen:l1"), api.listeIstekleri)
    }

    @Test
    fun begeniBasarisizOlursaGeriAlinirBaskasininListesiSilinemez() = viewModelTesti {
        api.listeler["l1"] = api.ornekListe("l1", ListeIstegi("Liste"), benim = false)
        api.listeler["l2"] = api.ornekListe("l2", ListeIstegi("Benim"))
        val benim = ListeDetayViewModel(api, "l2")
        val vm = ListeDetayViewModel(api, "l1")
        advanceUntilIdle()
        api.listeHatasi = IllegalStateException("bağlantı yok")

        vm.begeniDegistir()
        vm.sil()
        advanceUntilIdle()

        val hazir = vm.ekran.value as ListeDetayEkrani.Hazir
        assertEquals(0 to false, hazir.liste.begeniSayisi to hazir.liste.begendim)
        assertFalse(hazir.silindi)
        assertFalse(hazir.silmeHatasi)

        // Kendi listesi silinemezse hata gösterilir, ekran kapanmaz.
        benim.sil()
        advanceUntilIdle()
        val benimki = benim.ekran.value as ListeDetayEkrani.Hazir
        assertTrue(benimki.silmeHatasi)
        assertFalse(benimki.silindi)
    }

    @Test
    fun yokListeBulunamadiGosterir() = viewModelTesti {
        val vm = ListeDetayViewModel(api, "yok")
        advanceUntilIdle()
        assertEquals(ListeDetayEkrani.Bulunamadi, vm.ekran.value)
    }

    @Test
    fun yeniListeOyunlarAramaylaEklenirSiralanirKaydedilir() = viewModelTesti {
        api.katalog = listOf(oyun("a"), oyun("b"), oyun("c"))
        val vm = ListeDuzenleViewModel(api)
        assertFalse(vm.form.value.kaydedilebilir)

        vm.baslikDegisti("  En iyiler  ")
        vm.aramaDegisti("oy")
        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.form.value.aramaSonuclari.map { it.id })
        vm.ekle(oyun("a"))
        vm.ekle(oyun("b"))
        vm.ekle(oyun("a"))
        vm.tasi("b", yukari = true)
        vm.ekle(oyun("c"))
        vm.cikar("a")
        assertEquals(listOf("b", "c"), vm.form.value.oyunlar.map { it.id })

        vm.kaydet()
        advanceUntilIdle()
        assertEquals(listOf("olustur:En iyiler:b,c"), api.listeIstekleri)
        assertEquals("l1", vm.form.value.kaydedilen?.id)
    }

    @Test
    fun mevcutListeDuzenlenirHatadaFormKalir() = viewModelTesti {
        val mevcut = api.ornekListe("l9", ListeIstegi("Eski", "Not", oyunIdleri = listOf("a")))
        val vm = ListeDuzenleViewModel(api, mevcut)
        assertEquals("Eski", vm.form.value.baslik)
        assertEquals(listOf("a"), vm.form.value.oyunlar.map { it.id })

        api.listeHatasi = IllegalStateException("bağlantı yok")
        vm.baslikDegisti("Yeni")
        vm.kaydet()
        advanceUntilIdle()
        assertTrue(vm.form.value.hata)
        assertNull(vm.form.value.kaydedilen)

        api.listeHatasi = null
        vm.kaydet()
        advanceUntilIdle()
        assertEquals(listOf("guncelle:l9:Yeni:a"), api.listeIstekleri)
    }
}
