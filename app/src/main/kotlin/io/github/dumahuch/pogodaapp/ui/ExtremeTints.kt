package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Оттенки максимума (тёплый) и минимума (холодный) температуры дня на главном экране (правила надписей, раздел 4).
 * Цвет не заменяет основной цвет текста, а подмешивается к нему: яркость остаётся близкой к яркости текста, которую тема
 * подобрала под небо. Оттенок плавно меняется вместе с цветом текста по [PogodaPalette.smoke]
 * (0 тёмный текст на светлом небе, 1 светлый на тёмном), без скачка при смене.
 *
 * Читаемость важнее оттенка: любой подмешанный цвет уменьшает контраст относительно основного цвета текста, а тема
 * подбирает подложку под основной цвет ровно до [io.github.dumahuch.pogodaapp.sky.SkyTheme.INFO_TARGET]. Поэтому оттенок
 * подмешивается с наибольшей силой, при которой число всё ещё держит [MIN_TEXT_CONTRAST] (стрелка [MIN_ARROW_CONTRAST])
 * на худшем цвете неба под строкой ([PogodaPalette.infoBackdrop]); если не держит ни с каким оттенком, цвет остаётся основным.
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

    /** Контраст числа (мелкий текст, WCAG AA) и стрелки (значок, WCAG 1.4.11) с худшим цветом неба под ними. */
    const val MIN_TEXT_CONTRAST = 4.5f
    const val MIN_ARROW_CONTRAST = 3.0f

    /** На сколько шагов делится путь силы оттенка от желаемой до нуля при поиске. */
    private const val STEPS = 40

    private fun hue(smoke: Float, warm: Boolean): Color = if (warm) {
        lerp(WarmOnLight, WarmOnDark, smoke)
    } else {
        lerp(ColdOnLight, ColdOnDark, smoke)
    }

    /** Контраст двух непрозрачных цветов по WCAG 2: от 1 до 21. */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Цвет с наибольшей силой оттенка не выше [wanted], при которой контраст с [backdrop] не меньше [minContrast]. */
    private fun readable(ink: Color, hue: Color, wanted: Float, backdrop: Color, minContrast: Float): Color {
        for (step in 0..STEPS) {
            val candidate = lerp(ink, hue, wanted * (1f - step.toFloat() / STEPS))
            if (contrastRatio(candidate, backdrop) >= minContrast) return candidate
        }
        return ink
    }

    /**
     * Цвет числа максимума ([warm] = true) или минимума. [ink] основной цвет текста, [smoke] см. выше,
     * [backdrop] худший цвет неба под строкой.
     */
    fun text(ink: Color, smoke: Float, warm: Boolean, backdrop: Color): Color {
        val s = smoke.coerceIn(0f, 1f)
        val strength = TEXT_STRENGTH_DARK_INK + (TEXT_STRENGTH_LIGHT_INK - TEXT_STRENGTH_DARK_INK) * s
        return readable(ink, hue(s, warm), strength, backdrop, MIN_TEXT_CONTRAST)
    }

    /** Цвет стрелки. */
    fun arrow(ink: Color, smoke: Float, warm: Boolean, backdrop: Color): Color {
        val s = smoke.coerceIn(0f, 1f)
        return readable(ink, hue(s, warm), ARROW_STRENGTH, backdrop, MIN_ARROW_CONTRAST)
    }
}
