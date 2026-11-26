package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Мелкий текст должен иметь контраст не ниже 4,5 : 1 (WCAG AA) на любом месте фона.
 * Ink3 (50 %) этого не проходит, поэтому для мелкого текста он не используется.
 */
class PaletteContrastTest {

    private fun over(top: Color, bottom: Color): Color {
        val a = top.alpha
        return Color(
            red = top.red * a + bottom.red * (1 - a),
            green = top.green * a + bottom.green * (1 - a),
            blue = top.blue * a + bottom.blue * (1 - a),
            alpha = 1f,
        )
    }

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(text: Color, background: Color): Double {
        val shown = over(text, background)
        val a = luminance(shown)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** Цвет градиента неба на доле высоты экрана (0 сверху, 1 снизу). */
    private fun sky(fraction: Float): Color = if (fraction <= 0.5f) {
        lerp(PogodaColors.SkyTop, PogodaColors.SkyMiddle, fraction / 0.5f)
    } else {
        lerp(PogodaColors.SkyMiddle, PogodaColors.SkyHorizon, (fraction - 0.5f) / 0.5f)
    }

    private fun lerp(from: Color, to: Color, t: Float): Color = Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )

    @Test
    fun mainTextIsReadableOnTheWholeSky() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val ratio = contrast(PogodaColors.Ink, sky(fraction))
            assertTrue("Ink на небе $fraction: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun secondaryTextIsReadableOnTheSkyBelowTheTopQuarter() {
        // Выше четверти экрана на небе нет вторичного текста: там плашка города и системная строка состояния.
        for (fraction in listOf(0.25f, 0.4f, 0.5f, 0.75f, 1f)) {
            val ratio = contrast(PogodaColors.Ink2, sky(fraction))
            assertTrue("Ink2 на небе $fraction: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun secondaryTextIsReadableOnGlassAnywhere() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val glass = over(PogodaColors.Glass, sky(fraction))
            val ratio = contrast(PogodaColors.Ink2, glass)
            assertTrue("Ink2 на стекле $fraction: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun textOnTheButtonsIsReadable() {
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val button = over(PogodaColors.ButtonBackground, sky(fraction))
            assertTrue(contrast(PogodaColors.Ink, button) >= 4.5)
            assertTrue(contrast(PogodaColors.Ink2, button) >= 4.5)
            val pressed = over(PogodaColors.ButtonOn, sky(fraction))
            assertTrue(contrast(PogodaColors.Ink, pressed) >= 4.5)
        }
    }

    @Test
    fun faintInkIsTooPaleForSmallText() {
        // Фиксируем причину решения: Ink3 нельзя использовать для мелких подписей.
        assertTrue(contrast(PogodaColors.Ink3, sky(0.5f)) < 4.5)
    }

    @Test
    fun accentIsReadableOnGlass() {
        for (fraction in listOf(0.25f, 0.5f, 1f)) {
            val glass = over(PogodaColors.Glass, sky(fraction))
            assertTrue(contrast(PogodaColors.Accent, glass) >= 3.0)
        }
    }
}
