package com.sosyaloyun.app.kurator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.liste.ListeOzetiSatiri
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.kurator_basvur
import com.sosyaloyun.app.resources.kurator_baslik
import com.sosyaloyun.app.resources.kurator_bekliyor
import com.sosyaloyun.app.resources.kurator_hata
import com.sosyaloyun.app.resources.kurator_listeleri
import com.sosyaloyun.app.resources.kurator_oldun
import com.sosyaloyun.app.resources.kurator_reddedildi
import com.sosyaloyun.app.resources.kurator_sart_gun
import com.sosyaloyun.app.resources.kurator_sart_inceleme
import com.sosyaloyun.app.resources.kurator_sart_takipci
import com.sosyaloyun.shared.KuratorBasvuruDurumu
import com.sosyaloyun.shared.KuratorDurumu
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.ListeOzeti
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Ayarlardaki küratörlük bölümü (Faz 2, #50): şartlar, başvuru ve durum. Yüklenemezse bölüm gösterilmez. */
data class KuratorEkrani(val durum: KuratorDurumu? = null, val gonderiliyor: Boolean = false, val hata: Boolean = false)

class KuratorViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow(KuratorEkrani())
    val ekran: StateFlow<KuratorEkrani> = _ekran

    init {
        viewModelScope.launch {
            try {
                _ekran.value = KuratorEkrani(api.kuratorDurumu())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bölüm gösterilmez.
            }
        }
    }

    fun basvur() {
        val ekran = _ekran.value
        if (ekran.gonderiliyor || ekran.durum?.basvurabilir != true) return
        _ekran.value = ekran.copy(gonderiliyor = true, hata = false)
        viewModelScope.launch {
            try {
                val yeni = api.kuratorBasvur()
                _ekran.value = KuratorEkrani(yeni)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(gonderiliyor = false, hata = true) }
            }
        }
    }
}

@Composable
fun KuratorBolumu(viewModel: KuratorViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    val durum = ekran.durum ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.kurator_baslik), style = MaterialTheme.typography.titleMedium)
        when {
            durum.kurator -> Text(stringResource(Res.string.kurator_oldun))
            durum.basvuru == KuratorBasvuruDurumu.BEKLIYOR -> Text(stringResource(Res.string.kurator_bekliyor))
            else -> {
                if (durum.basvuru == KuratorBasvuruDurumu.REDDEDILDI) Text(stringResource(Res.string.kurator_reddedildi), style = MaterialTheme.typography.bodySmall)
                durum.sartlar.forEach { sart ->
                    val metin = when (sart.tur) {
                        KuratorSartTuru.INCELEME -> Res.string.kurator_sart_inceleme
                        KuratorSartTuru.TAKIPCI -> Res.string.kurator_sart_takipci
                        KuratorSartTuru.HESAP_YASI_GUN -> Res.string.kurator_sart_gun
                    }
                    Text((if (sart.saglandi) "✓ " else "✗ ") + stringResource(metin, sart.mevcut, sart.gereken), style = MaterialTheme.typography.bodySmall)
                }
                if (ekran.hata) Text(stringResource(Res.string.kurator_hata), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::basvur, enabled = durum.basvurabilir && !ekran.gonderiliyor) { Text(stringResource(Res.string.kurator_basvur)) }
            }
        }
    }
}

/** Keşifteki "Küratörlerden listeler" (Faz 2, #50); boşsa ya da yüklenemezse gösterilmez. */
class KuratorListeleriViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _listeler = MutableStateFlow<List<ListeOzeti>>(emptyList())
    val listeler: StateFlow<List<ListeOzeti>> = _listeler

    init {
        viewModelScope.launch {
            _listeler.value = try {
                api.kuratorListeleri().listeler
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Composable
fun KuratorListeleriBolumu(viewModel: KuratorListeleriViewModel, listeyeGit: (String) -> Unit) {
    val listeler by viewModel.listeler.collectAsStateWithLifecycle()
    if (listeler.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.kurator_listeleri), style = MaterialTheme.typography.titleMedium)
        listeler.forEach { ListeOzetiSatiri(it) { listeyeGit(it.id) } }
    }
}
