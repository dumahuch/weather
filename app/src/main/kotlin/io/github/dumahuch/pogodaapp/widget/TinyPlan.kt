package io.github.dumahuch.pogodaapp.widget

/**
 * Вертикальная раскладка виджета 1×1 (решение Димы 14.11.2026: цифра по центру, город сверху и максимум с минимумом внизу
 * остаются). Клетка Pixel около 62×94 dp, то есть в единицах макета 214 × 324: город у верхнего поля, максимум и минимум у
 * нижнего, между ними значок (не на небе) и цифра, по возможности по центру виджета. Если места мало, цифра становится меньше,
 * а подписи (не мельче [LABEL]) не уменьшаются и не налезают друг на друга.
 */
internal object TinyPlan {
    const val LABEL = 36f
    const val PAD = 38f
    private const val GAP = 14f
    private const val MIN_DIGITS = 60f

    /** [cityBase] нижняя линия города, [hiLoBase] нижняя линия максимума и минимума, [iconTop] верх значка (или цифр, если значка нет). */
    data class Plan(val digitSize: Float, val digitsBase: Float, val iconTop: Float, val cityBase: Float, val hiLoBase: Float)

    /**
     * Холст высотой [h]; [fitted] размер цифр, который позволяет ширина; [iconHeight] высота значка над цифрами (0 на небе);
     * [digitHeight] высота цифры в долях размера.
     */
    fun plan(h: Float, fitted: Float, iconHeight: Float, digitHeight: Float): Plan {
        val cityBase = PAD + LABEL * digitHeight
        val hiLoBase = h - PAD
        val zoneTop = cityBase + GAP
        val zoneBottom = hiLoBase - LABEL * digitHeight - GAP
        val room = ((zoneBottom - zoneTop - iconHeight) / digitHeight).coerceAtLeast(MIN_DIGITS)
        val size = minOf(fitted, room)
        val group = iconHeight + size * digitHeight
        val top = maxOf(zoneTop, minOf((h - group) / 2, zoneBottom - group))
        return Plan(size, top + group, top, cityBase, hiLoBase)
    }
}
