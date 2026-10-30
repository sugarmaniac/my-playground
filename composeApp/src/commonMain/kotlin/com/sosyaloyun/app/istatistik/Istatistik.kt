package com.sosyaloyun.app.istatistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.sunucuAdresi
import com.sosyaloyun.app.hesap.paylasici
import com.sosyaloyun.app.kesif.turAdi
import com.sosyaloyun.app.kutuphane.durumAdi
import com.sosyaloyun.app.oyun.puanMetni
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.istatistik_aylar
import com.sosyaloyun.app.resources.istatistik_aylar_ozet
import com.sosyaloyun.app.resources.istatistik_platformlar
import com.sosyaloyun.app.resources.istatistik_puanlar
import com.sosyaloyun.app.resources.istatistik_puanlar_ozet
import com.sosyaloyun.app.resources.istatistik_sure
import com.sosyaloyun.app.resources.istatistik_turler
import com.sosyaloyun.app.resources.istatistik_yillik_ozet
import com.sosyaloyun.app.resources.paylas
import com.sosyaloyun.app.resources.tekrar_dene
import com.sosyaloyun.app.resources.yillik_baslik
import com.sosyaloyun.app.resources.yillik_bitirilenler
import com.sosyaloyun.app.resources.yillik_en_cok_tur
import com.sosyaloyun.app.resources.yillik_en_yuksek
import com.sosyaloyun.app.resources.yillik_ozet_satiri
import com.sosyaloyun.shared.AdSayisi
import com.sosyaloyun.shared.Istatistikler
import com.sosyaloyun.shared.PaylasimLinkleri
import com.sosyaloyun.shared.Tur
import com.sosyaloyun.shared.YillikOzet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Profildeki İstatistik sekmesinin içeriği (Faz 3, #55); LazyColumn öğesi olarak çizilir. */
@Composable
fun IstatistikGorunumu(ist: Istatistikler, yillikOzeteGit: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val durumlar = ist.durumlar.map { "${durumAdi(it.durum)} ${it.sayi}" }
        Text(durumlar.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.istatistik_sure, ist.toplamSureDakika / 60), style = MaterialTheme.typography.bodyMedium)

        val puanOzeti = stringResource(Res.string.istatistik_puanlar_ozet, ist.puanDagilimi.sum())
        Text(stringResource(Res.string.istatistik_puanlar), style = MaterialTheme.typography.titleSmall)
        Cubuklar(ist.puanDagilimi, etiketler = (1..10).map { if (it % 2 == 0) "${it / 2}" else "" }, ozet = puanOzeti)

        val ayOzeti = stringResource(Res.string.istatistik_aylar_ozet, ist.yil, ist.aylaraGoreBitirilen.sum())
        Text(stringResource(Res.string.istatistik_aylar, ist.yil), style = MaterialTheme.typography.titleSmall)
        Cubuklar(ist.aylaraGoreBitirilen, etiketler = (1..12).map { "$it" }, ozet = ayOzeti)

        SayimListesi(stringResource(Res.string.istatistik_turler), ist.turler) { turAdiVeyaKendisi(it) }
        SayimListesi(stringResource(Res.string.istatistik_platformlar), ist.platformlar) { it }
        OutlinedButton(onClick = { yillikOzeteGit(ist.yil) }) { Text(stringResource(Res.string.istatistik_yillik_ozet, ist.yil)) }
    }
}

/**
 * Tek serili sütun grafiği: ince sütunlar, uçları 4dp yuvarlatılmış, tabana oturur; değer yalnızca sıfırdan büyükse
 * sütunun üstünde yazılır. Erişilebilirlik için [ozet] içerik açıklamasıdır.
 */
@Composable
private fun Cubuklar(degerler: List<Int>, etiketler: List<String>, ozet: String) {
    val renk = MaterialTheme.colorScheme.primary
    val taban = MaterialTheme.colorScheme.outlineVariant
    val enBuyuk = (degerler.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = ozet }) {
        Row(Modifier.fillMaxWidth()) {
            degerler.forEach { d ->
                Text(if (d > 0) "$d" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val dilim = size.width / degerler.size
            val genislik = (dilim * 0.6f).coerceAtMost(16.dp.toPx())
            drawLine(taban, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            degerler.forEachIndexed { i, d ->
                if (d == 0) return@forEachIndexed
                val yukseklik = size.height * d / enBuyuk
                drawRoundRect(
                    renk,
                    topLeft = Offset(i * dilim + (dilim - genislik) / 2, size.height - yukseklik),
                    size = Size(genislik, yukseklik),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            etiketler.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SayimListesi(baslik: String, sayimlar: List<AdSayisi>, ad: @Composable (String) -> String) {
    if (sayimlar.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(baslik, style = MaterialTheme.typography.titleSmall)
        sayimlar.forEach { Text("${ad(it.ad)} · ${it.sayi}", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** IGDB tür adı bilinen bir türse yerelleştirilmiş adı, değilse kendisi. */
@Composable
private fun turAdiVeyaKendisi(igdbAdi: String): String = Tur.entries.firstOrNull { it.igdbAdi == igdbAdi }?.let { turAdi(it) } ?: igdbAdi

sealed interface YillikOzetEkrani {
    data object Yukleniyor : YillikOzetEkrani
    data class Hazir(val ozet: YillikOzet, val paylasimLinki: String) : YillikOzetEkrani
    data object Hata : YillikOzetEkrani
}

/** Yıllık özet (Faz 3, #55); [kullaniciAdi] null ise kendi özetin. Web sitesinde `yillik.html` ile paylaşılır. */
class YillikOzetViewModel(
    private val api: ApiIstemcisi,
    private val kullaniciAdi: String?,
    private val yil: Int,
    private val webAdresi: String = sunucuAdresi(),
) : ViewModel() {
    private val _ekran = MutableStateFlow<YillikOzetEkrani>(YillikOzetEkrani.Yukleniyor)
    val ekran: StateFlow<YillikOzetEkrani> = _ekran

    init {
        yenile()
    }

    fun yenile() {
        _ekran.value = YillikOzetEkrani.Yukleniyor
        viewModelScope.launch {
            _ekran.value = try {
                val ozet = api.yillikOzet(kullaniciAdi, yil)
                YillikOzetEkrani.Hazir(ozet, PaylasimLinkleri.yillikOzet(webAdresi, ozet.kullanici.kullaniciAdi, ozet.yil))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                YillikOzetEkrani.Hata
            }
        }
    }
}

@Composable
fun YillikOzetGorunumu(viewModel: YillikOzetViewModel, geri: () -> Unit, oyunaGit: (String) -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    val paylas = paylasici()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) } }
        when (val durum = ekran) {
            YillikOzetEkrani.Yukleniyor -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            YillikOzetEkrani.Hata -> item {
                Column {
                    Text(stringResource(Res.string.durum_hata))
                    TextButton(onClick = viewModel::yenile) { Text(stringResource(Res.string.tekrar_dene)) }
                }
            }
            is YillikOzetEkrani.Hazir -> {
                val ozet = durum.ozet
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.yillik_baslik, ozet.yil, ozet.kullanici.gorunenAd), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { paylas(durum.paylasimLinki) }) { Text(stringResource(Res.string.paylas)) }
                    }
                }
                item { Text(stringResource(Res.string.yillik_ozet_satiri, ozet.bitirilenler.size, ozet.toplamSureDakika / 60)) }
                ozet.enCokTur?.let { tur -> item { Text(stringResource(Res.string.yillik_en_cok_tur, turAdiVeyaKendisi(tur))) } }
                if (ozet.enYuksekPuanlilar.isNotEmpty()) {
                    item { Text(stringResource(Res.string.yillik_en_yuksek), style = MaterialTheme.typography.titleSmall) }
                    ozet.enYuksekPuanlilar.forEach { p ->
                        item(key = "puan-${p.oyun.id}") {
                            Text("${p.oyun.ad} · ${puanMetni(p.puan.toDouble())}", Modifier.fillMaxWidth().clickable { oyunaGit(p.oyun.id) }.padding(vertical = 4.dp))
                        }
                    }
                }
                if (ozet.bitirilenler.isNotEmpty()) {
                    item { Text(stringResource(Res.string.yillik_bitirilenler), style = MaterialTheme.typography.titleSmall) }
                    ozet.bitirilenler.forEach { o ->
                        item(key = "bitti-${o.id}") { Text(o.ad, Modifier.fillMaxWidth().clickable { oyunaGit(o.id) }.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}
