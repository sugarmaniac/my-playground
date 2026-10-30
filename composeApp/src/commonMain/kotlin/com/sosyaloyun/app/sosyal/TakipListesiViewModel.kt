package com.sosyaloyun.app.sosyal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.shared.KullaniciOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface KullaniciListesiEkrani {
    data object Yukleniyor : KullaniciListesiEkrani
    data class Hazir(val kullanicilar: List<KullaniciOzeti>, val dahaVar: Boolean) : KullaniciListesiEkrani
    data object Hata : KullaniciListesiEkrani
}

/** Bir kullanıcının takipçileri, takip ettikleri ya da arkadaşları. */
class TakipListesiViewModel(private val api: ApiIstemcisi, private val kullaniciAdi: String, private val tur: KisiListesiTuru) : ViewModel() {
    private val _ekran = MutableStateFlow<KullaniciListesiEkrani>(KullaniciListesiEkrani.Yukleniyor)
    val ekran: StateFlow<KullaniciListesiEkrani> = _ekran
    private var sonrakiImlec: String? = null
    private var is_: Job? = null

    init {
        is_ = viewModelScope.launch { yukle(null) }
    }

    fun dahaFazlaYukle() {
        val imlec = sonrakiImlec ?: return
        if (is_?.isActive == true) return
        is_ = viewModelScope.launch { yukle(imlec) }
    }

    private suspend fun yukle(imlec: String?) {
        val onceki = (_ekran.value as? KullaniciListesiEkrani.Hazir)?.kullanicilar.orEmpty()
        _ekran.value = try {
            val liste = api.kisiListesi(kullaniciAdi, tur, imlec)
            sonrakiImlec = liste.sonrakiImlec
            KullaniciListesiEkrani.Hazir(onceki + liste.kullanicilar, liste.sonrakiImlec != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (onceki.isEmpty()) KullaniciListesiEkrani.Hata else KullaniciListesiEkrani.Hazir(onceki, dahaVar = true)
        }
    }
}
