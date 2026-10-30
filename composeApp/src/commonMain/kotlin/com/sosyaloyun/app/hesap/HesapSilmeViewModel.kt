package com.sosyaloyun.app.hesap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.HesapSilme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Geri alınamaz silmenin onay adımı: kullanıcı adını elle yazmadan silme düğmesi açılmaz. */
data class HesapSilmeEkrani(
    val onay: String = "",
    val siliniyor: Boolean = false,
    val hata: Boolean = false,
    private val kullaniciAdi: String,
) {
    val silinebilir: Boolean get() = !siliniyor && HesapSilme.onaylandiMi(onay, kullaniciAdi)
}

/** [silindi] hesap silinince cihazdaki oturumu kapatır; backend'deki oturum hesapla birlikte silindi. */
class HesapSilmeViewModel(
    private val api: ApiIstemcisi,
    kullaniciAdi: String,
    private val silindi: () -> Unit,
) : ViewModel() {
    private val _ekran = MutableStateFlow(HesapSilmeEkrani(kullaniciAdi = kullaniciAdi))
    val ekran: StateFlow<HesapSilmeEkrani> = _ekran

    fun onayDegisti(onay: String) = _ekran.update { it.copy(onay = onay, hata = false) }

    fun sil() {
        val durum = _ekran.value
        if (!durum.silinebilir) return
        _ekran.value = durum.copy(siliniyor = true, hata = false)
        viewModelScope.launch {
            try {
                api.hesabiSil(durum.onay)
                silindi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(siliniyor = false, hata = true) }
            }
        }
    }
}
