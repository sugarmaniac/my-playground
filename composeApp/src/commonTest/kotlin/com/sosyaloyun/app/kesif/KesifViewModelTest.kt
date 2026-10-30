package com.sosyaloyun.app.kesif

import com.sosyaloyun.app.onbellek.OyunOzetiBellegi
import com.sosyaloyun.app.onbellek.okuNesne
import com.sosyaloyun.app.onbellek.yazNesne
import com.sosyaloyun.app.test.BellekOnbellegi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import com.sosyaloyun.shared.PopulerOyun
import com.sosyaloyun.shared.Tur
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class KesifViewModelTest {
    private val api = SahteApiIstemcisi()
    private fun oyun(ad: String) = OyunOzeti(ad, ad, OyunTuru.ANA_OYUN, 2020, null, emptyList(), null)

    @Test
    fun kesifYuklenirHatadaTekrarDenenir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = KesifViewModel(api)
        advanceUntilIdle()
        assertEquals(KesifEkrani.Hata, vm.ekran.value)

        api.hata = null
        api.kesifYaniti = Kesif(listOf(PopulerOyun(oyun("a"), 2)), listOf(oyun("b")), Tur.entries, listOf("PC"))
        vm.yenile()
        advanceUntilIdle()
        assertEquals(KesifEkrani.Hazir(api.kesifYaniti!!), vm.ekran.value)
    }

    @Test
    fun sakliKesifHemenGosterilirAgdanGelenleTazelenir() = viewModelTesti {
        val onbellek = BellekOnbellegi()
        val eski = Kesif(emptyList(), listOf(oyun("eski")), Tur.entries, listOf("PC"))
        onbellek.yazNesne("kesif", eski)
        api.kesifYaniti = Kesif(emptyList(), listOf(oyun("yeni")), Tur.entries, listOf("PC"))

        val vm = KesifViewModel(api, onbellek)
        assertEquals(KesifEkrani.Hazir(eski), vm.ekran.value)
        advanceUntilIdle()

        assertEquals(KesifEkrani.Hazir(api.kesifYaniti!!), vm.ekran.value)
        assertEquals(api.kesifYaniti, onbellek.okuNesne<Kesif>("kesif"))
        assertEquals(oyun("yeni"), OyunOzetiBellegi.bul("yeni"))
    }

    @Test
    fun agHatasindaSakliKesifKalir() = viewModelTesti {
        val onbellek = BellekOnbellegi()
        val eski = Kesif(emptyList(), listOf(oyun("eski")), Tur.entries, listOf("PC"))
        onbellek.yazNesne("kesif", eski)
        api.hata = IllegalStateException("bağlantı yok")

        val vm = KesifViewModel(api, onbellek)
        advanceUntilIdle()

        assertEquals(KesifEkrani.Hazir(eski), vm.ekran.value)
    }

    @Test
    fun turListesiSayfalanir() = viewModelTesti {
        api.oyunListeleri = mapOf(
            ("tur:BULMACA" to null) to OyunAramaYaniti(listOf(oyun("a"), oyun("b")), "2"),
            ("tur:BULMACA" to "2") to OyunAramaYaniti(listOf(oyun("c")), null),
        )
        val vm = OyunListesiViewModel(api, Tur.BULMACA, null)
        advanceUntilIdle()
        vm.dahaFazlaYukle()
        vm.dahaFazlaYukle()
        advanceUntilIdle()

        assertEquals(OyunListesiEkrani.Hazir(listOf(oyun("a"), oyun("b"), oyun("c")), dahaVar = false), vm.ekran.value)
        assertEquals(listOf("tur:BULMACA:null", "tur:BULMACA:2"), api.oyunListesiSorgulari)
    }

    @Test
    fun platformListesiPlatformlaSorgular() = viewModelTesti {
        val vm = OyunListesiViewModel(api, null, "Nintendo Switch")
        advanceUntilIdle()
        assertEquals(OyunListesiEkrani.Hazir(emptyList(), dahaVar = false), vm.ekran.value)
        assertEquals(listOf("platform:Nintendo Switch:null"), api.oyunListesiSorgulari)
    }
}
