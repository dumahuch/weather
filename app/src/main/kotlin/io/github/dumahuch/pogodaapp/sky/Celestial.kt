package io.github.dumahuch.pogodaapp.sky

import java.time.Instant
import kotlin.math.hypot
import kotlin.math.sqrt

/** Луна в данный момент: где на небе места, какая фаза, северное или южное полушарие (от него зависит, какая сторона освещена). */
internal class MoonSky(val position: Moon.Position, val phase: Moon.Phase, val southern: Boolean)

/**
 * Солнце или луна на небе (решение Димы 06.11.2026): положение берётся по настоящему положению тела над горизонтом
 * для места и времени. [file] имя картинки без расширения (`sun`, `moon_quarter_waxing`), [hourAngleHours] часовой угол
 * (0 на юге, минус к востоку, плюс к западу), [alpha] прозрачность (луна появляется в сумерках), [lit] доля освещённого диска
 * на картинке (0,25 серп, 0,5 четверть, 0,75 растущая или убывающая, 1 полная), [lightOnRight] освещена ли правая сторона на экране,
 * [mirrored] картинку надо отразить по горизонтали (южное полушарие: картинки сделаны для северного).
 */
internal class SkyBody(
    val file: String,
    val moon: Boolean,
    val elevation: Double,
    val hourAngleHours: Double,
    val alpha: Double,
    val lit: Double,
    val lightOnRight: Boolean,
    val mirrored: Boolean = false,
)

/** Диск на экране: центр, видимый радиус диска и сторона квадрата картинки в пикселях экрана. */
internal class Disc(val body: SkyBody, val cx: Double, val cy: Double, val radius: Double, val imageSize: Double)

internal object Celestial {

    /** Какой кадр: слоистый «Ди» (за грядами) или «Нейтральный» (горизонт у нижнего края). */
    enum class Frame { Di, Neutral }

    /** Солнце ниже этой высоты не рисуется (диск целиком под горизонтом). */
    const val SUN_HIDDEN_BELOW = -11.0

    /** Луна ниже этой высоты не рисуется. */
    const val MOON_HIDDEN_BELOW = -10.0

    /** Луну днём не показываем (решение Димы: ночью и в сумерках): она появляется, когда солнце ниже этой высоты, и полностью видна на -5°. */
    private const val MOON_FADE_START = 1.0
    private const val MOON_FADE_END = -5.0

    /** Положение по горизонтали: 6 часов от юга сдвигают тело на [X_SPREAD] ширины экрана, дальше не больше [X_LIMIT] шагов. */
    private const val X_SPREAD = 0.32
    private const val X_LIMIT = 1.2
    private const val HOURS_PER_STEP = 6.0

    /** Слоистый «Ди»: горизонт на гряде far (середина 782 из 1536, взято 790), 9,5 пикселей холста на градус, выше 62° не поднимается. */
    private const val DI_HORIZON_Y = 790.0
    private const val DI_PIXELS_PER_DEGREE = 9.5
    private const val ELEVATION_CAP = 62.0

    /** «Нейтральный»: горизонт у нижнего края (0,97 высоты), 62° на 0,12 высоты. */
    private const val NEUTRAL_HORIZON = 0.97
    private const val NEUTRAL_TOP = 0.12

    /** Видимый диск: доля ширины экрана; доля картинки, которую занимает диск (кольца солнца, лунный диск). */
    private const val SUN_DIAMETER = 0.20
    private const val SUN_FRACTION = 0.56
    private const val MOON_DIAMETER = 0.17
    private const val MOON_FRACTION = 664.0 / 1254.0

    /** Свечение при расчёте цветов: радиус в долях радиуса диска и его сила. */
    private const val GLOW_RADIUS = 1.35
    private const val GLOW_STRENGTH = 0.3

    private val SUN_COLOR = Rgb(240.0, 155.0, 35.0)
    private val MOON_COLOR = Rgb(226.0, 230.0, 228.0)

    fun moonSky(latitude: Double, longitude: Double, time: Instant) =
        MoonSky(Moon.positionAt(time, latitude, longitude), Moon.phaseAt(time), latitude < 0)

    /** Тела на небе: солнце над горизонтом и (ночью и в сумерках) луна. Список пуст, если видеть нечего. */
    fun bodies(sun: SunPosition.Sun, moon: MoonSky?): List<SkyBody> {
        val result = ArrayList<SkyBody>(2)
        if (sun.elevation > SUN_HIDDEN_BELOW) {
            result += SkyBody("sun", false, sun.elevation, hourAngle(sun.solarHour), 1.0, 1.0, true)
        }
        if (moon != null && moon.position.elevation > MOON_HIDDEN_BELOW) {
            val alpha = clamp01((MOON_FADE_START - sun.elevation) / (MOON_FADE_START - MOON_FADE_END))
            val image = moonImage(moon.phase.name)
            if (alpha > 0 && image != null) {
                // Растущая на картинке освещена справа, убывающая слева; на юге всё наоборот (картинку отражаем).
                val lightOnRight = moon.phase.waxing != moon.southern
                result += SkyBody(image.file, true, moon.position.elevation, moon.position.hourAngleHours, alpha, image.lit, lightOnRight, moon.southern)
            }
        }
        return result
    }

    private class MoonImage(val file: String, val lit: Double)

    private fun moonImage(name: Moon.PhaseName): MoonImage? = when (name) {
        Moon.PhaseName.New -> null
        Moon.PhaseName.WaxingCrescent -> MoonImage("moon_crescent_waxing", 0.25)
        Moon.PhaseName.FirstQuarter -> MoonImage("moon_quarter_waxing", 0.5)
        Moon.PhaseName.WaxingGibbous -> MoonImage("moon_gibbous_waxing", 0.75)
        Moon.PhaseName.Full -> MoonImage("moon_full", 1.0)
        Moon.PhaseName.WaningGibbous -> MoonImage("moon_gibbous_waning", 0.75)
        Moon.PhaseName.LastQuarter -> MoonImage("moon_quarter_waning", 0.5)
        Moon.PhaseName.WaningCrescent -> MoonImage("moon_crescent_waning", 0.25)
    }

    /** Солнечный час (0…24, 12 полдень) в часовой угол (−12…12). */
    private fun hourAngle(solarHour: Double): Double = solarHour - 12.0

    /** Диски на экране [width]×[height] для кадра [frame]. */
    fun discs(bodies: List<SkyBody>, frame: Frame, width: Double, height: Double): List<Disc> {
        if (bodies.isEmpty()) return emptyList()
        val skyScale = DiArt.place(DiArt.Layer.Sky, width, height, 0.5, 0.0).scale
        return bodies.map { body ->
            val elevation = body.elevation.coerceAtMost(ELEVATION_CAP)
            val cx = width * (0.5 + X_SPREAD * (body.hourAngleHours / HOURS_PER_STEP).coerceIn(-X_LIMIT, X_LIMIT))
            val cy = when (frame) {
                Frame.Di -> (DI_HORIZON_Y - elevation * DI_PIXELS_PER_DEGREE) * skyScale
                Frame.Neutral -> height * (NEUTRAL_HORIZON - elevation * (NEUTRAL_HORIZON - NEUTRAL_TOP) / ELEVATION_CAP)
            }
            val diameter = width * if (body.moon) MOON_DIAMETER else SUN_DIAMETER
            val fraction = if (body.moon) MOON_FRACTION else SUN_FRACTION
            Disc(body, cx, cy, diameter / 2, diameter / fraction)
        }
    }

    /**
     * Кладёт диски поверх цвета [rgb] в точке (x, y) экрана для расчёта цветов интерфейса: освещённая часть диска
     * одним цветом и мягкое свечение вокруг. Совпадает по положению и размеру с тем, что рисует экран.
     */
    fun blend(discs: List<Disc>, x: Double, y: Double, rgb: DoubleArray) {
        for (disc in discs) {
            val dx = x - disc.cx
            val dy = y - disc.cy
            val d = hypot(dx, dy)
            val r = disc.radius
            if (d > r * GLOW_RADIUS) continue
            val color = if (disc.body.moon) MOON_COLOR else SUN_COLOR
            val alpha = if (d <= r) {
                if (isLit(disc, dx / r, dy / r)) disc.body.alpha else 0.0
            } else {
                disc.body.alpha * GLOW_STRENGTH * (1 - (d - r) / (r * (GLOW_RADIUS - 1)))
            }
            if (alpha <= 0) continue
            rgb[0] = lerp(rgb[0], color.r, alpha)
            rgb[1] = lerp(rgb[1], color.g, alpha)
            rgb[2] = lerp(rgb[2], color.b, alpha)
        }
    }

    /** Освещена ли точка (u, v) диска радиуса 1: терминатор как эллипс, как на картинках фаз. */
    private fun isLit(disc: Disc, u: Double, v: Double): Boolean {
        val body = disc.body
        if (!body.moon || body.lit >= 1.0) return true
        val side = if (body.lightOnRight) u else -u
        val terminator = (1 - 2 * body.lit) * sqrt((1 - v * v).coerceAtLeast(0.0))
        return side > terminator
    }
}
