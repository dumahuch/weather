package io.github.dumahuch.pogodaapp.sky

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Положение солнца по координатам места и времени. Приближённые формулы Астрономического ежегодника
 * (Военно-морская обсерватория США, https://aa.usno.navy.mil/faq/sun_approx): ошибка около одной угловой минуты
 * в пределах двух веков от 2000 года. Для выбора картины неба это с огромным запасом. Рефракция не учитывается.
 * Данные из сети не нужны.
 */
internal object SunPosition {

    /** [elevation] высота над горизонтом в градусах, [solarHour] истинное солнечное время места, от 0 до 24 (12 это полдень). */
    data class Sun(val elevation: Double, val solarHour: Double)

    /** Склонение солнца (радианы) и уравнение времени (часы) в один момент. */
    private class Coordinates(val declination: Double, val equationOfTimeHours: Double)

    fun at(latitude: Double, longitude: Double, time: Instant): Sun {
        val coordinates = coordinates(time)
        val secondOfDay = Math.floorMod(time.epochSecond, SECONDS_PER_DAY) + time.nano / 1e9
        val solarHours = secondOfDay / 3600.0 + longitude / 15.0 + coordinates.equationOfTimeHours
        val solarHour = solarHours - 24.0 * floor(solarHours / 24.0)
        return Sun(elevation(latitude, coordinates.declination, solarHour), solarHour)
    }

    /**
     * Солнце в заданный солнечный час того же дня, что и [day] (склонение берётся на полдень этого дня).
     * Нужно экрану проверки: показать небо в любое время суток при настоящей дате и широте.
     */
    fun atSolarHour(latitude: Double, day: Instant, solarHour: Double): Sun {
        val noon = Instant.ofEpochSecond(Math.floorDiv(day.epochSecond, SECONDS_PER_DAY) * SECONDS_PER_DAY + SECONDS_PER_DAY / 2)
        return Sun(elevation(latitude, coordinates(noon).declination, solarHour), solarHour)
    }

    private fun coordinates(time: Instant): Coordinates {
        // D: дней от эпохи J2000.0 (2000-01-01 12:00), как в источнике.
        val d = (time.epochSecond - J2000_EPOCH_SECOND + time.nano / 1e9) / SECONDS_PER_DAY
        val meanAnomaly = Math.toRadians(reduce(357.529 + 0.98560028 * d))
        val meanLongitude = reduce(280.459 + 0.98564736 * d)
        val longitude = Math.toRadians(meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly))
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        val declination = asin(sin(obliquity) * sin(longitude))
        val rightAscensionHours = reduce(Math.toDegrees(atan2(cos(obliquity) * sin(longitude), cos(longitude)))) / 15.0
        val equation = meanLongitude / 15.0 - rightAscensionHours
        // Уравнение времени небольшое (минуты): значения около ±24 часов это перескок через полночь по прямому восхождению.
        val wrapped = equation - 24.0 * floor((equation + 12.0) / 24.0)
        return Coordinates(declination, wrapped)
    }

    private fun reduce(degrees: Double): Double = degrees - 360.0 * floor(degrees / 360.0)

    private fun elevation(latitude: Double, declination: Double, solarHour: Double): Double {
        val lat = Math.toRadians(latitude)
        val hourAngle = Math.toRadians((solarHour - 12) * 15)
        val sinElevation = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
    }

    private const val SECONDS_PER_DAY = 86_400L
    private const val J2000_EPOCH_SECOND = 946_728_000L
}
