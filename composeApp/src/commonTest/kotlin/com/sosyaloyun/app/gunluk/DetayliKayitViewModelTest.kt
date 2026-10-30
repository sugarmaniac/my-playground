package com.sosyaloyun.app.gunluk

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DetayliKayitViewModelTest {
    private val api = SahteApiIstemcisi()
    private var tamamlanma = 0
    private val mevcutKayit = GunlukKaydi(
        id = "k1", oyunId = "o1", platform = "PC", baslama = LocalDate(2026, 1, 1), bitis = LocalDate(2026, 1, 3),
        sureDakika = 150, puan = 8, inceleme = "Güzeldi", spoiler = true, gorunurluk = Gorunurluk.TAKIPCILER, sessiz = false,
        olusturulma = Instant.parse("2026-01-05T09:00:00Z"),
    )

    private fun detay(sonPlatform: String? = null) = OyunDetayi(
        oyun = OyunOzeti("o1", "Uzun Yolculuk", OyunTuru.ANA_OYUN, 2025, null, listOf("PC", "PlayStation 5"), null),
        aciklama = null, gelistiriciler = emptyList(), yayincilar = emptyList(), turler = emptyList(),
        magazaLinkleri = emptyList(), eklentiler = emptyList(), kaynakUrl = "https://www.igdb.com",
        gunlukKayitlarim = listOf(mevcutKayit), sonPlatform = sonPlatform,
    )

    private suspend fun TestScope.viewModel(sonPlatform: String? = null, kayitId: String? = null): DetayliKayitViewModel {
        api.oyunDetaylari = mapOf("o1" to detay(sonPlatform))
        val vm = DetayliKayitViewModel(api, "o1", kayitId) { tamamlanma++ }
        advanceUntilIdle()
        return vm
    }

    private fun DetayliKayitViewModel.form() = (ekran.value as DetayliKayitEkrani.Form).form

    @Test
    fun yeniKayittaSonSecilenPlatformOnerilir() = viewModelTesti {
        val vm = viewModel(sonPlatform = "PlayStation 5")
        assertEquals("PlayStation 5", vm.form().platform)
        assertEquals(listOf("PC", "PlayStation 5"), vm.form().platformlar)
        assertEquals(Gorunurluk.HERKES, vm.form().gorunurluk)
    }

    @Test
    fun sonPlatformOyundaYoksaOnerilmez() = viewModelTesti {
        val vm = viewModel(sonPlatform = "Nintendo Switch")
        assertNull(vm.form().platform)
    }

    @Test
    fun duzenlemedeMevcutDegerlerleBaslar() = viewModelTesti {
        val vm = viewModel(kayitId = "k1")
        val form = vm.form()
        assertEquals("PC", form.platform)
        assertEquals(LocalDate(2026, 1, 1), form.baslama)
        assertEquals(LocalDate(2026, 1, 3), form.bitis)
        assertEquals("2,5", form.sureSaat)
        assertEquals("Güzeldi", form.inceleme)
        assertTrue(form.spoiler)
        assertEquals(Gorunurluk.TAKIPCILER, form.gorunurluk)
        assertTrue(form.duzenleme)
    }

    @Test
    fun yeniKayitGirilinceButunAlanlarGonderilir() = viewModelTesti {
        val vm = viewModel()
        vm.platformSecildi("PC")
        vm.baslamaSecildi(LocalDate(2026, 2, 1))
        vm.bitisSecildi(LocalDate(2026, 2, 10))
        vm.sureDegisti("1,5")
        vm.incelemeDegisti("  Harika  ")
        vm.spoilerDegisti()
        vm.gorunurlukSecildi(Gorunurluk.ARKADASLAR)
        vm.sessizDegisti()

        vm.kaydet()
        advanceUntilIdle()

        val beklenen = GunlukKaydiIstegi(
            platform = "PC", baslama = LocalDate(2026, 2, 1), bitis = LocalDate(2026, 2, 10), sureDakika = 90,
            inceleme = "Harika", spoiler = true, gorunurluk = Gorunurluk.ARKADASLAR, sessiz = true,
        )
        assertEquals(listOf("o1" to beklenen), api.girilenGunlukler)
        assertEquals(1, tamamlanma)
    }

    @Test
    fun secilenPlatformaTekrarDokununcaKalkar() = viewModelTesti {
        val vm = viewModel()
        vm.platformSecildi("PC")
        vm.platformSecildi("PC")
        assertNull(vm.form().platform)
    }

    @Test
    fun gecersizSureKaydedilmez() = viewModelTesti {
        val vm = viewModel()
        vm.sureDegisti("çok")
        vm.kaydet()
        advanceUntilIdle()

        assertEquals(KayitHatasi.SURE, vm.form().hata)
        assertEquals(emptyList(), api.girilenGunlukler)
    }

    @Test
    fun bitisBaslamadanOnceOlamaz() = viewModelTesti {
        val vm = viewModel()
        vm.baslamaSecildi(LocalDate(2026, 3, 10))
        vm.bitisSecildi(LocalDate(2026, 3, 1))
        vm.kaydet()
        advanceUntilIdle()

        assertEquals(KayitHatasi.TARIH, vm.form().hata)
        assertEquals(emptyList(), api.girilenGunlukler)
    }

    @Test
    fun duzenlenenKayitGuncellenir() = viewModelTesti {
        val vm = viewModel(kayitId = "k1")
        vm.incelemeDegisti("Fikrim değişti")
        vm.kaydet()
        advanceUntilIdle()

        assertEquals("k1", api.duzenlenenGunlukler.single().first)
        assertEquals("Fikrim değişti", api.duzenlenenGunlukler.single().second.inceleme)
        assertEquals(150, api.duzenlenenGunlukler.single().second.sureDakika)
        assertEquals(1, tamamlanma)
    }

    @Test
    fun duzenlenenKayitSilinebilir() = viewModelTesti {
        val vm = viewModel(kayitId = "k1")
        vm.sil()
        advanceUntilIdle()

        assertEquals(listOf("k1"), api.silinenGunlukler)
        assertEquals(1, tamamlanma)
    }

    @Test
    fun kaydedilemezseHataGosterilirFormKorunur() = viewModelTesti {
        val vm = viewModel()
        api.gunlukHatasi = IllegalStateException("bağlantı yok")
        vm.incelemeDegisti("Kaybolmasın")
        vm.kaydet()
        advanceUntilIdle()

        assertEquals(KayitHatasi.BAGLANTI, vm.form().hata)
        assertEquals("Kaybolmasın", vm.form().inceleme)
        assertEquals(0, tamamlanma)
    }

    @Test
    fun duzenlenecekKayitArtikYoksaHataGosterilir() = viewModelTesti {
        val vm = viewModel(kayitId = "silinmis")
        assertEquals(DetayliKayitEkrani.Hata, vm.ekran.value)
    }

    @Test
    fun oyunYuklenemezseHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = DetayliKayitViewModel(api, "o1", null) { tamamlanma++ }
        advanceUntilIdle()
        assertEquals(DetayliKayitEkrani.Hata, vm.ekran.value)
    }
}
