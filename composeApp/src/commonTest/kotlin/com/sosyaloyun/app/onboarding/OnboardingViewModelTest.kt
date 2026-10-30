package com.sosyaloyun.app.onboarding

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.TelefonNumarasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import com.sosyaloyun.shared.SteamBaglantisi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val api = SahteApiIstemcisi()
    private var hesapYenilendi = 0
    private fun oyun(ad: String) = OyunOzeti(ad, ad, OyunTuru.ANA_OYUN, 2020, null, emptyList(), null)

    @Test
    fun steamliYolEnCokOynananlariGosterirVeBitirilir() = viewModelTesti {
        api.steamSonucu = SteamIceAktarmaSonucu(SteamBaglantisi("7656"), 2, listOf(KutuphaneOgesi(oyun("a"), KutuphaneGirisi())))
        val vm = OnboardingViewModel(api) { hesapYenilendi++ }

        vm.girdiDegisti(" ayseoyunda ")
        vm.bagla()
        advanceUntilIdle()

        val adim = vm.ekran.value as OnboardingAdimi.Durumlar
        assertTrue(adim.steamli)
        assertEquals(listOf("a"), adim.oyunlar.map { it.oyun.ad })
        assertTrue(adim.bitirilebilir)

        vm.durumSec("a", Durum.BITIRDIM)
        vm.durumlariBitir()
        advanceUntilIdle()
        vm.bitir()
        advanceUntilIdle()
        assertEquals(listOf("steam:ayseoyunda", "tamamla"), api.onboardingIstekleri)
        assertEquals(listOf("a" to KutuphaneGirisi(Durum.BITIRDIM)), api.kutuphaneKayitlari)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun gizliProfilAciklayiciHataGosterir() = viewModelTesti {
        api.steamHatasi = ApiHatasiIstisnasi(HataKodu.STEAM_PROFILI_GIZLI)
        val vm = OnboardingViewModel(api) {}
        vm.girdiDegisti("gizli")
        vm.bagla()
        advanceUntilIdle()
        assertEquals(OnboardingAdimi.Steam("gizli", hata = SteamHatasi.GIZLI), vm.ekran.value)
    }

    @Test
    fun steamsizYolEnAzUcOyunIster() = viewModelTesti {
        api.populerler = listOf(oyun("a"), oyun("b"), oyun("c"), oyun("d"))
        val vm = OnboardingViewModel(api) { hesapYenilendi++ }

        vm.steamiAtla()
        advanceUntilIdle()
        vm.durumSec("a", Durum.OYNUYORUM)
        vm.durumSec("b", Durum.BITIRDIM)
        advanceUntilIdle()
        assertFalse((vm.ekran.value as OnboardingAdimi.Durumlar).bitirilebilir)
        vm.durumlariBitir()
        assertTrue(vm.ekran.value is OnboardingAdimi.Durumlar)

        vm.durumSec("c", Durum.BIRAKTIM)
        advanceUntilIdle()
        assertTrue((vm.ekran.value as OnboardingAdimi.Durumlar).bitirilebilir)
        vm.durumlariBitir()
        advanceUntilIdle()
        vm.bitir()
        advanceUntilIdle()
        assertEquals(listOf("tamamla"), api.onboardingIstekleri)
        assertEquals(1, hesapYenilendi)
    }

    @Test
    fun durumKaydedilemezseSecimGeriAlinir() = viewModelTesti {
        api.populerler = listOf(oyun("a"))
        api.kutuphaneHatasi = IllegalStateException("bağlantı yok")
        val vm = OnboardingViewModel(api) {}
        vm.steamiAtla()
        advanceUntilIdle()

        vm.durumSec("a", Durum.OYNUYORUM)
        advanceUntilIdle()

        assertEquals(0, (vm.ekran.value as OnboardingAdimi.Durumlar).secilenSayisi)
    }
}

class ArkadasBulmaAdimiTest {
    private val api = SahteApiIstemcisi()
    private fun kisi(ad: String, takip: Boolean = false) = BulunanKisi(KullaniciOzeti(ad, ad, ad, Avatar.KEDI), takip)

    private suspend fun kurulum(vm: OnboardingViewModel, testKapsami: kotlinx.coroutines.test.TestScope) {
        vm.steamiAtla()
        testKapsami.advanceUntilIdle()
        listOf("a", "b", "c").forEach { vm.durumSec(it, Durum.BITIRDIM) }
        vm.durumlariBitir()
        testKapsami.advanceUntilIdle()
    }

    @Test
    fun rehberNumaralariHashlenerekGonderilirVeBulunanlarTakipEdilir() = viewModelTesti {
        api.populerler = listOf("a", "b", "c").map { OyunOzeti(it, it, OyunTuru.ANA_OYUN, 2020, null, emptyList(), null) }
        api.davetKodlari = listOf(DavetKodu("KULLANILDI", null, true), DavetKodu("BOS1", null, false))
        api.rehberEslesmeleri = listOf(kisi("mehmet"), kisi("zeynep"), kisi("ali", takip = true))
        val vm = OnboardingViewModel(api) {}
        kurulum(vm, this)
        assertEquals("BOS1", (vm.ekran.value as OnboardingAdimi.Arkadaslar).davetKodu)

        vm.rehberdenBul(listOf("0532 111 22 33", "acil", "+90 532 111 2233"))
        advanceUntilIdle()
        assertEquals(listOf(TelefonNumarasi.hash("05321112233")!!), api.gonderilenHashler)

        vm.takipEt("mehmet")
        vm.hepsiniTakipEt()
        advanceUntilIdle()
        assertEquals(listOf("takip:mehmet"), api.takipIstekleri)
        assertEquals(listOf(listOf("zeynep")), api.topluTakipler)
        assertTrue((vm.ekran.value as OnboardingAdimi.Arkadaslar).kisiler.all { it.takipEdiyorum })
    }

    @Test
    fun rehberIzniYoksaAdimAtlanabilir() = viewModelTesti {
        api.populerler = listOf("a", "b", "c").map { OyunOzeti(it, it, OyunTuru.ANA_OYUN, 2020, null, emptyList(), null) }
        var yenilendi = 0
        val vm = OnboardingViewModel(api) { yenilendi++ }
        kurulum(vm, this)

        vm.rehberdenBul(null)
        assertTrue((vm.ekran.value as OnboardingAdimi.Arkadaslar).rehberIzniYok)
        vm.bitir()
        advanceUntilIdle()
        assertEquals(listOf("tamamla"), api.onboardingIstekleri)
        assertEquals(1, yenilendi)
    }

    @Test
    fun steamliKullanicininSteamArkadaslariHemenAranir() = viewModelTesti {
        api.steamSonucu = SteamIceAktarmaSonucu(SteamBaglantisi("7656"), 0, emptyList())
        api.steamArkadaslari = listOf(kisi("mehmet"))
        val vm = OnboardingViewModel(api) {}
        vm.girdiDegisti("x")
        vm.bagla()
        advanceUntilIdle()
        vm.durumlariBitir()
        advanceUntilIdle()
        assertEquals(listOf("mehmet"), (vm.ekran.value as OnboardingAdimi.Arkadaslar).kisiler.map { it.kullanici.kullaniciAdi })
    }
}

class SteamAyariViewModelTest {
    private val api = SahteApiIstemcisi()

    @Test
    fun baglanirVeKaldirilir() = viewModelTesti {
        api.steamSonucu = SteamIceAktarmaSonucu(SteamBaglantisi("7656"), 12, emptyList())
        val vm = SteamAyariViewModel(api)
        advanceUntilIdle()
        assertEquals(SteamAyari.BagliDegil(), vm.ekran.value)

        vm.girdiDegisti("ayseoyunda")
        vm.bagla()
        advanceUntilIdle()
        assertEquals(SteamAyari.Bagli("7656", iceAktarilan = 12), vm.ekran.value)

        vm.kaldir()
        advanceUntilIdle()
        assertEquals(SteamAyari.BagliDegil(), vm.ekran.value)
        assertEquals(listOf("steam:ayseoyunda", "steamKaldir"), api.onboardingIstekleri)
    }
}
