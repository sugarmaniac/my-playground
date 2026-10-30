package com.sosyaloyun.app.profil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.sunucuAdresi
import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.Istatistikler
import com.sosyaloyun.shared.ListeOzeti
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.PaylasimLinkleri
import com.sosyaloyun.shared.ProfilSayfasi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [LISTELER] yalnızca Faz 2 (#49), [ISTATISTIK] yalnızca Faz 3 açıkken gösterilir (#55). */
enum class ProfilSekmesi { GUNLUK, OYUNLAR, INCELEMELER, ISTEK_LISTESI, LISTELER, ISTATISTIK }

/** Oyunlar sekmesinin süzgeci; `DURUMSUZ` henüz durum seçilmemiş girişler. */
enum class OyunFiltresi { HEPSI, OYNUYORUM, BITIRDIM, BIRAKTIM, BEKLEMEDE, DURUMSUZ }

sealed interface ProfilBasligi {
    data object Yukleniyor : ProfilBasligi
    data class Hazir(val sayfa: ProfilSayfasi) : ProfilBasligi
    data object Hata : ProfilBasligi
}

sealed interface SekmeIcerigi {
    data object Yukleniyor : SekmeIcerigi
    data class Gunluk(val ogeler: List<GunlukOgesi>, val dahaVar: Boolean) : SekmeIcerigi
    data class Kutuphane(val ogeler: List<KutuphaneOgesi>, val dahaVar: Boolean) : SekmeIcerigi
    data class Listeler(val ogeler: List<ListeOzeti>, val dahaVar: Boolean) : SekmeIcerigi
    data class Istatistik(val istatistikler: Istatistikler) : SekmeIcerigi
    data object Hata : SekmeIcerigi
}

data class ProfilSayfasiDurumu(
    val baslik: ProfilBasligi = ProfilBasligi.Yukleniyor,
    val sekme: ProfilSekmesi = ProfilSekmesi.GUNLUK,
    val filtre: OyunFiltresi = OyunFiltresi.HEPSI,
    val icerik: SekmeIcerigi = SekmeIcerigi.Yukleniyor,
    /** Arkadaşın profilinde ortak oyunlar ve istek listesi eşleşmeleri; arkadaş değilse null. */
    val ortak: OrtakOyunlar? = null,
    /** Engelleme sunucuda tamamlandı; ekran profilden çıkar, engellenen artık görünmez. */
    val engellendi: Boolean = false,
    /** Engelleme isteği sürüyor ya da başarısız oldu. */
    val engelHatasi: Boolean = false,
    /** Web sitesindeki profil paylaşım sayfası; profil yüklenince dolar. */
    val paylasimLinki: String? = null,
)

/** Profil sayfası: başlık (vitrin, bu yıl bitirilenler, sayaçlar) ve dört sekme. [kullaniciAdi] null ise kendi profilin. */
class ProfilSayfasiViewModel(
    private val api: ApiIstemcisi,
    private val kullaniciAdi: String? = null,
    private val webAdresi: String = sunucuAdresi(),
) : ViewModel() {
    private val _ekran = MutableStateFlow(ProfilSayfasiDurumu())
    val ekran: StateFlow<ProfilSayfasiDurumu> = _ekran
    private var sekmeIsi: Job? = null
    private var sonrakiImlec: String? = null
    private var islemIsi: Job? = null

    init {
        yenile()
    }

    /** Başlığı ve açık sekmeyi baştan yükler (ör. profil ya da vitrin düzenlendikten sonra). */
    fun yenile() {
        viewModelScope.launch {
            val baslik = try {
                ProfilBasligi.Hazir(api.profil(kullaniciAdi))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProfilBasligi.Hata
            }
            val link = (baslik as? ProfilBasligi.Hazir)?.let { PaylasimLinkleri.profil(webAdresi, it.sayfa.kullanici.kullaniciAdi) }
            _ekran.update { it.copy(baslik = baslik, paylasimLinki = link ?: it.paylasimLinki) }
            if ((baslik as? ProfilBasligi.Hazir)?.sayfa?.arkadaslik == ArkadaslikDurumu.ARKADAS) ortakOyunlariYukle()
        }
        sekmeyiYukle()
    }

    private suspend fun ortakOyunlariYukle() {
        val ad = kullaniciAdi ?: return
        val ortak = try {
            api.ortakOyunlar(ad)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _ekran.update { it.copy(ortak = ortak) }
    }

    /**
     * Sosyal işlemler sonuç beklenmeden gösterilir; sunucu reddederse sayfa işlemden önceki haline döner.
     * Önceki işlem bitmeden gelen dokunuş yok sayılır, böylece geri alma sunucuyla çelişmez.
     */
    private fun iyimser(donustur: (ProfilSayfasi) -> ProfilSayfasi?, istek: suspend (String) -> Unit, sonra: suspend () -> Unit = {}) {
        if (islemIsi?.isActive == true) return
        val ad = kullaniciAdi ?: return
        val sayfa = (_ekran.value.baslik as? ProfilBasligi.Hazir)?.sayfa ?: return
        val yeni = donustur(sayfa) ?: return
        _ekran.update { it.copy(baslik = ProfilBasligi.Hazir(yeni)) }
        islemIsi = viewModelScope.launch {
            try {
                istek(ad)
                sonra()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(baslik = ProfilBasligi.Hazir(sayfa)) }
            }
        }
    }

    fun arkadaslikIste() = iyimser(
        donustur = { if (it.arkadaslik == ArkadaslikDurumu.YOK) it.copy(arkadaslik = ArkadaslikDurumu.ISTEK_GONDERILDI) else null },
        istek = { api.arkadaslikIste(it) },
    )

    /** Kabul edince iki yönlü takip oluşur. */
    fun arkadaslikKabulEt() = iyimser(
        donustur = {
            if (it.arkadaslik != ArkadaslikDurumu.ISTEK_ALINDI) {
                null
            } else {
                it.copy(
                    arkadaslik = ArkadaslikDurumu.ARKADAS,
                    arkadasSayisi = it.arkadasSayisi + 1,
                    takipEdiyorum = true,
                    takipciSayisi = it.takipciSayisi + if (it.takipEdiyorum == true) 0 else 1,
                )
            }
        },
        istek = { api.arkadaslikKabulEt(it) },
        sonra = { ortakOyunlariYukle() },
    )

    fun arkadaslikReddet() = iyimser(
        donustur = { if (it.arkadaslik == ArkadaslikDurumu.ISTEK_ALINDI) it.copy(arkadaslik = ArkadaslikDurumu.YOK) else null },
        istek = { api.arkadaslikReddet(it) },
    )

    /** Arkadaşlığı bitirir ya da gönderilmiş isteği geri alır; takipler kalır. */
    fun arkadasligiBitir() = iyimser(
        donustur = {
            when (it.arkadaslik) {
                ArkadaslikDurumu.ARKADAS -> it.copy(arkadaslik = ArkadaslikDurumu.YOK, arkadasSayisi = it.arkadasSayisi - 1)
                ArkadaslikDurumu.ISTEK_GONDERILDI -> it.copy(arkadaslik = ArkadaslikDurumu.YOK)
                else -> null
            }
        },
        istek = {
            api.arkadasligiBitir(it)
            _ekran.update { durum -> durum.copy(ortak = null) }
        },
    )

    /** Takip et ya da bırak; sonuç beklenmeden gösterilir, sunucu reddederse geri alınır. */
    fun takipDegistir() = iyimser(
        donustur = {
            val takipEdiyor = it.takipEdiyorum
            when {
                // Arkadaşlık sürerken takipten çıkılamaz (CONTEXT.md: Arkadaşlık).
                takipEdiyor == null || it.arkadaslik == ArkadaslikDurumu.ARKADAS -> null
                // Gizli profilde takip bir istektir (#48); istek gönderildiyse dokunuş isteği geri alır.
                it.gizli && !takipEdiyor -> it.copy(takipIstegiGonderdim = it.takipIstegiGonderdim != true)
                else -> it.copy(takipEdiyorum = !takipEdiyor, takipciSayisi = it.takipciSayisi + if (takipEdiyor) -1 else 1)
            }
        },
        istek = { ad ->
            val yeni = (_ekran.value.baslik as ProfilBasligi.Hazir).sayfa
            if (yeni.takipEdiyorum == true || yeni.takipIstegiGonderdim == true) api.takipEt(ad) else api.takibiBirak(ad)
        },
    )

    /** Sessize al ya da geri al; takip ve arkadaşlık değişmez, yalnızca akış etkilenir. */
    fun sessizDegistir() = iyimser(
        donustur = { sayfa -> sayfa.sessizeAldim?.let { sayfa.copy(sessizeAldim = !it) } },
        istek = { ad -> if ((_ekran.value.baslik as ProfilBasligi.Hazir).sayfa.sessizeAldim == true) api.sessizeAl(ad) else api.sessizdenCikar(ad) },
    )

    /** Engelleme geri dönüşü olmayan sonuçlar doğurur (takip, arkadaşlık, beğeniler silinir); iyimser değildir, sunucu onaylayınca ekran kapanır. */
    fun engelle() {
        if (islemIsi?.isActive == true) return
        val ad = kullaniciAdi ?: return
        islemIsi = viewModelScope.launch {
            _ekran.update { it.copy(engelHatasi = false) }
            try {
                api.engelle(ad)
                _ekran.update { it.copy(engellendi = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(engelHatasi = true) }
            }
        }
    }

    fun sekmeSecildi(sekme: ProfilSekmesi) {
        if (sekme == _ekran.value.sekme) return
        _ekran.update { it.copy(sekme = sekme) }
        sekmeyiYukle()
    }

    fun filtreSecildi(filtre: OyunFiltresi) {
        _ekran.update { it.copy(filtre = filtre) }
        sekmeyiYukle()
    }

    fun dahaFazlaYukle() {
        val imlec = sonrakiImlec ?: return
        if (sekmeIsi?.isActive == true) return
        sekmeIsi = viewModelScope.launch { sayfaYukle(imlec) }
    }

    private fun sekmeyiYukle() {
        sekmeIsi?.cancel()
        sonrakiImlec = null
        _ekran.update { it.copy(icerik = SekmeIcerigi.Yukleniyor) }
        sekmeIsi = viewModelScope.launch { sayfaYukle(imlec = null) }
    }

    private suspend fun sayfaYukle(imlec: String?) {
        val durum = _ekran.value
        val onceki = if (imlec == null) null else durum.icerik
        val icerik = try {
            when (durum.sekme) {
                ProfilSekmesi.GUNLUK, ProfilSekmesi.INCELEMELER -> {
                    val sayfa = api.profilGunlugu(kullaniciAdi, yalnizcaIncelemeler = durum.sekme == ProfilSekmesi.INCELEMELER, imlec = imlec)
                    sonrakiImlec = sayfa.sonrakiImlec
                    SekmeIcerigi.Gunluk(((onceki as? SekmeIcerigi.Gunluk)?.ogeler.orEmpty()) + sayfa.ogeler, sayfa.sonrakiImlec != null)
                }
                ProfilSekmesi.OYUNLAR, ProfilSekmesi.ISTEK_LISTESI -> {
                    val istekListesi = durum.sekme == ProfilSekmesi.ISTEK_LISTESI
                    val filtre = if (istekListesi || durum.filtre == OyunFiltresi.HEPSI) null else durum.filtre.name.lowercase()
                    val sayfa = api.profilKutuphanesi(kullaniciAdi, istekListesi, filtre, imlec)
                    sonrakiImlec = sayfa.sonrakiImlec
                    SekmeIcerigi.Kutuphane(((onceki as? SekmeIcerigi.Kutuphane)?.ogeler.orEmpty()) + sayfa.ogeler, sayfa.sonrakiImlec != null)
                }
                ProfilSekmesi.LISTELER -> {
                    val sayfa = api.profilListeleri(kullaniciAdi, imlec)
                    sonrakiImlec = sayfa.sonrakiImlec
                    SekmeIcerigi.Listeler(((onceki as? SekmeIcerigi.Listeler)?.ogeler.orEmpty()) + sayfa.listeler, sayfa.sonrakiImlec != null)
                }
                ProfilSekmesi.ISTATISTIK -> SekmeIcerigi.Istatistik(api.istatistikler(kullaniciAdi))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onceki ?: SekmeIcerigi.Hata
        }
        _ekran.update { it.copy(icerik = icerik) }
    }
}
