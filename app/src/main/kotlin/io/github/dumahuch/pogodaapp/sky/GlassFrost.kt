package io.github.dumahuch.pogodaapp.sky

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Иней на стекле карточек (решение Димы 13.11.2026: «при минусовых температурах от минус трёх всё стекло замерзало»). При −3 °C
 * тонкая кайма по краям, к −10 °C стекло матовое целиком. Рисует [io.github.dumahuch.pogodaapp.ui.components.glassFrost].
 */
internal object GlassFrost {
    const val START_C = -3.0
    const val FULL_C = -10.0

    /** Доля стекла под инеем при −3 °C: только кайма. */
    private const val RIM = 0.25

    /** Насколько стекло затянуто инеем при температуре воздуха [temperatureC] (0 тепло, 1 матовое целиком). */
    fun coverage(temperatureC: Double?): Double {
        if (temperatureC == null || temperatureC > START_C) return 0.0
        return RIM + (1 - RIM) * smooth(START_C, FULL_C, temperatureC)
    }

    /** Отогретый пальцем след держится столько секунд и снова замерзает за столько (около минуты в сумме). */
    const val MELT_HOLD_SECONDS = 8f
    const val REFREEZE_SECONDS = 50f
}

/**
 * Узор инея на сетке [cols] × [rows] клеток стороной [cell] dp. [threshold] для каждой клетки: при какой доле [GlassFrost.coverage]
 * клетка замерзает (0 у самых краёв, 1 в глубине середины); ветки-«папоротники» растут от краёв и замерзают раньше соседей,
 * поэтому фронт инея идёт неровно, кристаллами вперёд. [crystal]: яркость прожилок (0 ровный иней, 1 ветка).
 */
internal class FrostPattern(val cols: Int, val rows: Int, val cell: Double, seed: Long) {
    val threshold = FloatArray(cols * rows)
    val crystal = FloatArray(cols * rows)
    private val random = Random(seed)
    private val widthDp = cols * cell
    private val heightDp = rows * cell
    private val reach = min(widthDp, heightDp) / 2

    init {
        // Ровный иней: от краёв внутрь, у углов раньше (там стекло остывает сильнее), граница неровная.
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = (col + 0.5) * cell
                val y = (row + 0.5) * cell
                val dx = min(x, widthDp - x)
                val dy = min(y, heightDp - y)
                val edge = min(dx, dy) / reach
                val corner = min(1.0, (dx + dy) / (1.4 * reach))
                val wobble = 0.85 + 0.3 * noise(col, row)
                threshold[row * cols + col] = (edge.pow(0.85) * (0.75 + 0.25 * corner) * wobble).coerceIn(0.0, 1.0).toFloat() * DEEPEST
            }
        }
        val count = max(4, ((widthDp + heightDp) * 2 / BRANCH_SPACING_DP).toInt())
        repeat(count) { seedBranch() }
    }

    private fun seedBranch() {
        val perimeter = 2 * (widthDp + heightDp)
        val p = random.nextDouble() * perimeter
        val (x, y, inward) = when {
            p < widthDp -> Triple(p, 0.0, PI / 2)
            p < widthDp + heightDp -> Triple(widthDp, p - widthDp, PI)
            p < 2 * widthDp + heightDp -> Triple(p - widthDp - heightDp, heightDp, -PI / 2)
            else -> Triple(0.0, p - 2 * widthDp - heightDp, 0.0)
        }
        val length = reach * (0.35 + 0.65 * random.nextDouble())
        grow(x, y, inward + (random.nextDouble() - 0.5) * 1.2, length, 0.0, depth = 0)
    }

    /** Ветка из ([x0], [y0]) в направлении [angle] длиной [length] dp; [start] сколько dp уже пройдено от края по родителю. */
    private fun grow(x0: Double, y0: Double, angle: Double, length: Double, start: Double, depth: Int) {
        var x = x0
        var y = y0
        var a = angle
        var walked = 0.0
        var nextSide = SIDE_EVERY_DP * (0.5 + random.nextDouble())
        val step = cell * 0.7
        while (walked < length) {
            a += (random.nextDouble() - 0.5) * 0.16
            x += cos(a) * step
            y += sin(a) * step
            walked += step
            if (x < 0 || y < 0 || x >= widthDp || y >= heightDp) return
            val along = start + walked
            val fade = 1.0 - walked / length * 0.6
            stamp(x, y, (along / reach * LEAD).toFloat(), (fade * (1.0 - depth * 0.25)).toFloat())
            if (depth < MAX_DEPTH && walked >= nextSide) {
                nextSide += SIDE_EVERY_DP * (0.6 + 0.8 * random.nextDouble())
                val side = if (random.nextBoolean()) 1 else -1
                val sideLength = (length - walked) * (0.35 + 0.35 * random.nextDouble())
                if (sideLength > cell * 3) grow(x, y, a + side * SIDE_ANGLE * (0.8 + 0.4 * random.nextDouble()), sideLength, along, depth + 1)
            }
        }
    }

    /** Ветка в точке ([x], [y]): клетка замерзает не позже [arrive], прожилка яркостью [bright]. */
    private fun stamp(x: Double, y: Double, arrive: Float, bright: Float) {
        val col = (x / cell).toInt().coerceIn(0, cols - 1)
        val row = (y / cell).toInt().coerceIn(0, rows - 1)
        val i = row * cols + col
        if (arrive < threshold[i]) threshold[i] = arrive
        if (bright > crystal[i]) crystal[i] = bright
    }

    /** Одно и то же число для одной клетки, от 0 до 1: крупная плавная неровность границы. */
    private fun noise(col: Int, row: Int): Double {
        val scale = NOISE_DP / cell
        val gx = col / scale
        val gy = row / scale
        val x0 = gx.toInt()
        val y0 = gy.toInt()
        val tx = smooth(0.0, 1.0, gx - x0)
        val ty = smooth(0.0, 1.0, gy - y0)
        return lerp(lerp(hash(x0, y0), hash(x0 + 1, y0), tx), lerp(hash(x0, y0 + 1), hash(x0 + 1, y0 + 1), tx), ty)
    }

    private fun hash(x: Int, y: Int): Double {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535.0
    }

    companion object {
        /** Одна ветка на столько dp кромки, боковые отростки через столько dp под таким углом, не глубже [MAX_DEPTH]. */
        private const val BRANCH_SPACING_DP = 22.0
        private const val SIDE_EVERY_DP = 7.0
        private const val SIDE_ANGLE = 1.0
        private const val MAX_DEPTH = 2

        /** Ветка забегает вперёд ровного инея: клетка на ветке замерзает при такой доле её расстояния от края. */
        private const val LEAD = 0.6
        private const val NOISE_DP = 30.0

        /** Самая глубокая клетка замерзает чуть раньше полного мороза: при −10 °C с мягким краем инея не остаётся чистого пятна. */
        const val DEEPEST = 0.92f
    }
}
