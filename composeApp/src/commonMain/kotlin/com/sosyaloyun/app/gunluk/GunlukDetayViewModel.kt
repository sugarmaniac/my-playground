package com.sosyaloyun.app.gunluk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GunlukDetayEkrani {
    data object Yukleniyor : GunlukDetayEkrani
    data class Hazir(val detay: GunlukKaydiDetayi, val begenenler: List<KullaniciOzeti>) : GunlukDetayEkrani

    /** Kayıt yok ya da görme yetkisi yok; ikisi aynı görünür. */
    data object Bulunamadi : GunlukDetayEkrani
    data object Hata : GunlukDetayEkrani
}

/** Günlük kaydı detayı: kayıt, beğeni ve beğenenler. */
class GunlukDetayViewModel(private val api: ApiIstemcisi, private val kayitId: String) : ViewModel() {
    private val _ekran = MutableStateFlow<GunlukDetayEkrani>(GunlukDetayEkrani.Yukleniyor)
    val ekran: StateFlow<GunlukDetayEkrani> = _ekran
    private var begeniIsi: Job? = null

    init {
        viewModelScope.launch {
            _ekran.value = try {
                GunlukDetayEkrani.Hazir(api.gunlukKaydiDetayi(kayitId), api.begenenler(kayitId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiHatasiIstisnasi) {
                if (e.kod == HataKodu.GUNLUK_KAYDI_BULUNAMADI) GunlukDetayEkrani.Bulunamadi else GunlukDetayEkrani.Hata
            } catch (e: Exception) {
                GunlukDetayEkrani.Hata
            }
        }
    }

    /** Beğeni hemen gösterilir; sunucu reddederse geri alınır. İstek sürerken gelen dokunuş yok sayılır. */
    fun begeniDegistir() {
        if (begeniIsi?.isActive == true) return
        val onceki = _ekran.value as? GunlukDetayEkrani.Hazir ?: return
        val kayit = onceki.detay.kayit
        _ekran.value = onceki.copy(detay = onceki.detay.copy(kayit = kayit.begenisiCevrilmis()))
        begeniIsi = viewModelScope.launch {
            try {
                if (kayit.begendim) api.begeniyiGeriAl(kayitId) else api.begen(kayitId)
                val begenenler = api.begenenler(kayitId)
                _ekran.update { if (it is GunlukDetayEkrani.Hazir) it.copy(begenenler = begenenler) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { if (it is GunlukDetayEkrani.Hazir) it.copy(detay = it.detay.copy(kayit = kayit)) else it }
            }
        }
    }
}
