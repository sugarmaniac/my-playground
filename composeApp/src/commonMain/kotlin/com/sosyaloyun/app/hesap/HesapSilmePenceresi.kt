package com.sosyaloyun.app.hesap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.hesap_sil_aciklama
import com.sosyaloyun.app.resources.hesap_sil_baslik
import com.sosyaloyun.app.resources.hesap_sil_hata
import com.sosyaloyun.app.resources.hesap_sil_onay
import com.sosyaloyun.app.resources.hesap_sil_onay_etiketi
import com.sosyaloyun.app.resources.vazgec
import org.jetbrains.compose.resources.stringResource

@Composable
fun HesapSilmePenceresi(viewModel: HesapSilmeViewModel, kullaniciAdi: String, kapat: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = { if (!ekran.siliniyor) kapat() },
        title = { Text(stringResource(Res.string.hesap_sil_baslik)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.hesap_sil_aciklama))
                OutlinedTextField(
                    value = ekran.onay,
                    onValueChange = viewModel::onayDegisti,
                    label = { Text(stringResource(Res.string.hesap_sil_onay_etiketi, kullaniciAdi)) },
                    singleLine = true,
                    enabled = !ekran.siliniyor,
                )
                if (ekran.hata) Text(stringResource(Res.string.hesap_sil_hata), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::sil, enabled = ekran.silinebilir) {
                Text(stringResource(Res.string.hesap_sil_onay), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = kapat, enabled = !ekran.siliniyor) { Text(stringResource(Res.string.vazgec)) } },
    )
}
