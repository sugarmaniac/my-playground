package com.sosyaloyun.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.kutuphane.durumAdi
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.onboarding_bitir
import com.sosyaloyun.app.resources.onboarding_durumlar_baslik
import com.sosyaloyun.app.resources.onboarding_durumlar_steamli
import com.sosyaloyun.app.resources.onboarding_durumlar_steamsiz
import com.sosyaloyun.app.resources.onboarding_hata
import com.sosyaloyun.app.resources.onboarding_steam_aciklama
import com.sosyaloyun.app.resources.onboarding_steam_atla
import com.sosyaloyun.app.resources.onboarding_steam_baslik
import com.sosyaloyun.app.resources.steam_bagla
import com.sosyaloyun.app.resources.steam_bagli
import com.sosyaloyun.app.resources.steam_ice_aktarildi
import com.sosyaloyun.app.resources.steam_kaldir
import com.sosyaloyun.app.resources.steam_baska_kullanicida
import com.sosyaloyun.app.resources.steam_bulunamadi
import com.sosyaloyun.app.resources.steam_gizli
import com.sosyaloyun.app.resources.steam_girdi
import com.sosyaloyun.app.resources.steam_ulasilamiyor
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.OnboardingKurallari
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingEkrani(viewModel: OnboardingViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    when (val adim = ekran) {
        is OnboardingAdimi.Steam -> SteamAdimi(adim, viewModel)
        is OnboardingAdimi.Durumlar -> DurumlarAdimi(adim, viewModel)
    }
}

@Composable
private fun SteamAdimi(adim: OnboardingAdimi.Steam, viewModel: OnboardingViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(stringResource(Res.string.onboarding_steam_baslik), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.onboarding_steam_aciklama), style = MaterialTheme.typography.bodyMedium)
        SteamGirdisi(adim.girdi, adim.hata, adim.gonderiliyor, viewModel::girdiDegisti, viewModel::bagla, adim.gonderilebilir)
        TextButton(onClick = viewModel::steamiAtla, enabled = !adim.gonderiliyor) { Text(stringResource(Res.string.onboarding_steam_atla)) }
    }
}

/** Steam profil linki/adı alanı, bağla butonu ve hata açıklaması; onboarding ve ayarlarda ortak. */
@Composable
fun SteamGirdisi(
    girdi: String,
    hata: SteamHatasi?,
    gonderiliyor: Boolean,
    degisti: (String) -> Unit,
    bagla: () -> Unit,
    gonderilebilir: Boolean = girdi.isNotBlank() && !gonderiliyor,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = girdi,
            onValueChange = degisti,
            label = { Text(stringResource(Res.string.steam_girdi)) },
            singleLine = true,
            isError = hata != null,
            modifier = Modifier.fillMaxWidth(),
        )
        hata?.let {
            Text(
                stringResource(
                    when (it) {
                        SteamHatasi.GIZLI -> Res.string.steam_gizli
                        SteamHatasi.BULUNAMADI -> Res.string.steam_bulunamadi
                        SteamHatasi.BASKA_KULLANICIDA -> Res.string.steam_baska_kullanicida
                        SteamHatasi.ULASILAMIYOR -> Res.string.steam_ulasilamiyor
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = bagla, enabled = gonderilebilir) { Text(stringResource(Res.string.steam_bagla)) }
            if (gonderiliyor) CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurumlarAdimi(adim: OnboardingAdimi.Durumlar, viewModel: OnboardingViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.onboarding_durumlar_baslik), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (adim.steamli) {
                        stringResource(Res.string.onboarding_durumlar_steamli)
                    } else {
                        stringResource(Res.string.onboarding_durumlar_steamsiz, OnboardingKurallari.EN_AZ_OYUN, adim.secilenSayisi)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(adim.oyunlar, key = { it.oyun.id }) { oge ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kapak(oge.oyun, Modifier.size(width = 48.dp, height = 64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(oge.oyun.ad, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Durum.entries.forEach { durum ->
                            FilterChip(
                                selected = oge.giris.durum == durum,
                                onClick = { viewModel.durumSec(oge.oyun.id, durum) },
                                label = { Text(durumAdi(durum)) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adim.hata) Text(stringResource(Res.string.onboarding_hata), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::bitir, enabled = adim.bitirilebilir, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.onboarding_bitir))
                }
            }
        }
    }
}

/** Ayarlarda Steam bölümü: bağlıysa hesap ve kaldırma, değilse bağlama. */
@Composable
fun SteamBolumu(viewModel: SteamAyariViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Steam", style = MaterialTheme.typography.titleMedium)
        when (val durum = ekran) {
            SteamAyari.Yukleniyor -> CircularProgressIndicator(Modifier.size(24.dp))
            SteamAyari.Hata -> TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.steam_ulasilamiyor)) }
            is SteamAyari.BagliDegil -> SteamGirdisi(durum.girdi, durum.hata, durum.gonderiliyor, viewModel::girdiDegisti, viewModel::bagla)
            is SteamAyari.Bagli -> {
                Text(stringResource(Res.string.steam_bagli, durum.steamId), style = MaterialTheme.typography.bodyMedium)
                durum.iceAktarilan?.let { Text(stringResource(Res.string.steam_ice_aktarildi, it), style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = viewModel::kaldir, enabled = !durum.kaldiriliyor) { Text(stringResource(Res.string.steam_kaldir)) }
            }
        }
    }
}
