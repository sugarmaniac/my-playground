package com.sosyaloyun.app.sosyal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Gelen arkadaşlık istekleri; kabul ya da ret edilen istek listeden hemen düşer, başarısızsa geri gelir. */
class GelenIsteklerViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow<KullaniciListesiEkrani>(KullaniciListesiEkrani.Yukleniyor)
    val ekran: StateFlow<KullaniciListesiEkrani> = _ekran

    init {
        viewModelScope.launch {
            _ekran.value = try {
                val liste = api.gelenArkadaslikIstekleri()
                KullaniciListesiEkrani.Hazir(liste.kullanicilar, dahaVar = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KullaniciListesiEkrani.Hata
            }
        }
    }

    fun kabulEt(kullaniciAdi: String) = islem(kullaniciAdi) { api.arkadaslikKabulEt(it) }

    fun reddet(kullaniciAdi: String) = islem(kullaniciAdi) { api.arkadaslikReddet(it) }

    private fun islem(kullaniciAdi: String, istek: suspend (String) -> Unit) {
        val onceki = _ekran.value as? KullaniciListesiEkrani.Hazir ?: return
        val kisi = onceki.kullanicilar.firstOrNull { it.kullaniciAdi == kullaniciAdi } ?: return
        _ekran.update { if (it is KullaniciListesiEkrani.Hazir) it.copy(kullanicilar = it.kullanicilar - kisi) else it }
        viewModelScope.launch {
            try {
                istek(kullaniciAdi)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { if (it is KullaniciListesiEkrani.Hazir) it.copy(kullanicilar = onceki.kullanicilar.filter { k -> k in it.kullanicilar || k == kisi }) else it }
            }
        }
    }
}
