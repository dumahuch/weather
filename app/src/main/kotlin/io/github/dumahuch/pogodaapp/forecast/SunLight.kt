package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.sky.SunPosition
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Свет суток для страницы «Солнце и Луна» (шаг В, 21.11.2026): восход и закат, золотой и синий час, три вида сумерек,
 * полдень и высота солнца, сторона восхода и заката. Считается на телефоне по положению солнца ([SunPosition]), сеть
 * не нужна.
 *
 * Границы по высоте центра солнца:
 * - восход и закат −0,833° (рефракция и радиус диска, как у USNO: https://aa.usno.navy.mil/faq/RST_defs);
 * - сумерки гражданские до −6°, навигационные до −12°, астрономические до −18° (там же);
 * - золотой час от −4° до +6°, синий от −6° до −4° (PhotoPills: https://photopills.com/glossary/b; единого определения
 *   нет, у приложений границы расходятся: https://photoephemeris.com/articles/magical-gold-and-blue-confusion-in-the-twilight-zone).
 */
enum class SunEdge(val degrees: Double) {
    Astronomical(-18.0),
    Nautical(-12.0),
    Civil(-6.0),
    Blue(-4.0),
    Horizon(-0.833),
    Golden(6.0),
}

/** Свет по высоте солнца: так раскрашена полоса суток. */
enum class LightPhase {
    Night, Astronomical, Nautical, Blue, Golden, Day;

    companion object {
        fun of(elevation: Double): LightPhase = when {
            elevation < SunEdge.Astronomical.degrees -> Night
            elevation < SunEdge.Nautical.degrees -> Astronomical
            elevation < SunEdge.Civil.degrees -> Nautical
            elevation < SunEdge.Blue.degrees -> Blue
            elevation < SunEdge.Golden.degrees -> Golden
            else -> Day
        }
    }
}

/**
 * Сутки [date] по местному времени. [rising] когда солнце поднимается через границу (утро), [setting] когда опускается
 * (вечер); границы, которых солнце в эти сутки не пересекает (белые ночи, полярный день и ночь), в словарях нет.
 * [noon] истинный полдень (самое высокое солнце), [noonElevation] его высота в градусах. Азимуты в градусах от севера.
 */
data class SunDay(
    val date: LocalDate,
    val rising: Map<SunEdge, Instant>,
    val setting: Map<SunEdge, Instant>,
    val noon: Instant,
    val noonElevation: Double,
    val riseAzimuth: Double?,
    val setAzimuth: Double?,
) {
    val sunrise: Instant? get() = rising[SunEdge.Horizon]
    val sunset: Instant? get() = setting[SunEdge.Horizon]

    /** Длина светового дня; пусто, если в эти сутки нет восхода или заката. */
    val daylight: Duration? get() = if (sunrise != null && sunset != null) Duration.between(sunrise, sunset) else null

    /** Утренний синий час: от −6° до −4°. */
    val morningBlue: Span? get() = span(rising[SunEdge.Civil], rising[SunEdge.Blue])

    /** Утренний золотой час: от −4° до +6°. */
    val morningGolden: Span? get() = span(rising[SunEdge.Blue], rising[SunEdge.Golden])

    /** Вечерний золотой час: от +6° до −4°. */
    val eveningGolden: Span? get() = span(setting[SunEdge.Golden], setting[SunEdge.Blue])

    /** Вечерний синий час: от −4° до −6°. */
    val eveningBlue: Span? get() = span(setting[SunEdge.Blue], setting[SunEdge.Civil])

    /** Сумерки вида [edge] (гражданские, навигационные, астрономические) утром: от этой границы до следующей светлее. */
    fun morningTwilight(edge: SunEdge): Span? = span(rising[edge], rising[lighter(edge)])

    fun eveningTwilight(edge: SunEdge): Span? = span(setting[lighter(edge)], setting[edge])

    private fun lighter(edge: SunEdge): SunEdge = when (edge) {
        SunEdge.Astronomical -> SunEdge.Nautical
        SunEdge.Nautical -> SunEdge.Civil
        else -> SunEdge.Horizon
    }

    private fun span(from: Instant?, to: Instant?): Span? = if (from != null && to != null && from < to) Span(from, to) else null
}

/** Промежуток времени [from, to). */
data class Span(val from: Instant, val to: Instant)

/** Где сейчас солнце или луна на кривой карточки: днём доля дня, ночью доля ночи (ночью метка показывает её ход). */
data class SkyRun(val daytime: Boolean, val from: Instant, val to: Instant, val fraction: Double)

object SunLight {

    /** Свет суток [date] в месте [latitude], [longitude] (часовой пояс [zone]). */
    fun day(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): SunDay {
        val start = date.atStartOfDay(zone).toInstant().epochSecond
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        fun elevation(second: Long) = SunPosition.at(latitude, longitude, Instant.ofEpochSecond(second)).elevation
        val rising = HashMap<SunEdge, Instant>()
        val setting = HashMap<SunEdge, Instant>()
        var bestSecond = start
        var bestElevation = elevation(start)
        var t = start
        var previous = bestElevation
        while (t < end) {
            val next = minOf(t + STEP_SECONDS, end)
            val current = elevation(next)
            if (current > bestElevation) {
                bestElevation = current
                bestSecond = next
            }
            for (edge in SunEdge.entries) {
                val level = edge.degrees
                if (previous < level && current >= level && edge !in rising) {
                    rising[edge] = Instant.ofEpochSecond(crossing(t, next, level, ::elevation))
                } else if (previous >= level && current < level && edge !in setting) {
                    setting[edge] = Instant.ofEpochSecond(crossing(t, next, level, ::elevation))
                }
            }
            t = next
            previous = current
        }
        val noon = peak(maxOf(start, bestSecond - STEP_SECONDS), minOf(end, bestSecond + STEP_SECONDS), ::elevation)
        fun azimuth(at: Instant?) = at?.let { SunPosition.horizontal(latitude, longitude, it).azimuth }
        return SunDay(
            date = date,
            rising = rising,
            setting = setting,
            noon = Instant.ofEpochSecond(noon),
            noonElevation = elevation(noon),
            riseAzimuth = azimuth(rising[SunEdge.Horizon]),
            setAzimuth = azimuth(setting[SunEdge.Horizon]),
        )
    }

    /** Свет в момент [time]: по высоте солнца. */
    fun phaseAt(time: Instant, latitude: Double, longitude: Double): LightPhase =
        LightPhase.of(SunPosition.at(latitude, longitude, time).elevation)

    /**
     * Полоса суток [date]: свет в начале каждого отрезка длиной [stepMinutes] (по умолчанию 144 отрезка по 10 минут).
     * В дни перевода часов отрезков на 6 больше или меньше.
     */
    fun strip(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double, stepMinutes: Long = 10): List<LightPhase> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val count = (Duration.between(start, end).toMinutes() / stepMinutes).toInt()
        return (0 until count).map { phaseAt(start.plusSeconds(it * stepMinutes * 60), latitude, longitude) }
    }

    /**
     * День или ночь в момент [now] и сколько их прошло. День от восхода до заката [today]; ночь от заката до восхода
     * (до восхода сегодня ночь началась вчера, после заката кончится завтра). Пусто в полярный день и полярную ночь.
     */
    fun run(now: Instant, yesterday: SunDay, today: SunDay, tomorrow: SunDay): SkyRun? {
        val rise = today.sunrise ?: return null
        val set = today.sunset ?: return null
        val (daytime, from, to) = when {
            now < rise -> Triple(false, yesterday.sunset ?: return null, rise)
            now < set -> Triple(true, rise, set)
            else -> Triple(false, set, tomorrow.sunrise ?: return null)
        }
        val total = Duration.between(from, to).seconds.toDouble()
        val gone = Duration.between(from, now).seconds.toDouble()
        return SkyRun(daytime, from, to, (gone / total).coerceIn(0.0, 1.0))
    }

    /** Ближайшее после [now] событие для съёмки в эти сутки (синий час, золотой час, восход, закат) или пусто. */
    fun nextShootingEvent(now: Instant, day: SunDay): Pair<ShootingEvent, Instant>? = listOfNotNull(
        day.rising[SunEdge.Civil]?.let { ShootingEvent.BlueHour to it },
        day.rising[SunEdge.Blue]?.let { ShootingEvent.GoldenHour to it },
        day.sunrise?.let { ShootingEvent.Sunrise to it },
        day.setting[SunEdge.Golden]?.let { ShootingEvent.GoldenHour to it },
        day.sunset?.let { ShootingEvent.Sunset to it },
        day.setting[SunEdge.Blue]?.let { ShootingEvent.BlueHour to it },
    ).filter { it.second > now }.minByOrNull { it.second }

    /** Момент пересечения уровня [level] между [from] и [to]: деление пополам до секунды. */
    private fun crossing(from: Long, to: Long, level: Double, elevation: (Long) -> Double): Long {
        var a = from
        var b = to
        val rising = elevation(a) < level
        while (b - a > 1) {
            val m = (a + b) / 2
            if ((elevation(m) < level) == rising) a = m else b = m
        }
        return b
    }

    /** Самая высокая точка между [from] и [to] (троичный поиск до секунды). */
    private fun peak(from: Long, to: Long, elevation: (Long) -> Double): Long {
        var a = from
        var b = to
        while (b - a > 2) {
            val m1 = a + (b - a) / 3
            val m2 = b - (b - a) / 3
            if (elevation(m1) < elevation(m2)) a = m1 else b = m2
        }
        return (a + b) / 2
    }

    private const val STEP_SECONDS = 300L
}

/** Слой облаков: низкие (и туман) до 3 км, средние 3–8 км, высокие выше 8 км (как у Open-Meteo). */
enum class CloudLayer { Low, Mid, High }

/** Облачность в промежутке: [percent] средняя общая, [layer] слой, которого больше всего (пусто, если почти ясно или слоёв нет). */
data class SpanClouds(val percent: Int, val layer: CloudLayer?)

object SpanCloudsLogic {

    /** Меньше этого общая облачность считается ясным небом, слой не называется. */
    const val CLEAR_BELOW = 10

    /**
     * Облака в промежутке [span] (золотой или синий час): среднее по часам, которые его задевают. Общая облачность из
     * ленты ([HourPoint.cloudCover]), слои из часовых значений. Пусто, если для этих часов облачности нет.
     */
    fun during(span: Span, hours: List<HourPoint>, details: List<HourDetails>): SpanClouds? {
        fun touches(start: java.time.Instant) = start < span.to && start.plusSeconds(3600) > span.from
        val total = hours.filter { touches(it.time) }.mapNotNull { it.cloudCover }
        if (total.isEmpty()) return null
        val percent = Math.round(total.average()).toInt()
        val touched = details.filter { touches(it.time) }
        fun mean(pick: (HourDetails) -> Int?): Double? = touched.mapNotNull(pick).takeIf { it.isNotEmpty() }?.average()
        val layers = listOf(
            CloudLayer.Low to mean { it.cloudLowPercent },
            CloudLayer.Mid to mean { it.cloudMidPercent },
            CloudLayer.High to mean { it.cloudHighPercent },
        ).mapNotNull { (layer, value) -> value?.let { layer to it } }
        val top = layers.maxByOrNull { it.second }
        val layer = top?.takeIf { percent >= CLEAR_BELOW && it.second >= CLEAR_BELOW }?.first
        return SpanClouds(percent, layer)
    }
}

/** События для съёмки на плитке «Подробно»: начало синего часа, золотого часа, восход и закат. */
enum class ShootingEvent { BlueHour, GoldenHour, Sunrise, Sunset }

/** Сторона света по азимуту (градусы от севера): восемь сторон, 0 север, 1 северо-восток … 7 северо-запад. */
fun compassPoint(azimuth: Double): Int = Math.floorMod(Math.round(azimuth / 45.0).toInt(), 8)
