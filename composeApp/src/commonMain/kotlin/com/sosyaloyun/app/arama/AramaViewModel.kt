package com.sosyaloyun.app.arama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.onbellek.OyunOzetiBellegi
import com.sosyaloyun.shared.AramaKurallari
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AramaSonucu {
    data object Bos : AramaSonucu
    data object Yukleniyor : AramaSonucu
    data class Oyunlar(val oyunlar: List<OyunOzeti>, val dahaVar: Boolean) : AramaSonucu
    data object SonucYok : AramaSonucu
    data object Hata : AramaSonucu
    data class Kullanicilar(val kullanicilar: List<KullaniciOzeti>) : AramaSonucu
}

enum class AramaTuru { OYUN, KULLANICI }

data class AramaDurumu(val sorgu: String = "", val sonuc: AramaSonucu = AramaSonucu.Bos, val tur: AramaTuru = AramaTuru.OYUN)

/** Oyun araması: yazma durunca ([BEKLEME_MS]) aranır; eski sorguların yanıtları yok sayılır. */
class AramaViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow(AramaDurumu())
    val ekran: StateFlow<AramaDurumu> = _ekran
    private var aramaIsi: Job? = null
    private var sonrakiImlec: String? = null

    fun sorguDegisti(sorgu: String) {
        aramaIsi?.cancel()
        sonrakiImlec = null
        val tur = _ekran.value.tur
        if (sorgu.trim().length < AramaKurallari.EN_AZ_KARAKTER) {
            _ekran.value = AramaDurumu(sorgu, AramaSonucu.Bos, tur)
            return
        }
        _ekran.value = AramaDurumu(sorgu, AramaSonucu.Yukleniyor, tur)
        aramaIsi = viewModelScope.launch {
            delay(BEKLEME_MS)
            if (tur == AramaTuru.KULLANICI) kullanicilariAra(sorgu.trim()) else sayfaYukle(sorgu.trim(), imlec = null, onceki = emptyList())
        }
    }

    /** Oyun ya da kullanıcı araması; yazılı sorgu yeni türde hemen yeniden aranır. */
    fun turSecildi(tur: AramaTuru) {
        if (tur == _ekran.value.tur) return
        _ekran.update { it.copy(tur = tur) }
        sorguDegisti(_ekran.value.sorgu)
    }

    private suspend fun kullanicilariAra(sorgu: String) {
        val sonuc = try {
            val kullanicilar = api.kullaniciAra(sorgu).kullanicilar
            if (kullanicilar.isEmpty()) AramaSonucu.SonucYok else AramaSonucu.Kullanicilar(kullanicilar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AramaSonucu.Hata
        }
        _ekran.update { it.copy(sonuc = sonuc) }
    }

    fun dahaFazlaYukle() {
        val mevcut = _ekran.value.sonuc as? AramaSonucu.Oyunlar ?: return
        val imlec = sonrakiImlec ?: return
        if (aramaIsi?.isActive == true) return
        aramaIsi = viewModelScope.launch { sayfaYukle(_ekran.value.sorgu.trim(), imlec, mevcut.oyunlar) }
    }

    private suspend fun sayfaYukle(sorgu: String, imlec: String?, onceki: List<OyunOzeti>) {
        val sonuc = try {
            val yanit = api.oyunAra(sorgu, imlec)
            OyunOzetiBellegi.ekle(yanit.oyunlar)
            sonrakiImlec = yanit.sonrakiImlec
            val oyunlar = onceki + yanit.oyunlar
            if (oyunlar.isEmpty()) AramaSonucu.SonucYok else AramaSonucu.Oyunlar(oyunlar, dahaVar = yanit.sonrakiImlec != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (onceki.isEmpty()) AramaSonucu.Hata else AramaSonucu.Oyunlar(onceki, dahaVar = true)
        }
        _ekran.update { it.copy(sonuc = sonuc) }
    }

    private companion object {
        const val BEKLEME_MS = 300L
    }
}
