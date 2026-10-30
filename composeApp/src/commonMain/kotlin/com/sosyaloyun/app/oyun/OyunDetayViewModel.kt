package com.sosyaloyun.app.oyun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunDetayi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OyunDetayEkrani {
    data object Yukleniyor : OyunDetayEkrani
    data class Hazir(val detay: OyunDetayi) : OyunDetayEkrani
    data object Bulunamadi : OyunDetayEkrani
    data object Hata : OyunDetayEkrani
}

class OyunDetayViewModel(private val api: ApiIstemcisi, private val oyunId: String) : ViewModel() {
    private val _ekran = MutableStateFlow<OyunDetayEkrani>(OyunDetayEkrani.Yukleniyor)
    val ekran: StateFlow<OyunDetayEkrani> = _ekran

    init {
        yukle()
    }

    /** Hızlı kayıttan sonra detay yeniden yüklenmeden güncellenir. */
    fun kutuphaneGuncellendi(giris: KutuphaneGirisi?) {
        _ekran.update { mevcut -> if (mevcut is OyunDetayEkrani.Hazir) mevcut.copy(detay = mevcut.detay.copy(kutuphaneGirisi = giris)) else mevcut }
    }

    fun yukle() {
        _ekran.value = OyunDetayEkrani.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                OyunDetayEkrani.Hazir(api.oyunDetayi(oyunId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiHatasiIstisnasi) {
                if (e.kod == HataKodu.OYUN_BULUNAMADI) OyunDetayEkrani.Bulunamadi else OyunDetayEkrani.Hata
            } catch (e: Exception) {
                OyunDetayEkrani.Hata
            }
        }
    }
}
