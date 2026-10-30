package com.sosyaloyun.app.oyun

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class OyunDetayViewModelTest {
    private val api = SahteApiIstemcisi()
    private val detay = OyunDetayi(
        oyun = OyunOzeti("o1", "Gece Avcısı", OyunTuru.ANA_OYUN, 2020, null, listOf("PC"), null),
        aciklama = "Açıklama",
        gelistiriciler = listOf("Ay Stüdyosu"),
        yayincilar = emptyList(),
        turler = listOf("Adventure"),
        magazaLinkleri = emptyList(),
        eklentiler = emptyList(),
        kaynakUrl = "https://www.igdb.com/games/gece-avcisi",
    )

    @Test
    fun oyunDetayiYuklenir() = viewModelTesti {
        api.oyunDetaylari = mapOf("o1" to detay)
        val vm = OyunDetayViewModel(api, "o1")
        assertEquals(OyunDetayEkrani.Yukleniyor, vm.ekran.value)
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Hazir(detay), vm.ekran.value)
    }

    @Test
    fun hizliKayittanSonraDetaydakiGirisGuncellenir() = viewModelTesti {
        api.oyunDetaylari = mapOf("o1" to detay)
        val vm = OyunDetayViewModel(api, "o1")
        advanceUntilIdle()

        vm.kutuphaneGuncellendi(KutuphaneGirisi(Durum.OYNUYORUM))

        assertEquals(OyunDetayEkrani.Hazir(detay.copy(kutuphaneGirisi = KutuphaneGirisi(Durum.OYNUYORUM))), vm.ekran.value)
    }

    @Test
    fun olmayanOyunBulunamadiGosterilir() = viewModelTesti {
        val vm = OyunDetayViewModel(api, "yok")
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Bulunamadi, vm.ekran.value)
    }

    @Test
    fun servereUlasilamazsaHataGosterilirVeTekrarDenenebilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = OyunDetayViewModel(api, "o1")
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Hata, vm.ekran.value)

        api.hata = null
        api.oyunDetaylari = mapOf("o1" to detay)
        vm.yukle()
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Hazir(detay), vm.ekran.value)
    }
}
