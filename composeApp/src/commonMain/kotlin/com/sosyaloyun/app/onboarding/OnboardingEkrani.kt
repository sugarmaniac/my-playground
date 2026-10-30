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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.hesap.paylasici
import com.sosyaloyun.app.resources.arkadas_bulunamadi
import com.sosyaloyun.app.resources.davet_paylas
import com.sosyaloyun.app.resources.davet_paylas_metni
import com.sosyaloyun.app.resources.hepsini_takip_et
import com.sosyaloyun.app.resources.kaydet
import com.sosyaloyun.app.resources.numaram
import com.sosyaloyun.app.resources.numaram_aciklama
import com.sosyaloyun.app.resources.numaram_kaydedildi
import com.sosyaloyun.app.resources.onboarding_arkadaslar_aciklama
import com.sosyaloyun.app.resources.onboarding_arkadaslar_baslik
import com.sosyaloyun.app.resources.onboarding_atla
import com.sosyaloyun.app.resources.onboarding_devam
import com.sosyaloyun.app.resources.rehber_izni_yok
import com.sosyaloyun.app.resources.rehberde_ara
import com.sosyaloyun.app.resources.takip_ediliyor
import com.sosyaloyun.app.resources.takip_et
import com.sosyaloyun.shared.TelefonNumarasi
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingEkrani(viewModel: OnboardingViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    when (val adim = ekran) {
        is OnboardingAdimi.Steam -> SteamAdimi(adim, viewModel)
        is OnboardingAdimi.Durumlar -> DurumlarAdimi(adim, viewModel)
        is OnboardingAdimi.Arkadaslar -> ArkadaslarAdimi(adim, viewModel)
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
                Button(onClick = viewModel::durumlariBitir, enabled = adim.bitirilebilir, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.onboarding_devam))
                }
            }
        }
    }
}

@Composable
private fun ArkadaslarAdimi(adim: OnboardingAdimi.Arkadaslar, viewModel: OnboardingViewModel) {
    val rehber = rehberOkuyucu()
    val kapsam = rememberCoroutineScope()
    val paylas = paylasici()
    var numara by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.onboarding_arkadaslar_baslik), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.onboarding_arkadaslar_aciklama), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { kapsam.launch { viewModel.rehberdenBul(rehber.numaralar()) } }, enabled = !adim.araniyor) {
                    Text(stringResource(Res.string.rehberde_ara))
                }
                if (adim.rehberIzniYok) Text(stringResource(Res.string.rehber_izni_yok), style = MaterialTheme.typography.bodySmall)
                if (adim.araniyor) CircularProgressIndicator(Modifier.size(24.dp))
                if (!adim.araniyor && adim.kisiler.isEmpty() && (adim.rehberTarandi || adim.steamli)) {
                    Text(stringResource(Res.string.arkadas_bulunamadi), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (adim.takipEdilmeyenler.size > 1) {
            item { Button(onClick = viewModel::hepsiniTakipEt) { Text(stringResource(Res.string.hepsini_takip_et, adim.takipEdilmeyenler.size)) } }
        }
        items(adim.kisiler, key = { it.kullanici.kullaniciId }) { kisi ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { KullaniciSatiri(kisi.kullanici) {} }
                if (kisi.takipEdiyorum) {
                    Text(stringResource(Res.string.takip_ediliyor), style = MaterialTheme.typography.labelMedium)
                } else {
                    Button(onClick = { viewModel.takipEt(kisi.kullanici.kullaniciAdi) }) { Text(stringResource(Res.string.takip_et)) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.numaram_aciklama), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = numara,
                        onValueChange = { numara = it },
                        label = { Text(stringResource(Res.string.numaram)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { viewModel.numaramiKaydet(numara) }, enabled = TelefonNumarasi.normallestir(numara) != null) {
                        Text(stringResource(Res.string.kaydet))
                    }
                }
                if (adim.numaraKaydedildi) Text(stringResource(Res.string.numaram_kaydedildi), style = MaterialTheme.typography.bodySmall)
            }
        }
        adim.davetKodu?.let { kod ->
            item {
                val metin = stringResource(Res.string.davet_paylas_metni, kod)
                OutlinedButton(onClick = { paylas(metin) }) { Text(stringResource(Res.string.davet_paylas)) }
            }
        }
        item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adim.hata) Text(stringResource(Res.string.onboarding_hata), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::bitir, enabled = !adim.bitiriliyor, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (adim.kisiler.any { it.takipEdiyorum }) Res.string.onboarding_bitir else Res.string.onboarding_atla))
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
