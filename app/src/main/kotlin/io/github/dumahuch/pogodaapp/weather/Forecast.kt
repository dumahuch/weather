package io.github.dumahuch.pogodaapp.weather

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Погода в данный момент (модель Open-Meteo, шаг 15 минут). */
data class CurrentWeather(
    val time: Instant,
    val temperature: Double,
    val feelsLike: Double,
    val condition: Condition,
    val isDay: Boolean,
)

/**
 * Один час ленты. Температура и вид погоды относятся к началу часа,
 * осадки (мм) к часу, который начинается в это время.
 */
data class HourPoint(
    val time: Instant,
    val temperature: Double,
    val condition: Condition,
    val isDay: Boolean,
    val precipitationMm: Double,
)

/** Наибольшая и наименьшая температура сегодняшних суток по местному времени. */
data class DayRange(val max: Double, val min: Double)

data class Forecast(
    /** Часовой пояс места: по нему считаются подписи часов и смена суток. */
    val zone: ZoneId,
    val current: CurrentWeather,
    val today: DayRange,
    val hours: List<HourPoint>,
) {
    /**
     * Лента «По часам»: до [count] часов, первый из них «сейчас».
     * Если данные свежие (час первой точки совпадает с часом «сейчас» из блока current),
     * первая точка берёт температуру и вид погоды оттуда же, откуда и число на экране,
     * чтобы они не расходились. У устаревших данных (из памяти) лента начинается
     * с часа, в который попадает [now], и показывает прогноз на этот час.
     */
    fun strip(now: Instant, count: Int = STRIP_HOURS): List<HourPoint> {
        val start = hours.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)
        val slice = hours.drop(start).take(count)
        val first = slice.firstOrNull() ?: return slice
        val currentHour = current.time.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()
        if (first.time != currentHour) return slice
        val head = first.copy(temperature = current.temperature, condition = current.condition, isDay = current.isDay)
        return listOf(head) + slice.drop(1)
    }

    companion object {
        const val STRIP_HOURS = 48
    }
}

/** Место, для которого получен прогноз. [name] пуст, пока название не определилось. */
data class Place(val latitude: Double, val longitude: Double, val name: String?)

/** Ошибка получения или разбора прогноза; текст для журнала, пользователю не показывается. */
class ForecastException(message: String, cause: Throwable? = null) : Exception(message, cause)
