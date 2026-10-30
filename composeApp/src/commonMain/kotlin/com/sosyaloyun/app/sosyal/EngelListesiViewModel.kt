package com.sosyaloyun.app.sosyal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.EngelListesiTuru
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ayarlarda engellenenler ya da sessize alınanlar; kaldırılan kişi listeden hemen düşer, başarısızsa geri gelir. */
class EngelListesiViewModel(private val api: ApiIstemcisi, private val tur: EngelListesiTuru) : ViewModel() {
    private val _ekran = MutableStateFlow<KullaniciListesiEkrani>(KullaniciListesiEkrani.Yukleniyor)
    val ekran: StateFlow<KullaniciListesiEkrani> = _ekran
    private var sonrakiImlec: String? = null
    private var yuklemeIsi: Job? = null

    init {
        yuklemeIsi = viewModelScope.launch { yukle(null) }
    }

    fun dahaFazlaYukle() {
        val imlec = sonrakiImlec ?: return
        if (yuklemeIsi?.isActive == true) return
        yuklemeIsi = viewModelScope.launch { yukle(imlec) }
    }

    private suspend fun yukle(imlec: String?) {
        val onceki = (_ekran.value as? KullaniciListesiEkrani.Hazir)?.kullanicilar.orEmpty()
        _ekran.value = try {
            val liste = api.engelListesi(tur, imlec)
            sonrakiImlec = liste.sonrakiImlec
            KullaniciListesiEkrani.Hazir(onceki + liste.kullanicilar, liste.sonrakiImlec != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (onceki.isEmpty()) KullaniciListesiEkrani.Hata else KullaniciListesiEkrani.Hazir(onceki, dahaVar = true)
        }
    }

    /** Engeli kaldırır ya da sessizden çıkarır. */
    fun kaldir(kullaniciAdi: String) {
        val onceki = _ekran.value as? KullaniciListesiEkrani.Hazir ?: return
        val kisi = onceki.kullanicilar.firstOrNull { it.kullaniciAdi == kullaniciAdi } ?: return
        _ekran.update { if (it is KullaniciListesiEkrani.Hazir) it.copy(kullanicilar = it.kullanicilar - kisi) else it }
        viewModelScope.launch {
            try {
                when (tur) {
                    EngelListesiTuru.ENGELLENENLER -> api.engeliKaldir(kullaniciAdi)
                    EngelListesiTuru.SESSIZE_ALINANLAR -> api.sessizdenCikar(kullaniciAdi)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { if (it is KullaniciListesiEkrani.Hazir) it.copy(kullanicilar = onceki.kullanicilar.filter { k -> k in it.kullanicilar || k == kisi }) else it }
            }
        }
    }
}
