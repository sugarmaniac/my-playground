package com.sosyaloyun.app.akis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.akis_arkadas_oldu
import com.sosyaloyun.app.resources.akis_bos
import com.sosyaloyun.app.resources.akis_gunluk_kaydi
import com.sosyaloyun.app.resources.akis_istek_listemde
import com.sosyaloyun.app.resources.akis_istek_listeme_ekle
import com.sosyaloyun.app.resources.akis_oynamaya_basladi
import com.sosyaloyun.app.resources.akis_spoiler
import com.sosyaloyun.app.resources.akis_spoiler_goster
import com.sosyaloyun.app.resources.akis_yeni
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.arkadaslar
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.sekme_takip
import com.sosyaloyun.app.resources.takip_et
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.AkisSekmesi
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Yeni olaylar ekran açıkken bu aralıkla denetlenir. */
private const val YENI_DENETIM_MS = 60_000L

@Composable
fun AkisEkrani(
    viewModel: AkisViewModel,
    ustCubuk: @Composable () -> Unit,
    oyunaGit: (String) -> Unit,
    kullaniciyaGit: (String) -> Unit,
) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        while (true) {
            delay(YENI_DENETIM_MS)
            viewModel.yeniOlaylariDenetle()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ustCubuk()
        PrimaryTabRow(selectedTabIndex = durum.sekme.ordinal) {
            Tab(selected = durum.sekme == AkisSekmesi.TAKIP, onClick = { viewModel.sekmeSecildi(AkisSekmesi.TAKIP) }, text = { Text(stringResource(Res.string.sekme_takip)) })
            Tab(selected = durum.sekme == AkisSekmesi.ARKADASLAR, onClick = { viewModel.sekmeSecildi(AkisSekmesi.ARKADASLAR) }, text = { Text(stringResource(Res.string.arkadaslar)) })
        }
        if (durum.yeniOlaySayisi > 0) {
            FilledTonalButton(onClick = viewModel::yenile, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)) {
                Text(stringResource(Res.string.akis_yeni, durum.yeniOlaySayisi))
            }
        }
        when (val icerik = durum.icerik) {
            AkisIcerigi.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AkisIcerigi.Hata -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.durum_hata))
                TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
            }
            is AkisIcerigi.Bos -> LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(Res.string.akis_bos), style = MaterialTheme.typography.bodyMedium) }
                items(icerik.oneriler, key = { it.kullaniciId }) { kisi ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { KullaniciSatiri(kisi) { kullaniciyaGit(kisi.kullaniciAdi) } }
                        Button(onClick = { viewModel.onerilenKisiyiTakipEt(kisi.kullaniciAdi) }) { Text(stringResource(Res.string.takip_et)) }
                    }
                }
            }
            is AkisIcerigi.Ogeler -> LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(icerik.ogeler, key = { it.id }) { oge -> AkisKarti(oge, viewModel, oyunaGit, kullaniciyaGit) }
                if (icerik.dahaVar) {
                    item { TextButton(onClick = viewModel::dahaFazlaYukle, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) } }
                }
            }
        }
    }
}

@Composable
private fun AkisKarti(oge: AkisOgesi, viewModel: AkisViewModel, oyunaGit: (String) -> Unit, kullaniciyaGit: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable { kullaniciyaGit(oge.kisi.kullaniciAdi) }) {
                AvatarGorunumu(oge.kisi.avatar, boyut = 32.dp)
                Column(Modifier.weight(1f)) {
                    val metin = when (oge.tur) {
                        AkisOlayTuru.GUNLUK_KAYDI -> stringResource(Res.string.akis_gunluk_kaydi, oge.kisi.gorunenAd)
                        AkisOlayTuru.OYNAMAYA_BASLADI -> stringResource(Res.string.akis_oynamaya_basladi, oge.kisi.gorunenAd)
                        AkisOlayTuru.ARKADAS_OLDU -> stringResource(Res.string.akis_arkadas_oldu, oge.kisi.gorunenAd, oge.arkadas?.gorunenAd.orEmpty())
                    }
                    Text(metin, style = MaterialTheme.typography.bodyMedium)
                    Text(zamanMetni(oge.zaman), style = MaterialTheme.typography.labelSmall)
                }
            }
            oge.oyun?.let { oyun ->
                Row(Modifier.clickable { oyunaGit(oyun.id) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kapak(oyun, Modifier.size(width = 56.dp, height = 74.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(oyun.ad, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        oge.gunlukKaydi?.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
                        oge.gunlukKaydi?.inceleme?.let { Inceleme(it, oge.gunlukKaydi!!.spoiler) }
                    }
                }
                if (oge.istekListemde) {
                    Text(stringResource(Res.string.akis_istek_listemde), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(onClick = { viewModel.istekListesineEkle(oyun.id) }) { Text(stringResource(Res.string.akis_istek_listeme_ekle)) }
                }
            }
        }
    }
}

/** Spoiler içeren inceleme dokunulana kadar gizli (spec #1, hikaye 39). */
@Composable
private fun Inceleme(metin: String, spoiler: Boolean) {
    var acik by remember { mutableStateOf(!spoiler) }
    if (acik) {
        Text(metin, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
    } else {
        TextButton(onClick = { acik = true }) { Text("${stringResource(Res.string.akis_spoiler)} · ${stringResource(Res.string.akis_spoiler_goster)}") }
    }
}

private fun zamanMetni(zaman: Instant): String {
    val yerel = zaman.toLocalDateTime(TimeZone.currentSystemDefault())
    fun iki(sayi: Int) = sayi.toString().padStart(2, '0')
    return "${iki(yerel.day)}.${iki(yerel.month.ordinal + 1)}.${yerel.year} ${iki(yerel.hour)}:${iki(yerel.minute)}"
}
