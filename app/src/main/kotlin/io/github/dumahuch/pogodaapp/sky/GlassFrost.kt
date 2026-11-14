package io.github.dumahuch.pogodaapp.sky

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Иней на стекле карточек (решение Димы 13.11.2026: «при минусовых температурах от минус трёх всё стекло замерзало»). При −3 °C
 * иней только у краёв, к −10 °C стекло замёрзло целиком. Рисует [io.github.dumahuch.pogodaapp.ui.components.glassFrost].
 */
internal object GlassFrost {
    const val START_C = -3.0
    const val FULL_C = -10.0

    /** Доля стекла под инеем при −3 °C: только у краёв. */
    private const val RIM = 0.25

    /** Насколько стекло затянуто инеем при температуре воздуха [temperatureC] (0 тепло, 1 замёрзло целиком). */
    fun coverage(temperatureC: Double?): Double {
        if (temperatureC == null || temperatureC > START_C) return 0.0
        return RIM + (1 - RIM) * smooth(START_C, FULL_C, temperatureC)
    }

    /** Отогретый пальцем след держится столько секунд и снова замерзает за столько (около минуты в сумме). */
    const val MELT_HOLD_SECONDS = 8f
    const val REFREEZE_SECONDS = 50f

    /** Сетка следа пальца: узор инея теперь картинка, сетка нужна только для следа, мягкого при растяжке. */
    const val CELL_DP = 2.0
    const val MAX_CELLS = 40_000.0
}

/** Серая картинка инея (белый иней на чёрном) стороной [size] пикселей, яркость 0…255 построчно. */
internal class FrostTexture(val size: Int, private val gray: ByteArray) {
    init {
        require(gray.size == size * size)
    }

    /** Яркость 0…1 в точке ([x], [y]) пикселей со сглаживанием; за краем берётся крайний пиксель. */
    fun at(x: Double, y: Double): Float {
        val gx = (x - 0.5).coerceIn(0.0, size - 1.0)
        val gy = (y - 0.5).coerceIn(0.0, size - 1.0)
        val x0 = min(gx.toInt(), size - 2).coerceAtLeast(0)
        val y0 = min(gy.toInt(), size - 2).coerceAtLeast(0)
        val fx = (gx - x0).toFloat()
        val fy = (gy - y0).toFloat()
        val x1 = min(x0 + 1, size - 1)
        val y1 = min(y0 + 1, size - 1)
        val top = value(x0, y0) * (1 - fx) + value(x1, y0) * fx
        val bottom = value(x0, y1) * (1 - fx) + value(x1, y1) * fx
        return (top * (1 - fy) + bottom * fy) / 255f
    }

    private fun value(x: Int, y: Int): Float = (gray[y * size + x].toInt() and 0xFF).toFloat()
}

/**
 * Готовый иней для стекла [width] × [height] пикселей: [ice] густота инея, [order] когда место замерзает (доля стекла под инеем,
 * при которой оно уже замёрзло), [haze] мягкая дымка вокруг инея. Всё 0…255.
 */
internal class FrostSheet(val width: Int, val height: Int) {
    val ice = ByteArray(width * height)
    val order = ByteArray(width * height)
    val haze = ByteArray(width * height)
}

/**
 * Иней из готовых картинок (Дима 14.11.2026: нарисованные кодом перья «похожи на плесень под микроскопом», картинки он
 * сгенерировал сам и сказал «их надо все комбинировать»). Стекло делится на участки, как настоящее окно, где соседние места
 * замерзают каждое своим узором: у каждого участка своя картинка, поворот, зеркало и масштаб. Участки заходят друг на друга
 * широкой полосой (прямой стык читался трещиной). На маленьком стекле только мелкие узоры.
 *
 * Иней растёт от нескольких точек на кромке и снизу (там стекло холоднее), фронт неровный и идёт по перьям: светлое место
 * замерзает раньше тёмного на том же расстоянии. Не кольцом по форме кнопки (Дима 14.11: «идеальным кругом, неестественно»).
 */
internal object FrostComposer {
    /** Стекло уже этого (dp) считается маленьким: кнопки, строка города, плашки, нижнее меню. */
    private const val SMALL_DP = 86.0

    /** Сторона участка (dp) и ширина полосы, где соседние участки заходят друг на друга. */
    private const val PART_DP = 120.0
    private const val SMALL_PART_DP = 40.0
    private const val BAND_DP = 26.0
    private const val SMALL_BAND_DP = 13.0

    /** Сколько dp на ширину картинки: крупные перья на карточках, мелкие на маленьком стекле. */
    private const val SPAN_DP = 360.0
    private const val SPAN_MORE_DP = 125.0
    private const val SMALL_SPAN_DP = 165.0
    private const val SMALL_SPAN_MORE_DP = 50.0

    /** Иней растёт в основном вверх: поворот картинки не дальше ±[TURN] радиан, изредка вверх ногами. */
    private const val TURN = 1.2
    private const val UPSIDE_DOWN = 0.15

    /** Яркость картинки в густоту: чёрный фон с лёгким шумом отсекается. */
    private const val BLACK = 0.07f
    private const val WHITE = 0.9f

    /** Точка начала роста на столько dp кромки (и одна снизу), неровность фронта по шуму и по перьям. */
    private const val SEED_EVERY_DP = 257.0
    private const val FRONT_NOISE = 0.06
    private const val FRONT_FEATHERS = 0.25
    private const val NOISE_DP = 15.0

    /** Отступ окна от края картинки (пикселей картинки). */
    private const val MARGIN = 4.0

    /** Дымка вокруг инея: размытие и усиление. */
    private const val HAZE_DP = 4.0
    private const val HAZE_GAIN = 1.6f

    fun isSmall(widthDp: Double, heightDp: Double): Boolean = min(widthDp, heightDp) < SMALL_DP

    /**
     * Иней для стекла [width] × [height] пикселей при [pxPerDp] пикселей на dp. [large] крупные узоры, [dense] мелкие; одно
     * и то же [seed] даёт тот же иней.
     */
    fun compose(width: Int, height: Int, pxPerDp: Double, large: List<FrostTexture>, dense: List<FrostTexture>, seed: Long): FrostSheet {
        val sheet = FrostSheet(width, height)
        val random = Random(seed)
        val small = isSmall(width / pxPerDp, height / pxPerDp)
        val pool = if (small || large.isEmpty()) dense.ifEmpty { large } else large + dense.take(3)
        val parts = parts(width, height, pxPerDp, small, pool, random)
        val band = (if (small) SMALL_BAND_DP else BAND_DP) * pxPerDp
        val d = DoubleArray(parts.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var nearest = Double.MAX_VALUE
                for (k in parts.indices) {
                    d[k] = hypot(x - parts[k].x, y - parts[k].y)
                    if (d[k] < nearest) nearest = d[k]
                }
                var a = 0f
                for (k in parts.indices) {
                    val gap = d[k] - nearest
                    if (gap >= band) continue
                    val weight = (1 - gap / band).pow(1.5).toFloat()
                    a = max(a, parts[k].sample(x + 0.5 - width / 2.0, y + 0.5 - height / 2.0) * weight)
                }
                sheet.ice[y * width + x] = byte(((a - BLACK) / (WHITE - BLACK)).coerceIn(0f, 1f))
            }
        }
        growth(sheet, pxPerDp, random)
        haze(sheet, (HAZE_DP * pxPerDp).roundToInt().coerceAtLeast(1))
        return sheet
    }

    /** Участок: середина ([x], [y]) на стекле и как картинка ложится на стекло. */
    private class Part(val x: Double, val y: Double, val texture: FrostTexture, private val centre: Double,
                       private val ox: Double, private val oy: Double, private val c: Double, private val s: Double, private val flip: Double) {
        /** Густота картинки в точке стекла ([dx], [dy]) пикселей от его середины. */
        fun sample(dx: Double, dy: Double): Float {
            val fx = dx * flip
            return texture.at(centre + ox + c * fx - s * dy, centre + oy + s * fx + c * dy)
        }
    }

    private fun parts(width: Int, height: Int, pxPerDp: Double, small: Boolean, pool: List<FrostTexture>, random: Random): List<Part> {
        val widthDp = width / pxPerDp
        val heightDp = height / pxPerDp
        val partDp = if (small) SMALL_PART_DP else PART_DP
        val count = max(1, (widthDp * heightDp / (partDp * partDp) * 1.3).toInt()) + if (small) 0 else 2
        return List(count) {
            val texture = pool[random.nextInt(pool.size)]
            val spanDp = if (small) SMALL_SPAN_DP + SMALL_SPAN_MORE_DP * random.nextDouble() else SPAN_DP + SPAN_MORE_DP * random.nextDouble()
            val angle = (random.nextDouble() * 2 - 1) * TURN + if (random.nextDouble() < UPSIDE_DOWN) PI else 0.0
            val flip = if (random.nextBoolean()) -1.0 else 1.0
            // Пикселей картинки на пиксель стекла; окно стекла целиком внутри картинки, не ближе [MARGIN] к краю: края картинок
            // не стыкуются (при повторе видна прямая отсечка).
            var k = texture.size / (spanDp * pxPerDp)
            val ac = abs(cos(angle))
            val asn = abs(sin(angle))
            val room = texture.size - 2.0 * MARGIN
            while ((ac * width + asn * height) * k > room || (asn * width + ac * height) * k > room) k /= 1.08
            val ew = (ac * width + asn * height) * k
            val eh = (asn * width + ac * height) * k
            val ox = (random.nextDouble() - 0.5) * (room - ew)
            val oy = (random.nextDouble() - 0.5) * (room - eh)
            Part(random.nextDouble() * width, random.nextDouble() * height, texture, texture.size / 2.0, ox, oy, cos(angle) * k, sin(angle) * k, flip)
        }
    }

    /** Порядок замерзания: расстояние до ближайшей точки роста с неровностью, перевёрнутое в долю стекла (ровно [order] / 255). */
    private fun growth(sheet: FrostSheet, pxPerDp: Double, random: Random) {
        val w = sheet.width
        val h = sheet.height
        val perimeter = 2.0 * (w + h)
        val seeds = ArrayList<DoubleArray>()
        repeat(max(2, (perimeter / pxPerDp / SEED_EVERY_DP).toInt())) {
            val u = random.nextDouble() * perimeter
            val point = when {
                u < w -> doubleArrayOf(u, h.toDouble())
                u < w + h -> doubleArrayOf(w.toDouble(), h - (u - w))
                u < 2.0 * w + h -> doubleArrayOf(w - (u - w - h), 0.0)
                else -> doubleArrayOf(0.0, u - 2.0 * w - h)
            }
            seeds += point + (0.7 + 0.6 * random.nextDouble())
        }
        seeds += doubleArrayOf(random.nextDouble() * w, h.toDouble(), 0.7 + 0.6 * random.nextDouble())
        val longest = max(w, h).toDouble()
        val noiseSeed = random.nextInt()
        val f = FloatArray(w * h)
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (y in 0 until h) {
            for (x in 0 until w) {
                var near = Double.MAX_VALUE
                for (s in seeds) near = min(near, hypot(x - s[0], y - s[1]) * s[2])
                val i = y * w + x
                val ice = (sheet.ice[i].toInt() and 0xFF) / 255.0
                val value = (near / longest + FRONT_NOISE * noise(x / pxPerDp, y / pxPerDp, noiseSeed) - FRONT_FEATHERS * (ice - 0.5)).toFloat()
                f[i] = value
                low = min(low, value)
                high = max(high, value)
            }
        }
        // Доля стекла, замёрзшая раньше: по ней порог, чтобы «доля инея 0,4» и правда закрывала 40 % стекла.
        val bins = 2048
        val counts = IntArray(bins)
        val span = (high - low).coerceAtLeast(1e-6f)
        fun bin(v: Float) = ((v - low) / span * (bins - 1)).toInt().coerceIn(0, bins - 1)
        for (v in f) counts[bin(v)]++
        val below = FloatArray(bins)
        var sum = 0
        for (b in 0 until bins) {
            below[b] = (sum + counts[b] / 2f) / f.size
            sum += counts[b]
        }
        for (i in f.indices) sheet.order[i] = byte(below[bin(f[i])])
    }

    /** Плавный шум примерно с разбросом 1 на клетках [NOISE_DP] dp (две октавы). */
    private fun noise(xDp: Double, yDp: Double, seed: Int): Double =
        (valueNoise(xDp / NOISE_DP, yDp / NOISE_DP, seed) * 0.67 + valueNoise(xDp / NOISE_DP * 2, yDp / NOISE_DP * 2, seed + 1) * 0.33 - 0.5) * 4.0

    private fun valueNoise(x: Double, y: Double, seed: Int): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = smooth(0.0, 1.0, x - x0)
        val ty = smooth(0.0, 1.0, y - y0)
        val top = lerp(hash(x0, y0, seed), hash(x0 + 1, y0, seed), tx)
        val bottom = lerp(hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed), tx)
        return lerp(top, bottom, ty)
    }

    private fun hash(x: Int, y: Int, seed: Int): Double {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535.0
    }

    /** Дымка: иней, размытый двумя проходами скользящего среднего радиуса [radius] по строкам и столбцам. */
    private fun haze(sheet: FrostSheet, radius: Int) {
        val w = sheet.width
        val h = sheet.height
        val a = FloatArray(w * h) { (sheet.ice[it].toInt() and 0xFF) / 255f }
        val b = FloatArray(w * h)
        repeat(2) {
            boxBlur(a, b, w, h, radius, horizontal = true)
            boxBlur(b, a, w, h, radius, horizontal = false)
        }
        for (i in a.indices) sheet.haze[i] = byte((a[i] * HAZE_GAIN).coerceIn(0f, 1f))
    }

    private fun boxBlur(from: FloatArray, to: FloatArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            fun at(p: Int): Float {
                val q = p.coerceIn(0, length - 1)
                return if (horizontal) from[line * w + q] else from[q * w + line]
            }
            var sum = 0f
            for (p in -radius..radius) sum += at(p)
            for (p in 0 until length) {
                if (horizontal) to[line * w + p] = sum / window else to[p * w + line] = sum / window
                sum += at(p + radius + 1) - at(p - radius)
            }
        }
    }

    private fun byte(v: Float): Byte = (v * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

    /** Больше стольких пикселей иней собирается мельче и растягивается: сборка остаётся в доле секунды. */
    const val MAX_PIXELS = 1_200_000
}
