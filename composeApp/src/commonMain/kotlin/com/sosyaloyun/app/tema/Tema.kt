package com.sosyaloyun.app.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.bricolage_700
import com.sosyaloyun.app.resources.bricolage_800
import com.sosyaloyun.app.resources.inter_400
import com.sosyaloyun.app.resources.inter_500
import com.sosyaloyun.app.resources.inter_600
import com.sosyaloyun.app.resources.inter_700
import org.jetbrains.compose.resources.Font

/** Tasarım tuvalindeki renkler ("Oyun Sosyal Platformu – UI", #59). Uygulama tek, koyu temadadır. */
object Renk {
    val Zemin = Color(0xFF0E0F13)
    val Yuzey = Color(0xFF171920)
    val YuzeyYuksek = Color(0xFF20232C)
    val AltMenu = Color(0xFF12141A)
    val Cizgi = Color(0xFF2A2E39)
    val Yazi = Color(0xFFF2F3F5)
    val YaziIkincil = Color(0xFFD9DCE2)
    val Soluk = Color(0xFFA3A9B7)
    val Pasif = Color(0xFF5B6270)
    val Vurgu = Color(0xFFC8F542)
    val VurguKoyu = Color(0xFF1B2410)
    val Yildiz = Color(0xFFFFA94D)
    val Hata = Color(0xFFFF6B6B)
    val Dusus = Color(0xFFFF8A8A)

    /** Avatar ve kapak yer tutucularının koyu, doygunluğu düşük zeminleri; sıra sabittir. */
    private val kartelaRenkleri = listOf(0xFF4A3B6B, 0xFF6B3B4A, 0xFF2F5D50, 0xFF5D4A2F, 0xFF3B4A6B, 0xFF8A3B3B).map(::Color)

    fun avatarZemini(sira: Int): Color = kartelaRenkleri[sira.mod(kartelaRenkleri.size)]
}

private val koyuRenkler = darkColorScheme(
    primary = Renk.Vurgu,
    onPrimary = Renk.Zemin,
    primaryContainer = Renk.VurguKoyu,
    onPrimaryContainer = Renk.Vurgu,
    secondary = Renk.Soluk,
    onSecondary = Renk.Zemin,
    secondaryContainer = Renk.YuzeyYuksek,
    onSecondaryContainer = Renk.Yazi,
    tertiary = Renk.Yildiz,
    onTertiary = Renk.Zemin,
    background = Renk.Zemin,
    onBackground = Renk.Yazi,
    surface = Renk.Zemin,
    onSurface = Renk.Yazi,
    surfaceVariant = Renk.Yuzey,
    onSurfaceVariant = Renk.Soluk,
    surfaceContainerLowest = Renk.Zemin,
    surfaceContainerLow = Renk.Yuzey,
    surfaceContainer = Renk.Yuzey,
    surfaceContainerHigh = Renk.YuzeyYuksek,
    surfaceContainerHighest = Renk.YuzeyYuksek,
    surfaceBright = Renk.YuzeyYuksek,
    surfaceDim = Renk.Zemin,
    outline = Renk.Cizgi,
    outlineVariant = Renk.Cizgi,
    error = Renk.Hata,
    onError = Renk.Zemin,
    inverseSurface = Renk.Yazi,
    inverseOnSurface = Renk.Zemin,
    inversePrimary = Renk.VurguKoyu,
    scrim = Color(0xCC000000),
)

private val sekiller = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Başlıklarda Bricolage Grotesque, gövdede Inter; ikisi de uygulamaya gömülü. */
@Composable
private fun yaziTipleri(): Typography {
    val baslik = FontFamily(Font(Res.font.bricolage_700, FontWeight.Bold), Font(Res.font.bricolage_800, FontWeight.ExtraBold))
    val govde = FontFamily(
        Font(Res.font.inter_400, FontWeight.Normal),
        Font(Res.font.inter_500, FontWeight.Medium),
        Font(Res.font.inter_600, FontWeight.SemiBold),
        Font(Res.font.inter_700, FontWeight.Bold),
    )
    fun b(boyut: Int, kalinlik: FontWeight, satir: Int, harf: Double = -0.3) =
        TextStyle(fontFamily = baslik, fontWeight = kalinlik, fontSize = boyut.sp, lineHeight = satir.sp, letterSpacing = harf.sp)
    fun g(boyut: Int, kalinlik: FontWeight, satir: Int, harf: Double = 0.0) =
        TextStyle(fontFamily = govde, fontWeight = kalinlik, fontSize = boyut.sp, lineHeight = satir.sp, letterSpacing = harf.sp)
    return Typography(
        displayLarge = b(48, FontWeight.ExtraBold, 54),
        displayMedium = b(40, FontWeight.ExtraBold, 46),
        displaySmall = b(34, FontWeight.ExtraBold, 40),
        headlineLarge = b(30, FontWeight.ExtraBold, 36, -0.5),
        headlineMedium = b(26, FontWeight.ExtraBold, 32, -0.4),
        headlineSmall = b(24, FontWeight.ExtraBold, 30, -0.4),
        titleLarge = b(20, FontWeight.Bold, 26),
        titleMedium = g(17, FontWeight.Bold, 24),
        titleSmall = g(15, FontWeight.Bold, 20),
        bodyLarge = g(16, FontWeight.Normal, 24),
        bodyMedium = g(14, FontWeight.Normal, 21),
        bodySmall = g(13, FontWeight.Normal, 18),
        labelLarge = g(14, FontWeight.SemiBold, 20),
        labelMedium = g(12, FontWeight.SemiBold, 16),
        labelSmall = g(11, FontWeight.SemiBold, 14, 0.4),
    )
}

@Composable
fun SosyalOyunTemasi(icerik: @Composable () -> Unit) {
    MaterialTheme(colorScheme = koyuRenkler, typography = yaziTipleri(), shapes = sekiller, content = icerik)
}
