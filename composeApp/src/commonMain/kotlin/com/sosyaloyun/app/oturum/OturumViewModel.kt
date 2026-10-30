package com.sosyaloyun.app.oturum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.KimlikSaglayici
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class GirisHatasi { SAGLAYICI, REDDEDILDI, BAGLANTI }

sealed interface OturumEkrani {
    data class Giris(val yukleniyor: Boolean = false, val hata: GirisHatasi? = null) : OturumEkrani
    data object Acik : OturumEkrani
}

/** Uygulamanın girişli mi girişsiz mi açılacağını ve giriş/çıkış akışını yönetir. */
class OturumViewModel(private val api: ApiIstemcisi, private val depo: OturumDeposu) : ViewModel() {
    private val _ekran = MutableStateFlow(if (depo.oku() != null) OturumEkrani.Acik else OturumEkrani.Giris())
    val ekran: StateFlow<OturumEkrani> = _ekran

    /** [tokenAl] sağlayıcının cihazdaki giriş akışını açar (Apple/Google sayfası). */
    fun girisYap(saglayici: KimlikSaglayici, tokenAl: suspend () -> KimlikTokeniSonucu) {
        _ekran.value = OturumEkrani.Giris(yukleniyor = true)
        viewModelScope.launch {
            _ekran.value = when (val sonuc = tokenAl()) {
                KimlikTokeniSonucu.Iptal -> OturumEkrani.Giris()
                KimlikTokeniSonucu.Hata -> OturumEkrani.Giris(hata = GirisHatasi.SAGLAYICI)
                is KimlikTokeniSonucu.Token -> try {
                    depo.yaz(api.girisYap(saglayici, sonuc.kimlikTokeni).oturumTokeni)
                    OturumEkrani.Acik
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiHatasiIstisnasi) {
                    OturumEkrani.Giris(hata = GirisHatasi.REDDEDILDI)
                } catch (e: Exception) {
                    OturumEkrani.Giris(hata = GirisHatasi.BAGLANTI)
                }
            }
        }
    }

    /** Oturum cihazdan hemen silinir; backend'e ulaşılamasa da kullanıcı çıkmış olur. */
    fun cikisYap() {
        viewModelScope.launch {
            try {
                api.cikisYap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Backend'deki oturum açık kalır ama token cihazda olmadığı için kullanılamaz.
            }
            depo.yaz(null)
            _ekran.value = OturumEkrani.Giris()
        }
    }
}
