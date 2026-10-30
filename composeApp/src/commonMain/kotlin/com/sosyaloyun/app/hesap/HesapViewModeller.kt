package com.sosyaloyun.app.hesap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciAdiDurumu
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilKurallari
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bağlantı hatalarını [baglantiYok]'a, iptali yukarıya iletir; backend hataları olduğu gibi fırlar. */
private suspend fun <T> baglantiyla(baglantiYok: () -> T, blok: suspend () -> T): T = try {
    blok()
} catch (e: CancellationException) {
    throw e
} catch (e: ApiHatasiIstisnasi) {
    throw e
} catch (e: Exception) {
    baglantiYok()
}

// ── Onboarding sırası ──────────────────────────────────────────────────────

sealed interface HesapEkrani {
    data object Yukleniyor : HesapEkrani
    data object DavetKodu : HesapEkrani
    data object Profil : HesapEkrani

    /** Steam bağlama (atlanabilir) ve ilk durumlar. */
    data object Onboarding : HesapEkrani
    /** [aktifFaz]: açık faz (#46); kapalı fazın ekran girişleri gösterilmez. */
    data class Hazir(val profil: KullaniciOzeti, val aktifFaz: Faz = Faz.FAZ_1) : HesapEkrani

    /** Oturum sunucuda kapanmış (ör. başka cihazdan çıkış); uygulama giriş ekranına döner. */
    data object OturumGecersiz : HesapEkrani
    data object Hata : HesapEkrani
}

/** Girişten sonra hangi onboarding adımının gösterileceğine `GET /ben`'e bakarak karar verir. */
class HesapViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow<HesapEkrani>(HesapEkrani.Yukleniyor)
    val ekran: StateFlow<HesapEkrani> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        _ekran.value = HesapEkrani.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                baglantiyla({ HesapEkrani.Hata }) {
                    val ben = api.ben()
                    when {
                        !ben.davetKabulEdildi -> HesapEkrani.DavetKodu
                        ben.profil == null -> HesapEkrani.Profil
                        !ben.onboardingTamamlandi -> HesapEkrani.Onboarding
                        else -> HesapEkrani.Hazir(ben.profil!!, ben.aktifFaz)
                    }
                }
            } catch (e: ApiHatasiIstisnasi) {
                if (e.kod == HataKodu.OTURUM_GEREKLI) HesapEkrani.OturumGecersiz else HesapEkrani.Hata
            }
        }
    }
}

// ── Davet kodu adımı ───────────────────────────────────────────────────────

enum class DavetKoduHatasi { GECERSIZ, KULLANILMIS, BAGLANTI }

data class DavetKoduDurumu(val kod: String = "", val gonderiliyor: Boolean = false, val hata: DavetKoduHatasi? = null) {
    val gonderilebilir: Boolean get() = kod.isNotBlank() && !gonderiliyor
}

/** [hesabiYenile]: adım bitti ya da beklenmeyen bir backend hatası oldu (ör. oturum düştü); hesap yeniden yüklenir. */
class DavetKoduViewModel(private val api: ApiIstemcisi, private val hesabiYenile: () -> Unit) : ViewModel() {
    private val _ekran = MutableStateFlow(DavetKoduDurumu())
    val ekran: StateFlow<DavetKoduDurumu> = _ekran

    fun kodDegisti(kod: String) {
        _ekran.update { it.copy(kod = kod, hata = null) }
    }

    fun gonder() {
        if (!_ekran.value.gonderilebilir) return
        _ekran.update { it.copy(gonderiliyor = true, hata = null) }
        viewModelScope.launch {
            val hata = try {
                baglantiyla({ DavetKoduHatasi.BAGLANTI }) {
                    api.davetKoduKullan(_ekran.value.kod.trim())
                    null
                }
            } catch (e: ApiHatasiIstisnasi) {
                when (e.kod) {
                    HataKodu.DAVET_KODU_GECERSIZ -> DavetKoduHatasi.GECERSIZ
                    HataKodu.DAVET_KODU_KULLANILMIS -> DavetKoduHatasi.KULLANILMIS
                    else -> null
                }
            }
            _ekran.update { it.copy(gonderiliyor = false, hata = hata) }
            if (hata == null) hesabiYenile()
        }
    }
}

// ── Kullanıcı adı, görünen ad ve avatar adımı ──────────────────────────────

enum class Musaitlik { BILINMIYOR, KONTROL_EDILIYOR, MUSAIT, ALINMIS, GECERSIZ, BAGLANTI_YOK }

data class ProfilDurumu(
    val kullaniciAdi: String = "",
    val musaitlik: Musaitlik = Musaitlik.BILINMIYOR,
    val gorunenAd: String = "",
    val avatar: Avatar = Avatar.KUMANDA,
    val kaydediliyor: Boolean = false,
    val baglantiHatasi: Boolean = false,
    val biyografi: String = "",
    /** Onboarding değil, var olan profilin düzenlenmesi. */
    val duzenleme: Boolean = false,
) {
    val kaydedilebilir: Boolean
        get() = musaitlik == Musaitlik.MUSAIT && ProfilKurallari.gorunenAdGecerliMi(gorunenAd) && !kaydediliyor
}

/** [hesabiYenile]: adım bitti ya da beklenmeyen bir backend hatası oldu (ör. oturum düştü); hesap yeniden yüklenir. */
class ProfilViewModel(
    private val api: ApiIstemcisi,
    /** Düzenlenen profil; onboarding'de null. */
    mevcut: KullaniciOzeti?,
    mevcutBiyografi: String?,
    private val hesabiYenile: () -> Unit,
) : ViewModel() {
    constructor(api: ApiIstemcisi, hesabiYenile: () -> Unit) : this(api, null, null, hesabiYenile)

    private val _ekran = MutableStateFlow(
        mevcut?.let {
            ProfilDurumu(
                kullaniciAdi = it.kullaniciAdi,
                musaitlik = Musaitlik.MUSAIT,
                gorunenAd = it.gorunenAd,
                avatar = it.avatar,
                biyografi = mevcutBiyografi.orEmpty(),
                duzenleme = true,
            )
        } ?: ProfilDurumu(),
    )
    val ekran: StateFlow<ProfilDurumu> = _ekran
    private var musaitlikIsi: Job? = null

    /** Kural dışı ad hemen gösterilir; geçerli ad yazma durunca ([BEKLEME_MS]) sunucuya sorulur. */
    fun kullaniciAdiDegisti(ad: String) {
        musaitlikIsi?.cancel()
        val duzenlenmis = ProfilKurallari.kullaniciAdiniDuzenle(ad)
        if (!ProfilKurallari.kullaniciAdiGecerliMi(duzenlenmis)) {
            _ekran.update { it.copy(kullaniciAdi = ad, musaitlik = if (duzenlenmis.isEmpty()) Musaitlik.BILINMIYOR else Musaitlik.GECERSIZ) }
            return
        }
        _ekran.update { it.copy(kullaniciAdi = ad, musaitlik = Musaitlik.KONTROL_EDILIYOR) }
        musaitlikIsi = viewModelScope.launch {
            delay(BEKLEME_MS)
            val musaitlik = try {
                baglantiyla({ Musaitlik.BAGLANTI_YOK }) {
                    when (api.kullaniciAdiMusaitlik(duzenlenmis).durum) {
                        KullaniciAdiDurumu.MUSAIT -> Musaitlik.MUSAIT
                        KullaniciAdiDurumu.ALINMIS -> Musaitlik.ALINMIS
                        KullaniciAdiDurumu.GECERSIZ -> Musaitlik.GECERSIZ
                    }
                }
            } catch (e: ApiHatasiIstisnasi) {
                Musaitlik.BAGLANTI_YOK
            }
            _ekran.update { it.copy(musaitlik = musaitlik) }
        }
    }

    fun gorunenAdDegisti(ad: String) {
        _ekran.update { it.copy(gorunenAd = ad) }
    }

    fun avatarSecildi(avatar: Avatar) {
        _ekran.update { it.copy(avatar = avatar) }
    }

    fun biyografiDegisti(metin: String) {
        _ekran.update { it.copy(biyografi = metin.take(ProfilKurallari.BIYOGRAFI_EN_FAZLA)) }
    }

    fun kaydet() {
        val durum = _ekran.value
        if (!durum.kaydedilebilir) return
        _ekran.update { it.copy(kaydediliyor = true, baglantiHatasi = false) }
        viewModelScope.launch {
            val istek = ProfilIstegi(
                kullaniciAdi = ProfilKurallari.kullaniciAdiniDuzenle(durum.kullaniciAdi),
                gorunenAd = durum.gorunenAd.trim(),
                avatar = durum.avatar,
                biyografi = durum.biyografi.trim().ifEmpty { null },
            )
            val sonuc: Musaitlik? = try {
                baglantiyla({ Musaitlik.BAGLANTI_YOK }) {
                    api.profilKaydet(istek)
                    null
                }
            } catch (e: ApiHatasiIstisnasi) {
                when (e.kod) {
                    HataKodu.KULLANICI_ADI_ALINMIS -> Musaitlik.ALINMIS
                    HataKodu.KULLANICI_ADI_GECERSIZ -> Musaitlik.GECERSIZ
                    else -> null
                }
            }
            when (sonuc) {
                null -> {
                    _ekran.update { it.copy(kaydediliyor = false) }
                    hesabiYenile()
                }
                Musaitlik.BAGLANTI_YOK -> _ekran.update { it.copy(kaydediliyor = false, baglantiHatasi = true) }
                else -> _ekran.update { it.copy(kaydediliyor = false, musaitlik = sonuc) }
            }
        }
    }

    private companion object {
        const val BEKLEME_MS = 300L
    }
}

// ── Davet kodlarım ─────────────────────────────────────────────────────────

sealed interface DavetKodlarimEkrani {
    data object Yukleniyor : DavetKodlarimEkrani
    data class Hazir(val kodlar: List<DavetKodu>) : DavetKodlarimEkrani
    data object Hata : DavetKodlarimEkrani
}

class DavetKodlarimViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow<DavetKodlarimEkrani>(DavetKodlarimEkrani.Yukleniyor)
    val ekran: StateFlow<DavetKodlarimEkrani> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        _ekran.value = DavetKodlarimEkrani.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                baglantiyla({ DavetKodlarimEkrani.Hata }) { DavetKodlarimEkrani.Hazir(api.davetKodlarim()) }
            } catch (e: ApiHatasiIstisnasi) {
                DavetKodlarimEkrani.Hata
            }
        }
    }
}
