package io.github.dumahuch.pogodaapp.home

/**
 * Предел роста блока цифр по ширине экрана (step-40: предел по высоте поднят до 1,7, и «66°» со значком погоды при
 * крупном шрифте системы уже не поместилась бы). Ширины знаков измерены по Manrope (табличные цифры 0,62 em при весе 200 и 400); расчёт
 * повторяет раскладку `HeroBlock`: цифры, зазор и правая колонка (градус или значок, что шире) стоят в ряд.
 * Чистая логика без Android: [HeroWidthTest].
 */
internal object HeroWidth {

    private const val DIGIT_EM = 0.62
    private const val MINUS_EM = 0.54
    private const val LETTER_SPACING_EM = -0.035

    /** Размер градуса в долях размера цифр (step-42: меньше прежних 0,44, просьба Димы 07.11.2026). */
    const val DEGREE_SCALE = 0.34f

    /** Зазор между цифрами и правой колонкой, в долях размера градуса. */
    const val DEGREE_GAP_EM = 0.04f

    /**
     * Градус придвинут к цифрам (step-30, ближе с step-42), доля размера цифр. Самое узкое правое поле у табличной «4»
     * при весе 200: 0,063 em (измерено по Manrope); после сдвига просвет остаётся около 0,02 размера цифр.
     */
    const val DEGREE_PULL_EM = 0.035f

    /** Знак градуса 0,45 em шириной. */
    private const val DEGREE_EM = 0.45 * DEGREE_SCALE - DEGREE_PULL_EM

    private const val GAP_EM = DEGREE_SCALE * DEGREE_GAP_EM

    /**
     * Значок 72 dp при цифрах 124 sp и масштабе 1; в dp, шрифт системы его не меняет. С step-44 значок растёт вместе с
     * цифрами (при масштабе 1,3 это 94 dp; решение Димы 08.11.2026, вариант D): иначе облако выглядело приписью (0,31
     * высоты цифр вместо 0,41).
     */
    private const val GLYPH_DP_AT_124 = 72.0
    private const val GLYPH_BASE_DIGITS = 124.0

    /**
     * Вес значка при центровке блока (вариант D, решение Димы 08.11.2026): центруются цифры с градусом и половина
     * ширины значка, а не весь значок. Цифры стоят ближе к центру экрана (сдвиг влево 29 dp вместо 48 dp), значок
     * свисает вправо. Стеклянное облако лёгкое, цифры тяжёлее: так блок выглядит ровнее.
     */
    const val ICON_CENTER_WEIGHT = 0.5f

    /** Размер значка в dp при цифрах [digitsSize] sp и масштабе блока [scale]. */
    fun glyphDp(digitsSize: Int, scale: Float): Float = (GLYPH_DP_AT_124 * digitsSize / GLYPH_BASE_DIGITS).toFloat() * scale

    /**
     * Ширина правой колонки (градус [degree] или значок [glyph], что шире), которую учитывает центровка: градус
     * целиком, значок с весом [ICON_CENTER_WEIGHT].
     */
    fun centeringSide(degree: Float, glyph: Float): Float = degree + (maxOf(degree, glyph) - degree) * ICON_CENTER_WEIGHT

    /** Запас до края экрана с каждой стороны. */
    private const val SIDE_MARGIN_DP = 8f

    /** Ширина цифр в em: «−12» это три знака. */
    fun digitsEm(digits: String): Double =
        digits.sumOf { if (it == '−') MINUS_EM else DIGIT_EM } + LETTER_SPACING_EM * digits.length

    /**
     * Наибольший масштаб блока, при котором он помещается в [widthDp] (с запасом по краям). Значок считается всегда,
     * даже при ясном небе, где его нет: размер цифр не должен зависеть от погоды. Не меньше 1: при нехватке места уже
     * на обычном размере блок решает [HeroFit]. Цифры, градус и значок растут вместе с масштабом (с step-44); блок
     * центрируется по [centeringSide], поэтому слева проверяется он, а справа край значка.
     */
    fun maxScale(digits: String, digitsSize: Int, widthDp: Float, fontScale: Float): Float {
        val size = digitsSize.toDouble()
        val text = fontScale * size * (digitsEm(digits) + GAP_EM)
        val degree = fontScale * size * DEGREE_EM
        val glyph = GLYPH_DP_AT_124 * size / GLYPH_BASE_DIGITS
        val side = maxOf(degree, glyph)
        val centering = centeringSide(degree.toFloat(), glyph.toFloat())
        val available = widthDp - 2 * SIDE_MARGIN_DP
        if (text <= 0.0) return 1f
        val left = available / (text + centering)
        // Правый край значка: середина экрана плюс половина цифр с зазором, плюс вся колонка без половины центровки.
        val rightReach = text / 2 + side - centering / 2
        val right = (widthDp / 2 - SIDE_MARGIN_DP) / rightReach
        return minOf(left, right).toFloat().coerceAtLeast(1f)
    }
}
