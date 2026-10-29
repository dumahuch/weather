package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.WeatherCodes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Разбор ответа Open-Meteo (запрос собирает [OpenMeteoClient]): время в секундах Unix,
 * часовой пояс в поле timezone. Ответ с «error: true» или без нужных полей даёт [ForecastException].
 */
object OpenMeteoParser {

    fun parse(json: String): Forecast {
        try {
            val root = JSONObject(json)
            if (root.optBoolean("error", false)) {
                throw ForecastException("Open-Meteo: ${root.optString("reason", "ошибка без пояснения")}")
            }
            val zone = ZoneId.of(root.getString("timezone"))
            return Forecast(
                zone = zone,
                current = parseCurrent(root.getJSONObject("current")),
                today = parseToday(root.getJSONObject("daily")),
                hours = parseHours(root.getJSONObject("hourly")),
                days = parseDays(root.getJSONObject("daily"), zone),
                hourDetails = parseHourDetails(root.getJSONObject("hourly")),
            )
        } catch (e: JSONException) {
            throw ForecastException("Ответ Open-Meteo не разобран: ${e.message}", e)
        } catch (e: DateTimeException) {
            throw ForecastException("Неизвестный часовой пояс в ответе: ${e.message}", e)
        }
    }

    private fun parseCurrent(current: JSONObject) = CurrentWeather(
        time = Instant.ofEpochSecond(current.getLong("time")),
        temperature = current.getDouble("temperature_2m"),
        feelsLike = current.getDouble("apparent_temperature"),
        condition = WeatherCodes.toCondition(current.getInt("weather_code")),
        isDay = current.getInt("is_day") == 1,
    )

    private fun parseToday(daily: JSONObject) = DayRange(
        max = daily.getJSONArray("temperature_2m_max").getDouble(0),
        min = daily.getJSONArray("temperature_2m_min").getDouble(0),
    )

    /**
     * Сутки вкладки «Прогноз»: не больше [Forecast.DAYS_SHOWN]. Ответ в старом формате (из сохранённого прогноза
     * версии до 0.3.0) не содержит суточных кодов погоды: тогда список пуст и вкладка просит обновить прогноз.
     * Если у какого-то дня нет температуры или кода погоды, список обрывается на нём: дни идут подряд, дыр не бывает.
     * Остальные суточные значения без данных (вероятность, ветер, УФ, восход) заменяются нулём или «нет данных».
     */
    private fun parseDays(daily: JSONObject, zone: ZoneId): List<DayForecast> {
        val times = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code") ?: return emptyList()
        val maxima = daily.getJSONArray("temperature_2m_max")
        val minima = daily.getJSONArray("temperature_2m_min")
        val sums = daily.optJSONArray("precipitation_sum")
        val probabilities = daily.optJSONArray("precipitation_probability_max")
        val winds = daily.optJSONArray("wind_speed_10m_max")
        val gusts = daily.optJSONArray("wind_gusts_10m_max")
        val uvs = daily.optJSONArray("uv_index_max")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")

        val count = minOf(times.length(), codes.length(), maxima.length(), minima.length(), Forecast.DAYS_SHOWN)
        val days = ArrayList<DayForecast>(count)
        for (i in 0 until count) {
            if (times.isNull(i) || codes.isNull(i) || maxima.isNull(i) || minima.isNull(i)) break
            days += DayForecast(
                date = Instant.ofEpochSecond(times.getLong(i)).atZone(zone).toLocalDate(),
                condition = WeatherCodes.toCondition(codes.getInt(i)),
                max = maxima.getDouble(i),
                min = minima.getDouble(i),
                precipitationMm = sums.numberAt(i) ?: 0.0,
                precipitationProbability = probabilities.numberAt(i)?.let { Math.round(it).toInt() },
                windMaxKmh = winds.numberAt(i) ?: 0.0,
                gustMaxKmh = gusts.numberAt(i) ?: 0.0,
                uvMax = uvs.numberAt(i) ?: 0.0,
                sunrise = sunrises.instantAt(i, zone),
                sunset = sunsets.instantAt(i, zone),
            )
        }
        return days
    }

    /**
     * Осадки в ответе относятся к часу, который закончился в указанное время,
     * а точка ленты описывает час, который начинается. Поэтому осадки берутся из следующей записи,
     * а последняя запись служит только для этого и в ленту не попадает.
     */
    private fun parseHours(hourly: JSONObject): List<HourPoint> {
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val isDay = hourly.getJSONArray("is_day")
        val precipitation = hourly.getJSONArray("precipitation")
        val count = times.length()
        if (count < 2) throw ForecastException("В ответе слишком мало часов: $count")
        if (listOf(temperatures, codes, isDay, precipitation).any { it.length() < count }) {
            throw ForecastException("Массивы почасовых данных разной длины")
        }
        return (0 until count - 1).map { i ->
            HourPoint(
                time = Instant.ofEpochSecond(times.getLong(i)),
                temperature = temperatures.getDouble(i),
                condition = WeatherCodes.toCondition(codes.getInt(i)),
                isDay = isDay.getInt(i) == 1,
                precipitationMm = precipitation.optNumber(i + 1),
            )
        }
    }

    /**
     * Часовые значения «Подробно». Все записи берутся как есть, без сдвига (это мгновенные значения, не суммы).
     * Ответ старого формата (до 0.4.0) полей не содержит: тогда список пуст и вкладка просит обновить прогноз.
     * Недостающее значение остаётся пустым.
     */
    private fun parseHourDetails(hourly: JSONObject): List<HourDetails> {
        val times = hourly.getJSONArray("time")
        val humidity = hourly.optJSONArray("relative_humidity_2m")
        val pressure = hourly.optJSONArray("pressure_msl")
        val visibility = hourly.optJSONArray("visibility")
        val wind = hourly.optJSONArray("wind_speed_10m")
        val direction = hourly.optJSONArray("wind_direction_10m")
        val gusts = hourly.optJSONArray("wind_gusts_10m")
        val uv = hourly.optJSONArray("uv_index")
        if (listOf(humidity, pressure, visibility, wind, direction, gusts, uv).all { it == null }) return emptyList()
        return (0 until times.length()).map { i ->
            HourDetails(
                time = Instant.ofEpochSecond(times.getLong(i)),
                humidity = humidity.numberAt(i)?.let { Math.round(it).toInt() },
                pressureHpa = pressure.numberAt(i),
                visibilityM = visibility.numberAt(i),
                windKmh = wind.numberAt(i),
                windDirection = direction.numberAt(i)?.let { Math.round(it).toInt() },
                gustKmh = gusts.numberAt(i),
                uvIndex = uv.numberAt(i),
            )
        }
    }

    /** Число из массива или null, если массива нет, он короче или в нём пусто. */
    private fun JSONArray?.numberAt(index: Int): Double? =
        if (this == null || index >= length() || isNull(index)) null else getDouble(index)

    /** Момент из массива: секунды Unix (формат запроса) или запасной вариант «2026-10-29T07:20» по времени места. */
    private fun JSONArray?.instantAt(index: Int, zone: ZoneId): Instant? {
        if (this == null || index >= length() || isNull(index)) return null
        return when (val value = get(index)) {
            is Number -> Instant.ofEpochSecond(value.toLong())
            is String -> try {
                LocalDateTime.parse(value).atZone(zone).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
            else -> null
        }
    }

    /** Пустое значение (null) в осадках считается нулём: час без данных не должен ломать ленту. */
    private fun JSONArray.optNumber(index: Int): Double = if (isNull(index)) 0.0 else getDouble(index)
}
