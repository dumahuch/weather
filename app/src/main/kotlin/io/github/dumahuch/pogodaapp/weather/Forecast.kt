package io.github.dumahuch.pogodaapp.weather

import java.time.Instant
import java.time.LocalDate
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

/**
 * Прогноз на одни сутки по местному времени (вкладка «Прогноз» и подробности дня).
 * [precipitationProbability] в процентах, пусто, если модель на этот день вероятность не даёт.
 * Ветер в км/ч, УФ-индекс безразмерный. Время восхода и заката пусто в полярный день и полярную ночь.
 */
data class DayForecast(
    val date: LocalDate,
    val condition: Condition,
    val max: Double,
    val min: Double,
    val precipitationMm: Double,
    val precipitationProbability: Int?,
    val windMaxKmh: Double,
    val gustMaxKmh: Double,
    val uvMax: Double,
    val sunrise: Instant?,
    val sunset: Instant?,
)

/**
 * Значения часа для вкладки «Подробно» (мгновенные, на начало часа). Любое поле пусто, если модель его не дала.
 * Ветер в км/ч, давление приведено к уровню моря (гПа), видимость в метрах.
 */
data class HourDetails(
    val time: Instant,
    val humidity: Int?,
    val pressureHpa: Double?,
    val visibilityM: Double?,
    val windKmh: Double?,
    val windDirection: Int?,
    val gustKmh: Double?,
    val uvIndex: Double?,
)

data class Forecast(
    /** Часовой пояс места: по нему считаются подписи часов и смена суток. */
    val zone: ZoneId,
    val current: CurrentWeather,
    val today: DayRange,
    val hours: List<HourPoint>,
    /**
     * Сутки по порядку, первые сегодняшние. Пусто у прогноза в старом формате (сохранённого версией до 0.3.0),
     * пока он не обновится.
     */
    val days: List<DayForecast> = emptyList(),
    /** Часовые значения для «Подробно». Пусто у прогноза в старом формате (до 0.4.0), пока он не обновится. */
    val hourDetails: List<HourDetails> = emptyList(),
) {
    /** Значения часа, в который попадает [now]; если [now] раньше всех данных, первого часа. */
    fun detailsAt(now: Instant): HourDetails? {
        val index = hourDetails.indexOfLast { !it.time.isAfter(now) }
        return hourDetails.getOrNull(index.coerceAtLeast(0))
    }

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

    /** Часы указанных суток по местному времени (в день перехода на зимнее или летнее время их 25 или 23). */
    fun hoursOf(date: LocalDate): List<HourPoint> = hours.filter { it.time.atZone(zone).toLocalDate() == date }

    companion object {
        const val STRIP_HOURS = 48

        /** Сколько суток показывает вкладка «Прогноз» (переключатель «7 дней» и «14 дней»). */
        const val DAYS_SHOWN = 14
    }
}

/** Место, для которого получен прогноз. [name] пуст, пока название не определилось. */
data class Place(val latitude: Double, val longitude: Double, val name: String?)

/** Ошибка получения или разбора прогноза; текст для журнала, пользователю не показывается. */
class ForecastException(message: String, cause: Throwable? = null) : Exception(message, cause)
