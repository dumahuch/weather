package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Приглушённый цвет второстепенного текста на главном экране («Ощущается как», строка влажности, ветра и осадков).
 * Основной цвет текста тема подбирает с запасом (подложка ведёт контраст до
 * [io.github.dumahuch.pogodaapp.sky.SkyTheme.INFO_TARGET]), а ночью и на тёмном небе он вообще много выше нужного.
 * Для второстепенных данных лишний контраст не нужен: цвет ослабляется (смешивается с цветом неба под строкой) ровно
 * настолько, чтобы контраст остался около 3,6 : 1, но не слабее [MIN_ALPHA] основного цвета. Это ниже порога WCAG AA для
 * мелкого текста (4,5 : 1): сознательное решение Димы (06.11.2026), второстепенные надписи должны быть бледнее.
 * Расчёт идёт по худшему цвету неба под строкой ([PogodaPalette.infoBackdrop]): там, где читаемость на пределе,
 * цвет остаётся основным.
 */
internal object QuietInk {
    /** Контраст приглушённого текста с худшим цветом неба: ниже порога AA (4,5 : 1), решение Димы. */
    const val TEXT_CONTRAST = 3.6f

    /** Контраст приглушённого значка (значки держат 3 : 1, WCAG 1.4.11, с запасом). */
    const val ICON_CONTRAST = 3.2f

    /** Самая слабая доля основного цвета: ниже текст выглядит бледным пятном, даже если контраст по расчёту держится. */
    const val MIN_ALPHA = 0.50f

    /** Самая слабая доля основного цвета для значка «нет данных или осадков» (он слабее остальных). */
    const val MIN_ICON_ALPHA = 0.45f

    private const val STEPS = 40

    /** Цвет второстепенного текста: основной цвет [ink], ослабленный на фоне [backdrop]. */
    fun text(ink: Color, backdrop: Color): Color = weakest(ink, backdrop, TEXT_CONTRAST, MIN_ALPHA)

    /** Цвет значка, который подан как «ничего нет» (например, сухо): ещё слабее текста, но заметный. */
    fun fadedIcon(ink: Color, backdrop: Color): Color = weakest(ink, backdrop, ICON_CONTRAST, MIN_ICON_ALPHA)

    /** Самое слабое смешение [ink] с [backdrop] (не слабее [minAlpha]), которое держит контраст [minContrast]; иначе [ink]. */
    private fun weakest(ink: Color, backdrop: Color, minContrast: Float, minAlpha: Float): Color {
        for (step in 0..STEPS) {
            val alpha = minAlpha + (1f - minAlpha) * step / STEPS
            val candidate = lerp(backdrop, ink, alpha)
            if (ExtremeTints.contrastRatio(candidate, backdrop) >= minContrast) return candidate
        }
        return ink
    }
}
