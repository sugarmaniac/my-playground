package com.sosyaloyun.app.bildirim

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.sosyaloyun.app.resources.tercih_yorum
import com.sosyaloyun.app.resources.bildirim_yorum
import com.sosyaloyun.app.resources.bildirim_arkadaslik_istegi
import com.sosyaloyun.app.resources.bildirim_arkadaslik_kabulu
import com.sosyaloyun.app.resources.bildirim_begeni
import com.sosyaloyun.app.resources.bildirim_begeni_grup
import com.sosyaloyun.app.resources.bildirim_davet
import com.sosyaloyun.app.resources.bildirim_tercihleri
import com.sosyaloyun.app.resources.bildirim_yeni_takipci
import com.sosyaloyun.app.resources.bildirim_sikayet_sonuclandi
import com.sosyaloyun.app.resources.bildirim_icerik_kaldirildi
import com.sosyaloyun.app.resources.tercih_sikayet
import com.sosyaloyun.app.resources.tercih_icerik_kaldirildi
import com.sosyaloyun.app.resources.bildirimler
import com.sosyaloyun.app.resources.bildirimler_bos
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.app.resources.tercih_arkadaslik_istegi
import com.sosyaloyun.app.resources.tercih_arkadaslik_kabulu
import com.sosyaloyun.app.resources.tercih_begeni
import com.sosyaloyun.app.resources.tercih_davet
import com.sosyaloyun.app.resources.tercih_yeni_takipci
import com.sosyaloyun.shared.Bildirim
import com.sosyaloyun.shared.BildirimTuru
import org.jetbrains.compose.resources.stringResource

/** Bildirim listesi; okunmamışlar vurgulu. Beğeni bildirimi günlük kaydına, diğerleri kişinin profiline gider. */
@Composable
fun BildirimlerGorunumu(
    viewModel: BildirimlerViewModel,
    kullaniciyaGit: (String) -> Unit,
    gunlukKaydinaGit: (String) -> Unit,
    geri: () -> Unit,
) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        Text(stringResource(Res.string.bildirimler), style = MaterialTheme.typography.headlineSmall)
        when (val durum = ekran) {
            BildirimlerEkrani.Yukleniyor -> CircularProgressIndicator()
            BildirimlerEkrani.Hata -> Column {
                Text(stringResource(Res.string.durum_hata))
                TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
            }
            is BildirimlerEkrani.Hazir -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (durum.bildirimler.isEmpty()) item { Text(stringResource(Res.string.bildirimler_bos)) }
                items(durum.bildirimler, key = { it.id }) { bildirim ->
                    BildirimSatiri(bildirim) {
                        val kayit = bildirim.gunlukKaydiId
                        when {
                            bildirim.tur == BildirimTuru.BEGENI && kayit != null -> gunlukKaydinaGit(kayit)
                            else -> bildirim.kisi?.let { kullaniciyaGit(it.kullaniciAdi) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BildirimSatiri(bildirim: Bildirim, tikla: () -> Unit) {
    val arkaPlan = if (bildirim.okundu) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer
    Row(
        Modifier.fillMaxWidth().background(arkaPlan).clickable(onClick = tikla).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bildirim.kisi?.let { AvatarGorunumu(it.avatar, boyut = 36.dp) }
        val ad = bildirim.kisi?.gorunenAd.orEmpty()
        Text(
            when (bildirim.tur) {
                BildirimTuru.YENI_TAKIPCI -> stringResource(Res.string.bildirim_yeni_takipci, ad)
                BildirimTuru.ARKADASLIK_ISTEGI -> stringResource(Res.string.bildirim_arkadaslik_istegi, ad)
                BildirimTuru.ARKADASLIK_KABULU -> stringResource(Res.string.bildirim_arkadaslik_kabulu, ad)
                BildirimTuru.BEGENI -> if (bildirim.digerSayisi > 0) {
                    stringResource(Res.string.bildirim_begeni_grup, ad, bildirim.digerSayisi)
                } else {
                    stringResource(Res.string.bildirim_begeni, ad)
                }
                BildirimTuru.DAVET_KODU_KULLANILDI -> stringResource(Res.string.bildirim_davet, ad)
                BildirimTuru.SIKAYET_SONUCLANDI -> stringResource(Res.string.bildirim_sikayet_sonuclandi)
                BildirimTuru.ICERIK_KALDIRILDI -> stringResource(Res.string.bildirim_icerik_kaldirildi)
                BildirimTuru.YORUM -> stringResource(Res.string.bildirim_yorum, ad)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Ayarlarda tür başına push anahtarları; tercihler yüklenemezse gösterilmez. */
@Composable
fun BildirimTercihleriBolumu(viewModel: BildirimTercihleriViewModel) {
    val tercihler by viewModel.tercihler.collectAsStateWithLifecycle()
    val mevcut = tercihler ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.bildirim_tercihleri), style = MaterialTheme.typography.titleMedium)
        // Sunucu kapalı fazın türlerini döndürmez; yalnızca dönenler gösterilir.
        BildirimTuru.entries.filter { it in mevcut }.forEach { tur ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when (tur) {
                            BildirimTuru.YENI_TAKIPCI -> Res.string.tercih_yeni_takipci
                            BildirimTuru.ARKADASLIK_ISTEGI -> Res.string.tercih_arkadaslik_istegi
                            BildirimTuru.ARKADASLIK_KABULU -> Res.string.tercih_arkadaslik_kabulu
                            BildirimTuru.BEGENI -> Res.string.tercih_begeni
                            BildirimTuru.DAVET_KODU_KULLANILDI -> Res.string.tercih_davet
                            BildirimTuru.SIKAYET_SONUCLANDI -> Res.string.tercih_sikayet
                            BildirimTuru.ICERIK_KALDIRILDI -> Res.string.tercih_icerik_kaldirildi
                            BildirimTuru.YORUM -> Res.string.tercih_yorum
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = mevcut[tur] ?: true, onCheckedChange = { viewModel.degistir(tur, it) })
            }
        }
    }
}
