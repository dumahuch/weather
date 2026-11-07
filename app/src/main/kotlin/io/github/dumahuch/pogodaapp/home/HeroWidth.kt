package io.github.dumahuch.pogodaapp.home

/**
 * Предел роста блока цифр по ширине экрана (step-40: предел по высоте поднят до 1,7, и «66°» со значком погоды при
 * крупном шрифте системы уже не поместилась бы). Ширины знаков измерены по Manrope (вес 400, табличные цифры); расчёт
 * повторяет раскладку `HeroBlock`: цифры, зазор и правая колонка (градус или значок, что шире) стоят в ряд.
 * Чистая логика без Android: [HeroWidthTest].
 */
internal object HeroWidth {

    private const val DIGIT_EM = 0.62
    private const val MINUS_EM = 0.54
    private const val LETTER_SPACING_EM = -0.035

    /** Градус 0,44 размера цифр, его знак 0,45 em шириной, сдвинут к цифрам на 0,025 размера цифр. */
    private const val DEGREE_EM = 0.45 * 0.44 - 0.025

    /** Зазор между цифрами и правой колонкой: 0,04 от размера градуса. */
    private const val GAP_EM = 0.44 * 0.04

    /** Значок 72 dp при цифрах 124 sp; в dp, шрифт системы его не меняет, и с масштабом он не растёт (step-42). */
    private const val GLYPH_DP_AT_124 = 72.0
    private const val GLYPH_BASE_DIGITS = 124.0

    /** Запас до края экрана с каждой стороны. */
    private const val SIDE_MARGIN_DP = 8f

    /** Ширина цифр в em: «−12» это три знака. */
    fun digitsEm(digits: String): Double =
        digits.sumOf { if (it == '−') MINUS_EM else DIGIT_EM } + LETTER_SPACING_EM * digits.length

    /**
     * Наибольший масштаб блока, при котором он помещается в [widthDp] (с запасом по краям). Значок считается всегда,
     * даже при ясном небе, где его нет: размер цифр не должен зависеть от погоды. Не меньше 1: при нехватке места уже
     * на обычном размере блок решает [HeroFit].
     */
    fun maxScale(digits: String, digitsSize: Int, widthDp: Float, fontScale: Float): Float {
        val size = digitsSize.toDouble()
        val text = fontScale * size * (digitsEm(digits) + GAP_EM)
        val degree = fontScale * size * DEGREE_EM
        val glyph = GLYPH_DP_AT_124 * size / GLYPH_BASE_DIGITS
        val available = widthDp - 2 * SIDE_MARGIN_DP
        // Ширина: цифры и градус растут с масштабом, значок нет; правая колонка шире из двух, поэтому нужны оба условия.
        if (text + degree <= 0.0 || text <= 0.0) return 1f
        return minOf(available / (text + degree), (available - glyph) / text).toFloat().coerceAtLeast(1f)
    }
}
