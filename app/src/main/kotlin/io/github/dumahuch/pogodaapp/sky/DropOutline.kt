package io.github.dumahuch.pogodaapp.sky

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Контур капли на вертикальном стекле. Капля тянется вниз под собственным весом: снизу круглая и широкая, кверху
 * сужается. [tail] 0 даёт круг, около 0,5 яйцо, 1 и больше каплю с острым хвостом вверх («комета» стекающей капли).
 */
internal object DropOutline {

    const val POINTS = 40

    /**
     * Точки контура по часовой стрелке от вершины, пары (x, y) подряд: вершина (0, −1), низ (0, 1), самая широкая
     * часть по x ровно в ±1. Так форму можно растянуть до любых полуосей простым масштабом.
     */
    fun points(tail: Double, count: Int = POINTS): DoubleArray {
        val out = DoubleArray(count * 2)
        var widest = 0.0
        for (i in 0 until count) {
            val t = 2 * PI * i / count
            val x = sin(t) * sin(t / 2).pow(max(0.0, tail))
            out[2 * i] = x
            out[2 * i + 1] = -cos(t)
            widest = max(widest, abs(x))
        }
        if (widest > 0) for (i in 0 until count) out[2 * i] /= widest
        return out
    }
}
