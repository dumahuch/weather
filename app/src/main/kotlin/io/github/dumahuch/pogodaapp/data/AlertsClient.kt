package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.AlertKinds
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.AlertSet
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Источник официальных предупреждений в виде сырого ответа; в тестах подменяется. */
interface AlertsSource {
    suspend fun fetchRaw(latitude: Double, longitude: Double): String
}

/**
 * Предупреждения DWD через Bright Sky (https://brightsky.dev/, описание: https://api.brightsky.dev/openapi.json).
 * Bright Sky переводит CAP-ленту DWD в JSON и сам находит район предупреждений (Warnzelle) по координатам; ключа не нужно,
 * код сервиса под лицензией MIT, к самим данным применяются условия DWD (ссылка на источник «Deutscher Wetterdienst»).
 * Координаты в запросе с тремя знаками, как у прогноза (см. Coordinates).
 */
class BrightSkyAlertsClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : AlertsSource {

    override suspend fun fetchRaw(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(latitude, longitude), userAgent, source = "Bright Sky")
    }

    internal fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = Coordinates.param(latitude)
        val lon = Coordinates.param(longitude)
        return "$baseUrl?lat=$lat&lon=$lon"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.brightsky.dev/alerts"
    }
}

/**
 * Разбор ответа Bright Sky `/alerts`: `{"alerts":[…], "location":{"name":…}}`. Снисходителен к отдельным записям:
 * запись без времени начала, без уровня или без заголовка пропускается, остальные остаются. Пропускаются и учебные
 * сообщения (status «test») и отбои (response_type «allclear»). Повреждённый ответ целиком даёт null.
 */
object AlertsParser {

    /** Больше этого записей не читается: столько предупреждений сразу на один район не бывает. */
    internal const val MAX_ALERTS = 50

    fun parse(json: String): AlertSet? = if (JsonGuard.tooDeep(json)) null else try {
        val root = JSONObject(json)
        val list = root.getJSONArray("alerts")
        val alerts = ArrayList<WeatherAlert>()
        val seen = HashSet<String>()
        for (index in 0 until minOf(list.length(), MAX_ALERTS)) {
            val alert = list.optJSONObject(index)?.let { alertOrNull(it) } ?: continue
            if (seen.add(alert.id)) alerts += alert
        }
        val location = root.optJSONObject("location")
        val area = location?.let { text(it, "name") ?: text(it, "name_short") }
        AlertSet(area, alerts)
    } catch (e: JSONException) {
        null
    }

    private fun alertOrNull(entry: JSONObject): WeatherAlert? {
        if (text(entry, "status")?.lowercase() == "test") return null
        if (text(entry, "response_type")?.lowercase() == "allclear") return null
        val level = AlertLevel.ofSeverity(text(entry, "severity")) ?: return null
        val onset = time(entry, "onset") ?: return null
        val headlineDe = text(entry, "headline_de")
        val headlineEn = text(entry, "headline_en")
        if (headlineDe == null && headlineEn == null) return null
        val id = text(entry, "alert_id") ?: entry.optLong("id", -1).takeIf { it >= 0 }?.toString() ?: return null
        return WeatherAlert(
            id = id,
            level = level,
            kind = AlertKinds.classify(text(entry, "event_de"), text(entry, "event_en")),
            onset = onset,
            expires = time(entry, "expires"),
            issued = time(entry, "effective"),
            headlineDe = headlineDe ?: headlineEn.orEmpty(),
            headlineEn = headlineEn ?: headlineDe.orEmpty(),
            descriptionDe = text(entry, "description_de") ?: text(entry, "description_en").orEmpty(),
            descriptionEn = text(entry, "description_en") ?: text(entry, "description_de").orEmpty(),
            instructionDe = text(entry, "instruction_de"),
            instructionEn = text(entry, "instruction_en"),
        )
    }

    /** Строковое поле без пустых значений и без JSON null (`optString` превращает его в слово «null»). */
    private fun text(entry: JSONObject, key: String): String? {
        if (!entry.has(key) || entry.isNull(key)) return null
        val value = entry.opt(key) as? String ?: return null
        return value.trim().takeIf { it.isNotEmpty() }
    }

    private fun time(entry: JSONObject, key: String): Instant? = text(entry, key)?.let {
        try {
            OffsetDateTime.parse(it).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
