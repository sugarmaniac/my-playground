package com.sosyaloyun.app.gizlilik

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.gizli_profil
import com.sosyaloyun.app.resources.gizli_profil_aciklama
import com.sosyaloyun.app.resources.gizlilik_hata
import com.sosyaloyun.app.resources.kabul_et
import com.sosyaloyun.app.resources.reddet
import com.sosyaloyun.app.resources.takip_istekleri
import com.sosyaloyun.app.resources.tekrar_dene
import org.jetbrains.compose.resources.stringResource

/** Ayarlarda gizli profil anahtarı ve gelen takip istekleri (Faz 2, #48). */
@Composable
fun GizlilikBolumu(viewModel: GizlilikViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    if (!ekran.yuklendi) {
        if (ekran.hata) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.gizlilik_hata), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.gizli_profil), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.gizli_profil_aciklama), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = ekran.gizli, onCheckedChange = viewModel::gizliDegistir)
        }
        if (ekran.hata) Text(stringResource(Res.string.gizlilik_hata), color = MaterialTheme.colorScheme.error)
        if (ekran.istekler.isNotEmpty()) {
            Text(stringResource(Res.string.takip_istekleri, ekran.istekler.size), style = MaterialTheme.typography.titleSmall)
            ekran.istekler.forEach { kisi ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarGorunumu(kisi.avatar, boyut = 32.dp)
                    Text("${kisi.gorunenAd} (@${kisi.kullaniciAdi})", modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.kabulEt(kisi.kullaniciAdi) }) { Text(stringResource(Res.string.kabul_et)) }
                    TextButton(onClick = { viewModel.reddet(kisi.kullaniciAdi) }) { Text(stringResource(Res.string.reddet)) }
                }
            }
        }
    }
}
