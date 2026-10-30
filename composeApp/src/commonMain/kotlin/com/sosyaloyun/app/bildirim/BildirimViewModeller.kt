package com.sosyaloyun.app.bildirim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.Bildirim
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BildirimlerEkrani {
    data object Yukleniyor : BildirimlerEkrani

    /** [bildirimler]'deki okunmamışlar açılışta işaretlenir; sunucuda okundu sayılsa da bu açılışta vurgulu kalırlar. */
    data class Hazir(val bildirimler: List<Bildirim>) : BildirimlerEkrani
    data object Hata : BildirimlerEkrani
}

/** Bildirim ekranı: açılınca liste yüklenir ve hepsi okundu sayılır. */
class BildirimlerViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow<BildirimlerEkrani>(BildirimlerEkrani.Yukleniyor)
    val ekran: StateFlow<BildirimlerEkrani> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        _ekran.value = BildirimlerEkrani.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                val liste = api.bildirimler()
                if (liste.okunmamisSayisi > 0) runCatching { api.bildirimleriOkunduSay() }
                BildirimlerEkrani.Hazir(liste.bildirimler)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BildirimlerEkrani.Hata
            }
        }
    }
}

/** Akışın üst çubuğundaki okunmamış bildirim sayısı. */
class OkunmamisBildirimViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _sayi = MutableStateFlow(0)
    val sayi: StateFlow<Int> = _sayi

    fun yenile() {
        viewModelScope.launch {
            try {
                _sayi.value = api.okunmamisBildirimSayisi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sayı gösterilemezse önceki değer kalır.
            }
        }
    }
}

/** Ayarlarda tür başına push tercihleri; değişiklik hemen gösterilir, kaydedilemezse geri alınır. */
class BildirimTercihleriViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _tercihler = MutableStateFlow<Map<BildirimTuru, Boolean>?>(null)
    val tercihler: StateFlow<Map<BildirimTuru, Boolean>?> = _tercihler

    init {
        viewModelScope.launch {
            try {
                _tercihler.value = api.bildirimTercihleri().push
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tercihler yüklenemezse bölüm gösterilmez.
            }
        }
    }

    fun degistir(tur: BildirimTuru, acik: Boolean) {
        val onceki = _tercihler.value ?: return
        _tercihler.update { it?.plus(tur to acik) }
        viewModelScope.launch {
            try {
                api.bildirimTercihleriniKaydet(BildirimTercihleri(mapOf(tur to acik)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tercihler.update { it?.plus(tur to onceki.getValue(tur)) }
            }
        }
    }
}
