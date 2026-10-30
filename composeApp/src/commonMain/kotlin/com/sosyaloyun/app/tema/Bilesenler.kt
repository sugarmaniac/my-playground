package com.sosyaloyun.app.tema

import androidx.compose.foundation.BorderStroke
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
