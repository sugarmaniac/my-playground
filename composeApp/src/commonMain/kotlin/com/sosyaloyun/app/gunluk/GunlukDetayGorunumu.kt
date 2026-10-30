package com.sosyaloyun.app.gunluk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.resources.sikayet_et
import com.sosyaloyun.app.sikayet.SikayetHedefi
import com.sosyaloyun.app.sikayet.SikayetPenceresi
import com.sosyaloyun.app.sikayet.SikayetViewModel
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.akis_spoiler
import com.sosyaloyun.app.resources.akis_spoiler_goster
import com.sosyaloyun.app.resources.begenenler
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.gunluk_kaydi_bulunamadi
import com.sosyaloyun.shared.GunlukKaydi
import org.jetbrains.compose.resources.stringResource

/** Günlük kaydı detayı: sahibi, oyun, kayıt, beğeni butonu ve beğenenler. */
@Composable
fun GunlukDetayGorunumu(viewModel: GunlukDetayViewModel, api: ApiIstemcisi, oyunaGit: (String) -> Unit, kullaniciyaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        when (val durum = ekran) {
            GunlukDetayEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            GunlukDetayEkrani.Bulunamadi -> Text(stringResource(Res.string.gunluk_kaydi_bulunamadi))
            GunlukDetayEkrani.Hata -> Text(stringResource(Res.string.durum_hata))
            is GunlukDetayEkrani.Hazir -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (kayit, oyun, sahip) = durum.detay
                item {
                    Row(Modifier.clickable { kullaniciyaGit(sahip.kullaniciAdi) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvatarGorunumu(sahip.avatar, boyut = 40.dp)
                        Column {
                            Text(sahip.gorunenAd, style = MaterialTheme.typography.titleMedium)
                            Text("@${sahip.kullaniciAdi}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item {
                    Row(Modifier.clickable { oyunaGit(oyun.id) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kapak(oyun, Modifier.size(width = 72.dp, height = 96.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(oyun.ad, style = MaterialTheme.typography.titleLarge)
                            val tarihler = listOfNotNull(kayit.baslama, kayit.bitis).map(::tarihMetni).joinToString(" – ")
                            Text(listOf(tarihler, kayit.platform.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                            kayit.puan?.let { YildizPuani(it, sec = {}, boyut = 20) }
                        }
                    }
                }
                kayit.inceleme?.let { item { Inceleme(it, kayit.spoiler, kisalt = false) } }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BegeniButonu(kayit, viewModel::begeniDegistir)
                        var sikayetAcik by remember { mutableStateOf(false) }
                        TextButton(onClick = { sikayetAcik = true }) { Text(stringResource(Res.string.sikayet_et)) }
                        if (sikayetAcik) {
                            SikayetPenceresi(
                                viewModel(key = "sikayet-${kayit.id}") { SikayetViewModel(api, SikayetHedefi(sahip.kullaniciAdi, kayit.id)) },
                                kapat = { sikayetAcik = false },
                            )
                        }
                    }
                }
                if (durum.begenenler.isNotEmpty()) {
                    item { Text(stringResource(Res.string.begenenler), style = MaterialTheme.typography.titleSmall) }
                    items(durum.begenenler, key = { it.kullaniciId }) { kisi -> KullaniciSatiri(kisi) { kullaniciyaGit(kisi.kullaniciAdi) } }
                }
            }
        }
    }
}

/** Kalp ve beğeni sayısı; beğenilmişse dolu kalp. */
@Composable
fun BegeniButonu(kayit: GunlukKaydi, tikla: () -> Unit) {
    TextButton(onClick = tikla) {
        Text("${if (kayit.begendim) "♥" else "♡"} ${kayit.begeniSayisi}", color = if (kayit.begendim) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Spoiler içeren inceleme dokunulana kadar gizli (spec #1, hikaye 39). */
@Composable
fun Inceleme(metin: String, spoiler: Boolean, kisalt: Boolean = true) {
    var acik by remember { mutableStateOf(!spoiler) }
    if (acik) {
        if (kisalt) {
            Text(metin, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        } else {
            Text(metin, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        TextButton(onClick = { acik = true }) { Text("${stringResource(Res.string.akis_spoiler)} · ${stringResource(Res.string.akis_spoiler_goster)}") }
    }
}
