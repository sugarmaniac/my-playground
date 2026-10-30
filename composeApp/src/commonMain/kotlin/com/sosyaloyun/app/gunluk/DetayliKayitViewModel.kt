package com.sosyaloyun.app.gunluk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukKurallari
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class KayitHatasi { SURE, TARIH, BAGLANTI }

data class KayitFormu(
    val oyunAdi: String = "",
    val platformlar: List<String> = emptyList(),
    val platform: String? = null,
    val baslama: LocalDate? = null,
    val bitis: LocalDate? = null,
    /** Kullanıcının yazdığı saat ("2", "1,5"); kaydederken dakikaya çevrilir. */
    val sureSaat: String = "",
    val inceleme: String = "",
    val spoiler: Boolean = false,
    val gorunurluk: Gorunurluk = Gorunurluk.HERKES,
    val sessiz: Boolean = false,
    val duzenleme: Boolean = false,
    val kaydediliyor: Boolean = false,
    val hata: KayitHatasi? = null,
)

sealed interface DetayliKayitEkrani {
    data object Yukleniyor : DetayliKayitEkrani
    data class Form(val form: KayitFormu) : DetayliKayitEkrani
    data object Hata : DetayliKayitEkrani
}

/**
 * Detaylı kayıt ekranı: yeni günlük kaydı ([kayitId] null) ya da var olanı düzenleme. Yeni kayıtta
 * kullanıcının son seçtiği platform, oyunda varsa önerilir. Puan buraya girilmez, kütüphane
 * girişinden kopyalanır.
 */
class DetayliKayitViewModel(
    private val api: ApiIstemcisi,
    private val oyunId: String,
    private val kayitId: String?,
    private val tamamlandi: () -> Unit,
) : ViewModel() {
    private val _ekran = MutableStateFlow<DetayliKayitEkrani>(DetayliKayitEkrani.Yukleniyor)
    val ekran: StateFlow<DetayliKayitEkrani> = _ekran

    init {
        viewModelScope.launch {
            _ekran.value = try {
                val detay = api.oyunDetayi(oyunId)
                val platformlar = detay.oyun.platformlar
                if (kayitId == null) {
                    DetayliKayitEkrani.Form(
                        KayitFormu(oyunAdi = detay.oyun.ad, platformlar = platformlar, platform = detay.sonPlatform?.takeIf { it in platformlar }),
                    )
                } else {
                    // Kayıt bu arada silinmişse boş bir "yeni kayıt" formu gösterilmez.
                    detay.gunlukKayitlarim.firstOrNull { it.id == kayitId }
                        ?.let { DetayliKayitEkrani.Form(it.formu(detay.oyun.ad, platformlar)) }
                        ?: DetayliKayitEkrani.Hata
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetayliKayitEkrani.Hata
            }
        }
    }

    fun platformSecildi(platform: String) = degistir { it.copy(platform = if (it.platform == platform) null else platform) }

    fun baslamaSecildi(tarih: LocalDate?) = degistir { it.copy(baslama = tarih) }

    fun bitisSecildi(tarih: LocalDate?) = degistir { it.copy(bitis = tarih) }

    fun sureDegisti(saat: String) = degistir { it.copy(sureSaat = saat) }

    fun incelemeDegisti(metin: String) = degistir { it.copy(inceleme = metin.take(GunlukKurallari.INCELEME_EN_FAZLA)) }

    fun spoilerDegisti() = degistir { it.copy(spoiler = !it.spoiler) }

    fun gorunurlukSecildi(gorunurluk: Gorunurluk) = degistir { it.copy(gorunurluk = gorunurluk) }

    fun sessizDegisti() = degistir { it.copy(sessiz = !it.sessiz) }

    fun kaydet() {
        val form = (_ekran.value as? DetayliKayitEkrani.Form)?.form ?: return
        if (form.kaydediliyor) return
        val sure = if (form.sureSaat.isBlank()) null else saattenDakikaya(form.sureSaat)
        val istek = GunlukKaydiIstegi(
            platform = form.platform,
            baslama = form.baslama,
            bitis = form.bitis,
            sureDakika = sure,
            inceleme = form.inceleme.trim().ifEmpty { null },
            spoiler = form.spoiler,
            gorunurluk = form.gorunurluk,
            sessiz = form.sessiz,
        )
        val hata = when {
            form.sureSaat.isNotBlank() && (sure == null || sure > GunlukKurallari.SURE_EN_FAZLA_DAKIKA) -> KayitHatasi.SURE
            form.baslama != null && form.bitis != null && form.bitis < form.baslama -> KayitHatasi.TARIH
            else -> null
        }
        if (hata != null) {
            formuGuncelle { it.copy(hata = hata) }
            return
        }
        islem { if (kayitId == null) api.gunlukKaydiGir(oyunId, istek) else api.gunlukKaydiDuzenle(kayitId, istek) }
    }

    fun sil() {
        val id = kayitId ?: return
        islem { api.gunlukKaydiSil(id) }
    }

    private fun islem(blok: suspend () -> Unit) {
        formuGuncelle { it.copy(kaydediliyor = true, hata = null) }
        viewModelScope.launch {
            try {
                blok()
                formuGuncelle { it.copy(kaydediliyor = false) }
                tamamlandi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                formuGuncelle { it.copy(kaydediliyor = false, hata = KayitHatasi.BAGLANTI) }
            }
        }
    }

    /** Kullanıcının değişikliği; önceki hata temizlenir. */
    private fun degistir(donustur: (KayitFormu) -> KayitFormu) = formuGuncelle { donustur(it).copy(hata = null) }

    private fun formuGuncelle(donustur: (KayitFormu) -> KayitFormu) {
        _ekran.update { if (it is DetayliKayitEkrani.Form) it.copy(form = donustur(it.form)) else it }
    }

    private fun GunlukKaydi.formu(oyunAdi: String, platformlar: List<String>) = KayitFormu(
        oyunAdi = oyunAdi,
        platformlar = (platformlar + listOfNotNull(platform)).distinct(),
        platform = platform,
        baslama = baslama,
        bitis = bitis,
        sureSaat = sureDakika?.let(::dakikadanSaate).orEmpty(),
        inceleme = inceleme.orEmpty(),
        spoiler = spoiler,
        gorunurluk = gorunurluk,
        sessiz = sessiz,
        duzenleme = true,
    )
}

/** "2", "1,5" ya da "1.5" saat → dakika; sayı değilse ya da negatifse null. */
fun saattenDakikaya(metin: String): Int? {
    val saat = metin.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (saat < 0 || saat.isNaN()) return null
    return (saat * 60).roundToInt()
}

/** 150 → "2,5"; 120 → "2"; en fazla iki ondalık. */
fun dakikadanSaate(dakika: Int): String {
    val yuzdeBirSaat = (dakika * 100 + 30) / 60
    val tam = yuzdeBirSaat / 100
    val kesir = (yuzdeBirSaat % 100).toString().padStart(2, '0').trimEnd('0')
    return if (kesir.isEmpty()) tam.toString() else "$tam,$kesir"
}
