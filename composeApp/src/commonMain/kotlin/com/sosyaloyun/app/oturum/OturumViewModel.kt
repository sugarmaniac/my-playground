package com.sosyaloyun.app.oturum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.ItirazKurallari
import com.sosyaloyun.shared.KimlikSaglayici
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GirisHatasi { SAGLAYICI, REDDEDILDI, BAGLANTI, HESAP_ASKIDA, HESAP_BANLI }

/** Giriş ekranından itirazın durumu (Faz 2, #53). */
enum class GirisItirazi { YAZILIYOR, GONDERILIYOR, GONDERILDI, HATA }

sealed interface OturumEkrani {
    /**
     * [askiBitisi]: askıdaki hesapta askının bittiği gün (YYYY-AA-GG). [itirazAnahtari]: Faz 2'de askıdaki ya da banlı
     * hesabın reddedilen girişinden; varsa giriş ekranında itiraz alanı gösterilir.
     */
    data class Giris(
        val yukleniyor: Boolean = false,
        val hata: GirisHatasi? = null,
        val askiBitisi: String? = null,
        val itirazAnahtari: String? = null,
        val itirazMetni: String = "",
        val itiraz: GirisItirazi = GirisItirazi.YAZILIYOR,
    ) : OturumEkrani {
        val itirazGonderilebilir: Boolean
            get() = itirazAnahtari != null && itiraz != GirisItirazi.GONDERILIYOR && itiraz != GirisItirazi.GONDERILDI &&
                itirazMetni.isNotBlank() && itirazMetni.trim().length <= ItirazKurallari.EN_FAZLA
    }
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
                    when (e.kod) {
                        HataKodu.HESAP_ASKIDA -> OturumEkrani.Giris(hata = GirisHatasi.HESAP_ASKIDA, askiBitisi = e.ayrinti?.take(10), itirazAnahtari = e.itirazAnahtari)
                        HataKodu.HESAP_BANLI -> OturumEkrani.Giris(hata = GirisHatasi.HESAP_BANLI, itirazAnahtari = e.itirazAnahtari)
                        else -> OturumEkrani.Giris(hata = GirisHatasi.REDDEDILDI)
                    }
                } catch (e: Exception) {
                    OturumEkrani.Giris(hata = GirisHatasi.BAGLANTI)
                }
            }
        }
    }

    fun itirazMetniDegisti(metin: String) {
        _ekran.update { if (it is OturumEkrani.Giris && it.itiraz != GirisItirazi.GONDERILDI) it.copy(itirazMetni = metin, itiraz = GirisItirazi.YAZILIYOR) else it }
    }

    fun itirazGonder() {
        val ekran = _ekran.value as? OturumEkrani.Giris ?: return
        if (!ekran.itirazGonderilebilir) return
        _ekran.value = ekran.copy(itiraz = GirisItirazi.GONDERILIYOR)
        viewModelScope.launch {
            val sonuc = try {
                api.girisItirazi(ekran.itirazAnahtari!!, ekran.itirazMetni.trim())
                GirisItirazi.GONDERILDI
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GirisItirazi.HATA
            }
            _ekran.update { if (it is OturumEkrani.Giris) it.copy(itiraz = sonuc) else it }
        }
    }

    /** Hesap silinince backend'deki oturum da silindi; yalnızca cihazdaki token kalkar. */
    fun hesapSilindi() {
        depo.yaz(null)
        _ekran.value = OturumEkrani.Giris()
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
