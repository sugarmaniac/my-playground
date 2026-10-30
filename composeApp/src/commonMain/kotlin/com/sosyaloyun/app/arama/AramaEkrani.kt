package com.sosyaloyun.app.arama

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.arama_alani
import com.sosyaloyun.app.resources.arama_bos
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.arama_hata
import com.sosyaloyun.app.resources.arama_kullanicilar
import com.sosyaloyun.app.resources.arama_oyunlar
import com.sosyaloyun.app.resources.arama_sonuc_yok
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.oyun_turu_dlc
import com.sosyaloyun.app.resources.oyun_turu_genisleme
import com.sosyaloyun.app.resources.oyun_turu_remake
import com.sosyaloyun.app.resources.oyun_turu_remaster
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import org.jetbrains.compose.resources.stringResource

@Composable
fun AramaEkrani(
    viewModel: AramaViewModel,
    geri: () -> Unit,
    oyunSecildi: (OyunOzeti) -> Unit,
    /** Null ise yalnızca oyun aranır (kayıt ve vitrin seçimi). */
    kullaniciSecildi: ((String) -> Unit)? = null,
) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
            OutlinedTextField(
                value = durum.sorgu,
                onValueChange = viewModel::sorguDegisti,
                placeholder = { Text(stringResource(Res.string.arama_alani)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (kullaniciSecildi != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = durum.tur == AramaTuru.OYUN,
                    onClick = { viewModel.turSecildi(AramaTuru.OYUN) },
                    label = { Text(stringResource(Res.string.arama_oyunlar)) },
                )
                FilterChip(
                    selected = durum.tur == AramaTuru.KULLANICI,
                    onClick = { viewModel.turSecildi(AramaTuru.KULLANICI) },
                    label = { Text(stringResource(Res.string.arama_kullanicilar)) },
                )
            }
        }
        when (val sonuc = durum.sonuc) {
            AramaSonucu.Bos -> Bilgi(stringResource(Res.string.arama_bos))
            AramaSonucu.Yukleniyor -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AramaSonucu.SonucYok -> Bilgi(stringResource(Res.string.arama_sonuc_yok))
            AramaSonucu.Hata -> Bilgi(stringResource(Res.string.arama_hata))
            is AramaSonucu.Kullanicilar -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sonuc.kullanicilar, key = { it.kullaniciId }) { kisi -> KullaniciSatiri(kisi) { kullaniciSecildi?.invoke(kisi.kullaniciAdi) } }
            }
            is AramaSonucu.Oyunlar -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sonuc.oyunlar, key = { it.id }) { oyun -> OyunSatiri(oyun) { oyunSecildi(oyun) } }
                if (sonuc.dahaVar) {
                    item {
                        TextButton(onClick = viewModel::dahaFazlaYukle, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.arama_daha_fazla))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KullaniciSatiri(kisi: KullaniciOzeti, tikla: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = tikla).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarGorunumu(kisi.avatar, boyut = 40.dp)
        Column {
            Text(kisi.gorunenAd, style = MaterialTheme.typography.titleSmall)
            Text("@${kisi.kullaniciAdi}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Bilgi(metin: String) {
    Text(metin, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 24.dp))
}

@Composable
private fun OyunSatiri(oyun: OyunOzeti, tikla: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = tikla), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = oyun.kapakUrl,
            contentDescription = oyun.ad,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(oyun.ad, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val ayrintilar = listOfNotNull(oyun.cikisYili?.toString(), oyun.platformlar.takeIf { it.isNotEmpty() }?.joinToString(", "))
            if (ayrintilar.isNotEmpty()) {
                Text(ayrintilar.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            turEtiketi(oyun)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Ana oyun dışındaki türler ve DLC/genişlemelerin ana oyunu. */
@Composable
private fun turEtiketi(oyun: OyunOzeti): String? {
    val tur = when (oyun.tur) {
        OyunTuru.ANA_OYUN -> return null
        OyunTuru.DLC -> stringResource(Res.string.oyun_turu_dlc)
        OyunTuru.GENISLEME -> stringResource(Res.string.oyun_turu_genisleme)
        OyunTuru.REMAKE -> stringResource(Res.string.oyun_turu_remake)
        OyunTuru.REMASTER -> stringResource(Res.string.oyun_turu_remaster)
    }
    return listOfNotNull(tur, oyun.anaOyun?.ad).joinToString(" · ")
}
