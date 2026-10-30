package com.sosyaloyun.app.hesap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.cikis_yap
import com.sosyaloyun.app.resources.hesap_sil
import com.sosyaloyun.app.resources.davet_kodlarim_baslik
import com.sosyaloyun.app.resources.davet_kodlarim_aciklama
import com.sosyaloyun.app.resources.davet_kodu_bekliyor
import com.sosyaloyun.app.resources.davet_kodu_baslik
import com.sosyaloyun.app.resources.davet_kodu_aciklama
import com.sosyaloyun.app.resources.davet_kodu_alani
import com.sosyaloyun.app.resources.davet_kodu_gonder
import com.sosyaloyun.app.resources.davet_kodu_hata_baglanti
import com.sosyaloyun.app.resources.davet_kodu_hata_gecersiz
import com.sosyaloyun.app.resources.davet_kodu_hata_kullanilmis
import com.sosyaloyun.app.resources.davet_kodu_kullanildi
import com.sosyaloyun.app.resources.davet_kodu_kullanan
import com.sosyaloyun.app.resources.davet_paylas
import com.sosyaloyun.app.resources.davet_paylas_metni
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.hosgeldin
import com.sosyaloyun.app.resources.kullanici_adi_alani
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.kullanici_adi_alinmis
import com.sosyaloyun.app.resources.kullanici_adi_baglanti
import com.sosyaloyun.app.resources.kullanici_adi_gecersiz
import com.sosyaloyun.app.resources.kullanici_adi_kontrol
import com.sosyaloyun.app.resources.kullanici_adi_musait
import com.sosyaloyun.app.resources.gorunen_ad_alani
import com.sosyaloyun.app.resources.profil_avatar
import com.sosyaloyun.app.resources.profil_baslik
import com.sosyaloyun.app.resources.profil_biyografi
import com.sosyaloyun.app.resources.profil_duzenle
import com.sosyaloyun.app.resources.profil_baglanti_hatasi
import com.sosyaloyun.app.resources.profil_kaydet
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.bildirim.BildirimTercihleriBolumu
import com.sosyaloyun.app.gizlilik.GizlilikBolumu
import com.sosyaloyun.app.gizlilik.GizlilikViewModel
import com.sosyaloyun.app.bildirim.BildirimTercihleriViewModel
import com.sosyaloyun.app.onboarding.SteamAyariViewModel
import com.sosyaloyun.app.onboarding.SteamBolumu
import com.sosyaloyun.app.resources.engellenenler
import com.sosyaloyun.app.resources.sessize_alinanlar
import org.jetbrains.compose.resources.stringResource

@Composable
fun YukleniyorGorunumu() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
fun HataGorunumu(tekrarDene: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.durum_hata))
        Button(onClick = tekrarDene) { Text(stringResource(Res.string.tekrar_dene)) }
    }
}

@Composable
fun DavetKoduEkrani(viewModel: DavetKoduViewModel) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.davet_kodu_baslik), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.davet_kodu_aciklama))
        OutlinedTextField(
            value = durum.kod,
            onValueChange = viewModel::kodDegisti,
            label = { Text(stringResource(Res.string.davet_kodu_alani)) },
            singleLine = true,
            isError = durum.hata != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        durum.hata?.let { hata ->
            Text(
                stringResource(
                    when (hata) {
                        DavetKoduHatasi.GECERSIZ -> Res.string.davet_kodu_hata_gecersiz
                        DavetKoduHatasi.KULLANILMIS -> Res.string.davet_kodu_hata_kullanilmis
                        DavetKoduHatasi.BAGLANTI -> Res.string.davet_kodu_hata_baglanti
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = viewModel::gonder, enabled = durum.gonderilebilir, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.davet_kodu_gonder))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilEkrani(viewModel: ProfilViewModel) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (durum.duzenleme) Res.string.profil_duzenle else Res.string.profil_baslik),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = durum.kullaniciAdi,
            onValueChange = viewModel::kullaniciAdiDegisti,
            label = { Text(stringResource(Res.string.kullanici_adi_alani)) },
            prefix = { Text("@") },
            singleLine = true,
            isError = durum.musaitlik in setOf(Musaitlik.ALINMIS, Musaitlik.GECERSIZ),
            supportingText = {
                val metin = when (durum.musaitlik) {
                    Musaitlik.BILINMIYOR -> null
                    Musaitlik.KONTROL_EDILIYOR -> Res.string.kullanici_adi_kontrol
                    Musaitlik.MUSAIT -> Res.string.kullanici_adi_musait
                    Musaitlik.ALINMIS -> Res.string.kullanici_adi_alinmis
                    Musaitlik.GECERSIZ -> Res.string.kullanici_adi_gecersiz
                    Musaitlik.BAGLANTI_YOK -> Res.string.kullanici_adi_baglanti
                }
                metin?.let { Text(stringResource(it)) }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = durum.gorunenAd,
            onValueChange = viewModel::gorunenAdDegisti,
            label = { Text(stringResource(Res.string.gorunen_ad_alani)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (durum.duzenleme) {
            OutlinedTextField(
                value = durum.biyografi,
                onValueChange = viewModel::biyografiDegisti,
                label = { Text(stringResource(Res.string.profil_biyografi)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(Res.string.profil_avatar), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar.entries.forEach { avatar ->
                AvatarGorunumu(
                    avatar = avatar,
                    secili = avatar == durum.avatar,
                    modifier = Modifier.clickable { viewModel.avatarSecildi(avatar) },
                )
            }
        }
        if (durum.baglantiHatasi) Text(stringResource(Res.string.profil_baglanti_hatasi), color = MaterialTheme.colorScheme.error)
        Button(onClick = viewModel::kaydet, enabled = durum.kaydedilebilir, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.profil_kaydet))
        }
    }
}

@Composable
fun AnaEkran(
    profil: KullaniciOzeti,
    davetKodlari: DavetKodlarimViewModel,
    geri: () -> Unit,
    steamAyari: SteamAyariViewModel,
    bildirimTercihleri: BildirimTercihleriViewModel,
    engelListesi: (EngelListesiTuru) -> Unit,
    cikisYap: () -> Unit,
    hesapSilme: @Composable () -> HesapSilmeViewModel,
    /** Faz 2 açıksa gizlilik bölümü (#48); kapalıyken null. */
    gizlilik: (@Composable () -> GizlilikViewModel)? = null,
) {
    val ekran by davetKodlari.ekran.collectAsStateWithLifecycle()
    var silmeAcik by remember { mutableStateOf(false) }
    if (silmeAcik) HesapSilmePenceresi(hesapSilme(), profil.kullaniciAdi, kapat = { silmeAcik = false })
    val paylas = paylasici()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarGorunumu(profil.avatar, boyut = 56.dp)
            Column {
                Text(stringResource(Res.string.hosgeldin, profil.gorunenAd), style = MaterialTheme.typography.titleLarge)
                Text("@${profil.kullaniciAdi}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider()
        Text(stringResource(Res.string.davet_kodlarim_baslik), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.davet_kodlarim_aciklama), style = MaterialTheme.typography.bodySmall)
        when (val durum = ekran) {
            DavetKodlarimEkrani.Yukleniyor -> CircularProgressIndicator()
            DavetKodlarimEkrani.Hata -> TextButton(onClick = davetKodlari::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
            is DavetKodlarimEkrani.Hazir -> LazyColumn(Modifier.weight(1f)) {
                items(durum.kodlar, key = { it.kod }) { kod ->
                    DavetKoduSatiri(kod, paylas)
                }
            }
        }
        HorizontalDivider()
        SteamBolumu(steamAyari)
        HorizontalDivider()
        BildirimTercihleriBolumu(bildirimTercihleri)
        HorizontalDivider()
        gizlilik?.let {
            GizlilikBolumu(it())
            HorizontalDivider()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { engelListesi(EngelListesiTuru.ENGELLENENLER) }) { Text(stringResource(Res.string.engellenenler)) }
            TextButton(onClick = { engelListesi(EngelListesiTuru.SESSIZE_ALINANLAR) }) { Text(stringResource(Res.string.sessize_alinanlar)) }
        }
        TextButton(onClick = cikisYap, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.cikis_yap))
        }
        TextButton(onClick = { silmeAcik = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.hesap_sil), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DavetKoduSatiri(kod: DavetKodu, paylas: (String) -> Unit) {
    val paylasMetni = stringResource(Res.string.davet_paylas_metni, kod.kod)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kod.kod, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    kod.kullanan != null -> stringResource(Res.string.davet_kodu_kullanan, kod.kullanan!!.gorunenAd, kod.kullanan!!.kullaniciAdi)
                    kod.kullanildi -> stringResource(Res.string.davet_kodu_kullanildi)
                    else -> stringResource(Res.string.davet_kodu_bekliyor)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!kod.kullanildi) TextButton(onClick = { paylas(paylasMetni) }) { Text(stringResource(Res.string.davet_paylas)) }
    }
}
