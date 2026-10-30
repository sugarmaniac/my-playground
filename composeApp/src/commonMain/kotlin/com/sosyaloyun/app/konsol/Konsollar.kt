package com.sosyaloyun.app.konsol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.konsol_bagla
import com.sosyaloyun.app.resources.konsol_bagli
import com.sosyaloyun.app.resources.konsol_baslik
import com.sosyaloyun.app.resources.konsol_hata_baska
import com.sosyaloyun.app.resources.konsol_hata_bulunamadi
import com.sosyaloyun.app.resources.konsol_hata_gizli
import com.sosyaloyun.app.resources.konsol_hata_kullanilamiyor
import com.sosyaloyun.app.resources.konsol_hesap_playstation
import com.sosyaloyun.app.resources.konsol_hesap_xbox
import com.sosyaloyun.app.resources.konsol_kaldir
import com.sosyaloyun.app.resources.konsol_sonuc
import com.sosyaloyun.app.resources.konsol_xbox
import com.sosyaloyun.app.resources.konsol_playstation
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KonsolAgi
import com.sosyaloyun.shared.KonsolBaglantisi
import com.sosyaloyun.shared.KonsolIceAktarmaSonucu
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Bağlama hatası; sunucunun hata koduna göre (#57). */
enum class KonsolHatasi { BULUNAMADI, GIZLI, BASKA_KULLANICIDA, KULLANILAMIYOR }

/** Bir ağın satırı: [girdi] yazılan hesap adı; [sonuc] son içe aktarmanın özeti. */
data class KonsolSatiri(
    val baglanti: KonsolBaglantisi,
    val girdi: String = "",
    val gonderiliyor: Boolean = false,
    val hata: KonsolHatasi? = null,
    val sonuc: KonsolIceAktarmaSonucu? = null,
)

/** Ayarlardaki Xbox/PlayStation bölümü (Faz 3, #57); sunucuda anahtarı olan ağ yoksa ya da yüklenemezse gösterilmez. */
class KonsolViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _satirlar = MutableStateFlow<List<KonsolSatiri>>(emptyList())
    val satirlar: StateFlow<List<KonsolSatiri>> = _satirlar

    init {
        viewModelScope.launch {
            try {
                _satirlar.value = api.konsollar().baglantilar.map { KonsolSatiri(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bölüm gösterilmez.
            }
        }
    }

    fun girdiDegisti(ag: KonsolAgi, girdi: String) = guncelle(ag) { it.copy(girdi = girdi, hata = null) }

    fun bagla(ag: KonsolAgi) {
        val satir = _satirlar.value.firstOrNull { it.baglanti.ag == ag } ?: return
        if (satir.gonderiliyor || satir.girdi.isBlank()) return
        guncelle(ag) { it.copy(gonderiliyor = true, hata = null, sonuc = null) }
        viewModelScope.launch {
            try {
                val sonuc = api.konsolBagla(ag, satir.girdi.trim())
                guncelle(ag) { KonsolSatiri(sonuc.baglanti, sonuc = sonuc) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val hata = when ((e as? ApiHatasiIstisnasi)?.kod) {
                    HataKodu.KONSOL_HESABI_BULUNAMADI -> KonsolHatasi.BULUNAMADI
                    HataKodu.KONSOL_PROFILI_GIZLI -> KonsolHatasi.GIZLI
                    HataKodu.KONSOL_HESABI_BASKA_KULLANICIDA -> KonsolHatasi.BASKA_KULLANICIDA
                    else -> KonsolHatasi.KULLANILAMIYOR
                }
                guncelle(ag) { it.copy(gonderiliyor = false, hata = hata) }
            }
        }
    }

    /** Bağlantı hemen kalkar; sunucu reddederse geri gelir. İçe aktarılan oyunlar kütüphanede kalır. */
    fun kaldir(ag: KonsolAgi) {
        val onceki = _satirlar.value.firstOrNull { it.baglanti.ag == ag } ?: return
        guncelle(ag) { KonsolSatiri(KonsolBaglantisi(ag)) }
        viewModelScope.launch {
            try {
                api.konsolKaldir(ag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                guncelle(ag) { onceki.copy(hata = KonsolHatasi.KULLANILAMIYOR) }
            }
        }
    }

    private fun guncelle(ag: KonsolAgi, donustur: (KonsolSatiri) -> KonsolSatiri) =
        _satirlar.update { liste -> liste.map { if (it.baglanti.ag == ag) donustur(it) else it } }
}

@Composable
fun KonsolBolumu(viewModel: KonsolViewModel) {
    val satirlar by viewModel.satirlar.collectAsStateWithLifecycle()
    if (satirlar.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.konsol_baslik), style = MaterialTheme.typography.titleMedium)
        satirlar.forEach { satir ->
            val ag = satir.baglanti.ag
            val agAdi = stringResource(if (ag == KonsolAgi.XBOX) Res.string.konsol_xbox else Res.string.konsol_playstation)
            val hesap = satir.baglanti.hesap
            if (hesap != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.konsol_bagli, agAdi, hesap), modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.kaldir(ag) }) { Text(stringResource(Res.string.konsol_kaldir)) }
                }
            } else {
                OutlinedTextField(
                    satir.girdi,
                    { viewModel.girdiDegisti(ag, it) },
                    label = { Text(stringResource(if (ag == KonsolAgi.XBOX) Res.string.konsol_hesap_xbox else Res.string.konsol_hesap_playstation)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !satir.gonderiliyor,
                )
                Button(onClick = { viewModel.bagla(ag) }, enabled = !satir.gonderiliyor && satir.girdi.isNotBlank()) {
                    Text(stringResource(Res.string.konsol_bagla, agAdi))
                }
            }
            satir.sonuc?.let { Text(stringResource(Res.string.konsol_sonuc, it.iceAktarilanSayisi, it.eslesmeyenSayisi), style = MaterialTheme.typography.bodySmall) }
            satir.hata?.let { hata ->
                Text(
                    stringResource(
                        when (hata) {
                            KonsolHatasi.BULUNAMADI -> Res.string.konsol_hata_bulunamadi
                            KonsolHatasi.GIZLI -> Res.string.konsol_hata_gizli
                            KonsolHatasi.BASKA_KULLANICIDA -> Res.string.konsol_hata_baska
                            KonsolHatasi.KULLANILAMIYOR -> Res.string.konsol_hata_kullanilamiyor
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
