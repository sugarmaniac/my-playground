package com.sosyaloyun.app.gunluk

import com.sosyaloyun.app.tema.Kart
import com.sosyaloyun.app.tema.GeriDugmesi
import com.sosyaloyun.app.tema.DahaMenusu
import com.sosyaloyun.app.akis.goreliZaman
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.sosyaloyun.app.tema.Ikonlar
import com.sosyaloyun.app.tema.Renk
import com.sosyaloyun.app.resources.begen
import androidx.compose.foundation.clickable
import com.sosyaloyun.shared.Yorum
import com.sosyaloyun.app.resources.yorumlar_baslik
import com.sosyaloyun.app.resources.yorum_yok
import com.sosyaloyun.app.resources.yorum_yaz
import com.sosyaloyun.app.resources.yorum_sil
import com.sosyaloyun.app.resources.yorum_silme_hata
import com.sosyaloyun.app.resources.yorum_hata
import com.sosyaloyun.app.resources.yorum_yasagi_hata
import com.sosyaloyun.app.resources.yorum_gonder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.resources.sikayet_et
import com.sosyaloyun.app.sikayet.SikayetHedefi
import com.sosyaloyun.app.sikayet.SikayetPenceresi
import com.sosyaloyun.app.sikayet.SikayetViewModel
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.akis_spoiler
import com.sosyaloyun.app.resources.akis_spoiler_goster
import com.sosyaloyun.app.resources.begenenler
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.paylas
import com.sosyaloyun.app.hesap.paylasici
import com.sosyaloyun.app.resources.gunluk_kaydi_bulunamadi
import com.sosyaloyun.shared.GunlukKaydi
import org.jetbrains.compose.resources.stringResource

/**
 * Günlük kaydı detayı (#65): üstte geri ve ⋯ (paylaş, şikayet et); sahibi, oyun kartı, inceleme, beğeni, beğenenler,
 * yorumlar. Yorum yazma çubuğu altta sabit.
 */
@Composable
fun GunlukDetayGorunumu(viewModel: GunlukDetayViewModel, api: ApiIstemcisi, oyunaGit: (String) -> Unit, kullaniciyaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    val hazir = ekran as? GunlukDetayEkrani.Hazir
    var sikayetAcik by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GeriDugmesi(geri)
            Spacer(Modifier.weight(1f))
            hazir?.let { durum ->
                val paylas = paylasici()
                val paylasEtiketi = stringResource(Res.string.paylas)
                val sikayetEtiketi = stringResource(Res.string.sikayet_et)
                DahaMenusu(
                    listOfNotNull(
                        durum.paylasimLinki?.let { link -> paylasEtiketi to { paylas(link) } },
                        sikayetEtiketi to { sikayetAcik = true },
                    ),
                )
            }
        }
        when (val durum = ekran) {
            GunlukDetayEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            GunlukDetayEkrani.Bulunamadi -> Text(stringResource(Res.string.gunluk_kaydi_bulunamadi), modifier = Modifier.padding(24.dp))
            GunlukDetayEkrani.Hata -> Text(stringResource(Res.string.durum_hata), modifier = Modifier.padding(24.dp))
            is GunlukDetayEkrani.Hazir -> {
                val (kayit, oyun, sahip) = durum.detay
                if (sikayetAcik) {
                    SikayetPenceresi(
                        viewModel(key = "sikayet-${kayit.id}") { SikayetViewModel(api, SikayetHedefi(sahip.kullaniciAdi, kayit.id)) },
                        kapat = { sikayetAcik = false },
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(Modifier.clickable { kullaniciyaGit(sahip.kullaniciAdi) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarGorunumu(sahip.avatar, boyut = 44.dp)
                            Column {
                                Text(sahip.gorunenAd, style = MaterialTheme.typography.titleMedium)
                                Text("@${sahip.kullaniciAdi} · ${goreliZaman(kayit.olusturulma)}", style = MaterialTheme.typography.bodySmall, color = Renk.Soluk)
                            }
                        }
                    }
                    item {
                        Kart(tikla = { oyunaGit(oyun.id) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Kapak(oyun, Modifier.size(width = 72.dp, height = 96.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(oyun.ad, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    kayit.puan?.let { YildizPuani(it, boyut = 20) }
                                    val tarihler = listOfNotNull(kayit.baslama, kayit.bitis).map(::tarihMetni).joinToString(" – ")
                                    val ayrinti = listOf(tarihler, kayit.platform.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                                    if (ayrinti.isNotEmpty()) Text(ayrinti, style = MaterialTheme.typography.bodySmall, color = Renk.Soluk)
                                }
                            }
                        }
                    }
                    kayit.inceleme?.let { item { Inceleme(it, kayit.spoiler, kisalt = false) } }
                    item {
                        BegeniButonu(kayit, viewModel::begeniDegistir)
                    }
                    if (durum.begenenler.isNotEmpty()) {
                        item { Text(stringResource(Res.string.begenenler), style = MaterialTheme.typography.titleSmall, color = Renk.Soluk) }
                        items(durum.begenenler, key = { it.kullaniciId }) { kisi -> KullaniciSatiri(kisi) { kullaniciyaGit(kisi.kullaniciAdi) } }
                    }
                    durum.yorumlar?.let { yorumlar ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(color = Renk.Cizgi)
                                Text(stringResource(Res.string.yorumlar_baslik, yorumlar.size), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (yorumlar.isEmpty()) item { Text(stringResource(Res.string.yorum_yok), style = MaterialTheme.typography.bodySmall, color = Renk.Soluk) }
                        items(yorumlar, key = { "yorum-${it.id}" }) { yorum ->
                            YorumSatiri(yorum, api, kullaniciyaGit = { kullaniciyaGit(yorum.kisi.kullaniciAdi) }, sil = { viewModel.yorumSil(yorum.id) })
                        }
                    }
                }
                if (durum.yorumlar != null) YorumYazmaCubugu(durum, viewModel)
            }
        }
    }
}

/** Kalp ve beğeni sayısı; beğenilmişse dolu kalp. */
@Composable
fun BegeniButonu(kayit: GunlukKaydi, tikla: () -> Unit) {
    val renk = if (kayit.begendim) Renk.Vurgu else Renk.Soluk
    TextButton(onClick = tikla, colors = ButtonDefaults.textButtonColors(contentColor = renk)) {
        Icon(if (kayit.begendim) Ikonlar.KalpDolu else Ikonlar.Kalp, contentDescription = stringResource(Res.string.begen), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(kayit.begeniSayisi.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

/** Spoiler içeren inceleme dokunulana kadar gizli (spec #1, hikaye 39). */
@Composable
fun Inceleme(metin: String, spoiler: Boolean, kisalt: Boolean = true) {
    var acik by remember { mutableStateOf(!spoiler) }
    if (acik) {
        if (kisalt) {
            Text(metin, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        } else {
            Text(metin, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        TextButton(onClick = { acik = true }) { Text("${stringResource(Res.string.akis_spoiler)} · ${stringResource(Res.string.akis_spoiler_goster)}") }
    }
}

@Composable
private fun YorumSatiri(yorum: Yorum, api: ApiIstemcisi, kullaniciyaGit: () -> Unit, sil: () -> Unit) {
    var sikayetAcik by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        AvatarGorunumu(yorum.kisi.avatar, boyut = 36.dp, modifier = Modifier.clickable(onClick = kullaniciyaGit))
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(yorum.kisi.gorunenAd, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = kullaniciyaGit))
                Text(goreliZaman(yorum.zaman), style = MaterialTheme.typography.labelSmall, color = Renk.Soluk)
            }
            Text(yorum.metin, style = MaterialTheme.typography.bodyMedium)
        }
        // Sil ve şikayet her satırda düğme değil, ⋯ menüsünde.
        val silEtiketi = stringResource(Res.string.yorum_sil)
        val sikayetEtiketi = stringResource(Res.string.sikayet_et)
        DahaMenusu(
            listOfNotNull(
                if (yorum.silebilirim) silEtiketi to sil else null,
                if (!yorum.benim) sikayetEtiketi to { sikayetAcik = true } else null,
            ),
            boyut = 32.dp,
        )
    }
    if (sikayetAcik) {
        SikayetPenceresi(
            viewModel(key = "sikayet-yorum-${yorum.id}") { SikayetViewModel(api, SikayetHedefi(yorum.kisi.kullaniciAdi, yorumId = yorum.id)) },
            kapat = { sikayetAcik = false },
        )
    }
}

/** Altta sabit yorum çubuğu: yuvarlak alan ve vurgu renginde gönder düğmesi. */
@Composable
private fun YorumYazmaCubugu(durum: GunlukDetayEkrani.Hazir, viewModel: GunlukDetayViewModel) {
    Column(Modifier.fillMaxWidth().background(Renk.AltMenu)) {
        HorizontalDivider(color = Renk.Cizgi)
        val hata = when {
            durum.yorumYasagi -> Res.string.yorum_yasagi_hata
            durum.yorumHatasi -> Res.string.yorum_hata
            durum.yorumSilmeHatasi -> Res.string.yorum_silme_hata
            else -> null
        }
        hata?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = durum.yorumMetni,
                onValueChange = viewModel::yorumMetniDegisti,
                placeholder = { Text(stringResource(Res.string.yorum_yaz), color = Renk.Soluk) },
                enabled = !durum.yorumGonderiliyor,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Renk.Yuzey,
                    unfocusedContainerColor = Renk.Yuzey,
                    disabledContainerColor = Renk.Yuzey,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            val etkin = durum.yorumGonderilebilir
            Surface(
                onClick = viewModel::yorumGonder,
                enabled = etkin,
                shape = CircleShape,
                color = if (etkin) Renk.Vurgu else Renk.YuzeyYuksek,
                contentColor = if (etkin) Renk.Zemin else Renk.Soluk,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (durum.yorumGonderiliyor) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Ikonlar.Gonder, contentDescription = stringResource(Res.string.yorum_gonder), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
