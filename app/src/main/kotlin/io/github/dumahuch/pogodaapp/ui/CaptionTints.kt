package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Что показывает значок в строке под температурой: от этого зависит его оттенок. */
internal enum class CaptionHue { Humidity, Wind, Precipitation }

/**
 * Оттенки значков строки под температурой на «Главной» (решение Димы 08.11.2026: цветные, но не яркие; капля синяя,
 * ветер бирюзовый, осадки сине-фиолетовые). Приём тот же, что у стрелок максимума и минимума ([ExtremeTints]): оттенок
 * подмешивается к цвету текста с наибольшей силой, при которой значок держит [ExtremeTints.MIN_ARROW_CONTRAST] с худшим
 * цветом неба под строкой. Поэтому цвет сам тускнеет на сером небе и светлеет на тёмном; если не держит ни с каким
 * оттенком, остаётся цвет текста. Оттенок плавно меняется вместе с [PogodaPalette.smoke] (0 тёмный текст, 1 светлый).
 */
internal object CaptionTints {
    private val OnLight = mapOf(
        CaptionHue.Humidity to Color(0xFF1F4FA3),
        CaptionHue.Wind to Color(0xFF0E7C86),
        CaptionHue.Precipitation to Color(0xFF4A55B8),
    )
    private val OnDark = mapOf(
        CaptionHue.Humidity to Color(0xFF93C5FF),
        CaptionHue.Wind to Color(0xFF7FD8DC),
        CaptionHue.Precipitation to Color(0xFFB3B8F5),
    )

    /** Доля оттенка в цвете значка, как у стрелки максимума и минимума. */
    const val STRENGTH = 0.8f

    fun hue(hue: CaptionHue, smoke: Float): Color = lerp(OnLight.getValue(hue), OnDark.getValue(hue), smoke.coerceIn(0f, 1f))

    /** Цвет значка: [ink] основной цвет текста, [smoke] см. выше, [backdrop] худший цвет неба под строкой. */
    fun icon(ink: Color, smoke: Float, hue: CaptionHue, backdrop: Color): Color =
        ExtremeTints.readable(ink, hue(hue, smoke), STRENGTH, backdrop, ExtremeTints.MIN_ARROW_CONTRAST)
}
