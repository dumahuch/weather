package io.github.dumahuch.pogodaapp.weather

import java.time.Duration
import java.time.Instant

/**
 * Прогноз из памяти, приведённый к текущему моменту (виджет и уведомление, у которых нет пометки «данные N часов назад»).
 *
 * Блок «сейчас» и «сегодня» в сохранённом ответе относятся к моменту запроса. Прошла ночь без сети: вчерашние вечерние градусы
 * и вчерашние максимум с минимумом нельзя выдавать за сегодняшние. Поэтому:
 * - ответу не больше [FRESH_LIMIT]: без изменений;
 * - больше, но не больше [MAX_AGE]: «сейчас» берётся из почасового ряда на текущий час, максимум и минимум из суток с
 *   сегодняшней датой;
 * - старше [MAX_AGE] или нужных часов и суток в ответе нет: null, показывать нечего.
 */
object ForecastAge {
    val FRESH_LIMIT: Duration = Duration.ofHours(1)
    val MAX_AGE: Duration = Duration.ofHours(24)

    /** Сколько времени прошло с момента, к которому относится блок «сейчас»; не меньше нуля. */
    fun of(forecast: Forecast, now: Instant): Duration =
        Duration.between(forecast.current.time, now).let { if (it.isNegative) Duration.ZERO else it }

    fun asOf(forecast: Forecast, now: Instant): Forecast? {
        val age = of(forecast, now)
        if (age <= FRESH_LIMIT) return forecast
        if (age > MAX_AGE) return null
        val index = forecast.hours.indexOfLast { !it.time.isAfter(now) }
        val hour = forecast.hours.getOrNull(index) ?: return null
        val today = now.atZone(forecast.zone).toLocalDate()
        val range = forecast.days.firstOrNull { it.date == today }?.let { DayRange(it.max, it.min) }
            ?: forecast.hoursOf(today).takeIf { it.isNotEmpty() }?.let { hours ->
                DayRange(hours.maxOf { it.temperature }, hours.minOf { it.temperature })
            }
            ?: return null
        val feelsLike = forecast.detailsAt(now)?.feelsLikeC ?: hour.temperature
        return forecast.copy(
            current = CurrentWeather(
                time = hour.time,
                temperature = hour.temperature,
                feelsLike = feelsLike,
                condition = hour.condition,
                isDay = hour.isDay,
                cloudCover = hour.cloudCover,
                drizzle = false,
            ),
            today = range,
        )
    }
}
