package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.AirHour
import io.github.dumahuch.pogodaapp.weather.AirQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/** Источник качества воздуха в виде сырого ответа; в тестах подменяется. */
interface AirQualitySource {
    suspend fun fetchRaw(latitude: Double, longitude: Double): String
}

/**
 * Запрос к Open-Meteo Air Quality (https://open-meteo.com/en/docs/air-quality-api): европейский индекс и вещества по
 * часам на пять суток, данные CAMS. Координаты в запросе с тремя знаками, как у прогноза (см. Coordinates).
 */
class OpenMeteoAirQualityClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : AirQualitySource {

    override suspend fun fetchRaw(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(latitude, longitude), userAgent)
    }

    internal fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = Coordinates.param(latitude)
        val lon = Coordinates.param(longitude)
        return "$baseUrl?latitude=$lat&longitude=$lon&hourly=$HOURLY&timezone=auto&timeformat=unixtime&forecast_days=$DAYS"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"

        /** Индекс и вещества страницы «Воздух»; мкг/м³. */
        const val HOURLY = "european_aqi,pm2_5,pm10,nitrogen_dioxide,ozone,sulphur_dioxide"

        /** Сегодня и ещё четыре дня (у сервиса по умолчанию пять). */
        const val DAYS = 5
    }
}

/** Разбор ответа качества воздуха. */
object AirQualityParser {

    /**
     * Индекс часа, в который попадает [now]. Пусто, если в ответе нет индекса (за пределами Европы сервис отдаёт
     * пустые значения) или ответ не разобран: плитка тогда показывает прочерк, остальное не страдает.
     */
    fun parse(json: String, now: Instant): AirQuality? = if (JsonGuard.tooDeep(json)) null else try {
        val hourly = JSONObject(json).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val values = hourly.getJSONArray("european_aqi")
        fun JSONArray?.at(i: Int): Double? = if (this == null || i >= length() || isNull(i)) null else getDouble(i)
        val pm25 = hourly.optJSONArray("pm2_5")
        val pm10 = hourly.optJSONArray("pm10")
        val no2 = hourly.optJSONArray("nitrogen_dioxide")
        val o3 = hourly.optJSONArray("ozone")
        val so2 = hourly.optJSONArray("sulphur_dioxide")
        val hours = (0 until minOf(times.length(), values.length())).map { i ->
            AirHour(
                time = Instant.ofEpochSecond(times.getLong(i)),
                eaqi = values.at(i)?.let { Math.round(it).toInt().coerceAtLeast(0) },
                pm25 = pm25.at(i),
                pm10 = pm10.at(i),
                no2 = no2.at(i),
                o3 = o3.at(i),
                so2 = so2.at(i),
            )
        }
        hours.lastOrNull { !it.time.isAfter(now) && it.eaqi != null }?.let { AirQuality(it.eaqi!!, hours) }
    } catch (e: JSONException) {
        null
    }
}
