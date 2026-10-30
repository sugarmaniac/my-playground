package com.sosyaloyun.app.oyun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunIncelemesi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OyunDetayEkrani {
    data object Yukleniyor : OyunDetayEkrani
    data class Hazir(
        val detay: OyunDetayi,
        /** Önce arkadaşların, sonra herkesin incelemeleri; yüklenemezse boş kalır, detayı engellemez. */
        val incelemeler: List<OyunIncelemesi> = emptyList(),
        val incelemeImleci: String? = null,
    ) : OyunDetayEkrani
    data object Bulunamadi : OyunDetayEkrani
    data object Hata : OyunDetayEkrani
}

class OyunDetayViewModel(private val api: ApiIstemcisi, private val oyunId: String) : ViewModel() {
    private val _ekran = MutableStateFlow<OyunDetayEkrani>(OyunDetayEkrani.Yukleniyor)
    val ekran: StateFlow<OyunDetayEkrani> = _ekran
    private var incelemeIsi: Job? = null

    init {
        yukle()
    }

    /** Hızlı kayıttan sonra detay yeniden yüklenmeden güncellenir. */
    fun kutuphaneGuncellendi(giris: KutuphaneGirisi?) {
        _ekran.update { mevcut -> if (mevcut is OyunDetayEkrani.Hazir) mevcut.copy(detay = mevcut.detay.copy(kutuphaneGirisi = giris)) else mevcut }
    }

    fun yukle() {
        _ekran.value = OyunDetayEkrani.Yukleniyor
        // Yeniden yüklemede eski sayfanın devamı yeni listeye eklenmesin.
        incelemeIsi?.cancel()
        incelemeIsi = viewModelScope.launch {
            _ekran.value = try {
                OyunDetayEkrani.Hazir(api.oyunDetayi(oyunId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiHatasiIstisnasi) {
                if (e.kod == HataKodu.OYUN_BULUNAMADI) OyunDetayEkrani.Bulunamadi else OyunDetayEkrani.Hata
            } catch (e: Exception) {
                OyunDetayEkrani.Hata
            }
            if (_ekran.value is OyunDetayEkrani.Hazir) incelemeleriYukle(imlec = null)
        }
    }

    fun dahaFazlaInceleme() {
        val imlec = (_ekran.value as? OyunDetayEkrani.Hazir)?.incelemeImleci ?: return
        if (incelemeIsi?.isActive == true) return
        incelemeIsi = viewModelScope.launch { incelemeleriYukle(imlec) }
    }

    private suspend fun incelemeleriYukle(imlec: String?) {
        val sayfa = try {
            api.oyunIncelemeleri(oyunId, imlec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        _ekran.update { mevcut ->
            if (mevcut !is OyunDetayEkrani.Hazir) return@update mevcut
            val oncekiler = if (imlec == null) emptyList() else mevcut.incelemeler
            mevcut.copy(incelemeler = oncekiler + sayfa.incelemeler, incelemeImleci = sayfa.sonrakiImlec)
        }
    }
}
