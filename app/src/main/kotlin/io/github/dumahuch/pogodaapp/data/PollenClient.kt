package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** Источник индекса опасности пыльцы в виде сырого файла; в тестах подменяется. */
interface PollenSource {
    suspend fun fetchRaw(): String
}

/**
 * Файл DWD с индексом опасности пыльцы для всей Германии (одним файлом, обновляется раз в сутки около 11 часов):
 * https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json
 * Описание: https://opendata.dwd.de/climate_environment/health/alerts/Beschreibung_pollen_s31fg.pdf
 * Условия: данные открытые, при показе нужна ссылка на источник «Deutscher Wetterdienst» (GeoNutzV, § 3).
 */
class DwdPollenClient(
    private val url: String = DEFAULT_URL,
    private val userAgent: String = "PogodaApp",
) : PollenSource {

    override suspend fun fetchRaw(): String = withContext(Dispatchers.IO) {
        HttpJson.get(url, userAgent, source = "DWD")
    }

    companion object {
        const val DEFAULT_URL = "https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json"
    }
}

/** Разбор файла DWD. */
object PollenParser {

    private val DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val DAY_FIELDS = listOf("today", "tomorrow", "dayafter_to")

    /**
     * Прогноз для района [regionKey] (`partregion_id`, а у районов без частей `region_id`). [fetchedOn] это день
     * загрузки: он считается днём выпуска, если в файле нет даты `last_update`. Пусто, если файл не разобран
     * или такого района в нём нет: плитка тогда показывает «Нет данных», остальное не страдает.
     */
    fun parse(json: String, regionKey: Int, fetchedOn: LocalDate): PollenForecast? = try {
        val root = JSONObject(json)
        val issued = root.optString("last_update").let { text ->
            DATE.find(text)?.destructured?.let { (y, m, d) -> LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }
        } ?: fetchedOn
        val content = root.getJSONArray("content")
        var result: PollenForecast? = null
        for (i in 0 until content.length()) {
            val entry = content.getJSONObject(i)
            val part = entry.optInt("partregion_id", -1)
            val key = if (part >= 0) part else entry.optInt("region_id", -1)
            if (key != regionKey) continue
            val pollen = entry.getJSONObject("Pollen")
            val levels = HashMap<PollenKind, List<PollenLevel?>>()
            for (kind in PollenKind.entries) {
                val kindJson = pollen.optJSONObject(kind.dwdKey) ?: continue
                levels[kind] = DAY_FIELDS.map { PollenLevel.parse(kindJson.optString(it, "")) }
            }
            if (levels.isNotEmpty()) result = PollenForecast(key, issued, levels)
            break
        }
        result
    } catch (e: JSONException) {
        null
    } catch (e: java.time.DateTimeException) {
        null
    }
}
