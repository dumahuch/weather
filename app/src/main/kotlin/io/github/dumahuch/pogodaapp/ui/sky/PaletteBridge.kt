package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.graphics.Color
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.Rgba
import io.github.dumahuch.pogodaapp.sky.SkyPalette
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import kotlin.math.roundToInt

private fun Double.channel(): Int = roundToInt().coerceIn(0, 255)

internal fun Rgb.toColor(): Color = Color(r.channel(), g.channel(), b.channel())

/** Прозрачность округляется до сотых: так почти одинаковые палитры равны и не вызывают лишней перерисовки. */
internal fun Rgba.toColor(): Color =
    Color(rgb.r.channel(), rgb.g.channel(), rgb.b.channel(), (alpha.coerceIn(0.0, 1.0) * 100).roundToInt() * 255 / 100)

/** Цвета интерфейса, посчитанные под небо, в виде, который читают экранные компоненты. */
internal fun SkyPalette.toUi(): PogodaPalette = PogodaPalette(
    ink = ink.toColor(),
    ink2 = ink2.toColor(),
    glass = glass.toColor(),
    glassLine = glassLine.toColor(),
    sheen = sheen.toColor(),
    buttonBackground = buttonBackground.toColor(),
    buttonOn = buttonOn.toColor(),
    button2 = button2.toColor(),
    accent = accent.toColor(),
    accentSoft = accentSoft.toColor(),
    bar = bar.toColor(),
    warning = warning.toColor(),
    scrim = scrim.toColor(),
    darkInk = darkInk,
)
