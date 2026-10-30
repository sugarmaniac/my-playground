package com.sosyaloyun.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sosyaloyun.app.tema.Renk

/** Hesap kurulumu adımları: davet kodu, profil, Steam, oyun durumları, arkadaşlar, bildirim izni (#64). */
const val KURULUM_ADIMLARI = 6

/**
 * Giriş sonrası kurulum ekranlarının ortak iskeleti (#64): üstte adım göstergesi, kaydırılan içerik, altta sabit
 * ana düğme. [kenarlar] açıkken sistem çubukları ve klavye kadar iç boşluk bırakılır (edge-to-edge); ana
 * uygulamanın Scaffold'u içinde (profil düzenleme) kapalı, boşluğu Scaffold verir.
 */
@Composable
fun AdimIskeleti(
    baslik: String,
    aciklama: String?,
    adim: Int? = null,
    ikon: ImageVector? = null,
    kenarlar: Boolean = true,
    altKisim: @Composable ColumnScope.() -> Unit = {},
    icerik: LazyListScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Renk.Zemin).then(if (kenarlar) Modifier.safeDrawingPadding() else Modifier)) {
        adim?.let { AdimGostergesi(it, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "adim-basligi") {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ikon?.let {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Renk.VurguKoyu), contentAlignment = Alignment.Center) {
                            Icon(it, contentDescription = null, tint = Renk.Vurgu, modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(baslik, style = MaterialTheme.typography.headlineLarge)
                    aciklama?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Renk.Soluk) }
                }
            }
            icerik()
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = altKisim,
        )
    }
}

/** Kaç adımın geçildiği: dolu bölümler vurgu renginde. */
@Composable
private fun AdimGostergesi(adim: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(KURULUM_ADIMLARI) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < adim) Renk.Vurgu else Renk.Cizgi))
        }
    }
}

/** Adımın ana düğmesi: tam genişlik, iş sürerken içinde gösterge. */
@Composable
fun AnaDugme(metin: String, etkin: Boolean, tikla: () -> Unit, suruyor: Boolean = false) {
    Button(onClick = tikla, enabled = etkin && !suruyor, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (suruyor) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Renk.Zemin) else Text(metin, style = MaterialTheme.typography.titleSmall)
    }
}

/** Atla, şimdi değil gibi ikincil seçenek. */
@Composable
fun IkincilDugme(metin: String, etkin: Boolean = true, tikla: () -> Unit) {
    TextButton(onClick = tikla, enabled = etkin, modifier = Modifier.fillMaxWidth()) { Text(metin, color = Renk.Soluk) }
}
