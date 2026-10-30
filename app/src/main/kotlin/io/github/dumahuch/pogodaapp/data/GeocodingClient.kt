package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.places.PlaceSuggestion
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/** Источник подсказок поиска города в виде сырого ответа; в тестах подменяется. */
interface GeocodingSource {
    /** [language] двухбуквенный код языка названий («ru», «de», «en»). */
    suspend fun searchRaw(query: String, language: String): String
}

/**
 * Поиск города по названию или почтовому индексу: бесплатный интерфейс Open-Meteo (https://open-meteo.com/en/docs/geocoding-api).
 * От трёх знаков поиск идёт по началу слова, от двух по точному названию. Страну поиск не подставляет: подсказки приходят
 * со всего мира, в каждой указана страна. Данные GeoNames (CC BY 4.0), ссылка на источник стоит на экране поиска.
 */
class OpenMeteoGeocodingClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : GeocodingSource {

    override suspend fun searchRaw(query: String, language: String): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(query, language), userAgent, source = "Open-Meteo Geocoding")
    }

    internal fun buildUrl(query: String, language: String): String {
        val name = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return "$baseUrl?name=$name&count=$RESULTS&language=$language&format=json"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"

        /** Сколько подсказок просим: достаточно, чтобы после упорядочивания по стране хватило и местных, и дальних. */
        const val RESULTS = 12
    }
}

object GeocodingParser {

    /**
     * Разбор ответа поиска. Если ничего не найдено, ключа `results` в ответе нет: это пустой список, а не ошибка.
     * Записи без названия или координат пропускаются. Неразборчивый ответ даёт [ForecastException].
     */
    fun parse(raw: String): List<PlaceSuggestion> {
        val root = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw ForecastException("Ответ поиска не разобран: ${e.message}", e)
        }
        val results = root.optJSONArray("results") ?: return emptyList()
        val list = ArrayList<PlaceSuggestion>(results.length())
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty() || !item.has("latitude") || !item.has("longitude")) continue
            list += PlaceSuggestion(
                name = name,
                admin1 = item.optString("admin1").trim().ifEmpty { null },
                country = item.optString("country").trim().ifEmpty { null },
                countryCode = item.optString("country_code").trim().ifEmpty { null },
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"),
            )
        }
        return list
    }
}
