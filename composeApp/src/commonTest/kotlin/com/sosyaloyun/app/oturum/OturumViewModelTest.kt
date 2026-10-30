package com.sosyaloyun.app.oturum

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.app.test.SahteOturumDeposu
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KimlikSaglayici
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OturumViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = SahteApiIstemcisi()
    private val depo = SahteOturumDeposu()

    @BeforeTest
    fun kur() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun boz() = Dispatchers.resetMain()

    @Test
    fun kayitliOturumYoksaGirisEkraniGosterilir() {
        assertEquals(OturumEkrani.Giris(), OturumViewModel(api, depo).ekran.value)
    }

    @Test
    fun kayitliOturumVarsaUygulamaAcikBaslar() {
        depo.token = "eski-oturum"
        assertEquals(OturumEkrani.Acik, OturumViewModel(api, depo).ekran.value)
    }

    @Test
    fun basariliGiristeOturumKaydedilirVeUygulamaAcilir() = runTest(dispatcher) {
        val viewModel = OturumViewModel(api, depo)

        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("apple-token") }
        assertEquals(OturumEkrani.Giris(yukleniyor = true), viewModel.ekran.value)
        advanceUntilIdle()

        assertEquals(OturumEkrani.Acik, viewModel.ekran.value)
        assertEquals("oturum-1", depo.token)
        assertEquals(listOf(KimlikSaglayici.APPLE to "apple-token"), api.girisler)
    }

    @Test
    fun kullaniciGirisiIptalEderseHataGosterilmez() = runTest(dispatcher) {
        val viewModel = OturumViewModel(api, depo)

        viewModel.girisYap(KimlikSaglayici.GOOGLE) { KimlikTokeniSonucu.Iptal }
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(), viewModel.ekran.value)
        assertEquals(emptyList(), api.girisler)
    }

    @Test
    fun saglayiciAkisiBasarisizOlursaHataGosterilir() = runTest(dispatcher) {
        val viewModel = OturumViewModel(api, depo)

        viewModel.girisYap(KimlikSaglayici.GOOGLE) { KimlikTokeniSonucu.Hata }
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(hata = GirisHatasi.SAGLAYICI), viewModel.ekran.value)
    }

    @Test
    fun askidakiVeBanliHesapNedeniyleReddedilir() = runTest(dispatcher) {
        api.hata = ApiHatasiIstisnasi(HataKodu.HESAP_ASKIDA, "2026-01-12T09:00:00Z")
        val viewModel = OturumViewModel(api, depo)
        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("t") }
        advanceUntilIdle()
        assertEquals(OturumEkrani.Giris(hata = GirisHatasi.HESAP_ASKIDA, askiBitisi = "2026-01-12"), viewModel.ekran.value)

        api.hata = ApiHatasiIstisnasi(HataKodu.HESAP_BANLI)
        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("t") }
        advanceUntilIdle()
        assertEquals(OturumEkrani.Giris(hata = GirisHatasi.HESAP_BANLI), viewModel.ekran.value)
    }

    @Test
    fun faz2deAskidakiHesapGirisEkranindanBirKezItirazEder() = runTest(dispatcher) {
        api.hata = ApiHatasiIstisnasi(HataKodu.HESAP_ASKIDA, "2026-01-12T09:00:00Z", itirazAnahtari = "anahtar")
        val viewModel = OturumViewModel(api, depo)
        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("t") }
        advanceUntilIdle()
        assertEquals("anahtar", (viewModel.ekran.value as OturumEkrani.Giris).itirazAnahtari)

        api.itirazHatasi = IllegalStateException("bağlantı yok")
        viewModel.itirazMetniDegisti("Haksızlık")
        viewModel.itirazGonder()
        advanceUntilIdle()
        assertEquals(GirisItirazi.HATA, (viewModel.ekran.value as OturumEkrani.Giris).itiraz)

        api.itirazHatasi = null
        viewModel.itirazGonder()
        viewModel.itirazGonder()
        advanceUntilIdle()
        assertEquals(GirisItirazi.GONDERILDI, (viewModel.ekran.value as OturumEkrani.Giris).itiraz)
        assertEquals(listOf("anahtar:Haksızlık"), api.itirazlar)
    }

    @Test
    fun backendTokeniReddederseHataGosterilirVeOturumAcilmaz() = runTest(dispatcher) {
        api.hata = ApiHatasiIstisnasi(HataKodu.KIMLIK_TOKENI_GECERSIZ)
        val viewModel = OturumViewModel(api, depo)

        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("bozuk") }
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(hata = GirisHatasi.REDDEDILDI), viewModel.ekran.value)
        assertNull(depo.token)
    }

    @Test
    fun servereUlasilamazsaBaglantiHatasiGosterilir() = runTest(dispatcher) {
        api.hata = IllegalStateException("bağlantı yok")
        val viewModel = OturumViewModel(api, depo)

        viewModel.girisYap(KimlikSaglayici.APPLE) { KimlikTokeniSonucu.Token("apple-token") }
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(hata = GirisHatasi.BAGLANTI), viewModel.ekran.value)
    }

    @Test
    fun cikistaOturumSilinirVeBackenddeKapatilir() = runTest(dispatcher) {
        depo.token = "oturum-1"
        val viewModel = OturumViewModel(api, depo)

        viewModel.cikisYap()
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(), viewModel.ekran.value)
        assertNull(depo.token)
        assertEquals(1, api.cikisSayisi)
    }

    @Test
    fun backendeUlasilamasaDaCikisYapilir() = runTest(dispatcher) {
        depo.token = "oturum-1"
        api.hata = IllegalStateException("bağlantı yok")
        val viewModel = OturumViewModel(api, depo)

        viewModel.cikisYap()
        advanceUntilIdle()

        assertEquals(OturumEkrani.Giris(), viewModel.ekran.value)
        assertNull(depo.token)
    }

    @Test
    fun hesapSilininceOturumCihazdanSilinirBackendeCikisGitmez() = runTest(dispatcher) {
        depo.token = "oturum-1"
        val viewModel = OturumViewModel(api, depo)

        viewModel.hesapSilindi()

        assertEquals(OturumEkrani.Giris(), viewModel.ekran.value)
        assertNull(depo.token)
        assertEquals(0, api.cikisSayisi)
    }
}
