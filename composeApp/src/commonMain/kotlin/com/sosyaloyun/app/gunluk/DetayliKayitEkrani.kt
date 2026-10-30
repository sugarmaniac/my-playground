package com.sosyaloyun.app.gunluk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.gorunurluk_arkadaslar
import com.sosyaloyun.app.resources.gorunurluk_ben
import com.sosyaloyun.app.resources.gorunurluk_herkes
import com.sosyaloyun.app.resources.gorunurluk_takipciler
import com.sosyaloyun.app.resources.gunluk_baslama
import com.sosyaloyun.app.resources.gunluk_bitis
import com.sosyaloyun.app.resources.gunluk_gorunurluk
import com.sosyaloyun.app.resources.gunluk_hata_baglanti
import com.sosyaloyun.app.resources.gunluk_hata_sure
import com.sosyaloyun.app.resources.gunluk_hata_tarih
import com.sosyaloyun.app.resources.gunluk_inceleme
import com.sosyaloyun.app.resources.gunluk_kaydet
import com.sosyaloyun.app.resources.gunluk_platform
import com.sosyaloyun.app.resources.gunluk_sessiz
import com.sosyaloyun.app.resources.gunluk_sessiz_aciklama
import com.sosyaloyun.app.resources.gunluk_sil
import com.sosyaloyun.app.resources.gunluk_spoiler
import com.sosyaloyun.app.resources.gunluk_sure
import com.sosyaloyun.app.resources.gunluk_tarih_sec
import com.sosyaloyun.app.resources.gunluk_tarih_temizle
import com.sosyaloyun.app.resources.tamam
import com.sosyaloyun.shared.Gorunurluk
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
fun DetayliKayitEkrani(viewModel: DetayliKayitViewModel, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = geri, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text(stringResource(Res.string.geri)) }
        when (val durum = ekran) {
            DetayliKayitEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DetayliKayitEkrani.Hata -> Text(stringResource(Res.string.durum_hata), modifier = Modifier.padding(24.dp))
            is DetayliKayitEkrani.Form -> Form(durum.form, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(form: KayitFormu, viewModel: DetayliKayitViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(form.oyunAdi, style = MaterialTheme.typography.headlineSmall)
        if (form.platformlar.isNotEmpty()) {
            Text(stringResource(Res.string.gunluk_platform), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.platformlar.forEach { platform ->
                    FilterChip(selected = form.platform == platform, onClick = { viewModel.platformSecildi(platform) }, label = { Text(platform) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TarihSecici(stringResource(Res.string.gunluk_baslama), form.baslama, viewModel::baslamaSecildi, Modifier.weight(1f))
            TarihSecici(stringResource(Res.string.gunluk_bitis), form.bitis, viewModel::bitisSecildi, Modifier.weight(1f))
        }
        OutlinedTextField(
            value = form.sureSaat,
            onValueChange = viewModel::sureDegisti,
            label = { Text(stringResource(Res.string.gunluk_sure)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = form.hata == KayitHatasi.SURE,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.inceleme,
            onValueChange = viewModel::incelemeDegisti,
            label = { Text(stringResource(Res.string.gunluk_inceleme)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        Anahtar(stringResource(Res.string.gunluk_spoiler), form.spoiler, viewModel::spoilerDegisti)
        Text(stringResource(Res.string.gunluk_gorunurluk), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gorunurluk.entries.forEach { secenek ->
                FilterChip(selected = form.gorunurluk == secenek, onClick = { viewModel.gorunurlukSecildi(secenek) }, label = { Text(gorunurlukAdi(secenek)) })
            }
        }
        Anahtar(stringResource(Res.string.gunluk_sessiz), form.sessiz, viewModel::sessizDegisti)
        Text(stringResource(Res.string.gunluk_sessiz_aciklama), style = MaterialTheme.typography.bodySmall)
        form.hata?.let { hata ->
            Text(
                stringResource(
                    when (hata) {
                        KayitHatasi.SURE -> Res.string.gunluk_hata_sure
                        KayitHatasi.TARIH -> Res.string.gunluk_hata_tarih
                        KayitHatasi.BAGLANTI -> Res.string.gunluk_hata_baglanti
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = viewModel::kaydet, enabled = !form.kaydediliyor, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.gunluk_kaydet))
        }
        if (form.duzenleme) {
            OutlinedButton(onClick = viewModel::sil, enabled = !form.kaydediliyor, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(stringResource(Res.string.gunluk_sil), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Anahtar(metin: String, acik: Boolean, degistir: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(metin, modifier = Modifier.weight(1f))
        Switch(checked = acik, onCheckedChange = { degistir() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarihSecici(baslik: String, tarih: LocalDate?, sec: (LocalDate?) -> Unit, modifier: Modifier) {
    var acik by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(baslik, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { acik = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tarih?.let(::tarihMetni) ?: stringResource(Res.string.gunluk_tarih_sec))
        }
    }
    if (acik) {
        val durum = rememberDatePickerState(initialSelectedDateMillis = tarih?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { acik = false },
            confirmButton = {
                TextButton(onClick = {
                    sec(durum.selectedDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date })
                    acik = false
                }) { Text(stringResource(Res.string.tamam)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    sec(null)
                    acik = false
                }) { Text(stringResource(Res.string.gunluk_tarih_temizle)) }
            },
        ) { DatePicker(state = durum) }
    }
}

/** 2026-01-05 → "05.01.2026". */
fun tarihMetni(tarih: LocalDate): String =
    "${tarih.day.toString().padStart(2, '0')}.${tarih.month.ordinal.plus(1).toString().padStart(2, '0')}.${tarih.year}"

@Composable
fun gorunurlukAdi(gorunurluk: Gorunurluk): String = stringResource(
    when (gorunurluk) {
        Gorunurluk.HERKES -> Res.string.gorunurluk_herkes
        Gorunurluk.TAKIPCILER -> Res.string.gorunurluk_takipciler
        Gorunurluk.ARKADASLAR -> Res.string.gorunurluk_arkadaslar
        Gorunurluk.BEN -> Res.string.gorunurluk_ben
    },
)
