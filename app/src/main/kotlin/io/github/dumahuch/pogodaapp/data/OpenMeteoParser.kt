package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.WeatherCodes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

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

    /** Пустое значение (null) в осадках считается нулём: час без данных не должен ломать ленту. */
    private fun JSONArray.optNumber(index: Int): Double = if (isNull(index)) 0.0 else getDouble(index)
}
