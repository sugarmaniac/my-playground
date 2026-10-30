package com.sosyaloyun.app.oneri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.kesif.turAdi
import com.sosyaloyun.app.onbellek.BosOnbellek
import com.sosyaloyun.app.onbellek.OyunOzetiBellegi
import com.sosyaloyun.app.onbellek.YerelOnbellek
import com.sosyaloyun.app.onbellek.okuNesne
import com.sosyaloyun.app.onbellek.yazNesne
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.oneri_baslik
import com.sosyaloyun.app.resources.oneri_ilgilenmiyorum
import com.sosyaloyun.app.resources.oneri_neden_benzer
import com.sosyaloyun.app.resources.oneri_neden_platform
import com.sosyaloyun.app.resources.oneri_neden_populer
import com.sosyaloyun.app.resources.oneri_neden_tur
import com.sosyaloyun.app.tema.Iskelet
import com.sosyaloyun.app.tema.Renk
import com.sosyaloyun.shared.Oneri
import com.sosyaloyun.shared.OneriListesi
import com.sosyaloyun.shared.OneriNedeni
import com.sosyaloyun.shared.Tur
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Keşif'teki "Sana önerilenler" (Faz 3, #56); boşsa ya da yüklenemezse gösterilmez.
 * [oneriler] null iken yükleniyor: bölüm sabit yükseklikte iskelet gösterir, veri gelince liste kaymaz (#61).
 * Son öneriler cihazda saklanır; açılışta o gösterilir, arkadan tazelenir.
 */
class OnerilerViewModel(private val api: ApiIstemcisi, private val onbellek: YerelOnbellek = BosOnbellek) : ViewModel() {
    private val _oneriler = MutableStateFlow(onbellek.okuNesne<OneriListesi>(ONBELLEK_ANAHTARI)?.oneriler)
    val oneriler: StateFlow<List<Oneri>?> = _oneriler

    init {
        viewModelScope.launch {
            try {
                val liste = api.oneriler()
                OyunOzetiBellegi.ekle(liste.oneriler.map { it.oyun })
                onbellek.yazNesne(ONBELLEK_ANAHTARI, liste)
                _oneriler.value = liste.oneriler
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Saklı öneriler gösteriliyorsa onlar kalır.
                if (_oneriler.value == null) _oneriler.value = emptyList()
            }
        }
    }

    /** Öneri hemen kalkar; sunucu reddederse eski yerine geri döner. */
    fun ilgilenmiyorum(oyunId: String) {
        val mevcut = _oneriler.value ?: return
        val sira = mevcut.indexOfFirst { it.oyun.id == oyunId }
        if (sira < 0) return
        val oneri = mevcut[sira]
        _oneriler.update { liste -> liste?.filterNot { it.oyun.id == oyunId } }
        viewModelScope.launch {
            try {
                api.ilgilenmiyorum(oyunId)
                _oneriler.value?.let { onbellek.yazNesne(ONBELLEK_ANAHTARI, OneriListesi(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _oneriler.update { liste -> liste?.toMutableList()?.apply { add(sira.coerceAtMost(size), oneri) } }
            }
        }
    }

    private companion object {
        const val ONBELLEK_ANAHTARI = "oneriler"
    }
}

@Composable
fun OnerilerBolumu(viewModel: OnerilerViewModel, oyunaGit: (String) -> Unit) {
    val oneriler by viewModel.oneriler.collectAsStateWithLifecycle()
    val liste = oneriler
    if (liste != null && liste.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.oneri_baslik), style = MaterialTheme.typography.titleMedium)
        if (liste == null) {
            repeat(ISKELET_SATIRI) { Iskelet(Modifier.fillMaxWidth().height(58.dp)) }
        } else {
            liste.forEach { oneri ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.weight(1f).clickable { oyunaGit(oneri.oyun.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Kapak(oneri.oyun, Modifier.size(width = 44.dp, height = 58.dp), kucuk = true)
                        Column(Modifier.weight(1f)) {
                            Text(oneri.oyun.ad, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nedenMetni(oneri), style = MaterialTheme.typography.bodySmall, color = Renk.Soluk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = { viewModel.ilgilenmiyorum(oneri.oyun.id) }) {
                        Text(stringResource(Res.string.oneri_ilgilenmiyorum), color = Renk.Soluk, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private const val ISKELET_SATIRI = 3

@Composable
private fun nedenMetni(oneri: Oneri): String = when (oneri.neden) {
    OneriNedeni.BENZER_KULLANICILAR -> stringResource(Res.string.oneri_neden_benzer)
    OneriNedeni.TUR -> stringResource(Res.string.oneri_neden_tur, oneri.ayrinti?.let { ad -> Tur.entries.firstOrNull { it.igdbAdi == ad }?.let { turAdi(it) } ?: ad }.orEmpty())
    OneriNedeni.PLATFORM -> stringResource(Res.string.oneri_neden_platform, oneri.ayrinti.orEmpty())
    OneriNedeni.POPULER -> stringResource(Res.string.oneri_neden_populer)
}
