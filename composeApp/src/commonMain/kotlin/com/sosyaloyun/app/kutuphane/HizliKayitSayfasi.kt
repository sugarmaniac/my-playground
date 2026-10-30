package com.sosyaloyun.app.kutuphane

import com.sosyaloyun.app.tema.Renk
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sosyaloyun.app.tema.Ikonlar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.durum_beklemede
import com.sosyaloyun.app.resources.durum_biraktim
import com.sosyaloyun.app.resources.durum_bitirdim
import com.sosyaloyun.app.resources.durum_oynuyorum
import com.sosyaloyun.app.resources.kayit_begendim
import com.sosyaloyun.app.resources.kayit_hata
import com.sosyaloyun.app.resources.kayit_istek_listesi
import com.sosyaloyun.app.resources.kayit_kaydet
import com.sosyaloyun.app.resources.kayit_puan
import com.sosyaloyun.app.resources.kayit_yuzde_yuz
import com.sosyaloyun.shared.Durum
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HizliKayitSayfasi(oyunAdi: String, viewModel: HizliKayitViewModel) {
    val durum by viewModel.ekran.collectAsStateWithLifecycle()
    val giris = durum.giris
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(oyunAdi, style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Durum.entries.forEach { secenek ->
                FilterChip(
                    selected = giris.durum == secenek,
                    onClick = { viewModel.durumSecildi(secenek) },
                    label = { Text(durumAdi(secenek)) },
                )
            }
        }
        Text(stringResource(Res.string.kayit_puan), style = MaterialTheme.typography.titleSmall)
        YildizPuani(giris.puan, viewModel::puanSecildi)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = giris.begendim, onClick = viewModel::begendimDegisti, label = { Text(stringResource(Res.string.kayit_begendim)) })
            FilterChip(
                selected = giris.istekListesinde,
                onClick = viewModel::istekListesiDegisti,
                label = { Text(stringResource(Res.string.kayit_istek_listesi)) },
            )
            FilterChip(
                selected = giris.yuzdeYuz,
                onClick = viewModel::yuzdeYuzDegisti,
                enabled = durum.yuzdeYuzSecilebilir,
                label = { Text(stringResource(Res.string.kayit_yuzde_yuz)) },
            )
        }
        if (durum.hata) Text(stringResource(Res.string.kayit_hata), color = MaterialTheme.colorScheme.error)
        Button(onClick = viewModel::kaydet, enabled = !durum.kaydediliyor, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.kayit_kaydet))
        }
    }
}

/** Beş yıldız; her yıldızın sol yarısı yarım, sağ yarısı tam puan. */
@Composable
fun YildizPuani(yarimYildiz: Int?, sec: ((Int) -> Unit)? = null, boyut: Int = 40) {
    // Yıldızlar vektör ikonla çizilir: yazı karakterlerinin (☆ ★) boş ve dolu halleri fontta farklı boyutta ve
    // hizadaydı, yarım yıldız kayıyordu. Yarım yıldızda dolu ikon tam ortadan kırpılır.
    // [sec] null ise yalnız gösterim: dokunuş yutulmaz, satırın kendi tıklaması çalışır.
    Row {
        for (yildiz in 1..5) {
            val dolu = (yarimYildiz ?: 0) - (yildiz - 1) * 2
            Box(Modifier.size(boyut.dp), contentAlignment = Alignment.Center) {
                val ikon = Modifier.fillMaxSize().padding((boyut * 0.06f).dp)
                Icon(Ikonlar.YildizBos, contentDescription = null, tint = Renk.Yildiz, modifier = ikon)
                when {
                    dolu >= 2 -> Icon(Ikonlar.Yildiz, contentDescription = null, tint = Renk.Yildiz, modifier = ikon)
                    dolu == 1 -> Icon(
                        Ikonlar.Yildiz,
                        contentDescription = null,
                        tint = Renk.Yildiz,
                        modifier = ikon.drawWithContent { clipRect(right = size.width / 2) { this@drawWithContent.drawContent() } },
                    )
                }
                if (sec != null) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { sec(yildiz * 2 - 1) })
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { sec(yildiz * 2) })
                    }
                }
            }
        }
    }
}

@Composable
fun durumAdi(durum: Durum): String = stringResource(
    when (durum) {
        Durum.OYNUYORUM -> Res.string.durum_oynuyorum
        Durum.BITIRDIM -> Res.string.durum_bitirdim
        Durum.BIRAKTIM -> Res.string.durum_biraktim
        Durum.BEKLEMEDE -> Res.string.durum_beklemede
    },
)
