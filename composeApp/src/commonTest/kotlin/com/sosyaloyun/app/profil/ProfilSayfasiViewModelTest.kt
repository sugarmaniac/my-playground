package com.sosyaloyun.app.profil

import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.viewModelTesti
import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ProfilSayfasiViewModelTest {
    private val api = SahteApiIstemcisi()

    private fun oyun(ad: String) = OyunOzeti(ad, ad, OyunTuru.ANA_OYUN, 2020, null, emptyList(), null)
    private fun kayit(id: String, inceleme: String? = null) = GunlukOgesi(
        GunlukKaydi(id, "o", null, null, null, null, null, inceleme, false, Gorunurluk.HERKES, false, Instant.parse("2026-01-05T09:00:00Z")),
        oyun("Oyun $id"),
    )
    private fun giris(ad: String, durum: Durum?, istek: Boolean = false) = KutuphaneOgesi(oyun(ad), KutuphaneGirisi(durum, istekListesinde = istek))

    @Test
    fun baslikVeGunlukSekmesiYuklenir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(buYilBitirilen = 3, biyografi = "Merhaba")
        api.gunlukOgeleri = listOf(kayit("1"), kayit("2"))

        val vm = ProfilSayfasiViewModel(api)
        advanceUntilIdle()

        assertEquals(ProfilBasligi.Hazir(api.profilSayfasi), vm.ekran.value.baslik)
        assertEquals(ProfilSekmesi.GUNLUK, vm.ekran.value.sekme)
        assertEquals(SekmeIcerigi.Gunluk(api.gunlukOgeleri, dahaVar = false), vm.ekran.value.icerik)
    }

    @Test
    fun sekmelerDogruVeriyleDolar() = viewModelTesti {
        api.gunlukOgeleri = listOf(kayit("1", inceleme = "İyi"), kayit("2"))
        api.kutuphaneOgeleri = listOf(giris("Oynanan", Durum.OYNUYORUM), giris("İstenen", null, istek = true))
        val vm = ProfilSayfasiViewModel(api)
        advanceUntilIdle()

        vm.sekmeSecildi(ProfilSekmesi.INCELEMELER)
        advanceUntilIdle()
        assertEquals(SekmeIcerigi.Gunluk(listOf(api.gunlukOgeleri[0]), dahaVar = false), vm.ekran.value.icerik)

        vm.sekmeSecildi(ProfilSekmesi.ISTEK_LISTESI)
        advanceUntilIdle()
        assertEquals(SekmeIcerigi.Kutuphane(listOf(api.kutuphaneOgeleri[1]), dahaVar = false), vm.ekran.value.icerik)

        vm.sekmeSecildi(ProfilSekmesi.OYUNLAR)
        advanceUntilIdle()
        assertEquals(listOf("gunluk:false:null", "gunluk:true:null", "kutuphane:true:null:null", "kutuphane:false:null:null"), api.profilSorgulari)
    }

    @Test
    fun oyunlarSekmesiDurumaGoreSuzulur() = viewModelTesti {
        api.kutuphaneOgeleri = listOf(giris("Oynanan", Durum.OYNUYORUM), giris("Bitirilen", Durum.BITIRDIM))
        val vm = ProfilSayfasiViewModel(api)
        vm.sekmeSecildi(ProfilSekmesi.OYUNLAR)
        advanceUntilIdle()

        vm.filtreSecildi(OyunFiltresi.BITIRDIM)
        advanceUntilIdle()

        assertEquals(OyunFiltresi.BITIRDIM, vm.ekran.value.filtre)
        assertEquals(SekmeIcerigi.Kutuphane(listOf(api.kutuphaneOgeleri[1]), dahaVar = false), vm.ekran.value.icerik)
        assertEquals("kutuphane:false:bitirdim:null", api.profilSorgulari.last())

        vm.filtreSecildi(OyunFiltresi.DURUMSUZ)
        advanceUntilIdle()
        assertEquals("kutuphane:false:durumsuz:null", api.profilSorgulari.last())
    }

    @Test
    fun dahaFazlaSonrakiSayfayiEkler() = viewModelTesti {
        api.gunlukOgeleri = List(5) { kayit("$it") }
        api.profilSayfaBoyu = 3
        val vm = ProfilSayfasiViewModel(api)
        advanceUntilIdle()
        assertEquals(SekmeIcerigi.Gunluk(api.gunlukOgeleri.take(3), dahaVar = true), vm.ekran.value.icerik)

        vm.dahaFazlaYukle()
        advanceUntilIdle()

        assertEquals(SekmeIcerigi.Gunluk(api.gunlukOgeleri, dahaVar = false), vm.ekran.value.icerik)
    }

    @Test
    fun servereUlasilamazsaHataGosterilir() = viewModelTesti {
        api.hata = IllegalStateException("bağlantı yok")
        val vm = ProfilSayfasiViewModel(api)
        advanceUntilIdle()
        assertEquals(ProfilBasligi.Hata, vm.ekran.value.baslik)
        assertEquals(SekmeIcerigi.Hata, vm.ekran.value.icerik)
    }
}

class BaskasininProfiliViewModelTest {
    private val api = SahteApiIstemcisi()

    private fun takipDurumu(vm: ProfilSayfasiViewModel) =
        (vm.ekran.value.baslik as ProfilBasligi.Hazir).sayfa.let { it.takipEdiyorum to it.takipciSayisi }

    @Test
    fun baskasininProfiliVeSekmeleriAdiylaIstenir() = viewModelTesti {
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        assertEquals(listOf<String?>("mehmet"), api.istenenProfiller)
        assertEquals(ProfilBasligi.Hazir(api.profilSayfasi), vm.ekran.value.baslik)
    }

    @Test
    fun takipEdilinceSayacHemenArtarVeIstekGider() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = false, takipciSayisi = 4)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        vm.takipDegistir()
        assertEquals(true to 5, takipDurumu(vm))
        advanceUntilIdle()

        assertEquals(listOf("takip:mehmet"), api.takipIstekleri)
        assertEquals(true to 5, takipDurumu(vm))
    }

    @Test
    fun takipBirakilincaSayacAzalir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = true, takipciSayisi = 4)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        vm.takipDegistir()
        advanceUntilIdle()

        assertEquals(listOf("birak:mehmet"), api.takipIstekleri)
        assertEquals(false to 3, takipDurumu(vm))
    }

    @Test
    fun istekSurerkenGelenIkinciDokunusYokSayilir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = false, takipciSayisi = 4)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        vm.takipDegistir()
        vm.takipDegistir()
        advanceUntilIdle()

        assertEquals(listOf("takip:mehmet"), api.takipIstekleri)
        assertEquals(true to 5, takipDurumu(vm))
    }

    @Test
    fun sunucuReddederseTakipGeriAlinir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = false, takipciSayisi = 4)
        api.takipHatasi = IllegalStateException("bağlantı yok")
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        vm.takipDegistir()
        advanceUntilIdle()

        assertEquals(false to 4, takipDurumu(vm))
    }
}

class ArkadaslikViewModelTest {
    private val api = SahteApiIstemcisi()

    private fun sayfa(vm: ProfilSayfasiViewModel) = (vm.ekran.value.baslik as ProfilBasligi.Hazir).sayfa

    private suspend fun kurulum(durum: ArkadaslikDurumu, scope: kotlinx.coroutines.test.TestScope): ProfilSayfasiViewModel {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = durum == ArkadaslikDurumu.ARKADAS, arkadaslik = durum, arkadasSayisi = 2, takipciSayisi = 4)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        scope.advanceUntilIdle()
        return vm
    }

    @Test
    fun istekGonderilinceIstekGonderildiGorunur() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.YOK, this)

        vm.arkadaslikIste()
        assertEquals(ArkadaslikDurumu.ISTEK_GONDERILDI, sayfa(vm).arkadaslik)
        advanceUntilIdle()

        assertEquals(listOf("istek:mehmet"), api.takipIstekleri)
    }

    @Test
    fun gelenIstekKabulEdilinceArkadasOlunurVeTakipEdilir() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.ISTEK_ALINDI, this)

        vm.arkadaslikKabulEt()
        advanceUntilIdle()

        assertEquals(ArkadaslikDurumu.ARKADAS, sayfa(vm).arkadaslik)
        assertEquals(3, sayfa(vm).arkadasSayisi)
        assertEquals(true, sayfa(vm).takipEdiyorum)
        assertEquals(5, sayfa(vm).takipciSayisi)
        assertEquals(listOf("kabul:mehmet", "ortak:mehmet"), api.takipIstekleri)
    }

    @Test
    fun gelenIstekReddedilir() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.ISTEK_ALINDI, this)
        vm.arkadaslikReddet()
        advanceUntilIdle()
        assertEquals(ArkadaslikDurumu.YOK, sayfa(vm).arkadaslik)
        assertEquals(listOf("ret:mehmet"), api.takipIstekleri)
    }

    @Test
    fun arkadaslikBitinceTakipKalir() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.ARKADAS, this)

        vm.arkadasligiBitir()
        advanceUntilIdle()

        assertEquals(ArkadaslikDurumu.YOK, sayfa(vm).arkadaslik)
        assertEquals(1, sayfa(vm).arkadasSayisi)
        assertEquals(true, sayfa(vm).takipEdiyorum)
        assertEquals(null, vm.ekran.value.ortak)
    }

    @Test
    fun arkadaskenTakipBirakilamaz() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.ARKADAS, this)
        vm.takipDegistir()
        advanceUntilIdle()
        assertEquals(true, sayfa(vm).takipEdiyorum)
        assertEquals(listOf("ortak:mehmet"), api.takipIstekleri)
    }

    @Test
    fun arkadasProfilindeOrtakOyunlarYuklenir() = viewModelTesti {
        val oyun = OyunOzeti("o1", "Ortak", OyunTuru.ANA_OYUN, null, null, emptyList(), null)
        api.ortak = OrtakOyunlar(listOf(oyun), emptyList())
        val vm = kurulum(ArkadaslikDurumu.ARKADAS, this)
        assertEquals(api.ortak, vm.ekran.value.ortak)
    }

    @Test
    fun sunucuReddederseArkadaslikGeriAlinir() = viewModelTesti {
        val vm = kurulum(ArkadaslikDurumu.YOK, this)
        api.takipHatasi = IllegalStateException("bağlantı yok")
        vm.arkadaslikIste()
        advanceUntilIdle()
        assertEquals(ArkadaslikDurumu.YOK, sayfa(vm).arkadaslik)
    }

    @Test
    fun sessizeAlmaIyimserGosterilirVeHatadaGeriAlinir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = true, sessizeAldim = false)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        vm.sessizDegistir()
        assertEquals(true, (vm.ekran.value.baslik as ProfilBasligi.Hazir).sayfa.sessizeAldim)
        advanceUntilIdle()
        assertEquals(listOf("sessiz:mehmet"), api.engelIstekleri)

        api.engelHatasi = IllegalStateException("bağlantı yok")
        vm.sessizDegistir()
        advanceUntilIdle()
        assertEquals(true, (vm.ekran.value.baslik as ProfilBasligi.Hazir).sayfa.sessizeAldim)
    }

    @Test
    fun engellemeSunucuOnaylayincaTamamlanir() = viewModelTesti {
        api.profilSayfasi = api.profilSayfasi.copy(takipEdiyorum = true)
        val vm = ProfilSayfasiViewModel(api, "mehmet")
        advanceUntilIdle()

        api.engelHatasi = IllegalStateException("bağlantı yok")
        vm.engelle()
        advanceUntilIdle()
        assertEquals(false, vm.ekran.value.engellendi)
        assertEquals(true, vm.ekran.value.engelHatasi)

        api.engelHatasi = null
        vm.engelle()
        advanceUntilIdle()
        assertEquals(true, vm.ekran.value.engellendi)
        assertEquals(false, vm.ekran.value.engelHatasi)
        assertEquals(listOf("engelle:mehmet"), api.engelIstekleri)
    }
}
