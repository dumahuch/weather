package io.github.dumahuch.pogodaapp.widget

import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import io.github.dumahuch.pogodaapp.forecast.RangeSpan
import io.github.dumahuch.pogodaapp.forecast.TemperatureRange
import io.github.dumahuch.pogodaapp.forecast.precipitationCaption
import io.github.dumahuch.pogodaapp.home.isStrongWind
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Instant
import java.time.ZoneId

/**
 * Всё, что показывает виджет, из сохранённого прогноза: числа и виды погоды без текстов (тексты на нужном языке
 * подставляет рисовальщик). Чистая логика, без Android: [WidgetModelTest].
 */
data class WidgetModel(
    val placeName: String?,
    val zone: ZoneId,
    val temperature: Double,
    val feelsLike: Double,
    val condition: Condition,
    val drizzle: Boolean,
    val isDay: Boolean,
    val windy: Boolean,
    val high: Double,
    val low: Double,
    val precipitation: PrecipitationCaption,
    /** Часы ленты, первый «сейчас». */
    val hours: List<HourPoint>,
    /** Следующие сутки после сегодняшних. */
    val days: List<DayForecast>,
    /** Положение полосок температур дней [days] на общей шкале. */
    val ranges: List<RangeSpan>,
    val windKmh: Double?,
    val humidity: Int?,
    val pressureHpa: Double?,
    /** Осадки за сегодняшние сутки, мм. */
    val precipitationMm: Double?,
    val sunrise: Instant?,
    val sunset: Instant?,
)

object WidgetModels {
    /** Сколько часов и дней берётся: самая большая раскладка показывает шесть часов и пять дней. */
    const val HOURS = 6
    const val DAYS = 5

    fun of(place: Place, forecast: Forecast, now: Instant): WidgetModel {
        val current = forecast.current
        val today = now.atZone(forecast.zone).toLocalDate()
        val details = forecast.detailsAt(now)
        val todayForecast = forecast.days.firstOrNull { it.date == today }
        val days = forecast.days.filter { it.date.isAfter(today) }.take(DAYS)
        return WidgetModel(
            placeName = place.name,
            zone = forecast.zone,
            temperature = current.temperature,
            feelsLike = current.feelsLike,
            condition = current.condition,
            drizzle = current.drizzle,
            isDay = current.isDay,
            windy = isStrongWind(details?.windKmh),
            high = forecast.today.max,
            low = forecast.today.min,
            precipitation = precipitationCaption(forecast, now),
            hours = forecast.strip(now, HOURS),
            days = days,
            ranges = TemperatureRange.layout(days),
            windKmh = details?.windKmh,
            humidity = details?.humidity,
            pressureHpa = details?.pressureHpa,
            precipitationMm = todayForecast?.precipitationMm,
            sunrise = todayForecast?.sunrise,
            sunset = todayForecast?.sunset,
        )
    }
}
