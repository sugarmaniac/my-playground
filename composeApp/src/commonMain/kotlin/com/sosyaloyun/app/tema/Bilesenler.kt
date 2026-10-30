package com.sosyaloyun.app.tema

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** Tuvaldeki kart: yüzey rengi, ince çizgi, 16 dp köşe. */
@Composable
fun Kart(modifier: Modifier = Modifier, tikla: (() -> Unit)? = null, icerik: @Composable ColumnScope.() -> Unit) {
    val ic: @Composable () -> Unit = { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = icerik) }
    if (tikla != null) {
        Surface(onClick = tikla, modifier = modifier, shape = MaterialTheme.shapes.large, color = Renk.Yuzey, border = BorderStroke(1.dp, Renk.Cizgi), content = ic)
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = Renk.Yuzey, border = BorderStroke(1.dp, Renk.Cizgi), content = ic)
    }
}

/** Tuvaldeki hap sekme: seçiliyken açık zemin, değilken çizgili. */
@Composable
fun HapSekme(etiket: String, secili: Boolean, tikla: () -> Unit) {
    Surface(
        onClick = tikla,
        shape = RoundedCornerShape(18.dp),
        color = if (secili) Renk.Yazi else Renk.Zemin,
        contentColor = if (secili) Renk.Zemin else Renk.Yazi,
        border = if (secili) null else BorderStroke(1.dp, Renk.Cizgi),
        modifier = Modifier.height(36.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(etiket, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Yüklenen içeriğin yer tutucusu (#60). */
@Composable
fun Iskelet(modifier: Modifier = Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.small).background(Renk.YuzeyYuksek))
}

/**
 * Lazy listenin sonuna konur: öğe görünür ya da önceden hazırlanır olunca sonraki sayfa istenir (#60).
 * Görünür kaldıkça artan aralıkla yeniden istenir: ilk istek başka bir yükleme sürerken yok sayılmış ya da başarısız
 * olmuş olabilir. ViewModel'ler süren yükleme varken gelen isteği yok sayar. Sayfa gelince anahtar değişir, öğe yenilenir.
 */
fun LazyListScope.sayfaSonu(dahaVar: Boolean, yuklenen: Int, yukle: () -> Unit) {
    if (!dahaVar) return
    item(key = "sayfa-sonu-$yuklenen", contentType = "sayfa-sonu") {
        LaunchedEffect(Unit) {
            var bekleme = 1.seconds
            while (true) {
                yukle()
                delay(bekleme)
                bekleme = (bekleme * 2).coerceAtMost(30.seconds)
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * Tek satırda kalması gereken kısa yazılar (düğme, sayaç etiketi, ad): sığmazsa [enKucuk] boyuta kadar küçülür,
 * yine sığmazsa kırpılır. Üç noktayla kısaltma kullanılmaz: kısaltılan yazı taşmış sayılmadığından küçülme hiç başlamıyor. Büyük yazı boyutu ayarlı telefonlarda kelimenin bölünmesini ya da kesilmesini önler.
 */
@Composable
fun SigdirilanYazi(
    metin: String,
    stil: TextStyle,
    modifier: Modifier = Modifier,
    renk: Color = Color.Unspecified,
    enKucuk: TextUnit = 10.sp,
) {
    val sonStil = stil.merge(color = renk.takeOrElse { stil.color.takeOrElse { LocalContentColor.current } })
    BasicText(
        metin,
        modifier = modifier,
        style = sonStil,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = enKucuk, maxFontSize = stil.fontSize, stepSize = 0.5.sp),
    )
}
