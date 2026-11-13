package io.github.dumahuch.pogodaapp.sky

import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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

    /** Сетка инея мельче, чем у тумана: перья в одну клетку не видны квадратами (в 0.8.22 было 2 dp). */
    const val CELL_DP = 1.0
    const val MAX_CELLS = 120_000.0
}

/**
 * Узор инея на сетке [cols] × [rows] клеток стороной [cell] dp у стекла со скруглением углов [radiusDp]. Иней как настоящий
 * (Дима 14.11: в 0.8.22 «не похоже, выглядит как белые корни», на кнопках «квадратное»): прежде всего матовая зернистая
 * плёнка, гуще у краёв; кристаллы только проступают в ней тонкими полупрозрачными перьями. Всё идёт от кромки самой формы,
 * а не от прямоугольника, поэтому на круглой кнопке иней лежит кольцом.
 *
 * [threshold]: при какой доле [GlassFrost.coverage] клетка замерзает (0 у кромки, около [DEEPEST] в глубине середины), граница
 * зернистая. [matte]: густота матовой плёнки (0…1). [crystal]: яркость пера в клетке (0…1).
 */
internal class FrostPattern(val cols: Int, val rows: Int, val cell: Double, radiusDp: Double, seed: Long) {
    val threshold = FloatArray(cols * rows)
    val matte = FloatArray(cols * rows)
    val crystal = FloatArray(cols * rows)
    private val random = Random(seed)
    private val widthDp = cols * cell
    private val heightDp = rows * cell
    private val reach = min(widthDp, heightDp) / 2
    private val radius = radiusDp.coerceIn(0.0, reach)

    init {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val i = row * cols + col
                val edge = (inside((col + 0.5) * cell, (row + 0.5) * cell) / reach).coerceIn(0.0, 1.0)
                val grain = hash(col, row)
                val wobble = 0.85 + 0.3 * noise(col, row)
                threshold[i] = ((edge.pow(0.85) * wobble + (grain - 0.5) * GRAIN_EDGE).coerceIn(0.0, 1.0) * DEEPEST).toFloat()
                matte[i] = (MATTE_CENTRE + (1 - MATTE_CENTRE) * (1 - edge) + (grain - 0.5) * MATTE_GRAIN).coerceIn(0.0, 1.0).toFloat()
            }
        }
        val count = max(2, (2 * (widthDp + heightDp) / STEM_SPACING_DP).toInt())
        repeat(count) { seedStem() }
    }

    /** Расстояние от точки ([x], [y]) внутрь до кромки скруглённой формы (dp, 0 на кромке и снаружи). */
    private fun inside(x: Double, y: Double): Double {
        val qx = abs(x - widthDp / 2) - (widthDp / 2 - radius)
        val qy = abs(y - heightDp / 2) - (heightDp / 2 - radius)
        val outside = hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - radius
        return max(0.0, -outside)
    }

    /** Перо начинается у кромки в случайной точке и растёт внутрь, примерно поперёк кромки. */
    private fun seedStem() {
        repeat(ATTEMPTS) {
            val x = random.nextDouble() * widthDp
            val y = random.nextDouble() * heightDp
            val d = inside(x, y)
            if (d <= 0.0 || d > cell * 2.5) return@repeat
            // Внутрь: по наклону расстояния до кромки.
            val gx = inside(x + 0.5, y) - inside(x - 0.5, y)
            val gy = inside(x, y + 0.5) - inside(x, y - 0.5)
            val angle = atan2(gy, gx) + (random.nextDouble() - 0.5) * 0.9
            val length = reach * (0.3 + 0.6 * random.nextDouble())
            stem(x, y, angle, length, 0.0, BRIGHT, depth = 0)
            return
        }
    }

    /**
     * Стебель пера из ([x0], [y0]) в направлении [angle] длиной [length] dp: тонкая линия, по обе стороны частые короткие
     * бородки под острым углом вперёд; изредка отходит новый стебель.
     */
    private fun stem(x0: Double, y0: Double, angle: Double, length: Double, start: Double, bright: Double, depth: Int) {
        var x = x0
        var y = y0
        var a = angle
        var walked = 0.0
        var nextBarb = BARB_EVERY_DP * random.nextDouble()
        var nextFork = FORK_EVERY_DP * (0.6 + 0.8 * random.nextDouble())
        val step = cell * 0.5
        while (walked < length) {
            a += (random.nextDouble() - 0.5) * 0.1
            x += cos(a) * step
            y += sin(a) * step
            walked += step
            if (inside(x, y) <= 0.0) return
            val left = 1 - walked / length
            line(x, y, ((start + walked) / reach * LEAD).toFloat(), bright * (0.5 + 0.5 * left))
            if (walked >= nextBarb) {
                nextBarb += BARB_EVERY_DP * (0.7 + 0.6 * random.nextDouble())
                val barb = BARB_DP * (0.4 + 0.6 * left) * (0.6 + 0.8 * random.nextDouble())
                for (side in intArrayOf(-1, 1)) {
                    if (random.nextDouble() < 0.85) barb(x, y, a + side * BARB_ANGLE * (0.8 + 0.4 * random.nextDouble()), barb, start + walked, bright * 0.7 * left.coerceAtLeast(0.3))
                }
            }
            if (depth < MAX_DEPTH && walked >= nextFork) {
                nextFork += FORK_EVERY_DP * (0.8 + 0.8 * random.nextDouble())
                val side = if (random.nextBoolean()) 1 else -1
                val rest = (length - walked) * (0.4 + 0.3 * random.nextDouble())
                if (rest > BARB_DP * 2) stem(x, y, a + side * FORK_ANGLE, rest, start + walked, bright * 0.85, depth + 1)
            }
        }
    }

    /** Короткая прямая бородка: замерзает вместе со своим местом на стебле. */
    private fun barb(x0: Double, y0: Double, angle: Double, length: Double, start: Double, bright: Double) {
        var walked = 0.0
        val step = cell * 0.5
        while (walked < length) {
            walked += step
            val x = x0 + cos(angle) * walked
            val y = y0 + sin(angle) * walked
            if (inside(x, y) <= 0.0) return
            line(x, y, ((start + walked) / reach * LEAD).toFloat(), bright * (1 - 0.6 * walked / length), add = true)
        }
    }

    /** Перо в точке ([x], [y]) dp: тонкая линия с мягким краем (доля яркости на соседние клетки по расстоянию); бородки [add]. */
    private fun line(x: Double, y: Double, arrive: Float, bright: Double, add: Boolean = false) {
        val gx = x / cell - 0.5
        val gy = y / cell - 0.5
        val c0 = floor(gx).toInt()
        val r0 = floor(gy).toInt()
        val fx = gx - c0
        val fy = gy - r0
        for (dr in 0..1) {
            for (dc in 0..1) {
                val col = c0 + dc
                val row = r0 + dr
                if (col !in 0 until cols || row !in 0 until rows) continue
                val w = (if (dc == 0) 1 - fx else fx) * (if (dr == 0) 1 - fy else fy)
                val i = row * cols + col
                // Бородки складываются: частые бледные штрихи сливаются в пушистое перо. Стебель берёт яркость наибольшей.
                val value = (bright * w * LINE_GAIN).toFloat()
                crystal[i] = if (add) min(1f, crystal[i] + value * BARB_GAIN) else max(crystal[i], min(1f, value))
                if (w > 0.3 && arrive < threshold[i]) threshold[i] = arrive
            }
        }
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
        /** Одно перо на столько dp кромки; бородки через столько dp, такой длины и под таким углом; развилки реже. */
        private const val STEM_SPACING_DP = 30.0
        private const val BARB_EVERY_DP = 0.6
        private const val BARB_DP = 5.5
        private const val BARB_ANGLE = 0.7
        private const val FORK_EVERY_DP = 22.0
        private const val FORK_ANGLE = 0.75
        private const val MAX_DEPTH = 2
        private const val ATTEMPTS = 60
        private const val BRIGHT = 1.0
        private const val LINE_GAIN = 1.4
        private const val BARB_GAIN = 0.35f

        /** Перо забегает вперёд плёнки: клетка на пере замерзает при такой доле её расстояния от кромки. */
        private const val LEAD = 0.65
        private const val NOISE_DP = 30.0

        /** Зернистость границы роста и плёнки; густота плёнки в середине (у кромки 1). */
        private const val GRAIN_EDGE = 0.08
        private const val MATTE_GRAIN = 0.35
        private const val MATTE_CENTRE = 0.55

        /** Самая глубокая клетка замерзает чуть раньше полного мороза: при −10 °C с мягким краем инея не остаётся чистого пятна. */
        const val DEEPEST = 0.92f
    }
}
