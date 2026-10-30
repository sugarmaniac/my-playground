package com.sosyaloyun.app.sikayet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetKurallari
import com.sosyaloyun.shared.SikayetSebebi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Şikayet edilen: bir günlük kaydı ya da bir profil; her ikisinde de sahibinin kullanıcı adı bilinir. */
data class SikayetHedefi(val kullaniciAdi: String, val gunlukKaydiId: String? = null)

sealed interface SikayetEkrani {
    data class Form(
        val sebep: SikayetSebebi? = null,
        val aciklama: String = "",
        val gonderiliyor: Boolean = false,
        val hata: Boolean = false,
    ) : SikayetEkrani {
        val gonderilebilir: Boolean get() = sebep != null && !gonderiliyor && aciklama.length <= SikayetKurallari.ACIKLAMA_EN_FAZLA
    }

    /** Şikayet gönderildi; kişiyi engelleme ya da sessize alma önerilir. */
    data class Gonderildi(val islem: Islem? = null) : SikayetEkrani

    enum class Islem { ENGELLENDI, SESSIZE_ALINDI }
}

class SikayetViewModel(private val api: ApiIstemcisi, private val hedef: SikayetHedefi) : ViewModel() {
    private val _ekran = MutableStateFlow<SikayetEkrani>(SikayetEkrani.Form())
    val ekran: StateFlow<SikayetEkrani> = _ekran

    fun sebepSec(sebep: SikayetSebebi) = _ekran.update { if (it is SikayetEkrani.Form) it.copy(sebep = sebep, hata = false) else it }

    fun aciklamaDegisti(aciklama: String) = _ekran.update { if (it is SikayetEkrani.Form) it.copy(aciklama = aciklama, hata = false) else it }

    fun gonder() {
        val form = _ekran.value as? SikayetEkrani.Form ?: return
        if (!form.gonderilebilir) return
        _ekran.value = form.copy(gonderiliyor = true, hata = false)
        viewModelScope.launch {
            _ekran.value = try {
                api.sikayetEt(
                    SikayetIstegi(
                        sebep = form.sebep!!,
                        gunlukKaydiId = hedef.gunlukKaydiId,
                        kullaniciAdi = hedef.kullaniciAdi.takeIf { hedef.gunlukKaydiId == null },
                        aciklama = form.aciklama.trim().ifEmpty { null },
                    ),
                )
                SikayetEkrani.Gonderildi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.copy(gonderiliyor = false, hata = true)
            }
        }
    }

    fun engelle() = islem(SikayetEkrani.Islem.ENGELLENDI) { api.engelle(hedef.kullaniciAdi) }

    fun sessizeAl() = islem(SikayetEkrani.Islem.SESSIZE_ALINDI) { api.sessizeAl(hedef.kullaniciAdi) }

    private fun islem(sonuc: SikayetEkrani.Islem, istek: suspend () -> Unit) {
        if (_ekran.value != SikayetEkrani.Gonderildi()) return
        viewModelScope.launch {
            try {
                istek()
                _ekran.value = SikayetEkrani.Gonderildi(sonuc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Engelleme ya da sessize alma sonra profilden de yapılabilir; şikayet zaten gönderildi.
            }
        }
    }
}
