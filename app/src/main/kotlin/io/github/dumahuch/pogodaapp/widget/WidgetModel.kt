package io.github.dumahuch.pogodaapp.widget

import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import io.github.dumahuch.pogodaapp.forecast.RangeSpan
import io.github.dumahuch.pogodaapp.forecast.TemperatureRange
import io.github.dumahuch.pogodaapp.forecast.precipitationCaption
import io.github.dumahuch.pogodaapp.home.isStrongWind
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastAge
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Duration
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
    /** Когда осадки сегодня («Осадки днём», «Без осадков»). */
    val caption: PrecipitationCaption,
    /** Следующие сутки после сегодняшних. */
    val days: List<DayForecast>,
    /** Положение полосок температур дней [days] на общей шкале. */
    val ranges: List<RangeSpan>,
    val windKmh: Double?,
    val humidity: Int?,
    /** Осадки за сегодняшние сутки, мм. */
    val precipitationMm: Double?,
    /** Для неба «Ди»: где место, какой сейчас час и сколько осадков и облаков в этот час. */
    val latitude: Double,
    val longitude: Double,
    val now: Instant,
    val hourPrecipitationMm: Double?,
    val cloudCoverPercent: Double?,
    /** Сколько прошло с момента, к которому относятся данные ответа (для пометки «данные N часов назад»). */
    val dataAge: Duration = Duration.ZERO,
)

object WidgetModels {
    /** Сколько дней берётся: самая большая раскладка показывает пять. */
    const val DAYS = 5

    /**
     * Модель по прогнозу из памяти с поправкой на возраст ([ForecastAge.asOf]); null, если данные старше суток или нужных
     * часов нет: виджет пишет «Нет данных».
     */
    fun ofStored(place: Place, forecast: Forecast, now: Instant): WidgetModel? {
        val adjusted = ForecastAge.asOf(forecast, now) ?: return null
        return of(place, adjusted, now).copy(dataAge = ForecastAge.of(forecast, now))
    }

    fun of(place: Place, forecast: Forecast, now: Instant): WidgetModel {
        val current = forecast.current
        val today = now.atZone(forecast.zone).toLocalDate()
        val details = forecast.detailsAt(now)
        val todayForecast = forecast.days.firstOrNull { it.date == today }
        val days = forecast.days.filter { it.date.isAfter(today) }.take(DAYS)
        val hour = forecast.strip(now, 1).firstOrNull()
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
            caption = precipitationCaption(forecast, now),
            days = days,
            ranges = TemperatureRange.layout(days),
            windKmh = details?.windKmh,
            humidity = details?.humidity,
            precipitationMm = todayForecast?.precipitationMm,
            latitude = place.latitude,
            longitude = place.longitude,
            now = now,
            hourPrecipitationMm = hour?.precipitationMm,
            cloudCoverPercent = hour?.cloudCover?.toDouble(),
        )
    }
}
