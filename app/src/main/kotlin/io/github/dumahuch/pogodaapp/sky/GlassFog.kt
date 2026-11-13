package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Насколько запотевает стекло карточек (0 сухое, 1 густая плёнка). Решение Димы 13.11.2026: туман на стекле при погоде
 * «туман», при мелком дожде и при высокой влажности. Рисует [io.github.dumahuch.pogodaapp.ui.components.glassFog].
 */
internal object GlassFog {
    /** Туман: густота от 0,6 при видимости около километра до 1 при 200 м и меньше (неизвестная видимость считается густым туманом). */
    private const val FOG_MIN = 0.6
    private const val THICK_FOG_M = 200.0
    private const val THIN_FOG_M = 1000.0

    /** Мелкий дождь: 0,55 до 0,3 мм в час, к 1,2 мм плёнки нет (сильный дождь смывает её). */
    private const val DRIZZLE = 0.55
    private const val DRIZZLE_MM = 0.3
    private const val RAIN_MM = 1.2

    /** Влажность: плёнка появляется с 88 % и к 97 % доходит до половины густоты. */
    private const val DAMP = 0.5
    private const val DAMP_FROM = 88.0
    private const val DAMP_FULL = 97.0

    fun density(condition: Condition, precipitationMm: Double?, humidity: Int?, visibilityM: Double?): Double {
        val fog = if (condition == Condition.Fog) {
            FOG_MIN + (1 - FOG_MIN) * (1 - smooth(THICK_FOG_M, THIN_FOG_M, visibilityM ?: 0.0))
        } else {
            0.0
        }
        val drizzle = if (condition == Condition.Rain && precipitationMm != null) {
            DRIZZLE * (1 - smooth(DRIZZLE_MM, RAIN_MM, precipitationMm))
        } else {
            0.0
        }
        val damp = humidity?.let { DAMP * smooth(DAMP_FROM, DAMP_FULL, it.toDouble()) } ?: 0.0
        return maxOf(fog, drizzle, damp)
    }
}

/**
 * Где на запотевшем стекле размером [widthDp] × [heightDp] прошёл палец (всё в dp, время в секундах). Стекло делится на
 * мелкие клетки; стёртый след несколько секунд держится чистым, потом весь целиком плавно запотевает снова (не сужается
 * от краёв: так было в 0.8.19, и возврат выглядел ступенчатым).
 */
internal class FogWipe(val widthDp: Double, val heightDp: Double) {
    /** Сторона клетки: не меньше [MIN_CELL_DP], у большой карточки крупнее, чтобы клеток было не больше [MAX_CELLS]. */
    val cell: Double = max(MIN_CELL_DP, sqrt(widthDp * heightDp / MAX_CELLS))
    val cols: Int = max(1, ceil(widthDp / cell).toInt())
    val rows: Int = max(1, ceil(heightDp / cell).toInt())

    /** Насколько чистой стала клетка от пальца (0…1, край следа мягче) и сколько секунд назад. */
    private val clear = FloatArray(cols * rows)
    private val age = FloatArray(cols * rows)

    /** Есть ли стёртые клетки, которым ещё возвращаться. */
    var active = false
        private set

    /** Доля плёнки в клетке [index] (0 стёрта, 1 нетронута). */
    fun cover(index: Int): Float = 1f - clear[index] * fade(age[index])

    /** Палец прошёл от ([x1], [y1]) до ([x2], [y2]): клетки вдоль пути стираются, к краю следа мягче. */
    fun wipe(x1: Double, y1: Double, x2: Double, y2: Double) {
        if (max(x1, x2) < -BRUSH_DP || min(x1, x2) > widthDp + BRUSH_DP || max(y1, y2) < -BRUSH_DP || min(y1, y2) > heightDp + BRUSH_DP) return
        val left = ((min(x1, x2) - BRUSH_DP) / cell).toInt().coerceIn(0, cols - 1)
        val right = ((max(x1, x2) + BRUSH_DP) / cell).toInt().coerceIn(0, cols - 1)
        val top = ((min(y1, y2) - BRUSH_DP) / cell).toInt().coerceIn(0, rows - 1)
        val bottom = ((max(y1, y2) + BRUSH_DP) / cell).toInt().coerceIn(0, rows - 1)
        for (row in top..bottom) {
            for (col in left..right) {
                val d = distanceToSegment((col + 0.5) * cell, (row + 0.5) * cell, x1, y1, x2, y2)
                if (d >= BRUSH_DP) continue
                val value = (1 - smooth(BRUSH_DP * CORE, BRUSH_DP, d)).toFloat()
                val index = row * cols + col
                if (value > clear[index] * fade(age[index])) {
                    clear[index] = value
                    age[index] = 0f
                    active = true
                }
            }
        }
    }

    /** Время идёт на [dt] секунд; `true`, если ещё есть что возвращать. Большие перерывы режутся. */
    fun step(dt: Double): Boolean {
        if (!active) return false
        val d = dt.coerceIn(0.0, MAX_DT).toFloat()
        var any = false
        for (i in clear.indices) {
            if (clear[i] <= 0f) continue
            age[i] += d
            if (age[i] >= HOLD_SECONDS + RETURN_SECONDS) clear[i] = 0f else any = true
        }
        active = any
        return any
    }

    /** Какая доля чистоты ещё держится через [seconds] после стирания: 1 первые секунды, потом плавно до 0. */
    private fun fade(seconds: Float): Float =
        if (seconds <= HOLD_SECONDS) 1f else 1f - smooth(HOLD_SECONDS.toDouble(), (HOLD_SECONDS + RETURN_SECONDS).toDouble(), seconds.toDouble()).toFloat()

    private fun distanceToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else ((px - x1) * dx + (py - y1) * dy) / length
        val k = t.coerceIn(0.0, 1.0)
        return hypot(px - (x1 + k * dx), py - (y1 + k * dy))
    }

    companion object {
        /** Мелкая клетка: при растяжке со сглаживанием край следа мягкий, без лесенки (в 0.8.19 было 5 dp). */
        private const val MIN_CELL_DP = 2.0
        private const val MAX_CELLS = 40_000.0

        /** Полуширина следа пальца (dp) и доля, где след чистый полностью. */
        const val BRUSH_DP = 15.0
        private const val CORE = 0.45

        /** Стёртое держится чистым столько секунд, потом запотевает целиком за [RETURN_SECONDS]. */
        const val HOLD_SECONDS = 5f
        const val RETURN_SECONDS = 15f
        private const val MAX_DT = 0.1
    }
}
