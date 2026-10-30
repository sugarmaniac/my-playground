package com.sosyaloyun.app.gizlilik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.KullaniciOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ayarlardaki gizlilik bölümü (Faz 2, #48): gizli profil anahtarı ve gelen takip istekleri. */
data class GizlilikEkrani(
    val yuklendi: Boolean = false,
    val gizli: Boolean = false,
    val istekler: List<KullaniciOzeti> = emptyList(),
    val hata: Boolean = false,
)

class GizlilikViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow(GizlilikEkrani())
    val ekran: StateFlow<GizlilikEkrani> = _ekran

    init {
        viewModelScope.launch {
            try {
                val gizli = api.profil(null).gizli
                _ekran.value = GizlilikEkrani(yuklendi = true, gizli = gizli, istekler = api.takipIstekleri())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.value = GizlilikEkrani(hata = true)
            }
        }
    }

    /** Anahtar hemen döner; sunucu reddederse geri alınır. Açığa geçince bekleyen istekler kabul edilir. */
    fun gizliDegistir(gizli: Boolean) {
        val onceki = _ekran.value
        if (!onceki.yuklendi || onceki.gizli == gizli) return
        _ekran.value = onceki.copy(gizli = gizli, istekler = if (gizli) onceki.istekler else emptyList(), hata = false)
        islem(geriAl = onceki) { api.gizlilikAyarla(gizli) }
    }

    fun kabulEt(kullaniciAdi: String) = istegiKaldir(kullaniciAdi) { api.takipIsteginiKabulEt(kullaniciAdi) }

    fun reddet(kullaniciAdi: String) = istegiKaldir(kullaniciAdi) { api.takipIsteginiReddet(kullaniciAdi) }

    private fun istegiKaldir(kullaniciAdi: String, istek: suspend () -> Unit) {
        val onceki = _ekran.value
        if (onceki.istekler.none { it.kullaniciAdi == kullaniciAdi }) return
        _ekran.value = onceki.copy(istekler = onceki.istekler.filterNot { it.kullaniciAdi == kullaniciAdi }, hata = false)
        islem(geriAl = onceki, istek)
    }

    private fun islem(geriAl: GizlilikEkrani, istek: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                istek()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { geriAl.copy(hata = true) }
            }
        }
    }
}
