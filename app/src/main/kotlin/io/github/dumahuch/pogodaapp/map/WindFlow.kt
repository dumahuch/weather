package io.github.dumahuch.pogodaapp.map

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ветер одного часа в точках [PointGrid]: скорости на восток ([east]) и на север ([north]) в км/ч, построчно с юга на
 * север; NaN там, где данных нет. Между точками скорость плавная (по четырём соседним точкам).
 */
class WindFlow(private val east: FloatArray, private val north: FloatArray) {
    init {
        require(east.size == PointGrid.COUNT && north.size == PointGrid.COUNT) { "ветер: не тот размер поля" }
    }

    /**
     * Скорость в точке [lon], [lat] (км/ч): в [out] пишутся составляющие на восток и на север. Вне сетки берётся её край.
     * false, если рядом нет данных.
     */
    fun sample(lon: Double, lat: Double, out: FloatArray): Boolean {
        val gx = ((lon - PointGrid.WEST) / PointGrid.LON_STEP).coerceIn(0.0, PointGrid.COLUMNS - 1.0)
        val gy = ((lat - PointGrid.SOUTH) / PointGrid.LAT_STEP).coerceIn(0.0, PointGrid.ROWS - 1.0)
        val x0 = min(gx.toInt(), PointGrid.COLUMNS - 2)
        val y0 = min(gy.toInt(), PointGrid.ROWS - 2)
        val fx = (gx - x0).toFloat()
        val fy = (gy - y0).toFloat()
        val i00 = y0 * PointGrid.COLUMNS + x0
        val i10 = i00 + 1
        val i01 = i00 + PointGrid.COLUMNS
        val i11 = i01 + 1
        val u = blend(east[i00], east[i10], east[i01], east[i11], fx, fy)
        val v = blend(north[i00], north[i10], north[i01], north[i11], fx, fy)
        if (u.isNaN() || v.isNaN()) return false
        out[0] = u
        out[1] = v
        return true
    }

    private fun blend(a: Float, b: Float, c: Float, d: Float, fx: Float, fy: Float): Float {
        val bottom = a + (b - a) * fx
        val top = c + (d - c) * fx
        return bottom + (top - bottom) * fy
    }
}

/**
 * Видимая часть карты: экран [width] × [height] пикселей и его края в градусах. Карта не поворачивается и не наклоняется,
 * поэтому по горизонтали долгота растёт ровно, а по вертикали как в проекции Меркатора.
 */
class ScreenView(
    val width: Float,
    val height: Float,
    private val west: Double,
    private val east: Double,
    north: Double,
    south: Double,
) {
    private val top = Mercator.y(north)
    private val bottom = Mercator.y(south)

    fun lon(x: Float): Double = west + (east - west) * x / width

    fun lat(y: Float): Double = Mercator.lat(top + (bottom - top) * y / height)
}

/**
 * Линии, бегущие по ветру (слой «Ветер», 0.8.41): частицы появляются в случайном месте экрана, несутся по ветру и через
 * несколько секунд тают; за каждой тянется след из последних [TRAIL] положений, чем старше, тем прозрачнее. При
 * сильном ветре частица быстрее, ярче и толще. Положения в пикселях экрана: при сдвиге карты частицы начинаются заново.
 *
 * Шаг движения постоянный ([TICK_S]), сколько бы кадров в секунду ни рисовал экран; рисование ([collect]) группирует
 * отрезки следов по прозрачности и толщине, чтобы экран рисовал их немногими вызовами.
 */
class WindParticles(val count: Int, private val dp: Float, private val random: Random = Random.Default) {
    private val history = FloatArray(count * TRAIL * 2)
    private val start = IntArray(count)
    private val length = IntArray(count)
    private val age = FloatArray(count)
    private val life = FloatArray(count)
    private val speed = FloatArray(count)
    private var pending = 0f
    private val wind = FloatArray(2)

    private var placedFor: ScreenView? = null

    /** Продвигает частицы на [seconds] по полю [flow] на экране [view]; первый вызов с новым экраном расставляет их. */
    fun advance(seconds: Float, flow: WindFlow, view: ScreenView) {
        if (placedFor !== view) {
            placedFor = view
            for (i in 0 until count) {
                place(i, view)
                age[i] = random.nextFloat() * life[i]
            }
            pending = 0f
        }
        pending = min(pending + seconds, MAX_CATCH_UP_S)
        while (pending >= TICK_S) {
            pending -= TICK_S
            tick(flow, view)
        }
    }

    private fun tick(flow: WindFlow, view: ScreenView) {
        val step = TICK_S * SPEED_DP_PER_KMH * dp
        for (i in 0 until count) {
            age[i] += TICK_S
            val last = slot(i, length[i] - 1)
            val x = history[last]
            val y = history[last + 1]
            val known = flow.sample(view.lon(x), view.lat(y), wind)
            val kmh = if (known) sqrt(wind[0] * wind[0] + wind[1] * wind[1]) else 0f
            if (!known || kmh < CALM_KMH || age[i] >= life[i]) {
                place(i, view)
                continue
            }
            val nx = x + wind[0] * step
            val ny = y - wind[1] * step
            if (nx < 0 || nx > view.width || ny < 0 || ny > view.height) {
                place(i, view)
                continue
            }
            speed[i] = kmh
            push(i, nx, ny)
        }
    }

    /** Новая частица в случайном месте [view], без следа. */
    private fun place(i: Int, view: ScreenView) {
        start[i] = 0
        length[i] = 1
        history[i * TRAIL * 2] = random.nextFloat() * view.width
        history[i * TRAIL * 2 + 1] = random.nextFloat() * view.height
        age[i] = 0f
        life[i] = MIN_LIFE_S + random.nextFloat() * LIFE_SPREAD_S
        speed[i] = 0f
    }

    private fun push(i: Int, x: Float, y: Float) {
        if (length[i] < TRAIL) {
            length[i]++
        } else {
            start[i] = (start[i] + 1) % TRAIL
        }
        val at = slot(i, length[i] - 1)
        history[at] = x
        history[at + 1] = y
    }

    /** Индекс в [history] положения номер [k] частицы [i] (0 самое старое). */
    private fun slot(i: Int, k: Int): Int = (i * TRAIL + (start[i] + k) % TRAIL) * 2

    /** Где частица сейчас (для проверок). */
    internal fun position(i: Int): Pair<Float, Float> {
        val at = slot(i, length[i] - 1)
        return history[at] to history[at + 1]
    }

    /** Сколько положений в следе частицы [i] (вместе с нынешним). */
    internal fun trailLength(i: Int): Int = length[i]

    /** Раскладывает отрезки следов по корзинам [out] (прозрачность × толщина); корзины сначала очищаются. */
    fun collect(out: WindSegments) {
        out.clear()
        for (i in 0 until count) {
            val n = trailLength(i)
            if (n < 2) continue
            val base = min(min(1f, age[i] / FADE_IN_S), (life[i] - age[i]) / FADE_OUT_S).coerceAtLeast(0f) *
                (BASE_ALPHA + EXTRA_ALPHA * min(1f, speed[i] / BRIGHT_KMH))
            if (base <= 0f) continue
            val width = when {
                speed[i] < THIN_KMH -> 0
                speed[i] < THICK_KMH -> 1
                else -> 2
            }
            for (k in n - 1 downTo 1) {
                val alpha = base * TRAIL_FADE.pow(n - 1 - k)
                if (alpha < MIN_ALPHA) break
                val level = (alpha * ALPHA_LEVELS).toInt().coerceAtMost(ALPHA_LEVELS - 1)
                val a = slot(i, k - 1)
                val b = slot(i, k)
                out.add(level, width, history[a], history[a + 1], history[b], history[b + 1])
            }
        }
    }

    companion object {
        const val TRAIL = 32
        const val TICK_S = 1f / 30
        private const val MAX_CATCH_UP_S = 0.2f

        /** Скорость на экране: dp в секунду на 1 км/ч (при 15 км/ч полоска около 25 dp, при 50 км/ч около 85 dp; 0.8.42). */
        const val SPEED_DP_PER_KMH = 1.7f

        private const val CALM_KMH = 2f
        private const val MIN_LIFE_S = 3.0f
        private const val LIFE_SPREAD_S = 3.0f
        private const val FADE_IN_S = 0.5f
        private const val FADE_OUT_S = 0.8f
        private const val BASE_ALPHA = 0.4f
        private const val EXTRA_ALPHA = 0.3f
        private const val BRIGHT_KMH = 50f
        private const val TRAIL_FADE = 0.93f
        private const val MIN_ALPHA = 0.03f
        private const val THIN_KMH = 20f
        private const val THICK_KMH = 45f

        const val ALPHA_LEVELS = 8

        /** Толщина линии в dp для корзины толщины 0…2. */
        val WidthsDp: FloatArray = floatArrayOf(1.0f, 1.3f, 1.6f)

        /** Около одной частицы на [AREA_PER_PARTICLE_DP2] dp² экрана (0.8.42: реже, но следы длиннее). */
        private const val AREA_PER_PARTICLE_DP2 = 420f
        private const val MIN_COUNT = 200
        private const val MAX_COUNT = 1000

        /** Сколько частиц на экран [widthDp] × [heightDp]. */
        fun countFor(widthDp: Float, heightDp: Float): Int =
            (widthDp * heightDp / AREA_PER_PARTICLE_DP2).toInt().coerceIn(MIN_COUNT, MAX_COUNT)

        /** Непрозрачность корзины [level]: середина её промежутка. */
        fun alphaOf(level: Int): Float = (level + 0.5f) / ALPHA_LEVELS
    }
}

/** Отрезки следов, разложенные по корзинам «прозрачность × толщина»: по четыре числа (x1, y1, x2, y2) на отрезок. */
class WindSegments {
    private val buckets = Array(WindParticles.ALPHA_LEVELS * WindParticles.WidthsDp.size) { FloatArray(256) }
    private val sizes = IntArray(buckets.size)

    val bucketCount: Int get() = buckets.size

    fun clear() = sizes.fill(0)

    fun add(level: Int, width: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        val b = width * WindParticles.ALPHA_LEVELS + level
        var data = buckets[b]
        val n = sizes[b]
        if (n + 4 > data.size) {
            data = data.copyOf(data.size * 2)
            buckets[b] = data
        }
        data[n] = x1
        data[n + 1] = y1
        data[n + 2] = x2
        data[n + 3] = y2
        sizes[b] = n + 4
    }

    fun data(bucket: Int): FloatArray = buckets[bucket]

    /** Сколько чисел в корзине (вчетверо больше, чем отрезков). */
    fun size(bucket: Int): Int = sizes[bucket]

    fun level(bucket: Int): Int = bucket % WindParticles.ALPHA_LEVELS

    fun width(bucket: Int): Int = bucket / WindParticles.ALPHA_LEVELS

    fun total(): Int = sizes.sum() / 4
}
