package io.github.dumahuch.pogodaapp.sky

import kotlin.math.max

/**
 * Форма подложек на небе по высоте экрана (в dp экрана 412×892, как в макете): подложка под большими цифрами и подложка
 * под строками под температурой. Обе одного оттенка, поэтому в каждой точке берётся наибольшая из двух плотностей, а не
 * сумма: там, где они перекрываются (около 320…440 dp), иначе получалась бы двойная вуаль.
 * Чистая логика без Android: [ScrimShapeTest].
 */
internal object ScrimShape {

    /** Высота экрана, к которой привязаны числа. */
    const val SCREEN_HEIGHT = 892.0

    /** Под цифрами: плотность 0 до 96 dp, полная с 20 % до 72 % пути до 440 dp, затем снова 0 (как в макете). */
    private const val HERO_TOP = 96.0
    private const val HERO_BOTTOM = 440.0
    private const val HERO_FULL_FROM = HERO_TOP + 0.2 * (HERO_BOTTOM - HERO_TOP)
    private const val HERO_FULL_TO = HERO_TOP + 0.72 * (HERO_BOTTOM - HERO_TOP)

    /**
     * Под строками под температурой (на экране 412×892 они лежат между 320 и 460 dp): с запасом по краям на разные
     * высоты экрана и на перенос подписей в две строки.
     */
    private const val INFO_TOP = 285.0
    private const val INFO_FULL_FROM = 315.0
    private const val INFO_FULL_TO = 470.0
    private const val INFO_BOTTOM = 515.0

    /** Шаг, с которым снимается итоговая форма (кроме углов обеих форм, они берутся точно). */
    private const val SAMPLE_STEP = 20.0

    private fun ramp(y: Double, zero1: Double, full1: Double, full2: Double, zero2: Double): Double = when {
        y <= zero1 || y >= zero2 -> 0.0
        y < full1 -> (y - zero1) / (full1 - zero1)
        y <= full2 -> 1.0
        else -> (zero2 - y) / (zero2 - full2)
    }

    /** Плотность в точке [y] (dp от верха экрана 892 dp) при плотностях [hero] и [info] в полосах. */
    fun alphaAt(y: Double, hero: Double, info: Double): Double = max(
        hero * ramp(y, HERO_TOP, HERO_FULL_FROM, HERO_FULL_TO, HERO_BOTTOM),
        info * ramp(y, INFO_TOP, INFO_FULL_FROM, INFO_FULL_TO, INFO_BOTTOM),
    )

    /** Остановки градиента по высоте: пары (доля высоты от 0 до 1, плотность). */
    fun stops(hero: Double, info: Double): List<Pair<Float, Float>> {
        val ys = sortedSetOf(0.0, SCREEN_HEIGHT, HERO_TOP, HERO_FULL_FROM, HERO_FULL_TO, HERO_BOTTOM,
            INFO_TOP, INFO_FULL_FROM, INFO_FULL_TO, INFO_BOTTOM)
        var y = SAMPLE_STEP
        while (y < SCREEN_HEIGHT) {
            ys.add(y)
            y += SAMPLE_STEP
        }
        return ys.map { (it / SCREEN_HEIGHT).toFloat() to alphaAt(it, hero, info).toFloat() }
    }
}
