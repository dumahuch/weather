package io.github.dumahuch.pogodaapp.widget

import kotlin.math.max

/** Раскладка блоков по высоте: чистая арифметика для рисования виджета, [WidgetSpacingTest]. */
object WidgetSpacing {
    /**
     * Верхние края блоков высотой [heights] в области от [start] высотой [total] сверху вниз. Остаток высоты делится
     * поровну между блоками (между первым и последним, у одного блока по краям), так в виджете нет пустоты у края и
     * блоки не слипаются. Если блоки не помещаются, они идут подряд без промежутков от [start].
     */
    fun tops(start: Float, total: Float, heights: List<Float>): List<Float> {
        if (heights.isEmpty()) return emptyList()
        val slack = max(0f, total - heights.sum())
        val gap = if (heights.size == 1) 0f else slack / (heights.size - 1)
        var y = start + if (heights.size == 1) slack / 2 else 0f
        val result = ArrayList<Float>(heights.size)
        for (height in heights) {
            result += y
            y += height + gap
        }
        return result
    }

    /** Сколько строк высотой [rowHeight] помещается в [space], но не больше [limit] и не меньше нуля. */
    fun rows(space: Float, rowHeight: Float, limit: Int): Int = (space / rowHeight).toInt().coerceIn(0, limit)
}
