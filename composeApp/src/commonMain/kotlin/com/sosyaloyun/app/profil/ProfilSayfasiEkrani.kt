package com.sosyaloyun.app.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.app.gunluk.tarihMetni
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.kutuphane.durumAdi
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.arkadas_ekle
import com.sosyaloyun.app.resources.arkadasliktan_cik
import com.sosyaloyun.app.resources.durumsuz
import com.sosyaloyun.app.resources.istegi_geri_al
import com.sosyaloyun.app.resources.istek_listesi_eslesmeleri
import com.sosyaloyun.app.resources.kabul_et
import com.sosyaloyun.app.resources.ortak_oyunlar
import com.sosyaloyun.app.resources.profil_arkadas
import com.sosyaloyun.app.resources.reddet
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.profil_takip_edilen
import com.sosyaloyun.app.resources.profil_takipci
import com.sosyaloyun.app.resources.takibi_birak
import com.sosyaloyun.app.resources.takip_et
import com.sosyaloyun.app.resources.hepsi
import com.sosyaloyun.app.resources.profil_bos_sekme
import com.sosyaloyun.app.resources.profil_bu_yil
import com.sosyaloyun.app.resources.profil_duzenle
import com.sosyaloyun.app.resources.profil_sekme_gunluk
import com.sosyaloyun.app.resources.profil_sekme_incelemeler
import com.sosyaloyun.app.resources.profil_sekme_istek_listesi
import com.sosyaloyun.app.resources.profil_sekme_oyunlar
import com.sosyaloyun.app.resources.profil_vitrin
import com.sosyaloyun.app.resources.profil_vitrin_duzenle
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.ProfilSayfasi
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfilSayfasiEkrani(
    viewModel: ProfilSayfasiViewModel,
    profiliDuzenle: (ProfilSayfasi) -> Unit,
    vitriniDuzenle: (ProfilSayfasi) -> Unit,
    oyunaGit: (String) -> Unit,
    kisiListesi: (String, KisiListesiTuru) -> Unit,
    geri: (() -> Unit)? = null,
) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        geri?.let { item { TextButton(onClick = it, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(Res.string.geri)) } } }
        item {
            when (val baslik = durum.baslik) {
                ProfilBasligi.Yukleniyor -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ProfilBasligi.Hata -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(Res.string.durum_hata))
                    TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
                }
                is ProfilBasligi.Hazir -> Baslik(baslik.sayfa, profiliDuzenle, vitriniDuzenle, oyunaGit, kisiListesi, viewModel)
            }
        }
        item {
            PrimaryScrollableTabRow(selectedTabIndex = durum.sekme.ordinal, edgePadding = 16.dp) {
                ProfilSekmesi.entries.forEach { sekme ->
                    Tab(selected = durum.sekme == sekme, onClick = { viewModel.sekmeSecildi(sekme) }, text = { Text(sekmeAdi(sekme)) })
                }
            }
        }
        if (durum.sekme == ProfilSekmesi.OYUNLAR) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OyunFiltresi.entries.forEach { filtre ->
                        FilterChip(selected = durum.filtre == filtre, onClick = { viewModel.filtreSecildi(filtre) }, label = { Text(filtreAdi(filtre)) })
                    }
                }
            }
        }
        when (val icerik = durum.icerik) {
            SekmeIcerigi.Yukleniyor -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            SekmeIcerigi.Hata -> item { Text(stringResource(Res.string.durum_hata), modifier = Modifier.padding(24.dp)) }
            is SekmeIcerigi.Gunluk -> {
                if (icerik.ogeler.isEmpty()) item { BosSekme() }
                items(icerik.ogeler, key = { it.kayit.id }) { GunlukSatiri(it) { oyunaGit(it.oyun.id) } }
                if (icerik.dahaVar) item { DahaFazla(viewModel::dahaFazlaYukle) }
            }
            is SekmeIcerigi.Kutuphane -> {
                if (icerik.ogeler.isEmpty()) item { BosSekme() }
                items(icerik.ogeler, key = { it.oyun.id }) { KutuphaneSatiri(it) { oyunaGit(it.oyun.id) } }
                if (icerik.dahaVar) item { DahaFazla(viewModel::dahaFazlaYukle) }
            }
        }
    }
}

@Composable
private fun Baslik(
    sayfa: ProfilSayfasi,
    profiliDuzenle: (ProfilSayfasi) -> Unit,
    vitriniDuzenle: (ProfilSayfasi) -> Unit,
    oyunaGit: (String) -> Unit,
    kisiListesi: (String, KisiListesiTuru) -> Unit,
    viewModel: ProfilSayfasiViewModel,
) {
    val kullanici = sayfa.kullanici
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarGorunumu(kullanici.avatar, boyut = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(kullanici.gorunenAd, style = MaterialTheme.typography.titleLarge)
                Text("@${kullanici.kullaniciAdi}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        sayfa.biyografi?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.profil_takipci, sayfa.takipciSayisi),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { kisiListesi(kullanici.kullaniciAdi, KisiListesiTuru.TAKIPCILER) },
            )
            Text(
                stringResource(Res.string.profil_takip_edilen, sayfa.takipEdilenSayisi),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { kisiListesi(kullanici.kullaniciAdi, KisiListesiTuru.TAKIP_EDILENLER) },
            )
            Text(
                stringResource(Res.string.profil_arkadas, sayfa.arkadasSayisi),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { kisiListesi(kullanici.kullaniciAdi, KisiListesiTuru.ARKADASLAR) },
            )
        }
        Text(stringResource(Res.string.profil_bu_yil, sayfa.buYilBitirilen), style = MaterialTheme.typography.labelLarge)
        when (val takipEdiyorum = sayfa.takipEdiyorum) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { profiliDuzenle(sayfa) }) { Text(stringResource(Res.string.profil_duzenle)) }
                OutlinedButton(onClick = { vitriniDuzenle(sayfa) }) { Text(stringResource(Res.string.profil_vitrin_duzenle)) }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Arkadaşlık takibi kapsar; arkadaşken takipten çıkılamaz.
                if (sayfa.arkadaslik != ArkadaslikDurumu.ARKADAS) {
                    if (takipEdiyorum) {
                        OutlinedButton(onClick = viewModel::takipDegistir) { Text(stringResource(Res.string.takibi_birak)) }
                    } else {
                        Button(onClick = viewModel::takipDegistir) { Text(stringResource(Res.string.takip_et)) }
                    }
                }
                when (sayfa.arkadaslik) {
                    ArkadaslikDurumu.YOK, null -> OutlinedButton(onClick = viewModel::arkadaslikIste) { Text(stringResource(Res.string.arkadas_ekle)) }
                    ArkadaslikDurumu.ISTEK_GONDERILDI -> OutlinedButton(onClick = viewModel::arkadasligiBitir) { Text(stringResource(Res.string.istegi_geri_al)) }
                    ArkadaslikDurumu.ISTEK_ALINDI -> {
                        Button(onClick = viewModel::arkadaslikKabulEt) { Text(stringResource(Res.string.kabul_et)) }
                        TextButton(onClick = viewModel::arkadaslikReddet) { Text(stringResource(Res.string.reddet)) }
                    }
                    ArkadaslikDurumu.ARKADAS -> OutlinedButton(onClick = viewModel::arkadasligiBitir) { Text(stringResource(Res.string.arkadasliktan_cik)) }
                }
            }
        }
        val ortak by viewModel.ekran.collectAsStateWithLifecycle()
        ortak.ortak?.let { OrtakOyunlarBolumu(it, oyunaGit) }
        Text(stringResource(Res.string.profil_vitrin), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i ->
                val oyun = sayfa.vitrin.getOrNull(i)
                Kapak(oyun, Modifier.weight(1f).clickable(enabled = oyun != null) { oyun?.let { oyunaGit(it.id) } })
            }
        }
    }
}

/** Arkadaşın profilinde ortak oyunlar ve onun bitirip senin istek listende olanlar. */
@Composable
private fun OrtakOyunlarBolumu(ortak: OrtakOyunlar, oyunaGit: (String) -> Unit) {
    listOf(Res.string.ortak_oyunlar to ortak.ortakOyunlar, Res.string.istek_listesi_eslesmeleri to ortak.istekListesiEslesmeleri)
        .filter { it.second.isNotEmpty() }
        .forEach { (baslik, oyunlar) ->
            Text(stringResource(baslik), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                oyunlar.forEach { oyun -> Kapak(oyun, Modifier.clickable { oyunaGit(oyun.id) }) }
            }
        }
}

@Composable
fun Kapak(oyun: OyunOzeti?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 72.dp, height = 96.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (oyun != null) {
            AsyncImage(model = oyun.kapakUrl, contentDescription = oyun.ad, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (oyun.kapakUrl == null) Text(oyun.ad, style = MaterialTheme.typography.labelSmall, maxLines = 3, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun GunlukSatiri(oge: GunlukOgesi, tikla: () -> Unit) {
    val kayit = oge.kayit
    Row(Modifier.fillMaxWidth().clickable(onClick = tikla).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Kapak(oge.oyun, Modifier.size(width = 48.dp, height = 64.dp))
        Column(Modifier.weight(1f)) {
            Text(oge.oyun.ad, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tarih = kayit.bitis ?: kayit.baslama
            Text(listOfNotNull(tarih?.let(::tarihMetni), kayit.platform).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            kayit.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
            kayit.inceleme?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun KutuphaneSatiri(oge: KutuphaneOgesi, tikla: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = tikla).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kapak(oge.oyun, Modifier.size(width = 48.dp, height = 64.dp))
        Column(Modifier.weight(1f)) {
            Text(oge.oyun.ad, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            oge.giris.durum?.let { Text(durumAdi(it), style = MaterialTheme.typography.labelMedium) }
            oge.giris.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
        }
    }
}

@Composable
private fun BosSekme() {
    Text(stringResource(Res.string.profil_bos_sekme), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
}

@Composable
private fun DahaFazla(yukle: () -> Unit) {
    TextButton(onClick = yukle, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) }
}

@Composable
private fun sekmeAdi(sekme: ProfilSekmesi): String = stringResource(
    when (sekme) {
        ProfilSekmesi.GUNLUK -> Res.string.profil_sekme_gunluk
        ProfilSekmesi.OYUNLAR -> Res.string.profil_sekme_oyunlar
        ProfilSekmesi.INCELEMELER -> Res.string.profil_sekme_incelemeler
        ProfilSekmesi.ISTEK_LISTESI -> Res.string.profil_sekme_istek_listesi
    },
)

@Composable
private fun filtreAdi(filtre: OyunFiltresi): String = when (filtre) {
    OyunFiltresi.HEPSI -> stringResource(Res.string.hepsi)
    OyunFiltresi.DURUMSUZ -> stringResource(Res.string.durumsuz)
    OyunFiltresi.OYNUYORUM -> durumAdi(Durum.OYNUYORUM)
    OyunFiltresi.BITIRDIM -> durumAdi(Durum.BITIRDIM)
    OyunFiltresi.BIRAKTIM -> durumAdi(Durum.BIRAKTIM)
    OyunFiltresi.BEKLEMEDE -> durumAdi(Durum.BEKLEMEDE)
}
