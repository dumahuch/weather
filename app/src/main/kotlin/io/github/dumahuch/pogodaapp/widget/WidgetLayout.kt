package io.github.dumahuch.pogodaapp.widget

import kotlin.math.floor

/**
 * Раскладка единого растягиваемого виджета (0.8.8; решение Димы 12.11.2026: один виджет, чем больше размер, тем больше
 * сведений). Раскладку выбирает размер в клетках рабочего стола ([WidgetLayouts.pick]). [cols] и [rows] номинальный размер
 * в клетках, по нему считается размер рисунка в «единицах макета» ([designWidth], [designHeight]): рисунок макета Димы
 * (клетка 230, зазор 16), всё рисуется в этих единицах и масштабируется под настоящий размер.
 */
enum class WidgetLayout(val cols: Int, val rows: Int) {
    /** 1×1: значок и температура. */
    Tiny(1, 1),

    /** 2×1: значок, город, температура, максимум и минимум. */
    Narrow(2, 1),

    /** 4×1: то же плюс описание и часы. */
    Strip(4, 1),

    /** 2×2: город, описание, значок, температура, часы. */
    Square(2, 2),

    /** 4×2: слева текущая погода, справа часы, три дня и показатели. */
    Wide(4, 2),

    /** 4×4: всё сразу: часы, пять дней, шесть показателей. */
    Large(4, 4);

    val designWidth: Float get() = WidgetLayouts.CELL * cols - WidgetLayouts.GAP
    val designHeight: Float get() = WidgetLayouts.CELL * rows - WidgetLayouts.GAP
}

object WidgetLayouts {
    /** Клетка и зазор в единицах макета. */
    const val CELL = 230f
    const val GAP = 16f

    /** Размер виджета в dp при n клетках: 70·n − 30 (правило Android для рабочего стола). */
    private const val CELL_DP = 70.0
    private const val OFFSET_DP = 30.0

    /** Допуск: лаунчеры округляют размеры по-своему, 0,3 клетки не меняют число клеток. */
    private const val TOLERANCE = 0.3

    /** Сколько клеток занимает сторона [sizeDp] (не меньше одной). */
    fun cells(sizeDp: Int): Int = floor((sizeDp + OFFSET_DP) / CELL_DP + TOLERANCE).toInt().coerceAtLeast(1)

    /**
     * Раскладка по размеру в dp. Узкий виджет (одна клетка в ширину) всегда [WidgetLayout.Tiny]; одна клетка в высоту:
     * до трёх клеток в ширину [WidgetLayout.Narrow], от четырёх [WidgetLayout.Strip]; от двух клеток в высоту: до трёх
     * в ширину [WidgetLayout.Square], от четырёх в ширину и до трёх в высоту [WidgetLayout.Wide], иначе [WidgetLayout.Large].
     */
    fun pick(widthDp: Int, heightDp: Int): WidgetLayout {
        val cols = cells(widthDp)
        val rows = cells(heightDp)
        return when {
            cols <= 1 -> WidgetLayout.Tiny
            rows <= 1 -> if (cols <= 3) WidgetLayout.Narrow else WidgetLayout.Strip
            cols <= 3 -> WidgetLayout.Square
            rows <= 3 -> WidgetLayout.Wide
            else -> WidgetLayout.Large
        }
    }
}
