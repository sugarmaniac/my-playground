package com.sosyaloyun.app.sikayet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.engelle
import com.sosyaloyun.app.resources.kapat
import com.sosyaloyun.app.resources.sebep_diger
import com.sosyaloyun.app.resources.sebep_isaretsiz_spoiler
import com.sosyaloyun.app.resources.sebep_nefret_soylemi
import com.sosyaloyun.app.resources.sebep_sahte_hesap
import com.sosyaloyun.app.resources.sebep_spam
import com.sosyaloyun.app.resources.sebep_taciz
import com.sosyaloyun.app.resources.sebep_uygunsuz_icerik
import com.sosyaloyun.app.resources.sessize_al
import com.sosyaloyun.app.resources.sikayet_aciklama
import com.sosyaloyun.app.resources.sikayet_baslik
import com.sosyaloyun.app.resources.sikayet_engellendi
import com.sosyaloyun.app.resources.sikayet_gonder
import com.sosyaloyun.app.resources.sikayet_gonderildi
import com.sosyaloyun.app.resources.sikayet_hata
import com.sosyaloyun.app.resources.sikayet_sessize_alindi
import com.sosyaloyun.app.resources.vazgec
import com.sosyaloyun.shared.SikayetSebebi
import org.jetbrains.compose.resources.stringResource

/** Şikayet: sabit sebep, isteğe bağlı açıklama; gönderince kişiyi engelleme ya da sessize alma önerilir. Anonim. */
@Composable
fun SikayetPenceresi(viewModel: SikayetViewModel, kapat: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    when (val durum = ekran) {
        is SikayetEkrani.Form -> AlertDialog(
            onDismissRequest = kapat,
            title = { Text(stringResource(Res.string.sikayet_baslik)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SikayetSebebi.entries.forEach { sebep ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.sebepSec(sebep) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = durum.sebep == sebep, onClick = { viewModel.sebepSec(sebep) })
                            Text(sebepAdi(sebep))
                        }
                    }
                    OutlinedTextField(
                        value = durum.aciklama,
                        onValueChange = viewModel::aciklamaDegisti,
                        label = { Text(stringResource(Res.string.sikayet_aciklama)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (durum.hata) Text(stringResource(Res.string.sikayet_hata), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::gonder, enabled = durum.gonderilebilir) { Text(stringResource(Res.string.sikayet_gonder)) } },
            dismissButton = { TextButton(onClick = kapat) { Text(stringResource(Res.string.vazgec)) } },
        )
        is SikayetEkrani.Gonderildi -> AlertDialog(
            onDismissRequest = kapat,
            title = { Text(stringResource(Res.string.sikayet_gonderildi)) },
            text = {
                when (durum.islem) {
                    SikayetEkrani.Islem.ENGELLENDI -> Text(stringResource(Res.string.sikayet_engellendi))
                    SikayetEkrani.Islem.SESSIZE_ALINDI -> Text(stringResource(Res.string.sikayet_sessize_alindi))
                    null -> Column {
                        TextButton(onClick = viewModel::engelle) { Text(stringResource(Res.string.engelle), color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = viewModel::sessizeAl) { Text(stringResource(Res.string.sessize_al)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = kapat) { Text(stringResource(Res.string.kapat)) } },
        )
    }
}

@Composable
private fun sebepAdi(sebep: SikayetSebebi): String = stringResource(
    when (sebep) {
        SikayetSebebi.SPAM -> Res.string.sebep_spam
        SikayetSebebi.TACIZ -> Res.string.sebep_taciz
        SikayetSebebi.NEFRET_SOYLEMI -> Res.string.sebep_nefret_soylemi
        SikayetSebebi.UYGUNSUZ_ICERIK -> Res.string.sebep_uygunsuz_icerik
        SikayetSebebi.ISARETSIZ_SPOILER -> Res.string.sebep_isaretsiz_spoiler
        SikayetSebebi.SAHTE_HESAP -> Res.string.sebep_sahte_hesap
        SikayetSebebi.DIGER -> Res.string.sebep_diger
    },
)
