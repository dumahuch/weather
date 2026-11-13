package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Вид осадков в слое дождя и снега. */
internal enum class PrecipKind { Rain, Snow }

/**
 * Что рисует слой осадков: вид, сила (0…1 по мм в час) и ветер. Ветер даёт наклон дождя и снос снега;
 * [lean] знак стороны: +1 ветер несёт вправо (с запада на восток), −1 влево.
 */
internal data class PrecipSpec(
    val kind: PrecipKind,
    val strength: Double,
    val windKmh: Double,
    val lean: Double,
) {
    /** Горизонтальное смещение дождя на единицу падения со знаком ([lean]); у штиля почти нуль. */
    val rainTilt: Double get() = lean * (windKmh / KMH_PER_MS * RAIN_WIND_SHARE / RAIN_FALL_MS).coerceIn(0.0, MAX_TILT)

    /** Скорость сноса снежинок, dp в секунду, со знаком ([lean]). */
    val snowDrift: Double get() = lean * windKmh / KMH_PER_MS * SNOW_DRIFT_DP_PER_MS

    companion object {
        private const val KMH_PER_MS = 3.6
        private const val RAIN_WIND_SHARE = 0.35
        private const val RAIN_FALL_MS = 6.5
        private const val MAX_TILT = 0.5
        private const val SNOW_DRIFT_DP_PER_MS = 7.0

        /** Сила дождя при грозе не ниже этой: гроза без ливня выглядит неубедительно. */
        private const val STORM_MIN_STRENGTH = 0.75

        /** Сила, если осадки текущего часа неизвестны (экран проверки). */
        private const val UNKNOWN_STRENGTH = 0.5

        /** Направление, если оно неизвестно: чуть вправо, как у большинства дождей в западных ветрах. */
        private const val DEFAULT_LEAN = 1.0

        /** Условия слоя осадков для вида погоды [condition] или `null`, если осадков нет. [windFromDegrees] откуда дует (метеорологическое направление). */
        fun of(condition: Condition, precipitationMm: Double?, windKmh: Double, windFromDegrees: Int?): PrecipSpec? {
            val kind = when (condition) {
                Condition.Rain, Condition.Storm -> PrecipKind.Rain
                Condition.Snow -> PrecipKind.Snow
                else -> return null
            }
            val measured = precipitationMm?.let {
                smooth(SkyWeather.MIN_PRECIPITATION_MM, SkyWeather.HEAVY_PRECIPITATION_MM, it)
            } ?: UNKNOWN_STRENGTH
            val strength = if (condition == Condition.Storm) max(measured, STORM_MIN_STRENGTH) else measured
            // Ветер дует «с» направления d, значит несёт на восток при d = 270° (с запада): восток это вправо.
            val lean = windFromDegrees?.let { -sin(Math.toRadians(it.toDouble())) } ?: DEFAULT_LEAN
            return PrecipSpec(kind, strength, windKmh, lean)
        }
    }
}

/** Порывы ветра: общий множитель наклона и смещение, которое он накопил за [t] секунд. */
internal object Gusts {
    private const val RAIN_SLOW = 0.55
    private const val RAIN_FAST = 1.7
    private const val RAIN_SLOW_SHARE = 0.35
    private const val RAIN_FAST_SHARE = 0.15
    private const val SNOW_RATE = 0.35
    private const val SNOW_SHARE = 0.8
    private const val RAIN_PHASE = 1.0
    private const val SNOW_PHASE = 0.4

    /** Множитель наклона дождя в момент [t] (около 1, порывами от 0,5 до 1,5). */
    fun rainFactor(t: Double): Double =
        1.0 + RAIN_SLOW_SHARE * sin(RAIN_SLOW * t + RAIN_PHASE) + RAIN_FAST_SHARE * sin(RAIN_FAST * t)

    /** Накопленный к [t] сдвиг: множитель наклона, проинтегрированный по времени (то есть на единицу падения вниз со скоростью 1). */
    fun rainShift(t: Double): Double =
        t - RAIN_SLOW_SHARE / RAIN_SLOW * (cos(RAIN_SLOW * t + RAIN_PHASE) - cos(RAIN_PHASE)) -
            RAIN_FAST_SHARE / RAIN_FAST * (cos(RAIN_FAST * t) - 1.0)

    /** Накопленный к [t] сдвиг снега при постоянной скорости 1 и порывах. */
    fun snowShift(t: Double): Double =
        t + SNOW_SHARE / SNOW_RATE * (cos(SNOW_PHASE) - cos(SNOW_RATE * t + SNOW_PHASE))
}

/** Одна капля: слой глубины [depth] (0,2 далеко … 1 близко), скорость dp/с, длина штриха dp, ширина dp. */
internal class RainDrop(
    val depth: Double,
    val x0: Double,
    val y0: Double,
    val speed: Double,
    val length: Double,
    val width: Double,
    val alpha: Double,
)

/** Положение головы капли или снежинки, переиспользуется кадр за кадром. */
internal class Pose {
    var x = 0.0
    var y = 0.0
}

/** Дождь для поля [widthDp] × [heightDp]: четыре слоя глубины, у каждого своя скорость, длина штриха, толщина и прозрачность. */
internal class RainField private constructor(val drops: List<RainDrop>, val widthDp: Double, val heightDp: Double) {

    /** Голова капли [drop] в момент [t] при наклоне [tilt] (dp по горизонтали на dp падения). */
    fun head(drop: RainDrop, t: Double, tilt: Double, out: Pose) {
        val span = heightDp + 2 * drop.length + MARGIN
        out.y = wrap(drop.y0 + drop.speed * t, span) - drop.length - MARGIN / 2
        val shifted = drop.x0 + drop.speed * tilt * Gusts.rainShift(t)
        out.x = wrap(shifted + EDGE, widthDp + 2 * EDGE) - EDGE
    }

    companion object {
        private const val EDGE = 120.0
        private const val MARGIN = 80.0
        private const val Y_PERIOD = 2000.0

        /** Кадр образца (390 × 844 dp) и число капель по слоям при полной силе. */
        private const val REF_AREA = 390.0 * 844.0
        private val LAYERS = listOf(0.2 to 170, 0.4 to 120, 0.65 to 75, 1.0 to 30)
        private const val MIN_SHARE = 0.3
        private const val STRENGTH_SHARE = 1.1
        const val MAX_DROPS = 700

        fun create(strength: Double, widthDp: Double, heightDp: Double, seed: Long = 11): RainField {
            val random = Random(seed)
            val share = (MIN_SHARE + STRENGTH_SHARE * strength.coerceIn(0.0, 1.0)) * widthDp * heightDp / REF_AREA
            val out = ArrayList<RainDrop>()
            val counts = layerCounts(LAYERS.map { it.second }, share, MAX_DROPS)
            for ((index, layer) in LAYERS.withIndex()) {
                val depth = layer.first
                repeat(counts[index]) {
                    val speed = mix(350.0, 1100.0, depth) * (0.88 + 0.24 * random.nextDouble())
                    out += RainDrop(
                        depth = depth,
                        x0 = random.nextDouble() * (widthDp + 2 * EDGE) - EDGE,
                        y0 = random.nextDouble() * Y_PERIOD,
                        speed = speed,
                        length = speed / EXPOSURE * (0.8 + 0.4 * random.nextDouble()),
                        width = mix(0.7, 2.3, depth) * (0.85 + 0.3 * random.nextDouble()),
                        alpha = mix(0.10, 0.26, depth) * (0.7 + 0.5 * random.nextDouble()),
                    )
                }
            }
            return RainField(out, widthDp, heightDp)
        }

        /** Длина штриха равна пути за выдержку (1/19 с): быстрые ближние капли тянутся длиннее. Скорости уменьшены вдвое по просьбе Димы 13.11, выдержка удлинена вдвое, штрихи прежней длины. */
        private const val EXPOSURE = 19.0
    }
}

/** Одна снежинка: слой глубины, радиус dp, скорость падения dp/с, размах и частота качания, фаза, крестик у крупных. */
internal class Flake(
    val depth: Double,
    val radius: Double,
    val x0: Double,
    val y0: Double,
    val fall: Double,
    val sway: Double,
    val swayHz: Double,
    val phase: Double,
    val crystal: Boolean,
    val spin: Double,
)

/** Снег для поля [widthDp] × [heightDp]: пять слоёв глубины, дальние мелкие и медленные, ближние крупные и размытые. */
internal class SnowField private constructor(val flakes: List<Flake>, val widthDp: Double, val heightDp: Double) {

    /** Центр снежинки [flake] в момент [t] при сносе [drift] (dp/с). */
    fun centre(flake: Flake, t: Double, drift: Double, out: Pose) {
        val span = heightDp + 6 * flake.radius + MARGIN
        out.y = wrap(flake.y0 + flake.fall * t, span) - 3 * flake.radius - MARGIN / 2
        val w = 2 * PI * flake.swayHz * t
        val swing = flake.sway * sin(w + flake.phase) + SECOND_SHARE * flake.sway * sin(SECOND_RATE * w + SECOND_PHASE * flake.phase)
        val shifted = flake.x0 + swing + drift * (NEAR_SHARE + flake.depth) * Gusts.snowShift(t)
        out.x = wrap(shifted + EDGE, widthDp + 2 * EDGE) - EDGE
    }

    companion object {
        private const val EDGE = 100.0
        private const val MARGIN = 60.0
        private const val Y_PERIOD = 3000.0
        private const val SECOND_SHARE = 0.35
        private const val SECOND_RATE = 2.3
        private const val SECOND_PHASE = 1.7
        private const val NEAR_SHARE = 0.5
        private const val REF_AREA = 390.0 * 844.0
        private val LAYERS = listOf(0.15 to 190, 0.3 to 140, 0.5 to 90, 0.75 to 42, 1.0 to 14)
        private const val MIN_SHARE = 0.25
        private const val STRENGTH_SHARE = 1.05
        const val MAX_FLAKES = 700

        fun create(strength: Double, widthDp: Double, heightDp: Double, seed: Long = 21): SnowField {
            val random = Random(seed)
            val share = (MIN_SHARE + STRENGTH_SHARE * strength.coerceIn(0.0, 1.0)) * widthDp * heightDp / REF_AREA
            val out = ArrayList<Flake>()
            val counts = layerCounts(LAYERS.map { it.second }, share, MAX_FLAKES)
            for ((index, layer) in LAYERS.withIndex()) {
                val depth = layer.first
                repeat(counts[index]) {
                    val radius = mix(1.15, 8.5, Math.pow(depth, 1.3)) * (0.8 + 0.4 * random.nextDouble())
                    out += Flake(
                        depth = depth,
                        radius = radius,
                        x0 = random.nextDouble() * (widthDp + 2 * EDGE) - EDGE,
                        y0 = random.nextDouble() * Y_PERIOD,
                        fall = mix(14.0, 90.0, depth) * (0.8 + 0.4 * random.nextDouble()),
                        sway = mix(6.0, 30.0, depth) * (0.6 + 0.8 * random.nextDouble()) / (1 + radius * 0.08),
                        swayHz = (0.12 + 0.35 * random.nextDouble()) * (1.4 - 0.5 * depth),
                        phase = random.nextDouble() * 2 * PI,
                        crystal = radius > CRYSTAL_RADIUS && random.nextDouble() < CRYSTAL_SHARE,
                        spin = (random.nextDouble() - 0.5) * 0.8,
                    )
                }
            }
            return SnowField(out, widthDp, heightDp)
        }

        /** Лучи рисуются только у самых крупных снежинок. */
        private const val CRYSTAL_RADIUS = 5.0
        private const val CRYSTAL_SHARE = 0.45
    }
}

/**
 * Сколько частиц в каждом слое: базовое число слоя на долю [share], но всего не больше [cap]. Если не помещается,
 * сжимаются все слои поровну, чтобы не терять ближние (они идут последними и самые заметные); в каждом хотя бы одна.
 */
private fun layerCounts(bases: List<Int>, share: Double, cap: Int): List<Int> {
    val raw = bases.map { it * share }
    val scale = min(1.0, cap / raw.sum())
    return raw.map { (it * scale).toInt().coerceAtLeast(1) }
}

private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun wrap(value: Double, period: Double): Double {
    val r = value % period
    return if (r < 0) r + period else r
}

/** Сила, округлённая до пятой доли: поле частиц пересоздаётся только при смене ступени, а не от каждой десятой миллиметра. */
internal fun strengthStep(strength: Double): Double = (min(1.0, max(0.0, strength)) * STRENGTH_STEPS).let { Math.round(it) } / STRENGTH_STEPS

private const val STRENGTH_STEPS = 5.0
