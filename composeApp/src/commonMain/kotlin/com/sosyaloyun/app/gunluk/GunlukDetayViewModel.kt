package com.sosyaloyun.app.gunluk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.sunucuAdresi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.PaylasimLinkleri
import com.sosyaloyun.shared.Yorum
import com.sosyaloyun.shared.YorumKurallari
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GunlukDetayEkrani {
    data object Yukleniyor : GunlukDetayEkrani
    /** [paylasimLinki]: web sitesindeki paylaşım sayfası; yalnızca Herkes görünürlüğündeki kayıt paylaşılabilir. */
    data class Hazir(
        val detay: GunlukKaydiDetayi,
        val begenenler: List<KullaniciOzeti>,
        val paylasimLinki: String? = null,
        /** Faz 2 kapalıyken null: yorum bölümü gösterilmez (#47). */
        val yorumlar: List<Yorum>? = null,
        val yorumMetni: String = "",
        val yorumGonderiliyor: Boolean = false,
        val yorumHatasi: Boolean = false,
        /** Yorum yasağı sürüyor (#53); genel hatadan ayrı bir mesajla gösterilir. */
        val yorumYasagi: Boolean = false,
        val yorumSilmeHatasi: Boolean = false,
    ) : GunlukDetayEkrani {
        val yorumGonderilebilir: Boolean
            get() = yorumlar != null && !yorumGonderiliyor && yorumMetni.isNotBlank() && yorumMetni.trim().length <= YorumKurallari.EN_FAZLA
    }

    /** Kayıt yok ya da görme yetkisi yok; ikisi aynı görünür. */
    data object Bulunamadi : GunlukDetayEkrani
    data object Hata : GunlukDetayEkrani
}

/** Günlük kaydı detayı: kayıt, beğeni ve beğenenler. */
class GunlukDetayViewModel(
    private val api: ApiIstemcisi,
    private val kayitId: String,
    private val webAdresi: String = sunucuAdresi(),
    private val aktifFaz: Faz = Faz.FAZ_1,
) : ViewModel() {
    private val _ekran = MutableStateFlow<GunlukDetayEkrani>(GunlukDetayEkrani.Yukleniyor)
    val ekran: StateFlow<GunlukDetayEkrani> = _ekran
    private var begeniIsi: Job? = null

    init {
        viewModelScope.launch {
            _ekran.value = try {
                val detay = api.gunlukKaydiDetayi(kayitId)
                val link = PaylasimLinkleri.gunlukKaydi(webAdresi, kayitId).takeIf { detay.kayit.gorunurluk == Gorunurluk.HERKES }
                // Yorumlar yüklenemezse detay yine gösterilir; yorum bölümü boş başlar.
                val yorumlar = if (aktifFaz.kapsar(Faz.FAZ_2)) yorumlariGetir() ?: emptyList() else null
                GunlukDetayEkrani.Hazir(detay, api.begenenler(kayitId), link, yorumlar)
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

    fun yorumMetniDegisti(metin: String) =
        _ekran.update { if (it is GunlukDetayEkrani.Hazir) it.copy(yorumMetni = metin, yorumHatasi = false, yorumYasagi = false) else it }

    fun yorumGonder() {
        val durum = _ekran.value as? GunlukDetayEkrani.Hazir ?: return
        if (!durum.yorumGonderilebilir) return
        _ekran.value = durum.copy(yorumGonderiliyor = true, yorumHatasi = false)
        viewModelScope.launch {
            try {
                val yorum = api.yorumYaz(kayitId, durum.yorumMetni.trim())
                _ekran.update { if (it is GunlukDetayEkrani.Hazir) it.copy(yorumlar = it.yorumlar.orEmpty() + yorum, yorumMetni = "", yorumGonderiliyor = false) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val yasak = e is ApiHatasiIstisnasi && e.kod == HataKodu.YORUM_YASAGI
                _ekran.update { if (it is GunlukDetayEkrani.Hazir) it.copy(yorumGonderiliyor = false, yorumHatasi = !yasak, yorumYasagi = yasak) else it }
            }
        }
    }

    /** Yorum hemen kaldırılır; sunucu reddederse yalnızca o yorum zamanına göre yerine geri eklenir. */
    fun yorumSil(yorumId: String) {
        val durum = _ekran.value as? GunlukDetayEkrani.Hazir ?: return
        val silinen = durum.yorumlar?.firstOrNull { it.id == yorumId && it.silebilirim } ?: return
        _ekran.value = durum.copy(yorumlar = durum.yorumlar.filterNot { it.id == yorumId }, yorumSilmeHatasi = false)
        viewModelScope.launch {
            try {
                api.yorumSil(yorumId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update {
                    if (it is GunlukDetayEkrani.Hazir) {
                        it.copy(yorumlar = (it.yorumlar.orEmpty() + silinen).sortedBy { y -> y.zaman }, yorumSilmeHatasi = true)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private suspend fun yorumlariGetir(): List<Yorum>? = try {
        api.yorumlar(kayitId).yorumlar
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
