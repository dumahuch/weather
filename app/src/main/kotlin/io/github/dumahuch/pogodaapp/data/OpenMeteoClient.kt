package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
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
        download(buildUrl(latitude, longitude))
    }

    internal fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.ROOT, "%.2f", latitude)
        val lon = String.format(Locale.ROOT, "%.2f", longitude)
        return "$baseUrl?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&timeformat=unixtime&forecast_days=1&forecast_hours=$HOURS_REQUESTED"
    }

    private fun download(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw ForecastException("Open-Meteo ответил кодом $status: ${reasonOf(body)}")
            }
            return body
        } catch (e: IOException) {
            throw ForecastException("Нет связи с Open-Meteo: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun reasonOf(body: String): String = try {
        JSONObject(body).optString("reason", body.take(120))
    } catch (e: JSONException) {
        body.take(120)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast"

        /** 48 часов ленты плюс одна запись для осадков последнего часа (см. [OpenMeteoParser]). */
        const val HOURS_REQUESTED = 49
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
