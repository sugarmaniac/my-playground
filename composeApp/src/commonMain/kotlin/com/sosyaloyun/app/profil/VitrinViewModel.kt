package com.sosyaloyun.app.profil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.ProfilKurallari
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VitrinDurumu(val oyunlar: List<OyunOzeti> = emptyList(), val kaydediliyor: Boolean = false, val hata: Boolean = false) {
    val eklenebilir: Boolean get() = oyunlar.size < ProfilKurallari.VITRIN_EN_FAZLA
}

/** Profil vitrini: en fazla dört farklı oyun, eklenme sırasıyla. */
class VitrinViewModel(private val api: ApiIstemcisi, mevcut: List<OyunOzeti>, private val kaydedildi: () -> Unit) : ViewModel() {
    private val _ekran = MutableStateFlow(VitrinDurumu(oyunlar = mevcut))
    val ekran: StateFlow<VitrinDurumu> = _ekran

    fun ekle(oyun: OyunOzeti) = _ekran.update { durum ->
        if (!durum.eklenebilir || durum.oyunlar.any { it.id == oyun.id }) durum else durum.copy(oyunlar = durum.oyunlar + oyun, hata = false)
    }

    fun cikar(oyunId: String) = _ekran.update { durum -> durum.copy(oyunlar = durum.oyunlar.filterNot { it.id == oyunId }, hata = false) }

    fun kaydet() {
        if (_ekran.value.kaydediliyor) return
        _ekran.update { it.copy(kaydediliyor = true, hata = false) }
        viewModelScope.launch {
            try {
                api.vitrinKaydet(_ekran.value.oyunlar.map { it.id })
                _ekran.update { it.copy(kaydediliyor = false) }
                kaydedildi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(kaydediliyor = false, hata = true) }
            }
        }
    }
}
