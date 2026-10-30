package com.sosyaloyun.app.akis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.sosyaloyun.app.resources.akis_eylem_arkadas
import com.sosyaloyun.app.resources.akis_eylem_basladi
import com.sosyaloyun.app.resources.akis_eylem_gunluk
import com.sosyaloyun.app.resources.akis_sure_saat
import com.sosyaloyun.app.resources.zaman_dakika
import com.sosyaloyun.app.resources.zaman_gun
import com.sosyaloyun.app.resources.zaman_saat
import com.sosyaloyun.app.resources.zaman_simdi
import com.sosyaloyun.app.tema.HapSekme
import com.sosyaloyun.app.tema.Ikonlar
import com.sosyaloyun.app.tema.Kart
import com.sosyaloyun.app.tema.Renk
import kotlin.time.Clock
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.gunluk.BegeniButonu
import com.sosyaloyun.app.gunluk.Inceleme
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.akis_bos
import com.sosyaloyun.app.resources.akis_istek_listemde
import com.sosyaloyun.app.resources.akis_istek_listeme_ekle
import com.sosyaloyun.app.resources.akis_yeni
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.arkadaslar
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.sekme_takip
import com.sosyaloyun.app.resources.takip_et
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.AkisSekmesi
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Yeni olaylar ekran açıkken bu aralıkla denetlenir. */
private const val YENI_DENETIM_MS = 60_000L

@Composable
fun AkisEkrani(
    viewModel: AkisViewModel,
    ustCubuk: @Composable () -> Unit,
    oyunaGit: (String) -> Unit,
    kullaniciyaGit: (String) -> Unit,
    gunlukKaydinaGit: (String) -> Unit,
) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        while (true) {
            delay(YENI_DENETIM_MS)
            viewModel.yeniOlaylariDenetle()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ustCubuk()
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HapSekme(stringResource(Res.string.sekme_takip), durum.sekme == AkisSekmesi.TAKIP) { viewModel.sekmeSecildi(AkisSekmesi.TAKIP) }
            HapSekme(stringResource(Res.string.arkadaslar), durum.sekme == AkisSekmesi.ARKADASLAR) { viewModel.sekmeSecildi(AkisSekmesi.ARKADASLAR) }
        }
        if (durum.yeniOlaySayisi > 0) {
            Button(
                onClick = viewModel::yenile,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).height(32.dp),
            ) {
                Icon(Ikonlar.YukariOk, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.akis_yeni, durum.yeniOlaySayisi), style = MaterialTheme.typography.labelMedium)
            }
        }
        when (val icerik = durum.icerik) {
            AkisIcerigi.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AkisIcerigi.Hata -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.durum_hata))
                TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
            }
            is AkisIcerigi.Bos -> LazyColumn(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(Res.string.akis_bos), style = MaterialTheme.typography.bodyMedium, color = Renk.Soluk) }
                items(icerik.oneriler, key = { it.kullaniciId }) { kisi ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { KullaniciSatiri(kisi) { kullaniciyaGit(kisi.kullaniciAdi) } }
                        Button(onClick = { viewModel.onerilenKisiyiTakipEt(kisi.kullaniciAdi) }) { Text(stringResource(Res.string.takip_et)) }
                    }
                }
            }
            is AkisIcerigi.Ogeler -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(icerik.ogeler, key = { it.id }) { oge -> AkisKarti(oge, viewModel, oyunaGit, kullaniciyaGit, gunlukKaydinaGit) }
                if (icerik.dahaVar) {
                    item { TextButton(onClick = viewModel::dahaFazlaYukle, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) } }
                }
            }
        }
    }
}

@Composable
private fun AkisKarti(oge: AkisOgesi, viewModel: AkisViewModel, oyunaGit: (String) -> Unit, kullaniciyaGit: (String) -> Unit, gunlukKaydinaGit: (String) -> Unit) {
    Kart(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clickable { kullaniciyaGit(oge.kisi.kullaniciAdi) },
        ) {
            AvatarGorunumu(oge.kisi.avatar, boyut = 36.dp)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(oge.kisi.gorunenAd) }
                    withStyle(SpanStyle(color = Renk.Soluk)) {
                        append(" ")
                        append(
                            when (oge.tur) {
                                AkisOlayTuru.GUNLUK_KAYDI -> stringResource(Res.string.akis_eylem_gunluk)
                                AkisOlayTuru.OYNAMAYA_BASLADI -> stringResource(Res.string.akis_eylem_basladi)
                                AkisOlayTuru.ARKADAS_OLDU -> stringResource(Res.string.akis_eylem_arkadas, oge.arkadas?.gorunenAd.orEmpty())
                            },
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(goreliZaman(oge.zaman), style = MaterialTheme.typography.labelMedium, color = Renk.Soluk)
        }
        oge.oyun?.let { oyun ->
            Row(Modifier.fillMaxWidth().clickable { oyunaGit(oyun.id) }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Kapak(oyun, Modifier.size(width = 64.dp, height = 86.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(oyun.ad, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    oge.gunlukKaydi?.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
                    val ayrinti = listOfNotNull(
                        oge.gunlukKaydi?.sureDakika?.let { stringResource(Res.string.akis_sure_saat, (it + 30) / 60) },
                        oge.gunlukKaydi?.platform,
                    ).joinToString(" · ")
                    if (ayrinti.isNotEmpty()) Text(ayrinti, style = MaterialTheme.typography.bodySmall, color = Renk.Soluk)
                }
            }
            oge.gunlukKaydi?.let { kayit ->
                Column(Modifier.clickable { gunlukKaydinaGit(kayit.id) }.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kayit.inceleme?.let { Inceleme(it, kayit.spoiler) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                oge.gunlukKaydi?.let { kayit -> BegeniButonu(kayit) { viewModel.begeniDegistir(kayit.id) } }
                Spacer(Modifier.weight(1f))
                if (oge.istekListemde) {
                    Text(stringResource(Res.string.akis_istek_listemde), style = MaterialTheme.typography.labelMedium, color = Renk.Vurgu)
                } else {
                    OutlinedButton(
                        onClick = { viewModel.istekListesineEkle(oyun.id) },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, Renk.Cizgi),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Renk.Yazi),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text(stringResource(Res.string.akis_istek_listeme_ekle), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/** "şimdi", "12 dk", "2 sa", "3 g"; bir haftadan eskiyse tarih. */
@Composable
private fun goreliZaman(zaman: Instant): String {
    val fark = Clock.System.now() - zaman
    return when {
        fark.inWholeMinutes < 1 -> stringResource(Res.string.zaman_simdi)
        fark.inWholeHours < 1 -> stringResource(Res.string.zaman_dakika, fark.inWholeMinutes.toInt())
        fark.inWholeDays < 1 -> stringResource(Res.string.zaman_saat, fark.inWholeHours.toInt())
        fark.inWholeDays < 7 -> stringResource(Res.string.zaman_gun, fark.inWholeDays.toInt())
        else -> zamanMetni(zaman)
    }
}

private fun zamanMetni(zaman: Instant): String {
    val yerel = zaman.toLocalDateTime(TimeZone.currentSystemDefault())
    fun iki(sayi: Int) = sayi.toString().padStart(2, '0')
    return "${iki(yerel.day)}.${iki(yerel.month.ordinal + 1)}.${yerel.year} ${iki(yerel.hour)}:${iki(yerel.minute)}"
}
