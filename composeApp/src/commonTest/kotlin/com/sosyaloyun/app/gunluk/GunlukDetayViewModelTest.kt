package com.sosyaloyun.app.gunluk

import com.sosyaloyun.app.api.sunucuAdresi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import com.sosyaloyun.shared.Yorum
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GunlukDetayViewModelTest {
    private val api = SahteApiIstemcisi()
    private val ayse = KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.TILKI)
    private val detay = GunlukKaydiDetayi(
        kayit = GunlukKaydi("g1", "o1", null, null, null, null, 8, "İyi", false, Gorunurluk.HERKES, false,
            Instant.parse("2026-01-05T09:00:00Z"), begeniSayisi = 1, begendim = false),
        oyun = OyunOzeti("o1", "Oyun", OyunTuru.ANA_OYUN, null, null, emptyList(), null),
        sahip = ayse,
    )

    private fun kayit(vm: GunlukDetayViewModel) = (vm.ekran.value as GunlukDetayEkrani.Hazir).detay.kayit

    @Test
    fun detayVeBegenenlerYuklenir() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        api.begenenListesi = listOf(ayse)
        val vm = GunlukDetayViewModel(api, "g1")
        advanceUntilIdle()

        assertEquals(
            GunlukDetayEkrani.Hazir(detay, begenenler = listOf(ayse), paylasimLinki = "${sunucuAdresi()}/kayit.html?id=g1"),
            vm.ekran.value,
        )
    }

    @Test
    fun paylasButonuWebSitesindekiKayitSayfasinaLinkUretir() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        val vm = GunlukDetayViewModel(api, "g1", webAdresi = "https://ornek.test/")
        advanceUntilIdle()

        assertEquals("https://ornek.test/kayit.html?id=g1", (vm.ekran.value as GunlukDetayEkrani.Hazir).paylasimLinki)
    }

    @Test
    fun herkeseAcikOlmayanKayitPaylasilamaz() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay.copy(kayit = detay.kayit.copy(gorunurluk = Gorunurluk.TAKIPCILER)))
        val vm = GunlukDetayViewModel(api, "g1", webAdresi = "https://ornek.test")
        advanceUntilIdle()

        assertEquals(null, (vm.ekran.value as GunlukDetayEkrani.Hazir).paylasimLinki)
    }

    @Test
    fun begeniAnindaGorunurVeBegenenlerYenilenir() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        val vm = GunlukDetayViewModel(api, "g1")
        advanceUntilIdle()

        vm.begeniDegistir()
        assertEquals(2 to true, kayit(vm).begeniSayisi to kayit(vm).begendim)
        advanceUntilIdle()

        assertEquals(listOf("begenenler:g1", "begen:g1", "begenenler:g1"), api.begeniIstekleri)
    }

    @Test
    fun begeniBasarisizOlursaGeriAlinir() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        api.begeniHatasi = IllegalStateException("bağlantı yok")
        val vm = GunlukDetayViewModel(api, "g1")
        advanceUntilIdle()

        vm.begeniDegistir()
        advanceUntilIdle()

        assertEquals(1 to false, kayit(vm).begeniSayisi to kayit(vm).begendim)
    }

    @Test
    fun gormeYetkisiYoksaBulunamadiGosterilir() = viewModelTesti {
        val vm = GunlukDetayViewModel(api, "gizli")
        advanceUntilIdle()
        assertEquals(GunlukDetayEkrani.Bulunamadi, vm.ekran.value)
    }

    private fun yorum(id: String, benim: Boolean) =
        Yorum(id, ayse, "Yorum $id", Instant.parse("2026-01-05T09:30:00Z"), silebilirim = benim, benim = benim)

    @Test
    fun faz1deYorumlarYuklenmez() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        val vm = GunlukDetayViewModel(api, "g1")
        advanceUntilIdle()

        assertNull((vm.ekran.value as GunlukDetayEkrani.Hazir).yorumlar)
        assertEquals(emptyList(), api.yorumIstekleri)
    }

    @Test
    fun faz2deYorumlarYuklenirYazilirVeSilinir() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        api.yorumListesi = mutableListOf(yorum("a", benim = false))
        val vm = GunlukDetayViewModel(api, "g1", aktifFaz = Faz.FAZ_2)
        advanceUntilIdle()
        fun hazir() = vm.ekran.value as GunlukDetayEkrani.Hazir
        assertEquals(listOf("a"), hazir().yorumlar?.map { it.id })

        assertFalse(hazir().yorumGonderilebilir)
        vm.yorumMetniDegisti("  Güzel  ")
        vm.yorumGonder()
        advanceUntilIdle()
        assertEquals(listOf("a", "y2"), hazir().yorumlar?.map { it.id })
        assertEquals("", hazir().yorumMetni)
        assertEquals("yaz:g1:Güzel", api.yorumIstekleri.last())

        // Başkasının yorumu silinemez; kendi yorumu hemen kalkar.
        vm.yorumSil("a")
        vm.yorumSil("y2")
        advanceUntilIdle()
        assertEquals(listOf("a"), hazir().yorumlar?.map { it.id })
        assertEquals("sil:y2", api.yorumIstekleri.last())
    }

    @Test
    fun yorumGonderilemezseMetinKalirHataGorunur() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        val vm = GunlukDetayViewModel(api, "g1", aktifFaz = Faz.FAZ_3)
        advanceUntilIdle()
        api.yorumHatasi = IllegalStateException("bağlantı yok")
        vm.yorumMetniDegisti("Merhaba")
        vm.yorumGonder()
        advanceUntilIdle()

        val hazir = vm.ekran.value as GunlukDetayEkrani.Hazir
        assertEquals("Merhaba", hazir.yorumMetni)
        assertTrue(hazir.yorumHatasi)
        assertEquals(emptyList(), hazir.yorumlar)
    }

    @Test
    fun yorumSilinemezseYalnizcaOGeriGelirSilmeHatasiGorunur() = viewModelTesti {
        api.gunlukDetaylari = mapOf("g1" to detay)
        api.yorumListesi = mutableListOf(yorum("a", benim = true))
        val vm = GunlukDetayViewModel(api, "g1", aktifFaz = Faz.FAZ_2)
        advanceUntilIdle()
        api.yorumHatasi = IllegalStateException("bağlantı yok")

        vm.yorumSil("a")
        advanceUntilIdle()

        val hazir = vm.ekran.value as GunlukDetayEkrani.Hazir
        assertEquals(listOf("a"), hazir.yorumlar?.map { it.id })
        assertTrue(hazir.yorumSilmeHatasi)
        assertFalse(hazir.yorumHatasi)
    }
}
