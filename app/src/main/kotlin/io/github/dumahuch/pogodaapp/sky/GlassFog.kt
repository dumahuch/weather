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
 * мелкие клетки. След мягкий: чистая только середина, к краям плёнка плавно густеет, граница неровная. Возвращается
 * запотевание как настоящее: от краёв к середине (след сужается), мелкой дымкой, потому что клетки возвращаются в чуть
 * разное время; даже самая чистая середина остаётся слегка влажной ([MAX_CLEAR]). В 0.8.20 след был резкой нашлёпкой и
 * бледнел целиком (Дима 13.11: «след слишком резкий, обратное запотевание неестественное»).
 */
internal class FogWipe(
    val widthDp: Double,
    val heightDp: Double,
    /** Сколько секунд след держится и за сколько затягивается: у тумана [HOLD_SECONDS] и [RETURN_SECONDS], у инея дольше. */
    private val holdSeconds: Float = HOLD_SECONDS,
    private val returnSeconds: Float = RETURN_SECONDS,
    /** Сторона клетки не меньше этой (dp), клеток не больше [maxCells]: у инея сетка мельче, на ней же рисуется узор. */
    minCellDp: Double = MIN_CELL_DP,
    maxCells: Double = MAX_CELLS,
) {
    /** Сторона клетки: не меньше minCellDp, у большой карточки крупнее, чтобы клеток было не больше maxCells. */
    val cell: Double = max(minCellDp, sqrt(widthDp * heightDp / maxCells))
    val cols: Int = max(1, ceil(widthDp / cell).toInt())
    val rows: Int = max(1, ceil(heightDp / cell).toInt())

    /** Сила стирания в клетке от кисти (1 в середине следа, к краю меньше), сколько секунд назад стёрто и своя неровность клетки. */
    private val strength = FloatArray(cols * rows)
    private val age = FloatArray(cols * rows)
    private val grain = FloatArray(cols * rows) { noiseAt(it) * GRAIN }

    /** Есть ли стёртые клетки, которым ещё возвращаться. */
    var active = false
        private set

    /** Доля плёнки в клетке [index] (1 нетронута, [MAX_CLEAR] от неё убрано у самой чистой). */
    fun cover(index: Int): Float = 1f - MAX_CLEAR * clearness(index)

    /** Насколько клетка расчищена (0…1): кисть, сдвинутая зерном и отодвигаемая возвращающейся плёнкой. */
    private fun clearness(index: Int): Float {
        val base = strength[index]
        if (base <= 0f) return 0f
        return ((base + grain[index] - returned(age[index]) * (1f + SPREAD)) / SOFT).coerceIn(0f, 1f)
    }

    /** Сколько плёнки уже вернулось через [seconds] после стирания: первые [HOLD_SECONDS] ничего, потом плавно до 1. */
    private fun returned(seconds: Float): Float =
        smooth(holdSeconds.toDouble(), (holdSeconds + returnSeconds).toDouble(), seconds.toDouble()).toFloat()

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
                val value = (1 - smooth(0.0, BRUSH_DP, d)).toFloat()
                val index = row * cols + col
                // Новый мазок заменяет старый, только если расчищает клетку сильнее, чем она расчищена сейчас.
                if ((value + grain[index]) / SOFT > clearness(index)) {
                    strength[index] = value
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
        for (i in strength.indices) {
            if (strength[i] <= 0f) continue
            age[i] += d
            if (age[i] >= holdSeconds + returnSeconds) strength[i] = 0f else any = true
        }
        active = any
        return any
    }

    private fun distanceToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else ((px - x1) * dx + (py - y1) * dy) / length
        val k = t.coerceIn(0.0, 1.0)
        return hypot(px - (x1 + k * dx), py - (y1 + k * dy))
    }

    /** Одно и то же число для одной клетки при каждом запуске, от −1 до 1: без случайных чисел, чтобы стекло не мерцало при пересоздании. */
    private fun noiseAt(index: Int): Float {
        var h = index * -1640531535
        h = h xor (h ushr 15)
        h *= -2048144789
        h = h xor (h ushr 13)
        return ((h ushr 8) and 0xFFFF) / 32767.5f - 1f
    }

    companion object {
        /** Мелкая клетка: при растяжке со сглаживанием край следа мягкий, без лесенки. */
        private const val MIN_CELL_DP = 2.0
        private const val MAX_CELLS = 40_000.0

        /** Полуширина следа пальца (dp): в 0.8.20 было 15, Дима попросил пошире. Чистая середина занимает около половины. */
        const val BRUSH_DP = 24.0

        /** Где сила кисти делает клетку полностью чистой (доля), неровность края по клеткам и запас, чтобы всё успело вернуться. */
        private const val SOFT = 0.7f
        private const val GRAIN = 0.1f
        private const val SPREAD = 0.2f

        /** Даже самая чистая середина остаётся слегка влажной: доля плёнки, которую можно убрать. */
        const val MAX_CLEAR = 0.9f

        /** Туман начинает возвращаться через [HOLD_SECONDS] и возвращается целиком за [RETURN_SECONDS]. */
        const val HOLD_SECONDS = 3f
        const val RETURN_SECONDS = 17f
        private const val MAX_DT = 0.1
    }
}
