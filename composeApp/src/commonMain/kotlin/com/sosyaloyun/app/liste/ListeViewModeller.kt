package com.sosyaloyun.app.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.sunucuAdresi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.ListeKurallari
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.PaylasimLinkleri
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Liste detayı (Faz 2, #49) ──────────────────────────────────────────────

sealed interface ListeDetayEkrani {
    data object Yukleniyor : ListeDetayEkrani

    /** [paylasimLinki]: yalnızca herkese açık listenin web sitesindeki sayfası. */
    data class Hazir(val liste: Liste, val paylasimLinki: String?, val silindi: Boolean = false) : ListeDetayEkrani
    data object Bulunamadi : ListeDetayEkrani
    data object Hata : ListeDetayEkrani
}

class ListeDetayViewModel(
    private val api: ApiIstemcisi,
    private val listeId: String,
    private val webAdresi: String = sunucuAdresi(),
) : ViewModel() {
    private val _ekran = MutableStateFlow<ListeDetayEkrani>(ListeDetayEkrani.Yukleniyor)
    val ekran: StateFlow<ListeDetayEkrani> = _ekran
    private var begeniIsi: Job? = null

    init {
        yenile()
    }

    fun yenile() {
        viewModelScope.launch {
            _ekran.value = try {
                hazir(api.liste(listeId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiHatasiIstisnasi) {
                if (e.kod == HataKodu.LISTE_BULUNAMADI) ListeDetayEkrani.Bulunamadi else ListeDetayEkrani.Hata
            } catch (e: Exception) {
                ListeDetayEkrani.Hata
            }
        }
    }

    private fun hazir(liste: Liste) = ListeDetayEkrani.Hazir(
        liste,
        PaylasimLinkleri.liste(webAdresi, liste.id).takeIf { liste.gorunurluk == ListeGorunurlugu.HERKES },
    )

    /** Beğeni hemen gösterilir; sunucu reddederse geri alınır. */
    fun begeniDegistir() {
        if (begeniIsi?.isActive == true) return
        val durum = _ekran.value as? ListeDetayEkrani.Hazir ?: return
        val liste = durum.liste
        val yeni = liste.copy(begendim = !liste.begendim, begeniSayisi = liste.begeniSayisi + if (liste.begendim) -1 else 1)
        _ekran.value = durum.copy(liste = yeni)
        begeniIsi = viewModelScope.launch {
            try {
                if (liste.begendim) api.listeBegenisiniGeriAl(listeId) else api.listeBegen(listeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { if (it is ListeDetayEkrani.Hazir) it.copy(liste = liste) else it }
            }
        }
    }

    fun sil() {
        val durum = _ekran.value as? ListeDetayEkrani.Hazir ?: return
        if (!durum.liste.benim) return
        viewModelScope.launch {
            try {
                api.listeSil(listeId)
                _ekran.value = durum.copy(silindi = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Silinemezse liste olduğu gibi kalır.
            }
        }
    }

    /** Düzenleme ekranından dönünce güncel listeyi gösterir. */
    fun guncellendi(liste: Liste) {
        _ekran.value = hazir(liste)
    }
}

// ── Liste oluşturma ve düzenleme ───────────────────────────────────────────

data class ListeFormu(
    val baslik: String = "",
    val aciklama: String = "",
    val gorunurluk: ListeGorunurlugu = ListeGorunurlugu.HERKES,
    val oyunlar: List<OyunOzeti> = emptyList(),
    val arama: String = "",
    val aramaSonuclari: List<OyunOzeti> = emptyList(),
    val kaydediliyor: Boolean = false,
    val hata: Boolean = false,
    /** Kaydedilen liste; ekran bununla kapanır. */
    val kaydedilen: Liste? = null,
) {
    val kaydedilebilir: Boolean
        get() = !kaydediliyor && baslik.isNotBlank() && baslik.trim().length <= ListeKurallari.BASLIK_EN_FAZLA &&
            aciklama.trim().length <= ListeKurallari.ACIKLAMA_EN_FAZLA && oyunlar.size <= ListeKurallari.OYUN_EN_FAZLA
}

/** [mevcut] null ise yeni liste. Oyunlar aramayla eklenir, sırası yukarı/aşağı ile değişir. */
class ListeDuzenleViewModel(private val api: ApiIstemcisi, private val mevcut: Liste? = null) : ViewModel() {
    private val _form = MutableStateFlow(
        mevcut?.let { ListeFormu(it.baslik, it.aciklama.orEmpty(), it.gorunurluk, it.oyunlar) } ?: ListeFormu(),
    )
    val form: StateFlow<ListeFormu> = _form
    private var aramaIsi: Job? = null

    fun baslikDegisti(baslik: String) = _form.update { it.copy(baslik = baslik, hata = false) }

    fun aciklamaDegisti(aciklama: String) = _form.update { it.copy(aciklama = aciklama, hata = false) }

    fun gorunurlukSecildi(gorunurluk: ListeGorunurlugu) = _form.update { it.copy(gorunurluk = gorunurluk) }

    /** Yazmayı bekleyip arar; kısa sorguda sonuçlar temizlenir. */
    fun aramaDegisti(sorgu: String) {
        _form.update { it.copy(arama = sorgu) }
        aramaIsi?.cancel()
        if (sorgu.trim().length < 2) {
            _form.update { it.copy(aramaSonuclari = emptyList()) }
            return
        }
        aramaIsi = viewModelScope.launch {
            delay(300)
            val sonuclar = try {
                api.oyunAra(sorgu.trim()).oyunlar
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _form.update { it.copy(aramaSonuclari = sonuclar) }
        }
    }

    fun ekle(oyun: OyunOzeti) = _form.update {
        if (it.oyunlar.any { o -> o.id == oyun.id } || it.oyunlar.size >= ListeKurallari.OYUN_EN_FAZLA) it
        else it.copy(oyunlar = it.oyunlar + oyun, arama = "", aramaSonuclari = emptyList())
    }

    fun cikar(oyunId: String) = _form.update { it.copy(oyunlar = it.oyunlar.filterNot { o -> o.id == oyunId }) }

    fun tasi(oyunId: String, yukari: Boolean) = _form.update {
        val i = it.oyunlar.indexOfFirst { o -> o.id == oyunId }
        val j = if (yukari) i - 1 else i + 1
        if (i < 0 || j !in it.oyunlar.indices) it
        else it.copy(oyunlar = it.oyunlar.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun kaydet() {
        val form = _form.value
        if (!form.kaydedilebilir) return
        _form.value = form.copy(kaydediliyor = true, hata = false)
        val istek = ListeIstegi(form.baslik.trim(), form.aciklama.trim().ifEmpty { null }, form.gorunurluk, form.oyunlar.map { it.id })
        viewModelScope.launch {
            try {
                val liste = if (mevcut == null) api.listeOlustur(istek) else api.listeGuncelle(mevcut.id, istek)
                _form.update { it.copy(kaydediliyor = false, kaydedilen = liste) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it.copy(kaydediliyor = false, hata = true) }
            }
        }
    }
}

// ── Oyun detayındaki listeler ──────────────────────────────────────────────

/** Oyunu içeren herkese açık listeler; yüklenemezse bölüm gösterilmez. */
class OyunListeleriViewModel(private val api: ApiIstemcisi, private val oyunId: String) : ViewModel() {
    private val _listeler = MutableStateFlow<List<com.sosyaloyun.shared.ListeOzeti>>(emptyList())
    val listeler: StateFlow<List<com.sosyaloyun.shared.ListeOzeti>> = _listeler

    init {
        viewModelScope.launch {
            _listeler.value = try {
                api.oyunListeleri(oyunId).listeler
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
