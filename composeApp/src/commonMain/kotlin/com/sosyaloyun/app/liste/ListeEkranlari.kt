package com.sosyaloyun.app.liste

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.hesap.paylasici
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.liste_aciklama
import com.sosyaloyun.app.resources.liste_baslik
import com.sosyaloyun.app.resources.liste_begen
import com.sosyaloyun.app.resources.liste_begenildi
import com.sosyaloyun.app.resources.liste_bulunamadi
import com.sosyaloyun.app.resources.liste_duzenle
import com.sosyaloyun.app.resources.liste_herkes
import com.sosyaloyun.app.resources.liste_kaydet
import com.sosyaloyun.app.resources.liste_kayit_hatasi
import com.sosyaloyun.app.resources.liste_oyun_ara
import com.sosyaloyun.app.resources.liste_oyun_sayisi
import com.sosyaloyun.app.resources.liste_sadece_ben
import com.sosyaloyun.app.resources.liste_sil
import com.sosyaloyun.app.resources.oyun_listeleri
import com.sosyaloyun.app.resources.paylas
import com.sosyaloyun.app.resources.yorum_sil
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeOzeti
import org.jetbrains.compose.resources.stringResource

/** Profilde ve oyun detayında bir liste satırı: ön izleme kapakları, başlık, oyun sayısı. */
@Composable
fun ListeOzetiSatiri(liste: ListeOzeti, tikla: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = tikla).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            liste.onIzleme.take(3).forEach { Kapak(it, Modifier.size(width = 36.dp, height = 48.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(liste.baslik, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.liste_oyun_sayisi, liste.oyunSayisi, liste.sahip.gorunenAd),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun ListeDetayEkrani(viewModel: ListeDetayViewModel, oyunaGit: (String) -> Unit, duzenle: (Liste) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    LaunchedEffect((ekran as? ListeDetayEkrani.Hazir)?.silindi) { if ((ekran as? ListeDetayEkrani.Hazir)?.silindi == true) geri() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        when (val durum = ekran) {
            ListeDetayEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ListeDetayEkrani.Bulunamadi -> Text(stringResource(Res.string.liste_bulunamadi))
            ListeDetayEkrani.Hata -> Text(stringResource(Res.string.durum_hata))
            is ListeDetayEkrani.Hazir -> {
                val liste = durum.liste
                Text(liste.baslik, style = MaterialTheme.typography.headlineSmall)
                Text("@${liste.sahip.kullaniciAdi}", style = MaterialTheme.typography.labelLarge)
                liste.aciklama?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::begeniDegistir) {
                        Text(stringResource(if (liste.begendim) Res.string.liste_begenildi else Res.string.liste_begen, liste.begeniSayisi))
                    }
                    durum.paylasimLinki?.let { link ->
                        val paylas = paylasici()
                        TextButton(onClick = { paylas(link) }) { Text(stringResource(Res.string.paylas)) }
                    }
                    if (liste.benim) {
                        TextButton(onClick = { duzenle(liste) }) { Text(stringResource(Res.string.liste_duzenle)) }
                        TextButton(onClick = viewModel::sil) { Text(stringResource(Res.string.liste_sil), color = MaterialTheme.colorScheme.error) }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(liste.oyunlar, key = { _, o -> o.id }) { i, oyun ->
                        Row(Modifier.fillMaxWidth().clickable { oyunaGit(oyun.id) }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", style = MaterialTheme.typography.labelLarge)
                            Kapak(oyun, Modifier.size(width = 48.dp, height = 64.dp))
                            Text(oyun.ad, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListeDuzenleEkrani(viewModel: ListeDuzenleViewModel, kaydedildi: (Liste) -> Unit, geri: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(form.kaydedilen) { form.kaydedilen?.let(kaydedildi) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) } }
        item {
            OutlinedTextField(form.baslik, viewModel::baslikDegisti, label = { Text(stringResource(Res.string.liste_baslik)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(form.aciklama, viewModel::aciklamaDegisti, label = { Text(stringResource(Res.string.liste_aciklama)) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(form.gorunurluk == ListeGorunurlugu.HERKES, { viewModel.gorunurlukSecildi(ListeGorunurlugu.HERKES) }, label = { Text(stringResource(Res.string.liste_herkes)) })
                FilterChip(form.gorunurluk == ListeGorunurlugu.BEN, { viewModel.gorunurlukSecildi(ListeGorunurlugu.BEN) }, label = { Text(stringResource(Res.string.liste_sadece_ben)) })
            }
        }
        item {
            OutlinedTextField(form.arama, viewModel::aramaDegisti, label = { Text(stringResource(Res.string.liste_oyun_ara)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        itemsIndexed(form.aramaSonuclari, key = { _, o -> "sonuc-${o.id}" }) { _, oyun ->
            Text("+ ${oyun.ad}${oyun.cikisYili?.let { " ($it)" } ?: ""}", modifier = Modifier.fillMaxWidth().clickable { viewModel.ekle(oyun) }.padding(vertical = 6.dp))
        }
        itemsIndexed(form.oyunlar, key = { _, o -> "oyun-${o.id}" }) { i, oyun ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. ${oyun.ad}", modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.tasi(oyun.id, yukari = true) }, enabled = i > 0) { Text("↑") }
                TextButton(onClick = { viewModel.tasi(oyun.id, yukari = false) }, enabled = i < form.oyunlar.lastIndex) { Text("↓") }
                TextButton(onClick = { viewModel.cikar(oyun.id) }) { Text(stringResource(Res.string.yorum_sil)) }
            }
        }
        item {
            if (form.hata) Text(stringResource(Res.string.liste_kayit_hatasi), color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::kaydet, enabled = form.kaydedilebilir, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.liste_kaydet)) }
        }
    }
}

@Composable
fun OyunListeleriBolumu(viewModel: OyunListeleriViewModel, listeyeGit: (String) -> Unit) {
    val listeler by viewModel.listeler.collectAsStateWithLifecycle()
    if (listeler.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.oyun_listeleri), style = MaterialTheme.typography.titleMedium)
        listeler.forEach { ListeOzetiSatiri(it) { listeyeGit(it.id) } }
    }
}
