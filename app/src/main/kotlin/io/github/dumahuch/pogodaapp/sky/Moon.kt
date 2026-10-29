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
        return altitude - (PARALLAX_FACTOR * moon.parallax - REFRACTION_DEGREES)
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
}
