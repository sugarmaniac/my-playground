package com.sosyaloyun.app.puan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.oyun.puanMetni
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.puan_azalanlar
import com.sosyaloyun.app.resources.puan_artanlar
import com.sosyaloyun.app.resources.puan_degisimi_baslik
import com.sosyaloyun.app.resources.puan_gecmisi_baslik
import com.sosyaloyun.app.resources.puan_gecmisi_ozet
import com.sosyaloyun.shared.HaftalikPuanDegisimi
import com.sosyaloyun.shared.PuanDegisimi
import com.sosyaloyun.shared.PuanHaftasi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Oyun detayındaki puan grafiği (Faz 2, #51); en az iki puanlı hafta yoksa ya da yüklenemezse gösterilmez. */
class PuanGecmisiViewModel(private val api: ApiIstemcisi, oyunId: String) : ViewModel() {
    private val _haftalar = MutableStateFlow<List<PuanHaftasi>>(emptyList())
    val haftalar: StateFlow<List<PuanHaftasi>> = _haftalar

    init {
        viewModelScope.launch {
            _haftalar.value = try {
                api.puanGecmisi(oyunId).haftalar.takeIf { gecmis -> gecmis.count { it.puan != null } >= 2 }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Composable
fun PuanGecmisiBolumu(viewModel: PuanGecmisiViewModel) {
    val haftalar by viewModel.haftalar.collectAsStateWithLifecycle()
    if (haftalar.isEmpty()) return
    val son = haftalar.last { it.puan != null }
    val ilk = haftalar.first { it.puan != null }
    val ozet = stringResource(Res.string.puan_gecmisi_ozet, puanMetni(ilk.puan!!), puanMetni(son.puan!!))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.puan_gecmisi_baslik), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // Doğrudan etiket: yalnızca en son değer.
            Text(puanMetni(son.puan!!), style = MaterialTheme.typography.labelLarge)
        }
        PuanCizgisi(haftalar, Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = ozet })
        Text(ozet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tek seri: 2px çizgi, 8px noktalar; puansız haftalar çizgiyi böler. Y ekseni puan ölçeğinin tamamı (0–10). */
@Composable
private fun PuanCizgisi(haftalar: List<PuanHaftasi>, modifier: Modifier) {
    val cizgi = MaterialTheme.colorScheme.primary
    val izgara = MaterialTheme.colorScheme.outlineVariant
    val yuzey = MaterialTheme.colorScheme.surface
    Canvas(modifier.padding(vertical = 6.dp)) {
        val adim = if (haftalar.size > 1) size.width / (haftalar.size - 1) else 0f
        fun nokta(i: Int, puan: Double) = Offset(i * adim, size.height * (1f - (puan / 10.0).toFloat()))
        // Yalnızca orta çizgi (★ 2,5) silik kılavuz olarak.
        drawLine(izgara, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        haftalar.zipWithNext().forEachIndexed { i, (a, b) ->
            if (a.puan != null && b.puan != null) drawLine(cizgi, nokta(i, a.puan!!), nokta(i + 1, b.puan!!), strokeWidth = 2.dp.toPx())
        }
        haftalar.forEachIndexed { i, hafta ->
            val puan = hafta.puan ?: return@forEachIndexed
            drawCircle(yuzey, radius = 5.dp.toPx(), center = nokta(i, puan))
            drawCircle(cizgi, radius = 4.dp.toPx(), center = nokta(i, puan))
        }
        // Yüzey halkası işaretleri çizgiden ayırır; aynı renkte ince kontur noktayı belirginleştirir.
        haftalar.forEachIndexed { i, hafta ->
            val puan = hafta.puan ?: return@forEachIndexed
            drawCircle(cizgi, radius = 4.dp.toPx(), center = nokta(i, puan), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Keşif'teki "Bu hafta puanı en çok değişen oyunlar" kartı (Faz 2, #51); boşsa ya da yüklenemezse gösterilmez. */
class PuanDegisimiViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _degisim = MutableStateFlow(HaftalikPuanDegisimi(emptyList(), emptyList()))
    val degisim: StateFlow<HaftalikPuanDegisimi> = _degisim

    init {
        viewModelScope.launch {
            try {
                _degisim.value = api.puanDegisimi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kart gösterilmez.
            }
        }
    }
}

@Composable
fun PuanDegisimiBolumu(viewModel: PuanDegisimiViewModel, oyunaGit: (String) -> Unit) {
    val degisim by viewModel.degisim.collectAsStateWithLifecycle()
    if (degisim.artanlar.isEmpty() && degisim.azalanlar.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.puan_degisimi_baslik), style = MaterialTheme.typography.titleMedium)
        if (degisim.artanlar.isNotEmpty()) {
            Text(stringResource(Res.string.puan_artanlar), style = MaterialTheme.typography.labelLarge)
            degisim.artanlar.forEach { DegisimSatiri(it, oyunaGit) }
        }
        if (degisim.azalanlar.isNotEmpty()) {
            Text(stringResource(Res.string.puan_azalanlar), style = MaterialTheme.typography.labelLarge)
            degisim.azalanlar.forEach { DegisimSatiri(it, oyunaGit) }
        }
    }
}

@Composable
private fun DegisimSatiri(degisim: PuanDegisimi, oyunaGit: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { oyunaGit(degisim.oyun.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(degisim.oyun.ad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("${puanMetni(degisim.onceki)} → ${puanMetni(degisim.simdiki)}  ${farkMetni(degisim.fark)}", style = MaterialTheme.typography.bodySmall)
    }
}

/** Yıldız cinsinden, işaretli ve yönlü: +1.0 → "▲ 0,5"; renk tek başına anlam taşımaz. */
internal fun farkMetni(fark: Double): String {
    val onda = (abs(fark) / 2 * 10).roundToInt()
    return "${if (fark > 0) "▲" else "▼"} ${onda / 10},${onda % 10}"
}
