package com.sosyaloyun.app.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.kayit_hata
import com.sosyaloyun.app.resources.kayit_kaydet
import com.sosyaloyun.app.resources.profil_vitrin_aciklama
import com.sosyaloyun.app.resources.profil_vitrin_duzenle
import com.sosyaloyun.app.resources.vitrin_cikar
import com.sosyaloyun.app.resources.vitrin_oyun_ekle
import org.jetbrains.compose.resources.stringResource

@Composable
fun VitrinDuzenleEkrani(viewModel: VitrinViewModel, oyunEkle: () -> Unit, geri: () -> Unit) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        Text(stringResource(Res.string.profil_vitrin_duzenle), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.profil_vitrin_aciklama), style = MaterialTheme.typography.bodySmall)
        durum.oyunlar.forEach { oyun ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kapak(oyun, Modifier.size(width = 48.dp, height = 64.dp))
                Text(oyun.ad, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.cikar(oyun.id) }) { Text(stringResource(Res.string.vitrin_cikar)) }
            }
        }
        OutlinedButton(onClick = oyunEkle, enabled = durum.eklenebilir, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.vitrin_oyun_ekle))
        }
        if (durum.hata) Text(stringResource(Res.string.kayit_hata), color = MaterialTheme.colorScheme.error)
        Button(onClick = viewModel::kaydet, enabled = !durum.kaydediliyor, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.kayit_kaydet))
        }
    }
}
