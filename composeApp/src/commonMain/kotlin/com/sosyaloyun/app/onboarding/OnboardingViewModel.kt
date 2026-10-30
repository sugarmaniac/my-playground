package com.sosyaloyun.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.OnboardingKurallari
import com.sosyaloyun.shared.TelefonNumarasi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SteamHatasi { GIZLI, BULUNAMADI, BASKA_KULLANICIDA, ULASILAMIYOR }

/** Steam isteğinin hatası; bağlantı ve sunucu sorunları aynı görünür. */
internal fun Exception.steamHatasi(): SteamHatasi = when ((this as? ApiHatasiIstisnasi)?.kod) {
    HataKodu.STEAM_PROFILI_GIZLI -> SteamHatasi.GIZLI
    HataKodu.STEAM_HESABI_BULUNAMADI -> SteamHatasi.BULUNAMADI
    HataKodu.STEAM_HESABI_BASKA_KULLANICIDA -> SteamHatasi.BASKA_KULLANICIDA
    else -> SteamHatasi.ULASILAMIYOR
}

sealed interface OnboardingAdimi {
    /** Onboarding 4. adım: Steam bağlama, atlanabilir. */
    data class Steam(val girdi: String = "", val gonderiliyor: Boolean = false, val hata: SteamHatasi? = null) : OnboardingAdimi {
        val gonderilebilir: Boolean get() = girdi.isNotBlank() && !gonderiliyor
    }

    /**
     * Onboarding 5. adım: Steam'liyse en çok oynanan oyunların, Steam'sizse popüler oyunların durumu seçilir.
     * Steam'siz kullanıcı en az [OnboardingKurallari.EN_AZ_OYUN] oyun işaretlemeli.
     */
    data class Durumlar(
        val steamli: Boolean,
        val oyunlar: List<KutuphaneOgesi>,
        val bitiriliyor: Boolean = false,
        val hata: Boolean = false,
    ) : OnboardingAdimi {
        val secilenSayisi: Int get() = oyunlar.count { it.giris.durum != null }
        val bitirilebilir: Boolean get() = !bitiriliyor && (steamli || secilenSayisi >= OnboardingKurallari.EN_AZ_OYUN)
    }

    /** Onboarding 6. adım: rehberdeki ve Steam arkadaşlarındaki kullanıcıları bul, takip et, davet paylaş; atlanabilir. */
    data class Arkadaslar(
        val steamli: Boolean,
        val kisiler: List<BulunanKisi> = emptyList(),
        val araniyor: Boolean = false,
        val rehberTarandi: Boolean = false,
        val rehberIzniYok: Boolean = false,
        val numaraKaydedildi: Boolean = false,
        /** Paylaşılabilecek kullanılmamış davet kodu; yoksa null. */
        val davetKodu: String? = null,
        val bitiriliyor: Boolean = false,
        val hata: Boolean = false,
    ) : OnboardingAdimi {
        val takipEdilmeyenler: List<BulunanKisi> get() = kisiler.filterNot { it.takipEdiyorum }
    }

    /** Son adım: bildirim izni, nedeni açıklandıktan sonra istenir; reddedilse de onboarding biter. */
    data class BildirimIzni(val bitiriliyor: Boolean = false, val hata: Boolean = false) : OnboardingAdimi
}

class OnboardingViewModel(private val api: ApiIstemcisi, private val hesabiYenile: () -> Unit) : ViewModel() {
    private val _ekran = MutableStateFlow<OnboardingAdimi>(OnboardingAdimi.Steam())
    val ekran: StateFlow<OnboardingAdimi> = _ekran

    fun girdiDegisti(girdi: String) {
        _ekran.update { if (it is OnboardingAdimi.Steam) it.copy(girdi = girdi, hata = null) else it }
    }

    fun bagla() {
        val adim = _ekran.value as? OnboardingAdimi.Steam ?: return
        if (!adim.gonderilebilir) return
        _ekran.value = adim.copy(gonderiliyor = true, hata = null)
        viewModelScope.launch {
            _ekran.value = try {
                val sonuc = api.steamBagla(adim.girdi.trim())
                OnboardingAdimi.Durumlar(steamli = true, oyunlar = sonuc.enCokOynananlar)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                adim.copy(gonderiliyor = false, hata = e.steamHatasi())
            }
        }
    }

    /** Steam'siz devam: popüler oyunlardan durum seçilir. */
    fun steamiAtla() {
        val adim = _ekran.value as? OnboardingAdimi.Steam ?: return
        if (adim.gonderiliyor) return
        _ekran.value = adim.copy(gonderiliyor = true, hata = null)
        viewModelScope.launch {
            _ekran.value = try {
                OnboardingAdimi.Durumlar(steamli = false, oyunlar = api.populerOyunlar().map { KutuphaneOgesi(it, KutuphaneGirisi()) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                adim.copy(gonderiliyor = false, hata = SteamHatasi.ULASILAMIYOR)
            }
        }
    }

    /** Durum hemen gösterilir; kaydedilemezse eski haline döner. Aynı duruma ikinci dokunuş seçimi kaldırır. */
    fun durumSec(oyunId: String, durum: Durum) {
        val adim = _ekran.value as? OnboardingAdimi.Durumlar ?: return
        val oge = adim.oyunlar.firstOrNull { it.oyun.id == oyunId } ?: return
        val yeni = oge.giris.copy(durum = durum.takeUnless { it == oge.giris.durum }, yuzdeYuz = false)
        fun degistir(giris: KutuphaneGirisi) = _ekran.update { mevcut ->
            if (mevcut !is OnboardingAdimi.Durumlar) return@update mevcut
            mevcut.copy(oyunlar = mevcut.oyunlar.map { if (it.oyun.id == oyunId) it.copy(giris = giris) else it })
        }
        degistir(yeni)
        viewModelScope.launch {
            try {
                api.kutuphaneGirisiKaydet(oyunId, yeni)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                degistir(oge.giris)
            }
        }
    }

    /** Durumlar adımından arkadaş bulmaya geçer. Steam'liyse Steam arkadaşları hemen aranır. */
    fun durumlariBitir() {
        val adim = _ekran.value as? OnboardingAdimi.Durumlar ?: return
        if (!adim.bitirilebilir) return
        _ekran.value = OnboardingAdimi.Arkadaslar(steamli = adim.steamli, araniyor = adim.steamli)
        viewModelScope.launch {
            val davetKodu = runCatching { api.davetKodlarim().firstOrNull { !it.kullanildi }?.kod }.getOrNull()
            _ekran.update { if (it is OnboardingAdimi.Arkadaslar) it.copy(davetKodu = davetKodu) else it }
        }
        if (adim.steamli) arkadasAra { api.steamdenBul() }
    }

    /** [numaralar] null ise rehber izni verilmedi. Numaralar burada hash'lenir; açık hali cihazdan çıkmaz. */
    fun rehberdenBul(numaralar: List<String>?) {
        if (numaralar == null) {
            _ekran.update { if (it is OnboardingAdimi.Arkadaslar) it.copy(rehberIzniYok = true) else it }
            return
        }
        val hashler = numaralar.mapNotNull(TelefonNumarasi::hash).distinct().take(TelefonNumarasi.EN_FAZLA_HASH)
        arkadasAra(rehber = true) { if (hashler.isEmpty()) emptyList() else api.rehberdenBul(hashler) }
    }

    private fun arkadasAra(rehber: Boolean = false, ara: suspend () -> List<BulunanKisi>) {
        _ekran.update { if (it is OnboardingAdimi.Arkadaslar) it.copy(araniyor = true, rehberIzniYok = false) else it }
        viewModelScope.launch {
            val bulunanlar = try {
                ara()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Gizli Steam arkadaş listesi ya da bağlantı hatası: bulunan yok sayılır, adım sürer.
                emptyList()
            }
            _ekran.update { mevcut ->
                if (mevcut !is OnboardingAdimi.Arkadaslar) return@update mevcut
                val bilinenler = mevcut.kisiler.map { it.kullanici.kullaniciId }.toSet()
                mevcut.copy(
                    kisiler = mevcut.kisiler + bulunanlar.filter { it.kullanici.kullaniciId !in bilinenler },
                    araniyor = false,
                    rehberTarandi = mevcut.rehberTarandi || rehber,
                )
            }
        }
    }

    /** Kendi numaranı ekle ki arkadaşların seni bulsun; yalnızca hash'i gider. */
    fun numaramiKaydet(numara: String) {
        val hash = TelefonNumarasi.hash(numara) ?: return
        viewModelScope.launch {
            val kaydedildi = runCatching { api.telefonumuKaydet(hash) }.isSuccess
            _ekran.update { if (it is OnboardingAdimi.Arkadaslar) it.copy(numaraKaydedildi = kaydedildi) else it }
        }
    }

    fun takipEt(kullaniciAdi: String) = takipEt(listOf(kullaniciAdi)) { api.takipEt(kullaniciAdi) }

    fun hepsiniTakipEt() {
        val adlar = (_ekran.value as? OnboardingAdimi.Arkadaslar)?.takipEdilmeyenler?.map { it.kullanici.kullaniciAdi } ?: return
        if (adlar.isNotEmpty()) takipEt(adlar) { api.topluTakip(adlar) }
    }

    /** Takip hemen gösterilir; başarısızsa geri alınır. */
    private fun takipEt(adlar: List<String>, istek: suspend () -> Unit) {
        fun isaretle(takipEdiyorum: Boolean) = _ekran.update { mevcut ->
            if (mevcut !is OnboardingAdimi.Arkadaslar) return@update mevcut
            mevcut.copy(kisiler = mevcut.kisiler.map { if (it.kullanici.kullaniciAdi in adlar) it.copy(takipEdiyorum = takipEdiyorum) else it })
        }
        isaretle(true)
        viewModelScope.launch {
            try {
                istek()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isaretle(false)
            }
        }
    }

    /** Arkadaş bulma adımı atlanabilir; ardından bildirim izni adımı gelir. */
    fun arkadaslariBitir() {
        if (_ekran.value is OnboardingAdimi.Arkadaslar) _ekran.value = OnboardingAdimi.BildirimIzni()
    }

    /** İzin verilsin ya da verilmesin onboarding biter. */
    fun bitir() {
        val adim = _ekran.value as? OnboardingAdimi.BildirimIzni ?: return
        if (adim.bitiriliyor) return
        _ekran.value = adim.copy(bitiriliyor = true, hata = false)
        viewModelScope.launch {
            try {
                api.onboardingiTamamla()
                hesabiYenile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { if (it is OnboardingAdimi.BildirimIzni) it.copy(bitiriliyor = false, hata = true) else it }
            }
        }
    }
}

sealed interface SteamAyari {
    data object Yukleniyor : SteamAyari
    data class BagliDegil(val girdi: String = "", val gonderiliyor: Boolean = false, val hata: SteamHatasi? = null) : SteamAyari
    data class Bagli(val steamId: String, val iceAktarilan: Int? = null, val kaldiriliyor: Boolean = false) : SteamAyari
    data object Hata : SteamAyari
}

/** Ayarlarda Steam bağlantısı: sonradan bağlama ve bağlantıyı kaldırma. */
class SteamAyariViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow<SteamAyari>(SteamAyari.Yukleniyor)
    val ekran: StateFlow<SteamAyari> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        _ekran.value = SteamAyari.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                api.steamBaglantisi()?.let { SteamAyari.Bagli(it.steamId) } ?: SteamAyari.BagliDegil()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SteamAyari.Hata
            }
        }
    }

    fun girdiDegisti(girdi: String) {
        _ekran.update { if (it is SteamAyari.BagliDegil) it.copy(girdi = girdi, hata = null) else it }
    }

    fun bagla() {
        val durum = _ekran.value as? SteamAyari.BagliDegil ?: return
        if (durum.girdi.isBlank() || durum.gonderiliyor) return
        _ekran.value = durum.copy(gonderiliyor = true, hata = null)
        viewModelScope.launch {
            _ekran.value = try {
                val sonuc = api.steamBagla(durum.girdi.trim())
                SteamAyari.Bagli(sonuc.baglanti.steamId, iceAktarilan = sonuc.iceAktarilanSayisi)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                durum.copy(gonderiliyor = false, hata = e.steamHatasi())
            }
        }
    }

    fun kaldir() {
        val durum = _ekran.value as? SteamAyari.Bagli ?: return
        if (durum.kaldiriliyor) return
        _ekran.value = durum.copy(kaldiriliyor = true)
        viewModelScope.launch {
            _ekran.value = try {
                api.steamBaglantisiniKaldir()
                SteamAyari.BagliDegil()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                durum
            }
        }
    }
}
