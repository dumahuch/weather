package io.github.dumahuch.pogodaapp.sky

import java.time.Instant
import java.time.Year
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Положение солнца по координатам места и времени. Формулы NOAA (General Solar Position Calculations):
 * https://gml.noaa.gov/grad/solcalc/solareqns.PDF. Точность около десятых долей градуса, для выбора
 * картины неба с запасом. Рефракция не учитывается. Данные из сети не нужны.
 */
internal object SunPosition {

    /** [elevation] высота над горизонтом в градусах, [solarHour] истинное солнечное время места, от 0 до 24 (12 это полдень). */
    data class Sun(val elevation: Double, val solarHour: Double)

    fun at(latitude: Double, longitude: Double, time: Instant): Sun {
        val utc = time.atZone(ZoneOffset.UTC)
        val hour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0 + utc.nano / 3.6e12
        val gamma = fractionalYear(utc.year, utc.dayOfYear, hour)
        val solarHours = (hour * 60 + equationOfTime(gamma) + 4 * longitude) / 60.0
        val solarHour = solarHours - 24.0 * floor(solarHours / 24.0)
        return Sun(elevation(latitude, declination(gamma), solarHour), solarHour)
    }

    /**
     * Солнце в заданный солнечный час того же дня, что и [day] (склонение берётся на полдень этого дня).
     * Нужно экрану проверки: показать небо в любое время суток при настоящей дате и широте.
     */
    fun atSolarHour(latitude: Double, day: Instant, solarHour: Double): Sun {
        val utc = day.atZone(ZoneOffset.UTC)
        val gamma = fractionalYear(utc.year, utc.dayOfYear, 12.0)
        return Sun(elevation(latitude, declination(gamma), solarHour), solarHour)
    }

    private fun fractionalYear(year: Int, dayOfYear: Int, hour: Double): Double {
        val days = if (Year.isLeap(year.toLong())) 366.0 else 365.0
        return 2 * PI / days * (dayOfYear - 1 + (hour - 12) / 24)
    }

    /** Уравнение времени в минутах. */
    private fun equationOfTime(g: Double): Double =
        229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))

    /** Склонение солнца в радианах. */
    private fun declination(g: Double): Double =
        0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)

    private fun elevation(latitude: Double, declination: Double, solarHour: Double): Double {
        val lat = Math.toRadians(latitude)
        val hourAngle = Math.toRadians((solarHour - 12) * 15)
        val sinElevation = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
    }
}
