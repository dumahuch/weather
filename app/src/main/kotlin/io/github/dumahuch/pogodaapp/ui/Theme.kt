package io.github.dumahuch.pogodaapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R

/**
 * Цвета экрана из макета (дневной вариант). Пока вместо картин неба спокойный дневной фон;
 * подстройка цвета под время суток и погоду придёт вместе с небом.
 */
object PogodaColors {
    val Ink = Color(0xFF101722)
    val Ink2 = Ink.copy(alpha = 0.74f)
    val Ink3 = Ink.copy(alpha = 0.50f)
    val Glass = Color(0xFFEEF3F9).copy(alpha = 0.33f)
    val GlassLine = Ink.copy(alpha = 0.10f)
    val Sheen = Color.White.copy(alpha = 0.20f)
    val ButtonBackground = Color.White.copy(alpha = 0.55f)
    val ButtonOn = Color.White.copy(alpha = 0.80f)
    val Button2 = Ink.copy(alpha = 0.20f)
    val Accent = Color(0xFF1C56A0)
    val AccentSoft = Accent.copy(alpha = 0.20f)
    val Bar = Color(0xFF2E628E)
    val Warning = Color(0xFFC0692D)

    val SkyTop = Color(0xFF6F9CC8)
    val SkyMiddle = Color(0xFF9DBFDD)
    val SkyHorizon = Color(0xFFC9DCEA)
    val SkyBrush: Brush = Brush.verticalGradient(listOf(SkyTop, SkyMiddle, SkyHorizon))
}

/** Manrope: переменный шрифт, вес выбирается осью wght (200…800). */
val Manrope = FontFamily(
    listOf(200, 400, 500, 600, 700).map { weight ->
        Font(
            resId = R.font.manrope,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

/** Стиль текста экрана: размер, вес и межстрочный интервал в долях размера, как в макете. */
fun pogodaText(
    size: Float,
    weight: Int,
    color: Color = PogodaColors.Ink,
    lineHeight: Float = 1.25f,
    letterSpacing: TextUnit = 0.sp,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabular) "tnum" else null,
    color = color,
)

@Composable
fun PogodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PogodaColors.Accent,
            background = PogodaColors.SkyMiddle,
            onBackground = PogodaColors.Ink,
            surface = PogodaColors.SkyMiddle,
            onSurface = PogodaColors.Ink,
        ),
        content = content,
    )
}

/** Межбуквенный интервал крупных цифр в макете: −0,035 em. */
val HeroLetterSpacing = (-0.035).em
