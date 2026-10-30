package com.sosyaloyun.app.oyun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.gunluk.Inceleme
import com.sosyaloyun.app.gunluk.dakikadanSaate
import com.sosyaloyun.app.hesap.AvatarGorunumu
import com.sosyaloyun.app.kesif.igdbTurAdi
import com.sosyaloyun.app.gunluk.gorunurlukAdi
import com.sosyaloyun.app.gunluk.tarihMetni
import com.sosyaloyun.app.kutuphane.HizliKayitSayfasi
import com.sosyaloyun.app.kutuphane.HizliKayitViewModel
import com.sosyaloyun.app.kutuphane.YildizPuani
import com.sosyaloyun.app.kutuphane.durumAdi
import coil3.compose.AsyncImage
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.oyun_arkadas_ortalamasi
import com.sosyaloyun.app.resources.oyun_arkadasin
import com.sosyaloyun.app.resources.oyun_incelemeler
import com.sosyaloyun.app.resources.oyun_oynayan_arkadas
import com.sosyaloyun.app.resources.oyun_topluluk_puani
import com.sosyaloyun.app.resources.oyun_yeterli_puan_yok
import com.sosyaloyun.app.resources.gunluk_ekle
import com.sosyaloyun.app.resources.gunluk_saat
import com.sosyaloyun.app.resources.gunluk_toplam_sure
import com.sosyaloyun.app.resources.igdb_atfi
import com.sosyaloyun.app.resources.kayit_ac
import com.sosyaloyun.app.resources.kayit_begendim
import com.sosyaloyun.app.resources.kayit_duzenle
import com.sosyaloyun.app.resources.kayit_istek_listesi
import com.sosyaloyun.app.resources.kayit_yuzde_yuz
import com.sosyaloyun.app.resources.magaza_app_store
import com.sosyaloyun.app.resources.magaza_epic
import com.sosyaloyun.app.resources.magaza_gog
import com.sosyaloyun.app.resources.magaza_google_play
import com.sosyaloyun.app.resources.magaza_itch
import com.sosyaloyun.app.resources.magaza_resmi_site
import com.sosyaloyun.app.resources.magaza_steam
import com.sosyaloyun.app.resources.oyun_ana_oyun
import com.sosyaloyun.app.resources.oyun_bulunamadi
import com.sosyaloyun.app.resources.oyun_eklentiler
import com.sosyaloyun.app.resources.oyun_gelistirici
import com.sosyaloyun.app.resources.oyun_magazalar
import com.sosyaloyun.app.resources.oyun_yayinci
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.Magaza
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunPuanlari
import com.sosyaloyun.shared.OyunOzeti
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
/** Oyun detayı; IGDB atfı ve linki başlığın hemen altında, her oyunda aynı yerde durur (ADR 0001). */
@Composable
fun OyunDetayEkrani(
    viewModel: OyunDetayViewModel,
    api: ApiIstemcisi,
    geri: () -> Unit,
    oyunaGit: (String) -> Unit,
    /** Detaylı kayıt ekranı: yeni kayıt için null, düzenleme için kaydın kimliği. */
    gunlugeYaz: (String?) -> Unit,
    kullaniciyaGit: (String) -> Unit,
    gunlukKaydinaGit: (String) -> Unit,
    /** Ortadaki kaydet butonundan gelindiyse hızlı kayıt alt sayfası hemen açılır. */
    hizliKayitAcik: Boolean = false,
) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = geri, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text(stringResource(Res.string.geri)) }
        when (val durum = ekran) {
            OyunDetayEkrani.Yukleniyor -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            OyunDetayEkrani.Bulunamadi -> Text(stringResource(Res.string.oyun_bulunamadi), modifier = Modifier.padding(24.dp))
            OyunDetayEkrani.Hata -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.durum_hata))
                Button(onClick = viewModel::yukle) { Text(stringResource(Res.string.tekrar_dene)) }
            }
            is OyunDetayEkrani.Hazir -> {
                var kayitAcik by remember { mutableStateOf(hizliKayitAcik) }
                Detay(
                    durum,
                    oyunaGit,
                    kaydet = { kayitAcik = true },
                    gunlugeYaz = gunlugeYaz,
                    topluluk = { Topluluk(durum, viewModel::dahaFazlaInceleme, kullaniciyaGit, gunlukKaydinaGit) },
                )
                if (kayitAcik) {
                    val detay = durum.detay
                    ModalBottomSheet(onDismissRequest = { kayitAcik = false }) {
                        HizliKayitSayfasi(
                            oyunAdi = detay.oyun.ad,
                            viewModel = viewModel(key = "hizli-kayit-${detay.oyun.id}") {
                                HizliKayitViewModel(api, detay.oyun.id, detay.kutuphaneGirisi) { giris ->
                                    viewModel.kutuphaneGuncellendi(giris)
                                    kayitAcik = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detay(
    durum: OyunDetayEkrani.Hazir,
    oyunaGit: (String) -> Unit,
    kaydet: () -> Unit,
    gunlugeYaz: (String?) -> Unit,
    topluluk: @Composable () -> Unit,
) {
    val detay = durum.detay
    val uri = LocalUriHandler.current
    val oyun = detay.oyun
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = oyun.kapakUrl,
                contentDescription = oyun.ad,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(oyun.ad, style = MaterialTheme.typography.headlineSmall)
                oyun.cikisYili?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium) }
                if (oyun.platformlar.isNotEmpty()) Text(oyun.platformlar.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                oyun.anaOyun?.let { ana ->
                    Text(
                        stringResource(Res.string.oyun_ana_oyun, ana.ad),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { oyunaGit(ana.id) },
                    )
                }
            }
        }
        Puanlar(detay.puanlar)
        KutuphaneOzeti(detay.kutuphaneGirisi, kaydet)
        Gunlugum(detay, gunlugeYaz)
        Text(
            stringResource(Res.string.igdb_atfi),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uri.openUri(detay.kaynakUrl) },
        )
        if (detay.turler.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detay.turler.forEach { AssistChip(onClick = {}, label = { Text(igdbTurAdi(it)) }) }
            }
        }
        detay.aciklama?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (detay.gelistiriciler.isNotEmpty()) {
            Text(stringResource(Res.string.oyun_gelistirici, detay.gelistiriciler.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        }
        if (detay.yayincilar.isNotEmpty()) {
            Text(stringResource(Res.string.oyun_yayinci, detay.yayincilar.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        }
        if (detay.magazaLinkleri.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(Res.string.oyun_magazalar), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detay.magazaLinkleri.forEach { link ->
                    OutlinedButton(onClick = { uri.openUri(link.url) }) { Text(magazaAdi(link.magaza)) }
                }
            }
        }
        if (detay.eklentiler.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(Res.string.oyun_eklentiler), style = MaterialTheme.typography.titleSmall)
            detay.eklentiler.forEach { EklentiSatiri(it) { oyunaGit(it.id) } }
        }
        topluluk()
    }
}

/** Topluluk puanı, arkadaş ortalaması ve oynayan arkadaş sayısı. */
@Composable
private fun Puanlar(puanlar: OyunPuanlari) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            puanlar.toplulukPuani?.let { stringResource(Res.string.oyun_topluluk_puani, puanMetni(it)) }
                ?: stringResource(Res.string.oyun_yeterli_puan_yok),
            style = MaterialTheme.typography.titleSmall,
        )
        val arkadaslar = listOfNotNull(
            puanlar.arkadasOrtalamasi?.let { stringResource(Res.string.oyun_arkadas_ortalamasi, puanMetni(it)) },
            if (puanlar.oynayanArkadasSayisi > 0) stringResource(Res.string.oyun_oynayan_arkadas, puanlar.oynayanArkadasSayisi) else null,
        )
        if (arkadaslar.isNotEmpty()) Text(arkadaslar.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

/** Yarım yıldız cinsinden puanı beş üzerinden, tek ondalıkla gösterir: 7.6 → "★ 3,8". */
internal fun puanMetni(puan: Double): String {
    val onda = (puan / 2 * 10).roundToInt()
    return "★ ${onda / 10},${onda % 10}"
}

/** Önce arkadaşların, sonra herkesin incelemeleri. */
@Composable
private fun Topluluk(durum: OyunDetayEkrani.Hazir, dahaFazla: () -> Unit, kullaniciyaGit: (String) -> Unit, gunlukKaydinaGit: (String) -> Unit) {
    if (durum.incelemeler.isEmpty()) return
    HorizontalDivider()
    Text(stringResource(Res.string.oyun_incelemeler), style = MaterialTheme.typography.titleSmall)
    durum.incelemeler.forEach { (kayit, sahip, arkadas) ->
        Column(Modifier.fillMaxWidth().clickable { gunlukKaydinaGit(kayit.id) }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.clickable { kullaniciyaGit(sahip.kullaniciAdi) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarGorunumu(sahip.avatar, boyut = 28.dp)
                Text(sahip.gorunenAd, style = MaterialTheme.typography.labelLarge)
                if (arkadas) Text(stringResource(Res.string.oyun_arkadasin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            kayit.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
            kayit.inceleme?.let { Inceleme(it, kayit.spoiler) }
            if (kayit.begeniSayisi > 0) Text("♥ ${kayit.begeniSayisi}", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (durum.incelemeImleci != null) {
        TextButton(onClick = dahaFazla, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) }
    }
}

/** Kullanıcının bu oyun için kütüphane girişi ve hızlı kayıt düğmesi. */
@Composable
private fun KutuphaneOzeti(giris: KutuphaneGirisi?, kaydet: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (giris != null) {
            val isaretler = listOfNotNull(
                giris.durum?.let { durumAdi(it) },
                if (giris.yuzdeYuz) stringResource(Res.string.kayit_yuzde_yuz) else null,
                if (giris.begendim) stringResource(Res.string.kayit_begendim) else null,
                if (giris.istekListesinde) stringResource(Res.string.kayit_istek_listesi) else null,
            )
            if (isaretler.isNotEmpty()) Text(isaretler.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            giris.puan?.let { YildizPuani(it, sec = {}, boyut = 20) }
        }
        Button(onClick = kaydet) {
            Text(stringResource(if (giris == null) Res.string.kayit_ac else Res.string.kayit_duzenle))
        }
    }
}

/** Kullanıcının bu oyundaki günlük kayıtları ve toplam oyun süresi. */
@Composable
private fun Gunlugum(detay: OyunDetayi, gunlugeYaz: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { gunlugeYaz(null) }) { Text(stringResource(Res.string.gunluk_ekle)) }
        detay.toplamOyunSuresiDakika?.let {
            Text(stringResource(Res.string.gunluk_toplam_sure, dakikadanSaate(it)), style = MaterialTheme.typography.bodySmall)
        }
        detay.gunlukKayitlarim.forEach { kayit ->
            Column(Modifier.fillMaxWidth().clickable { gunlugeYaz(kayit.id) }.padding(vertical = 4.dp)) {
                val ust = listOfNotNull(
                    kayit.bitis?.let(::tarihMetni) ?: kayit.baslama?.let(::tarihMetni),
                    kayit.platform,
                    kayit.sureDakika?.let { stringResource(Res.string.gunluk_saat, dakikadanSaate(it)) },
                    gorunurlukAdi(kayit.gorunurluk),
                )
                Text(ust.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                kayit.puan?.let { YildizPuani(it, sec = {}, boyut = 16) }
                kayit.inceleme?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
            }
        }
    }
}

@Composable
private fun EklentiSatiri(oyun: OyunOzeti, tikla: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = tikla).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(oyun.ad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        oyun.cikisYili?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun magazaAdi(magaza: Magaza): String = stringResource(
    when (magaza) {
        Magaza.RESMI_SITE -> Res.string.magaza_resmi_site
        Magaza.STEAM -> Res.string.magaza_steam
        Magaza.EPIC -> Res.string.magaza_epic
        Magaza.GOG -> Res.string.magaza_gog
        Magaza.ITCH -> Res.string.magaza_itch
        Magaza.APP_STORE -> Res.string.magaza_app_store
        Magaza.GOOGLE_PLAY -> Res.string.magaza_google_play
    },
)
