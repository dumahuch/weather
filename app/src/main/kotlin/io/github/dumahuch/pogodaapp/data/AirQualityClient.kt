package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.AirQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

/** Источник качества воздуха в виде сырого ответа; в тестах подменяется. */
interface AirQualitySource {
    suspend fun fetchRaw(latitude: Double, longitude: Double): String
}

/**
 * Запрос к Open-Meteo Air Quality (https://open-meteo.com/en/docs/air-quality-api): европейский индекс по часам,
 * данные CAMS. Координаты округляются так же, как для прогноза.
 */
class OpenMeteoAirQualityClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : AirQualitySource {

    override suspend fun fetchRaw(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(latitude, longitude), userAgent)
    }

    internal fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.ROOT, "%.2f", latitude)
        val lon = String.format(Locale.ROOT, "%.2f", longitude)
        return "$baseUrl?latitude=$lat&longitude=$lon&hourly=european_aqi&timezone=auto&timeformat=unixtime&forecast_days=2"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
    }
}

/** Разбор ответа качества воздуха. */
object AirQualityParser {

    /**
     * Индекс часа, в который попадает [now]. Пусто, если в ответе нет индекса (за пределами Европы сервис отдаёт
     * пустые значения) или ответ не разобран: плитка тогда показывает прочерк, остальное не страдает.
     */
    fun parse(json: String, now: Instant): AirQuality? = try {
        val hourly = JSONObject(json).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val values = hourly.getJSONArray("european_aqi")
        var found: AirQuality? = null
        for (i in 0 until minOf(times.length(), values.length())) {
            if (Instant.ofEpochSecond(times.getLong(i)).isAfter(now)) break
            if (!values.isNull(i)) found = AirQuality(Math.round(values.getDouble(i)).toInt().coerceAtLeast(0))
        }
        found
    } catch (e: JSONException) {
        null
    }
}
