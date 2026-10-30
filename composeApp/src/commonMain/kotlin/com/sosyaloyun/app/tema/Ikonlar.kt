package com.sosyaloyun.app.tema

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Tasarım tuvalindeki çizgi ikonlar (24×24, 1.8 kalınlık). Renk [androidx.compose.material3.Icon]'un `tint`'inden gelir;
 * çizim rengi önemsizdir.
 */
object Ikonlar {
    val Ev = ikon("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Ara = ikon(daire(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Arti = ikon("M12 5v14M5 12h14", kalinlik = 2.4f)
    val Zil = ikon("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")
    val Kisi = ikon(daire(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0")
    val Geri = ikon("M15 5l-7 7 7 7", kalinlik = 2f)
    val Kalp = ikon("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val KalpDolu = ikon("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z", dolu = true)
    val Yildiz = ikon("M12 2.6l2.9 5.9 6.5.95-4.7 4.6 1.1 6.5L12 17.5l-5.8 3.05 1.1-6.5-4.7-4.6 6.5-.95z", dolu = true)
    val YildizBos = ikon("M12 2.6l2.9 5.9 6.5.95-4.7 4.6 1.1 6.5L12 17.5l-5.8 3.05 1.1-6.5-4.7-4.6 6.5-.95z", kalinlik = 1.6f)
    val Onay = ikon("M5 12l5 5 9-10", kalinlik = 2.6f)
    val YukariOk = ikon("M12 19V5M5 12l7-7 7 7", kalinlik = 2.4f)
    val Ayarlar = ikon(daire(12f, 12f, 3f), "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z")
    val KisiEkle = ikon(daire(9f, 8f, 4f), "M2 21a7 7 0 0 1 14 0", "M19 8v6M16 11h6")
    val Davet = ikon("M4 6h16v12H4z", "M4 7l8 6 8-6")

    private fun daire(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    private fun ikon(vararg yollar: String, kalinlik: Float = 1.8f, dolu: Boolean = false): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            yollar.forEach { yol ->
                addPath(
                    pathData = addPathNodes(yol),
                    fill = if (dolu) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = kalinlik,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
