package io.github.dumahuch.pogodaapp.sky

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Луна по координатам места и времени: фаза, освещённая доля, время восхода и захода. Считается на телефоне,
 * данные из сети не нужны. Положение луны по приближённым формулам Астрономического ежегодника (серия из шести
 * долготных членов, четырёх широтных и четырёх для параллакса, ошибка положения около 0,3°); восход и заход по тому же
 * принципу, что и у солнца: высота центра диска пересекает порог, который учитывает рефракцию и параллакс
 * (Meeus, «Astronomical Algorithms», гл. 15: h0 = 0,7275·π − 34′).
 * Сверка с независимой библиотекой PyEphem: освещённая доля до 0,5 %, время восхода и захода до двух минут.
 */
internal object Moon {

    /** [illumination] освещённая доля диска от 0 до 1, [cycle] доля лунного месяца: 0 новолуние, 0,5 полнолуние. */
    data class Phase(val illumination: Double, val cycle: Double) {
        val waxing: Boolean get() = cycle < 0.5

        /** Освещённость в процентах, как её показывает экран. */
        val percent: Int get() = (illumination * 100).roundToInt()

        val name: PhaseName
            get() {
                // Восемь равных частей месяца; новолуние и полнолуние занимают по одной шестнадцатой с каждой стороны.
                val eighth = ((cycle * 8) + 0.5).toInt() % 8
                return PhaseName.entries[eighth]
            }
    }

    enum class PhaseName {
        New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent
    }

    /** Восход и заход в пределах одних местных суток; пусто, если события в эти сутки не было (луна встаёт примерно на 50 минут позже с каждым днём). */
    data class Events(val rise: Instant?, val set: Instant?)

    fun phaseAt(time: Instant): Phase {
        val d = daysSinceJ2000(time)
        val moon = ecliptic(d)
        val elongation = reduce(moon.longitude - sunLongitude(d))
        return Phase((1 - cos(Math.toRadians(elongation))) / 2, elongation / 360.0)
    }

    fun eventsOn(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): Events {
        val start = date.atStartOfDay(zone).toInstant().epochSecond
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        var rise: Instant? = null
        var set: Instant? = null
        var t = start
        var previous = margin(start.toDouble(), latitude, longitude)
        while (t < end && (rise == null || set == null)) {
            val next = minOf(t + STEP_SECONDS, end)
            val current = margin(next.toDouble(), latitude, longitude)
            if ((previous < 0) != (current < 0)) {
                val moment = crossing(t.toDouble(), next.toDouble(), previous, current, latitude, longitude)
                val instant = Instant.ofEpochSecond(Math.round(moment))
                if (previous < 0) {
                    if (rise == null) rise = instant
                } else if (set == null) {
                    set = instant
                }
            }
            t = next
            previous = current
        }
        return Events(rise, set)
    }

    /** Самое высокое положение луны в сутки [date]: момент и высота центра диска (градусы; ниже нуля, если луна весь день под горизонтом). */
    data class Transit(val time: Instant, val elevation: Double)

    fun transitOn(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): Transit {
        val start = date.atStartOfDay(zone).toInstant().epochSecond
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        fun elevation(second: Long) = horizontal(second.toDouble(), latitude, longitude).elevation
        var best = start
        var bestElevation = elevation(start)
        var t = start
        while (t < end) {
            t = minOf(t + STEP_SECONDS, end)
            val e = elevation(t)
            if (e > bestElevation) {
                best = t
                bestElevation = e
            }
        }
        // Уточнение троичным поиском в соседних шагах (до минуты); у краёв суток не выходит за них.
        var a = maxOf(start, best - STEP_SECONDS)
        var b = minOf(end, best + STEP_SECONDS)
        while (b - a > 60) {
            val m1 = a + (b - a) / 3
            val m2 = b - (b - a) / 3
            if (elevation(m1) < elevation(m2)) a = m1 else b = m2
        }
        val top = (a + b) / 2
        return Transit(Instant.ofEpochSecond(top), elevation(top))
    }

    /** Главные фазы: новолуние, первая четверть, полнолуние, последняя четверть (доля месяца 0, ¼, ½, ¾). */
    enum class Quarter(val cycle: Double) { New(0.0), FirstQuarter(0.25), Full(0.5), LastQuarter(0.75) }

    /**
     * Ближайшие главные фазы после [from], по одной каждого вида, по порядку. Ищется момент, когда доля месяца проходит
     * через ¼, ½, ¾ и 0 (шаг 6 часов, уточнение до минуты). Ошибка положения луны около 0,3° даёт ошибку времени фазы
     * меньше часа: для даты на экране с запасом.
     */
    fun nextQuarters(from: Instant): List<Pair<Quarter, Instant>> {
        val result = ArrayList<Pair<Quarter, Instant>>()
        var t = from.epochSecond
        var previous = phaseAt(from).cycle
        val end = t + SEARCH_DAYS * 86_400L
        while (t < end && result.size < Quarter.entries.size) {
            val next = t + QUARTER_STEP_SECONDS
            val current = phaseAt(Instant.ofEpochSecond(next)).cycle
            for (quarter in Quarter.entries) {
                if (result.any { it.first == quarter }) continue
                if (passes(previous, current, quarter.cycle)) {
                    result += quarter to Instant.ofEpochSecond(refineQuarter(t, next, quarter.cycle))
                }
            }
            t = next
            previous = current
        }
        return result.sortedBy { it.second }
    }

    /** Доля месяца растёт от 0 до 1 и перескакивает к 0 в новолуние: прошла ли она через [target] между двумя значениями. */
    private fun passes(before: Double, after: Double, target: Double): Boolean =
        if (after >= before) target > before && target <= after else target > before || target <= after

    private fun refineQuarter(from: Long, to: Long, target: Double): Long {
        var a = from
        var b = to
        val start = phaseAt(Instant.ofEpochSecond(a)).cycle
        while (b - a > 60) {
            val m = (a + b) / 2
            if (passes(start, phaseAt(Instant.ofEpochSecond(m)).cycle, target)) b = m else a = m
        }
        return b
    }

    /** Возраст луны в сутках от новолуния (по доле месяца, средний синодический месяц 29,53 суток). */
    fun ageDays(phase: Phase): Int = (phase.cycle * SYNODIC_DAYS).toInt()

    /** Положение луны на эклиптике: долгота и широта в градусах, горизонтальный параллакс в градусах. */
    private class Ecliptic(val longitude: Double, val latitude: Double, val parallax: Double)

    private fun ecliptic(d: Double): Ecliptic {
        val t = d / 36525.0
        fun sinDeg(x: Double) = sin(Math.toRadians(x))
        fun cosDeg(x: Double) = cos(Math.toRadians(x))
        val longitude = 218.32 + 481267.881 * t +
            6.29 * sinDeg(135.0 + 477198.87 * t) - 1.27 * sinDeg(259.3 - 413335.36 * t) +
            0.66 * sinDeg(235.7 + 890534.22 * t) + 0.21 * sinDeg(269.9 + 954397.74 * t) -
            0.19 * sinDeg(357.5 + 35999.05 * t) - 0.11 * sinDeg(186.5 + 966404.03 * t)
        val latitude = 5.13 * sinDeg(93.3 + 483202.02 * t) + 0.28 * sinDeg(228.2 + 960400.89 * t) -
            0.28 * sinDeg(318.3 + 6003.15 * t) - 0.17 * sinDeg(217.6 - 407332.21 * t)
        val parallax = 0.9508 + 0.0518 * cosDeg(135.0 + 477198.87 * t) + 0.0095 * cosDeg(259.3 - 413335.36 * t) +
            0.0078 * cosDeg(235.7 + 890534.22 * t) + 0.0028 * cosDeg(269.9 + 954397.74 * t)
        return Ecliptic(reduce(longitude), latitude, parallax)
    }

    /** Долгота солнца на эклиптике (те же формулы, что в [SunPosition]). */
    private fun sunLongitude(d: Double): Double {
        val anomaly = Math.toRadians(reduce(357.529 + 0.98560028 * d))
        val mean = reduce(280.459 + 0.98564736 * d)
        return reduce(mean + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly))
    }

    /** Высота центра диска над порогом восхода (градусы): положительная, пока луна над горизонтом. */
    private fun margin(epochSecond: Double, latitude: Double, longitude: Double): Double {
        val moon = ecliptic((epochSecond - J2000_EPOCH_SECOND) / SECONDS_PER_DAY)
        return horizontal(epochSecond, latitude, longitude).elevation - (PARALLAX_FACTOR * moon.parallax - REFRACTION_DEGREES)
    }

    /**
     * Где луна на небе места: [elevation] высота центра диска в градусах, [hourAngleHours] часовой угол в часах (0 на юге,
     * минус к востоку, плюс к западу), [azimuth] азимут в градусах от севера по часовой стрелке (90 восток, 270 запад).
     */
    class Position(val elevation: Double, val hourAngleHours: Double, val azimuth: Double = 0.0)

    fun positionAt(time: Instant, latitude: Double, longitude: Double): Position =
        horizontal(time.epochSecond + time.nano / 1e9, latitude, longitude)

    private fun horizontal(epochSecond: Double, latitude: Double, longitude: Double): Position {
        val d = (epochSecond - J2000_EPOCH_SECOND) / SECONDS_PER_DAY
        val moon = ecliptic(d)
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        val lambda = Math.toRadians(moon.longitude)
        val beta = Math.toRadians(moon.latitude)
        val rightAscension = atan2(sin(lambda) * cos(obliquity) - tan(beta) * sin(obliquity), cos(lambda))
        val declination = asin(sin(beta) * cos(obliquity) + cos(beta) * sin(obliquity) * sin(lambda))
        val siderealDeg = reduce(280.46061837 + 360.98564736629 * d + longitude)
        val hourAngle = Math.toRadians(siderealDeg) - rightAscension
        val lat = Math.toRadians(latitude)
        val sinAltitude = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        val altitude = Math.toDegrees(asin(sinAltitude.coerceIn(-1.0, 1.0)))
        val wrapped = Math.toDegrees(atan2(sin(hourAngle), cos(hourAngle))) / 15.0
        // Азимут от юга к западу (Meeus, гл. 13), затем от севера.
        val fromSouth = Math.toDegrees(atan2(sin(hourAngle), cos(hourAngle) * sin(lat) - tan(declination) * cos(lat)))
        return Position(altitude, wrapped, reduce(fromSouth + 180.0))
    }

    /** Момент пересечения порога между [from] и [to]: ложное положение, восемь шагов. */
    private fun crossing(from: Double, to: Double, fromValue: Double, toValue: Double, lat: Double, lon: Double): Double {
        var a = from
        var b = to
        var fa = fromValue
        var fb = toValue
        repeat(REFINE_STEPS) {
            val m = a + (b - a) * fa / (fa - fb)
            val fm = margin(m, lat, lon)
            if ((fm < 0) == (fa < 0)) {
                a = m
                fa = fm
            } else {
                b = m
                fb = fm
            }
        }
        return a + (b - a) * fa / (fa - fb)
    }

    private fun daysSinceJ2000(time: Instant): Double =
        (time.epochSecond - J2000_EPOCH_SECOND + time.nano / 1e9) / SECONDS_PER_DAY

    private fun reduce(degrees: Double): Double = degrees - 360.0 * floor(degrees / 360.0)

    private const val SECONDS_PER_DAY = 86_400.0
    private const val J2000_EPOCH_SECOND = 946_728_000L
    private const val STEP_SECONDS = 600L
    private const val REFINE_STEPS = 8
    private const val PARALLAX_FACTOR = 0.7275
    private const val REFRACTION_DEGREES = 34.0 / 60.0
    private const val QUARTER_STEP_SECONDS = 6 * 3600L
    private const val SEARCH_DAYS = 32L
    private const val SYNODIC_DAYS = 29.530589
}
