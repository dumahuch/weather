package io.github.dumahuch.pogodaapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Источник прогноза в виде сырого ответа; в тестах подменяется. */
interface ForecastSource {
    suspend fun fetchRaw(latitude: Double, longitude: Double): String
}

/**
 * Запрос к бесплатному интерфейсу Open-Meteo (https://open-meteo.com/en/docs).
 * Координаты округляются до сотых долей градуса (около километра): точнее прогнозу не нужно,
 * а внешнему сервису не стоит знать место точнее.
 */
class OpenMeteoClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : ForecastSource {

    override suspend fun fetchRaw(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(latitude, longitude), userAgent)
    }

    internal fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.ROOT, "%.2f", latitude)
        val lon = String.format(Locale.ROOT, "%.2f", longitude)
        return "$baseUrl?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation,is_day,$HOURLY_DETAIL_FIELDS" +
            "&daily=$DAILY_FIELDS" +
            "&wind_speed_unit=kmh" +
            "&timezone=auto&timeformat=unixtime&forecast_days=$DAYS_REQUESTED"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast"

        /**
         * 14 суток вкладки «Прогноз» плюс одни сутки: последняя запись почасовых данных нужна только для осадков
         * последнего часа (см. [OpenMeteoParser]), так что 14-е сутки остаются целыми. Предел сервиса 16.
         * Почасовые данные идут с начала сегодняшних суток по местному времени.
         */
        const val DAYS_REQUESTED = 15

        /** Часовые значения вкладки «Подробно» (мгновенные); видимость в метрах, ветер в км/ч (см. wind_speed_unit). */
        const val HOURLY_DETAIL_FIELDS = "relative_humidity_2m,pressure_msl,visibility,wind_speed_10m," +
            "wind_direction_10m,wind_gusts_10m,uv_index"

        /** Суточные поля; ветер в км/ч, время восхода и заката в секундах Unix вместе с остальным временем. */
        const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunrise,sunset"
    }
}
