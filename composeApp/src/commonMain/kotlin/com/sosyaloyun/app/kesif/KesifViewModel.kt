package com.sosyaloyun.app.kesif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.onbellek.BosOnbellek
import com.sosyaloyun.app.onbellek.OyunOzetiBellegi
import com.sosyaloyun.app.onbellek.YerelOnbellek
import com.sosyaloyun.app.onbellek.okuNesne
import com.sosyaloyun.app.onbellek.yazNesne
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.Tur
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface KesifEkrani {
    data object Yukleniyor : KesifEkrani
    data class Hazir(val kesif: Kesif) : KesifEkrani
    data object Hata : KesifEkrani
}

/** Son keşif cihazda saklanır: ekran açılır açılmaz o gösterilir, arkadan tazelenir (#60). */
class KesifViewModel(private val api: ApiIstemcisi, private val onbellek: YerelOnbellek = BosOnbellek) : ViewModel() {
    private val _ekran = MutableStateFlow<KesifEkrani>(KesifEkrani.Yukleniyor)
    val ekran: StateFlow<KesifEkrani> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        val sakli = onbellek.okuNesne<Kesif>(ONBELLEK_ANAHTARI)
        _ekran.value = sakli?.let(KesifEkrani::Hazir) ?: KesifEkrani.Yukleniyor
        viewModelScope.launch {
            try {
                val kesif = api.kesif()
                OyunOzetiBellegi.ekle(kesif.trend + kesif.takipEdilenlerde.map { it.oyun })
                onbellek.yazNesne(ONBELLEK_ANAHTARI, kesif)
                _ekran.value = KesifEkrani.Hazir(kesif)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Saklı keşif gösteriliyorsa o kalır.
                if (sakli == null) _ekran.value = KesifEkrani.Hata
            }
        }
    }

    private companion object {
        const val ONBELLEK_ANAHTARI = "kesif"
    }
}

sealed interface OyunListesiEkrani {
    data object Yukleniyor : OyunListesiEkrani
    data class Hazir(val oyunlar: List<OyunOzeti>, val dahaVar: Boolean) : OyunListesiEkrani
    data object Hata : OyunListesiEkrani
}

/** Keşif kısayolu: bir türdeki ([tur]) ya da platformdaki ([platform]) oyunlar, sayfalı. */
class OyunListesiViewModel(private val api: ApiIstemcisi, private val tur: Tur?, private val platform: String?) : ViewModel() {
    private val _ekran = MutableStateFlow<OyunListesiEkrani>(OyunListesiEkrani.Yukleniyor)
    val ekran: StateFlow<OyunListesiEkrani> = _ekran
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
        val onceki = (_ekran.value as? OyunListesiEkrani.Hazir)?.oyunlar.orEmpty()
        _ekran.value = try {
            val sayfa = api.oyunListesi(tur, platform, imlec)
            OyunOzetiBellegi.ekle(sayfa.oyunlar)
            sonrakiImlec = sayfa.sonrakiImlec
            OyunListesiEkrani.Hazir(onceki + sayfa.oyunlar, sayfa.sonrakiImlec != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (onceki.isEmpty()) OyunListesiEkrani.Hata else OyunListesiEkrani.Hazir(onceki, dahaVar = true)
        }
    }
}
