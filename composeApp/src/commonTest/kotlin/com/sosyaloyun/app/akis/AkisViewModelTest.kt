package com.sosyaloyun.app.akis

import com.sosyaloyun.app.onbellek.okuNesne
import com.sosyaloyun.app.onbellek.yazNesne
import com.sosyaloyun.app.test.BellekOnbellegi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AkisViewModelTest {
    private val api = SahteApiIstemcisi()
    private val ayse = KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.TILKI)
    private val oyun = OyunOzeti("o1", "Oyun", OyunTuru.ANA_OYUN, null, null, emptyList(), null)
    private fun oge(id: String, oyunu: OyunOzeti? = oyun) =
        AkisOgesi(id, AkisOlayTuru.OYNAMAYA_BASLADI, ayse, Instant.parse("2026-01-05T09:00:00Z"), oyun = oyunu)

    private fun ogeler(vm: AkisViewModel) = (vm.ekran.value.icerik as AkisIcerigi.Ogeler).ogeler

    @Test
    fun takipSekmesiYuklenir() = viewModelTesti {
        api.akisOgeleri = listOf(oge("1"), oge("2"))
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        assertEquals(AkisSekmesi.TAKIP, vm.ekran.value.sekme)
        assertEquals(AkisIcerigi.Ogeler(api.akisOgeleri, dahaVar = false), vm.ekran.value.icerik)
        assertEquals(listOf("akis:TAKIP:null"), api.akisIstekleri)
    }

    @Test
    fun sakliIlkSayfaHemenGosterilirAgdanGelenleTazelenir() = viewModelTesti {
        val onbellek = BellekOnbellegi()
        onbellek.yazNesne("akis.TAKIP", AkisSayfasi(listOf(oge("eski")), sonrakiImlec = null, enYeniImlec = "e"))
        api.akisOgeleri = listOf(oge("yeni"))

        val vm = AkisViewModel(api, onbellek)
        assertEquals(listOf(oge("eski")), ogeler(vm))
        advanceUntilIdle()

        assertEquals(listOf(oge("yeni")), ogeler(vm))
        assertEquals(listOf(oge("yeni")), onbellek.okuNesne<AkisSayfasi>("akis.TAKIP")?.ogeler)
    }

    @Test
    fun agHatasindaSakliSayfaKalir() = viewModelTesti {
        val onbellek = BellekOnbellegi()
        onbellek.yazNesne("akis.TAKIP", AkisSayfasi(listOf(oge("eski")), sonrakiImlec = null, enYeniImlec = null))
        api.hata = IllegalStateException("bağlantı yok")

        val vm = AkisViewModel(api, onbellek)
        advanceUntilIdle()

        assertEquals(listOf(oge("eski")), ogeler(vm))
    }

    @Test
    fun arkadaslarSekmesiSecilir() = viewModelTesti {
        val vm = AkisViewModel(api)
        advanceUntilIdle()
        vm.sekmeSecildi(AkisSekmesi.ARKADASLAR)
        advanceUntilIdle()
        assertEquals("akis:ARKADASLAR:null", api.akisIstekleri.last())
    }

    @Test
    fun bosAkistaTakipOnerileriGosterilirVeOneridenTakipEdilir() = viewModelTesti {
        api.akisOnerileriListesi = listOf(ayse)
        val vm = AkisViewModel(api)
        advanceUntilIdle()
        assertEquals(AkisIcerigi.Bos(listOf(ayse)), vm.ekran.value.icerik)

        vm.onerilenKisiyiTakipEt("ayse")
        advanceUntilIdle()

        assertEquals(listOf("takip:ayse"), api.takipIstekleri)
        assertEquals(AkisIcerigi.Bos(emptyList()), vm.ekran.value.icerik)
    }

    @Test
    fun yeniOlaylarUyariylaGosterilirYenileyinceSifirlanir() = viewModelTesti {
        api.akisOgeleri = listOf(oge("1"))
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        api.akisYeni = 3
        vm.yeniOlaylariDenetle()
        advanceUntilIdle()
        assertEquals(3, vm.ekran.value.yeniOlaySayisi)
        assertEquals("yeni:TAKIP:en-yeni", api.akisIstekleri.last())

        api.akisOgeleri = listOf(oge("4"), oge("1"))
        vm.yenile()
        advanceUntilIdle()
        assertEquals(0, vm.ekran.value.yeniOlaySayisi)
        assertEquals(listOf("4", "1"), ogeler(vm).map { it.id })
    }

    @Test
    fun kartlardanIstekListesineHemenEklenir() = viewModelTesti {
        api.akisOgeleri = listOf(oge("1"), oge("2"), oge("3", oyunu = null))
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        vm.istekListesineEkle("o1")
        assertEquals(listOf(true, true, false), ogeler(vm).map { it.istekListemde })
        advanceUntilIdle()

        assertEquals("istek-listesi:o1", api.akisIstekleri.last())
    }

    @Test
    fun istekListesiEklenemezseGeriAlinir() = viewModelTesti {
        api.akisOgeleri = listOf(oge("1"))
        api.istekListesiHatasi = IllegalStateException("bağlantı yok")
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        vm.istekListesineEkle("o1")
        advanceUntilIdle()

        assertEquals(listOf(false), ogeler(vm).map { it.istekListemde })
    }

    private fun kayitOgesi(begeniSayisi: Int, begendim: Boolean) = AkisOgesi(
        "g1", AkisOlayTuru.GUNLUK_KAYDI, ayse, Instant.parse("2026-01-05T09:00:00Z"), oyun = oyun,
        gunlukKaydi = GunlukKaydi("k1", "o1", null, null, null, null, null, null, false, Gorunurluk.HERKES, false,
            Instant.parse("2026-01-05T09:00:00Z"), begeniSayisi = begeniSayisi, begendim = begendim),
    )

    @Test
    fun begeniAnindaGorunurVeIstekGider() = viewModelTesti {
        api.akisOgeleri = listOf(kayitOgesi(2, begendim = false))
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        vm.begeniDegistir("k1")
        assertEquals(3 to true, ogeler(vm).single().gunlukKaydi!!.let { it.begeniSayisi to it.begendim })
        advanceUntilIdle()
        assertEquals(listOf("begen:k1"), api.begeniIstekleri)

        vm.begeniDegistir("k1")
        advanceUntilIdle()
        assertEquals(2 to false, ogeler(vm).single().gunlukKaydi!!.let { it.begeniSayisi to it.begendim })
        assertEquals(listOf("begen:k1", "geri:k1"), api.begeniIstekleri)
    }

    @Test
    fun begeniSunucudaBasarisizOlursaGeriAlinir() = viewModelTesti {
        api.akisOgeleri = listOf(kayitOgesi(2, begendim = false))
        api.begeniHatasi = IllegalStateException("bağlantı yok")
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        vm.begeniDegistir("k1")
        advanceUntilIdle()

        assertEquals(2 to false, ogeler(vm).single().gunlukKaydi!!.let { it.begeniSayisi to it.begendim })
    }

    @Test
    fun dahaFazlaSonrakiSayfayiEkler() = viewModelTesti {
        api.akisOgeleri = List(5) { oge("$it") }
        api.profilSayfaBoyu = 3
        val vm = AkisViewModel(api)
        advanceUntilIdle()

        vm.dahaFazlaYukle()
        advanceUntilIdle()

        assertEquals(AkisIcerigi.Ogeler(api.akisOgeleri, dahaVar = false), vm.ekran.value.icerik)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = AkisViewModel(api)
        advanceUntilIdle()
        assertEquals(AkisIcerigi.Hata, vm.ekran.value.icerik)
    }
}
