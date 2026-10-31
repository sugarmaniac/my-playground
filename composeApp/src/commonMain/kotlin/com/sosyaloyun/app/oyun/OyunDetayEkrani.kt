package com.sosyaloyun.app.oyun

import com.sosyaloyun.app.tema.GeriDugmesi
import com.sosyaloyun.app.tema.SigdirilanYazi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.sosyaloyun.app.profil.Kapak
import com.sosyaloyun.app.tema.Ikonlar
import com.sosyaloyun.app.tema.Renk
import com.sosyaloyun.app.tema.Iskelet
import com.sosyaloyun.shared.Durum
import com.sosyaloyun.app.resources.oyun_topluluk
import com.sosyaloyun.app.resources.oyun_arkadaslar
import com.sosyaloyun.app.resources.oyun_oynayan
import com.sosyaloyun.app.resources.oyun_puan_yok
import com.sosyaloyun.app.resources.oyun_senin_puanin
import com.sosyaloyun.app.resources.begeni_sayisi_kisa
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
import com.sosyaloyun.app.resources.oyun_arkadasin
import com.sosyaloyun.app.resources.oyun_incelemeler
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
    /** Fazlara bağlı ek bölümler (ör. Faz 2'de bu oyunun geçtiği listeler); topluluğun altında. */
    ekBolumler: @Composable () -> Unit = {},
) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val ozet = (ekran as? OyunDetayEkrani.Yukleniyor)?.ozet
        if (ekran !is OyunDetayEkrani.Hazir && ozet == null) GeriDugmesi(geri, Modifier.padding(16.dp))
        when (val durum = ekran) {
            is OyunDetayEkrani.Yukleniyor -> if (ozet != null) {
                DetayIskeleti(ozet, geri, oyunaGit)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            OyunDetayEkrani.Bulunamadi -> Text(stringResource(Res.string.oyun_bulunamadi), modifier = Modifier.padding(24.dp))
            OyunDetayEkrani.Hata -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.durum_hata))
                Button(onClick = viewModel::yukle) { Text(stringResource(Res.string.tekrar_dene)) }
            }
            is OyunDetayEkrani.Hazir -> {
                var kayitAcik by remember { mutableStateOf(hizliKayitAcik) }
                Detay(
                    durum,
                    geri,
                    oyunaGit,
                    kaydet = { kayitAcik = true },
                    gunlugeYaz = gunlugeYaz,
                    topluluk = {
                        Topluluk(durum, viewModel::dahaFazlaInceleme, kullaniciyaGit, gunlukKaydinaGit)
                        ekBolumler()
                    },
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

/** Üst bant, kapak ve künye; [detay] gelmeden özetle de çizilir (#60). */
@Composable
private fun Bant(oyun: OyunOzeti, detay: OyunDetayi?, geri: () -> Unit, oyunaGit: (String) -> Unit) {
    // Tuvaldeki üst bant: kapağın bulanık olmayan kırpılmış hali ya da oyuna özgü renk; kapak bandın üstüne biner.
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(BANT_YUKSEKLIGI).background(Renk.avatarZemini(oyun.ad.hashCode()))) {
            AsyncImage(model = oyun.kapakUrl, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.45f, modifier = Modifier.fillMaxSize())
            GeriDugmesi(geri, Modifier.padding(16.dp))
        }
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = BANT_YUKSEKLIGI - KAPAK_TASMASI),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Kapak(oyun, Modifier.size(width = 96.dp, height = 128.dp).border(3.dp, Renk.Zemin, RoundedCornerShape(10.dp)))
            // Kalan genişliği kullanır: uzun künye kapağın yanında alt satıra geçer, ekrandan taşmaz.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                Text(oyun.ad, style = MaterialTheme.typography.headlineMedium)
                val kunye = listOfNotNull(oyun.cikisYili?.toString(), detay?.gelistiriciler?.firstOrNull()).joinToString(" · ")
                if (kunye.isNotEmpty()) Text(kunye, style = MaterialTheme.typography.bodySmall, color = Renk.Soluk)
                val turVePlatform = (detay?.turler.orEmpty().take(2).map { igdbTurAdi(it) } + oyun.platformlar.take(3)).joinToString(" · ")
                if (turVePlatform.isNotEmpty()) Text(turVePlatform, style = MaterialTheme.typography.bodySmall, color = Renk.Soluk)
                oyun.anaOyun?.let { ana ->
                    Text(
                        stringResource(Res.string.oyun_ana_oyun, ana.ad),
                        style = MaterialTheme.typography.labelLarge,
                        color = Renk.Vurgu,
                        modifier = Modifier.clickable { oyunaGit(ana.id) },
                    )
                }
            }
        }
    }
}

/** Detay yüklenirken bandın altındaki bölümlerin yer tutucuları. */
@Composable
private fun DetayIskeleti(ozet: OyunOzeti, geri: () -> Unit, oyunaGit: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Bant(ozet, null, geri, oyunaGit)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Iskelet(Modifier.weight(1f).height(72.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Iskelet(Modifier.weight(1f).height(52.dp)) } }
            Iskelet(Modifier.fillMaxWidth().height(56.dp))
            Iskelet(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Iskelet(Modifier.fillMaxWidth(0.6f).height(14.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detay(
    durum: OyunDetayEkrani.Hazir,
    geri: () -> Unit,
    oyunaGit: (String) -> Unit,
    kaydet: () -> Unit,
    gunlugeYaz: (String?) -> Unit,
    topluluk: @Composable () -> Unit,
) {
    val detay = durum.detay
    val uri = LocalUriHandler.current
    val oyun = detay.oyun
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Bant(oyun, detay, geri, oyunaGit)
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Puanlar(detay.puanlar)
            KutuphaneOzeti(detay.kutuphaneGirisi, kaydet)
            Gunlugum(detay, gunlugeYaz)
            Text(
                stringResource(Res.string.igdb_atfi),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uri.openUri(detay.kaynakUrl) },
            )
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
}

/** Topluluk puanı, arkadaş ortalaması ve oynayan arkadaş sayısı. */
@Composable
private fun Puanlar(puanlar: OyunPuanlari) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PuanKutusu(stringResource(Res.string.oyun_topluluk), puanlar.toplulukPuani?.let(::puanMetni) ?: stringResource(Res.string.oyun_puan_yok), Modifier.weight(1f), yildiz = puanlar.toplulukPuani != null)
        PuanKutusu(stringResource(Res.string.oyun_arkadaslar), puanlar.arkadasOrtalamasi?.let(::puanMetni) ?: "–", Modifier.weight(1f), yildiz = puanlar.arkadasOrtalamasi != null)
        PuanKutusu(stringResource(Res.string.oyun_oynayan), puanlar.oynayanArkadasSayisi.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun PuanKutusu(baslik: String, deger: String, modifier: Modifier, yildiz: Boolean = false) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Renk.Yuzey).border(1.dp, Renk.Cizgi, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SigdirilanYazi(deger, MaterialTheme.typography.titleMedium, renk = if (yildiz) Renk.Yildiz else Renk.Yazi)
        SigdirilanYazi(baslik, MaterialTheme.typography.labelSmall, renk = Renk.Soluk)
    }
}

/** Yarım yıldız cinsinden puanı beş üzerinden, tek ondalıkla gösterir: 7.6 → "★ 3,8". */
private val BANT_YUKSEKLIGI = 170.dp
private val KAPAK_TASMASI = 48.dp

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
            kayit.puan?.let { YildizPuani(it, boyut = 16) }
            kayit.inceleme?.let { Inceleme(it, kayit.spoiler) }
            if (kayit.begeniSayisi > 0) Text(stringResource(Res.string.begeni_sayisi_kisa, kayit.begeniSayisi), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (durum.incelemeImleci != null) {
        TextButton(onClick = dahaFazla, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.arama_daha_fazla)) }
    }
}

/** Kullanıcının bu oyun için kütüphane girişi ve hızlı kayıt düğmesi. */
@Composable
private fun KutuphaneOzeti(giris: KutuphaneGirisi?, kaydet: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Durum düğmeleri tuvaldeki gibi yan yana; seçili olan lime. Dokunmak hızlı kayıt sayfasını açar.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Durum.OYNUYORUM, Durum.BITIRDIM, Durum.BIRAKTIM).forEach { d ->
                val secili = giris?.durum == d
                Surface(
                    onClick = kaydet,
                    shape = MaterialTheme.shapes.medium,
                    color = if (secili) Renk.Vurgu else Renk.Yuzey,
                    contentColor = if (secili) Renk.Zemin else Renk.Yazi,
                    border = if (secili) null else BorderStroke(1.dp, Renk.Cizgi),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (secili) {
                            Icon(Ikonlar.Onay, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        SigdirilanYazi(durumAdi(d), MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Surface(
            onClick = kaydet,
            shape = MaterialTheme.shapes.medium,
            color = Renk.Yuzey,
            border = BorderStroke(1.dp, Renk.Cizgi),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                SigdirilanYazi(stringResource(Res.string.oyun_senin_puanin), MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (giris?.puan != null) {
                    YildizPuani(giris.puan, boyut = 18)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (giris == null) Res.string.kayit_ac else Res.string.kayit_duzenle), style = MaterialTheme.typography.labelMedium, color = Renk.Soluk)
            }
        }
        val isaretler = listOfNotNull(
            if (giris?.durum == Durum.BEKLEMEDE) durumAdi(Durum.BEKLEMEDE) else null,
            if (giris?.yuzdeYuz == true) stringResource(Res.string.kayit_yuzde_yuz) else null,
            if (giris?.begendim == true) stringResource(Res.string.kayit_begendim) else null,
            if (giris?.istekListesinde == true) stringResource(Res.string.kayit_istek_listesi) else null,
        )
        if (isaretler.isNotEmpty()) Text(isaretler.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Renk.Vurgu)
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
                kayit.puan?.let { YildizPuani(it, boyut = 16) }
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
