package com.sosyaloyun.app.oneri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.kesif.turAdi
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.oneri_baslik
import com.sosyaloyun.app.resources.oneri_ilgilenmiyorum
import com.sosyaloyun.app.resources.oneri_neden_benzer
import com.sosyaloyun.app.resources.oneri_neden_platform
import com.sosyaloyun.app.resources.oneri_neden_populer
import com.sosyaloyun.app.resources.oneri_neden_tur
import com.sosyaloyun.shared.Oneri
import com.sosyaloyun.shared.OneriNedeni
import com.sosyaloyun.shared.Tur
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Keşif'teki "Sana önerilenler" (Faz 3, #56); boşsa ya da yüklenemezse gösterilmez. */
class OnerilerViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _oneriler = MutableStateFlow<List<Oneri>>(emptyList())
    val oneriler: StateFlow<List<Oneri>> = _oneriler

    init {
        viewModelScope.launch {
            _oneriler.value = try {
                api.oneriler().oneriler
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Öneri hemen kalkar; sunucu reddederse eski yerine geri döner. */
    fun ilgilenmiyorum(oyunId: String) {
        val sira = _oneriler.value.indexOfFirst { it.oyun.id == oyunId }
        if (sira < 0) return
        val oneri = _oneriler.value[sira]
        _oneriler.update { liste -> liste.filterNot { it.oyun.id == oyunId } }
        viewModelScope.launch {
            try {
                api.ilgilenmiyorum(oyunId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _oneriler.update { liste -> liste.toMutableList().apply { add(sira.coerceAtMost(size), oneri) } }
            }
        }
    }
}

@Composable
fun OnerilerBolumu(viewModel: OnerilerViewModel, oyunaGit: (String) -> Unit) {
    val oneriler by viewModel.oneriler.collectAsStateWithLifecycle()
    if (oneriler.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.oneri_baslik), style = MaterialTheme.typography.titleMedium)
        oneriler.forEach { oneri ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { oyunaGit(oneri.oyun.id) }.padding(vertical = 4.dp)) {
                    Text(oneri.oyun.ad, style = MaterialTheme.typography.bodyLarge)
                    Text(nedenMetni(oneri), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { viewModel.ilgilenmiyorum(oneri.oyun.id) }) { Text(stringResource(Res.string.oneri_ilgilenmiyorum)) }
            }
        }
    }
}

@Composable
private fun nedenMetni(oneri: Oneri): String = when (oneri.neden) {
    OneriNedeni.BENZER_KULLANICILAR -> stringResource(Res.string.oneri_neden_benzer)
    OneriNedeni.TUR -> stringResource(Res.string.oneri_neden_tur, oneri.ayrinti?.let { ad -> Tur.entries.firstOrNull { it.igdbAdi == ad }?.let { turAdi(it) } ?: ad }.orEmpty())
    OneriNedeni.PLATFORM -> stringResource(Res.string.oneri_neden_platform, oneri.ayrinti.orEmpty())
    OneriNedeni.POPULER -> stringResource(Res.string.oneri_neden_populer)
}
