package com.sosyaloyun.app.kutuphane

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.KutuphaneGirisi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HizliKayitDurumu(
    val giris: KutuphaneGirisi = KutuphaneGirisi(),
    val kaydediliyor: Boolean = false,
    val hata: Boolean = false,
) {
    val yuzdeYuzSecilebilir: Boolean get() = giris.durum == Durum.BITIRDIM
}

/** Hızlı kayıt alt sayfası: durum, puan, beğendim, istek listesi ve %100 birkaç dokunuşta. */
class HizliKayitViewModel(
    private val api: ApiIstemcisi,
    private val oyunId: String,
    baslangic: KutuphaneGirisi?,
    /** Kaydedilen giriş; bütün işaretler kaldırıldıysa null (oyun kütüphaneden çıktı). */
    private val kaydedildi: (KutuphaneGirisi?) -> Unit,
) : ViewModel() {
    private val _ekran = MutableStateFlow(HizliKayitDurumu(giris = baslangic ?: KutuphaneGirisi()))
    val ekran: StateFlow<HizliKayitDurumu> = _ekran

    /** Seçili duruma tekrar dokunmak durumu kaldırır; Bitirdim'den çıkınca %100 de kalkar. */
    fun durumSecildi(durum: Durum) = degistir {
        val yeni = if (it.durum == durum) null else durum
        it.copy(durum = yeni, yuzdeYuz = it.yuzdeYuz && yeni == Durum.BITIRDIM)
    }

    /** [yarimYildiz] 1–10; seçili puana tekrar dokunmak puanı kaldırır. */
    fun puanSecildi(yarimYildiz: Int) = degistir { it.copy(puan = if (it.puan == yarimYildiz) null else yarimYildiz) }

    fun begendimDegisti() = degistir { it.copy(begendim = !it.begendim) }

    fun istekListesiDegisti() = degistir { it.copy(istekListesinde = !it.istekListesinde) }

    fun yuzdeYuzDegisti() {
        if (_ekran.value.yuzdeYuzSecilebilir) degistir { it.copy(yuzdeYuz = !it.yuzdeYuz) }
    }

    fun kaydet() {
        if (_ekran.value.kaydediliyor) return
        val giris = _ekran.value.giris
        _ekran.update { it.copy(kaydediliyor = true, hata = false) }
        viewModelScope.launch {
            try {
                api.kutuphaneGirisiKaydet(oyunId, giris)
                _ekran.update { it.copy(kaydediliyor = false) }
                kaydedildi(giris.takeUnless { it.bos })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(kaydediliyor = false, hata = true) }
            }
        }
    }

    private fun degistir(donustur: (KutuphaneGirisi) -> KutuphaneGirisi) {
        _ekran.update { it.copy(giris = donustur(it.giris), hata = false) }
    }
}
