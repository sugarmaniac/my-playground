package com.sosyaloyun.app.akis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.gunluk.begenisiCevrilmis
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.KullaniciOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AkisIcerigi {
    data object Yukleniyor : AkisIcerigi
    data class Ogeler(val ogeler: List<AkisOgesi>, val dahaVar: Boolean) : AkisIcerigi

    /** Akış boşsa takip önerileri. */
    data class Bos(val oneriler: List<KullaniciOzeti>) : AkisIcerigi
    data object Hata : AkisIcerigi
}

data class AkisDurumu(
    val sekme: AkisSekmesi = AkisSekmesi.TAKIP,
    val icerik: AkisIcerigi = AkisIcerigi.Yukleniyor,
    /** Ekran açıkken gelen yeni olaylar; liste kendiliğinden kaymaz, üstte uyarı gösterilir. */
    val yeniOlaySayisi: Int = 0,
)

/** Ana ekrandaki akış: Takip ve Arkadaşlar sekmeleri, yeni olay uyarısı, boş akışta öneriler. */
class AkisViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow(AkisDurumu())
    val ekran: StateFlow<AkisDurumu> = _ekran
    private var sayfaIsi: Job? = null
    private var sonrakiImlec: String? = null
    private var enYeniImlec: String? = null

    init {
        yenile()
    }

    fun sekmeSecildi(sekme: AkisSekmesi) {
        if (sekme == _ekran.value.sekme) return
        _ekran.update { it.copy(sekme = sekme) }
        yenile()
    }

    /** Akışı baştan yükler; yeni olay uyarısı kalkar. */
    fun yenile() {
        sayfaIsi?.cancel()
        sonrakiImlec = null
        _ekran.update { it.copy(icerik = AkisIcerigi.Yukleniyor, yeniOlaySayisi = 0) }
        sayfaIsi = viewModelScope.launch {
            val sekme = _ekran.value.sekme
            val icerik = try {
                val sayfa = api.akis(sekme)
                sonrakiImlec = sayfa.sonrakiImlec
                enYeniImlec = sayfa.enYeniImlec
                if (sayfa.ogeler.isEmpty()) AkisIcerigi.Bos(api.akisOnerileri()) else AkisIcerigi.Ogeler(sayfa.ogeler, sayfa.sonrakiImlec != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AkisIcerigi.Hata
            }
            _ekran.update { it.copy(icerik = icerik) }
        }
    }

    fun dahaFazlaYukle() {
        val mevcut = _ekran.value.icerik as? AkisIcerigi.Ogeler ?: return
        val imlec = sonrakiImlec ?: return
        if (sayfaIsi?.isActive == true) return
        sayfaIsi = viewModelScope.launch {
            try {
                val sayfa = api.akis(_ekran.value.sekme, imlec)
                sonrakiImlec = sayfa.sonrakiImlec
                _ekran.update { it.copy(icerik = AkisIcerigi.Ogeler(mevcut.ogeler + sayfa.ogeler, sayfa.sonrakiImlec != null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mevcut liste kalır; kullanıcı tekrar kaydırınca yeniden denenir.
            }
        }
    }

    /** Ekran açıkken aralıklarla çağrılır; daha yeni olay sayısını üstteki uyarı için günceller. */
    fun yeniOlaylariDenetle() {
        if (_ekran.value.icerik !is AkisIcerigi.Ogeler && _ekran.value.icerik !is AkisIcerigi.Bos) return
        viewModelScope.launch {
            try {
                val sayi = api.akisYeniSayisi(_ekran.value.sekme, enYeniImlec)
                _ekran.update { it.copy(yeniOlaySayisi = sayi) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Uyarı sonraki denetimde gelir.
            }
        }
    }

    /** Karttaki oyunu istek listesine ekler; hemen gösterilir, sunucu reddederse geri alınır. */
    fun istekListesineEkle(oyunId: String) {
        isaretle(oyunId, true)
        viewModelScope.launch {
            try {
                api.istekListesineEkle(oyunId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isaretle(oyunId, false)
            }
        }
    }

    /** Beğen ya da geri al; hemen gösterilir, sunucu reddederse geri alınır. */
    fun begeniDegistir(kayitId: String) {
        if (kayitId in suregelenBegeniler) return
        val kayit = (_ekran.value.icerik as? AkisIcerigi.Ogeler)?.ogeler?.firstNotNullOfOrNull { it.gunlukKaydi?.takeIf { k -> k.id == kayitId } } ?: return
        kaydiGuncelle(kayit.begenisiCevrilmis())
        suregelenBegeniler += kayitId
        viewModelScope.launch {
            try {
                if (kayit.begendim) api.begeniyiGeriAl(kayitId) else api.begen(kayitId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kaydiGuncelle(kayit)
            } finally {
                suregelenBegeniler -= kayitId
            }
        }
    }

    private val suregelenBegeniler = mutableSetOf<String>()

    private fun kaydiGuncelle(kayit: com.sosyaloyun.shared.GunlukKaydi) = _ekran.update { durum ->
        val icerik = durum.icerik as? AkisIcerigi.Ogeler ?: return@update durum
        durum.copy(icerik = icerik.copy(ogeler = icerik.ogeler.map { if (it.gunlukKaydi?.id == kayit.id) it.copy(gunlukKaydi = kayit) else it }))
    }

    fun onerilenKisiyiTakipEt(kullaniciAdi: String) {
        viewModelScope.launch {
            try {
                api.takipEt(kullaniciAdi)
                _ekran.update { durum ->
                    val icerik = durum.icerik
                    if (icerik is AkisIcerigi.Bos) durum.copy(icerik = icerik.copy(oneriler = icerik.oneriler.filterNot { it.kullaniciAdi == kullaniciAdi })) else durum
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Öneri listede kalır.
            }
        }
    }

    private fun isaretle(oyunId: String, istekListemde: Boolean) = _ekran.update { durum ->
        val icerik = durum.icerik as? AkisIcerigi.Ogeler ?: return@update durum
        durum.copy(icerik = icerik.copy(ogeler = icerik.ogeler.map { if (it.oyun?.id == oyunId) it.copy(istekListemde = istekListemde) else it }))
    }
}
