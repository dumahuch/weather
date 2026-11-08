package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.RadarFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Источник радара в виде сырого ответа; в тестах подменяется. */
interface RadarSource {
    suspend fun fetchRaw(latitude: Double, longitude: Double, now: Instant): String
}

/**
 * Радар осадков DWD через Bright Sky (https://api.brightsky.dev/radar, описание: https://api.brightsky.dev/openapi.json):
 * сетка 1 км, шаг 5 минут, прогноз на два часа. Запрашивается маленький квадрат (±2 км, 5×5 клеток) вокруг места в виде
 * обычных чисел (`format=plain`), чтобы не распаковывать сжатые данные. Ключа не нужно; к самим данным применяются условия
 * DWD (ссылка на источник «Deutscher Wetterdienst»). Время запроса: от 15 минут назад до 80 минут вперёд.
 */
class BrightSkyRadarClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "PogodaApp",
) : RadarSource {

    override suspend fun fetchRaw(latitude: Double, longitude: Double, now: Instant): String = withContext(Dispatchers.IO) {
        HttpJson.get(buildUrl(latitude, longitude, now), userAgent, source = "Bright Sky")
    }

    internal fun buildUrl(latitude: Double, longitude: Double, now: Instant): String {
        val lat = Coordinates.param(latitude)
        val lon = Coordinates.param(longitude)
        val from = now.minus(PAST).truncatedTo(ChronoUnit.SECONDS)
        val to = now.plus(AHEAD).truncatedTo(ChronoUnit.SECONDS)
        return "$baseUrl?lat=$lat&lon=$lon&distance=$DISTANCE_M&format=plain&tz=UTC&date=$from&last_date=$to"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.brightsky.dev/radar"
        private const val DISTANCE_M = 2000
        private val PAST: Duration = Duration.ofMinutes(15)
        private val AHEAD: Duration = Duration.ofMinutes(80)
    }
}

/**
 * Разбор ответа Bright Sky `/radar`: `{"radar":[{"timestamp":…, "precipitation_5":[[…],…]}, …]}`, числа в сотых долях
 * миллиметра за пять минут. Из квадрата берётся среднее по 3×3 клеткам вокруг его середины (место стоит в центре),
 * пересчитанное в мм/ч. Кадр с нечитаемым временем или без чисел пропускается; повреждённый ответ целиком даёт null.
 */
object RadarParser {

    /** Больше этого кадров не читается: по запросу их около двадцати. */
    internal const val MAX_FRAMES = 60

    /** Сотые доли мм за пять минут в мм/ч: ×12 пятиминуток в часе ÷ 100. */
    private const val TO_MM_PER_HOUR = 12.0 / 100.0

    fun parse(json: String): List<RadarFrame>? = if (JsonGuard.tooDeep(json)) null else try {
        val list = JSONObject(json).getJSONArray("radar")
        val frames = ArrayList<RadarFrame>()
        for (index in 0 until minOf(list.length(), MAX_FRAMES)) {
            val frame = list.optJSONObject(index)?.let { frameOrNull(it) } ?: continue
            frames += frame
        }
        frames
    } catch (e: JSONException) {
        null
    }

    private fun frameOrNull(entry: JSONObject): RadarFrame? {
        val time = timeOf(entry.opt("timestamp")) ?: return null
        val rows = entry.optJSONArray("precipitation_5") ?: return null
        val centreRow = rows.length() / 2
        val centreRowValues = rows.optJSONArray(centreRow) ?: return null
        val centreCol = centreRowValues.length() / 2
        var sum = 0.0
        var count = 0
        for (row in centreRow - 1..centreRow + 1) {
            val values = rows.optJSONArray(row) ?: continue
            for (col in centreCol - 1..centreCol + 1) {
                sum += valueAt(values, col) ?: continue
                count++
            }
        }
        if (count == 0) return null
        return RadarFrame(time, sum / count * TO_MM_PER_HOUR)
    }

    /** Число клетки; пустая клетка (нет данных) считается сухой. Вне массива `null`. */
    private fun valueAt(values: JSONArray, col: Int): Double? {
        if (col < 0 || col >= values.length()) return null
        val value = values.opt(col)
        return if (value is Number) value.toDouble().coerceAtLeast(0.0) else 0.0
    }

    private fun timeOf(value: Any?): Instant? = (value as? String)?.let {
        try {
            OffsetDateTime.parse(it).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
