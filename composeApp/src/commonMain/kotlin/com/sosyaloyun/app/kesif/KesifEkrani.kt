package com.sosyaloyun.app.kesif

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.arama.OyunSatiri
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.kesif_baslik
import com.sosyaloyun.app.resources.kesif_kaydeden
import com.sosyaloyun.app.resources.kesif_platformlar
import com.sosyaloyun.app.resources.kesif_takip_edilenlerde
import com.sosyaloyun.app.resources.kesif_takip_edilenlerde_bos
import com.sosyaloyun.app.resources.kesif_trend
import com.sosyaloyun.app.resources.kesif_turler
import com.sosyaloyun.app.resources.profil_bos_sekme
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.app.tema.HapSekme
import com.sosyaloyun.app.tema.Renk
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.Tur
import org.jetbrains.compose.resources.stringResource

/** Keşif: takip edilenler arasında bu hafta kaydedilenler, trend oyunlar, tür ve platform kısayolları. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KesifEkrani(
    viewModel: KesifViewModel,
    oyunaGit: (String) -> Unit,
    turAc: (Tur) -> Unit,
    platformAc: (String) -> Unit,
    /** Fazlara bağlı ek bölümler (ör. Faz 2'de küratör listeleri); keşfin başında. */
    ekBolumler: @Composable () -> Unit = {},
) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    when (val durum = ekran) {
        KesifEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        KesifEkrani.Hata -> Column(Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.durum_hata))
            TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
        }
        is KesifEkrani.Hazir -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { ekBolumler() } }
            val kesif = durum.kesif
            item { Text(stringResource(Res.string.kesif_baslik), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp)) }
            item {
                Bolum(stringResource(Res.string.kesif_takip_edilenlerde)) {
                    if (kesif.takipEdilenlerde.isEmpty()) {
                        Text(stringResource(Res.string.kesif_takip_edilenlerde_bos), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        KapakSirasi(kesif.takipEdilenlerde.map { it.oyun }, oyunaGit) { oyun ->
                            kesif.takipEdilenlerde.first { it.oyun.id == oyun.id }.kaydedenSayisi.let { stringResource(Res.string.kesif_kaydeden, it) }
                        }
                    }
                }
            }
            item {
                Bolum(stringResource(Res.string.kesif_trend)) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        kesif.trend.take(TREND_SATIRI).forEachIndexed { sira, oyun -> TrendSatiri(sira + 1, oyun, oyunaGit) }
                    }
                }
            }
            item {
                Bolum(stringResource(Res.string.kesif_turler)) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        kesif.turler.forEach { tur -> HapSekme(turAdi(tur), secili = false) { turAc(tur) } }
                    }
                }
            }
            item {
                Bolum(stringResource(Res.string.kesif_platformlar)) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        kesif.platformlar.forEach { platform -> HapSekme(platform, secili = false) { platformAc(platform) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bolum(baslik: String, icerik: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(baslik, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        icerik()
    }
}

private const val TREND_SATIRI = 10

/** Trend listesinin bir satırı: vurgu renginde sıra numarası, küçük kapak, ad ve yıl · platform. */
@Composable
private fun TrendSatiri(sira: Int, oyun: OyunOzeti, oyunaGit: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { oyunaGit(oyun.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(sira.toString(), style = MaterialTheme.typography.headlineSmall, color = Renk.Vurgu, modifier = Modifier.width(28.dp))
        Kapak(oyun, Modifier.size(width = 44.dp, height = 58.dp))
        Column(Modifier.weight(1f)) {
            Text(oyun.ad, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ayrinti = listOfNotNull(oyun.cikisYili?.toString(), oyun.platformlar.take(2).joinToString(", ").ifEmpty { null }).joinToString(" · ")
            if (ayrinti.isNotEmpty()) Text(ayrinti, style = MaterialTheme.typography.bodySmall, color = Renk.Soluk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KapakSirasi(oyunlar: List<OyunOzeti>, oyunaGit: (String) -> Unit, altYazi: (@Composable (OyunOzeti) -> String)? = null) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
        items(oyunlar, key = { it.id }) { oyun ->
            Column(Modifier.width(96.dp).clickable { oyunaGit(oyun.id) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kapak(oyun, Modifier.size(width = 96.dp, height = 128.dp))
                Text(oyun.ad, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                altYazi?.let { Text(it(oyun), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

/** Keşif kısayolunun açtığı liste: başlık tür adı ya da platform. */
@Composable
fun OyunListesiGorunumu(viewModel: OyunListesiViewModel, baslik: String, oyunaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        Text(baslik, style = MaterialTheme.typography.headlineSmall)
        when (val durum = ekran) {
            OyunListesiEkrani.Yukleniyor -> CircularProgressIndicator()
            OyunListesiEkrani.Hata -> Text(stringResource(Res.string.durum_hata))
            is OyunListesiEkrani.Hazir -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (durum.oyunlar.isEmpty()) item { Text(stringResource(Res.string.profil_bos_sekme)) }
                items(durum.oyunlar, key = { it.id }) { oyun -> OyunSatiri(oyun) { oyunaGit(oyun.id) } }
                if (durum.dahaVar) {
                    item { TextButton(onClick = viewModel::dahaFazlaYukle, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) } }
                }
            }
        }
    }
}
