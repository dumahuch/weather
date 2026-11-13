package io.github.dumahuch.pogodaapp.sky

import java.util.Random
import kotlin.math.floor

/**
 * Туман на самом небе (решение Димы 13.11.2026: «затуманено стекло, а тумана как такового нет»): ровная дымка, гуще к низу,
 * и поверх неё медленно плывущие полосы. Цвет берётся от горизонта неба, поэтому ночью туман тёмный и приглушённый, днём
 * молочный. Рисует [io.github.dumahuch.pogodaapp.ui.sky.FogLayer].
 */
internal object SkyFog {
    /** Насколько виден туман по туманности погоды неба (у «тумана» 0,9, у дождя и снега меньше 0,2): только настоящий туман. */
    fun amount(weatherFog: Double): Double = smooth(0.3, 0.85, weatherFog)

    /** Цвет тумана: горизонт неба, чуть светлее. */
    fun color(horizon: Rgb): Rgb = mix(horizon, Rgb.White, LIGHTEN)

    /** Полоса тумана: середина и высота (доли высоты экрана), скорость (dp в секунду), ширина узора (доли ширины экрана), непрозрачность. */
    class Band(val centre: Double, val height: Double, val speed: Double, val width: Double, val alpha: Double)

    /** Дальние полосы выше, медленнее и прозрачнее, ближние ниже, быстрее и плотнее. */
    val Bands = listOf(
        Band(centre = 0.3, height = 0.34, speed = 3.0, width = 1.9, alpha = 0.45),
        Band(centre = 0.52, height = 0.36, speed = 6.0, width = 1.6, alpha = 0.6),
        Band(centre = 0.74, height = 0.44, speed = 10.0, width = 1.3, alpha = 0.75),
    )

    /** Ровная дымка по всему небу: непрозрачность сверху, посередине и внизу. Сверху хватает, чтобы луна и звёзды тонули. */
    const val VEIL_TOP = 0.3
    const val VEIL_MID = 0.42
    const val VEIL_BOTTOM = 0.58

    private const val LIGHTEN = 0.12
    private const val OCTAVES = 4
    private const val PERSISTENCE = 0.5

    /**
     * Клочья: в каждой строке узора самая редкая доля [CLUMP_FROM] пустая, самая густая начиная с [CLUMP_TO] полная (доли
     * по порядку значений в строке). Так в любой полосе есть и просветы, и плотные клочья, какой бы ни выпал узор.
     */
    private const val CLUMP_FROM = 0.3
    private const val CLUMP_TO = 0.8

    /**
     * Узор полосы [width] × [height] (значения 0…1, построчно): клочья тумана, повторяется по горизонтали без шва, сверху и
     * снизу сходит на нет.
     */
    fun texture(width: Int, height: Int, seed: Long): FloatArray {
        val layers = (0 until OCTAVES).map { Lattice(3 shl it, 2 shl it, Random(seed * 31 + it)) }
        val out = FloatArray(width * height)
        val row = DoubleArray(width)
        for (y in 0 until height) {
            val v = if (height > 1) y.toDouble() / (height - 1) else 0.5
            val window = smooth(0.0, 0.35, v) * (1 - smooth(0.65, 1.0, v))
            for (x in 0 until width) {
                val u = x.toDouble() / width
                var sum = 0.0
                var total = 0.0
                var amp = 1.0
                for (layer in layers) {
                    sum += amp * layer.at(u * layer.cols, v * layer.rows)
                    total += amp
                    amp *= PERSISTENCE
                }
                row[x] = sum / total
            }
            val sorted = row.sorted()
            val from = sorted[(CLUMP_FROM * (width - 1)).toInt()]
            val to = sorted[(CLUMP_TO * (width - 1)).toInt()]
            for (x in 0 until width) out[y * width + x] = (smooth(from, to, row[x]) * window).toFloat()
        }
        return out
    }

    /** Случайные значения в узлах сетки [cols] × ([rows] + 1); по горизонтали сетка замкнута, поэтому узор повторяется без шва. */
    private class Lattice(val cols: Int, val rows: Int, random: Random) {
        private val values = DoubleArray(cols * (rows + 1)) { random.nextDouble() }

        fun at(x: Double, y: Double): Double {
            val fx = floor(x)
            val fy = floor(y)
            val x0 = Math.floorMod(fx.toInt(), cols)
            val x1 = (x0 + 1) % cols
            val y0 = fy.toInt().coerceIn(0, rows)
            val y1 = (y0 + 1).coerceAtMost(rows)
            val tx = smooth(0.0, 1.0, x - fx)
            val ty = smooth(0.0, 1.0, y - fy)
            val top = lerp(values[y0 * cols + x0], values[y0 * cols + x1], tx)
            val bottom = lerp(values[y1 * cols + x0], values[y1 * cols + x1], tx)
            return lerp(top, bottom, ty)
        }
    }
}
