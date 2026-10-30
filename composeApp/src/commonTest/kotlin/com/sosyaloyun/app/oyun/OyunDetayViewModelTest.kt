package com.sosyaloyun.app.oyun

import com.sosyaloyun.app.onbellek.OyunOzetiBellegi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunIncelemesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

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
        assertIs<OyunDetayEkrani.Yukleniyor>(vm.ekran.value)
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Hazir(detay), vm.ekran.value)
    }

    @Test
    fun listelerdeGorulenOyununOzetiYuklenirkenGosterilir() = viewModelTesti {
        OyunOzetiBellegi.ekle(listOf(detay.oyun))
        api.oyunDetaylari = mapOf("o1" to detay)
        val vm = OyunDetayViewModel(api, "o1")
        assertEquals(OyunDetayEkrani.Yukleniyor(detay.oyun), vm.ekran.value)
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

    private fun inceleme(id: String, arkadas: Boolean = false) = OyunIncelemesi(
        kayit = GunlukKaydi(id, "o1", null, null, null, null, null, "İnceleme $id", false, Gorunurluk.HERKES, false, Instant.parse("2026-01-05T09:00:00Z")),
        sahip = KullaniciOzeti("k-$id", "kisi$id", "Kişi $id", Avatar.entries.first()),
        arkadas = arkadas,
    )

    @Test
    fun incelemelerDetaydanSonraYuklenirVeSayfalanir() = viewModelTesti {
        api.oyunDetaylari = mapOf("o1" to detay)
        api.incelemeSayfalari = mapOf(
            null to IncelemeListesi(listOf(inceleme("1", arkadas = true), inceleme("2")), sonrakiImlec = "2"),
            "2" to IncelemeListesi(listOf(inceleme("3"))),
        )
        val vm = OyunDetayViewModel(api, "o1")
        advanceUntilIdle()
        assertEquals(listOf("1", "2"), (vm.ekran.value as OyunDetayEkrani.Hazir).incelemeler.map { it.kayit.id })

        vm.dahaFazlaInceleme()
        vm.dahaFazlaInceleme()
        advanceUntilIdle()

        val hazir = vm.ekran.value as OyunDetayEkrani.Hazir
        assertEquals(listOf("1", "2", "3"), hazir.incelemeler.map { it.kayit.id })
        assertEquals(null, hazir.incelemeImleci)
        assertEquals(listOf(null, "2"), api.incelemeSorgulari)
    }

    @Test
    fun incelemelerYuklenemezseDetayYineGosterilir() = viewModelTesti {
        api.oyunDetaylari = mapOf("o1" to detay)
        api.incelemeHatasi = IllegalStateException("bağlantı koptu")
        val vm = OyunDetayViewModel(api, "o1")
        advanceUntilIdle()
        assertEquals(OyunDetayEkrani.Hazir(detay), vm.ekran.value)
    }
}
