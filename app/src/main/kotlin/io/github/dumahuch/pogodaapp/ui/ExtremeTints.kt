package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Оттенки максимума (тёплый) и минимума (холодный) температуры дня на главном экране (правила надписей, раздел 4).
 * Цвет не заменяет основной цвет текста, а подмешивается к нему: яркость остаётся близкой к яркости текста, которую тема
 * подобрала под небо, поэтому читаемость не страдает. Оттенок плавно меняется вместе с цветом текста по [PogodaPalette.smoke]
 * (0 тёмный текст на светлом небе, 1 светлый на тёмном), без скачка при смене.
 */
internal object ExtremeTints {
    private val WarmOnLight = Color(0xFFB23A08)
    private val ColdOnLight = Color(0xFF1F4FA3)
    private val WarmOnDark = Color(0xFFFFB074)
    private val ColdOnDark = Color(0xFF93C5FF)

    /** Доля оттенка в цвете числа: у тёмного текста больше (иначе оттенок на тёмном почти не виден), у светлого меньше (чтобы не терять яркость). */
    private const val TEXT_STRENGTH_DARK_INK = 0.55f
    private const val TEXT_STRENGTH_LIGHT_INK = 0.30f

    /** Стрелка рисуется ярче числа: это значок, он держит оттенок сильнее. */
    private const val ARROW_STRENGTH = 0.8f

    private fun hue(smoke: Float, warm: Boolean): Color = if (warm) {
        lerp(WarmOnLight, WarmOnDark, smoke)
    } else {
        lerp(ColdOnLight, ColdOnDark, smoke)
    }

    /** Цвет числа максимума ([warm] = true) или минимума. [ink] основной цвет текста, [smoke] см. выше. */
    fun text(ink: Color, smoke: Float, warm: Boolean): Color {
        val s = smoke.coerceIn(0f, 1f)
        val strength = TEXT_STRENGTH_DARK_INK + (TEXT_STRENGTH_LIGHT_INK - TEXT_STRENGTH_DARK_INK) * s
        return lerp(ink, hue(s, warm), strength)
    }

    /** Цвет стрелки. */
    fun arrow(ink: Color, smoke: Float, warm: Boolean): Color {
        val s = smoke.coerceIn(0f, 1f)
        return lerp(ink, hue(s, warm), ARROW_STRENGTH)
    }
}
