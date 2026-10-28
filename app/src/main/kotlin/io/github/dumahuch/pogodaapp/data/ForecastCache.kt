package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.Place
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Последний удавшийся прогноз на диске: чтобы при запуске сразу было что показать
 * и чтобы без сети остались данные («нет сети · данные 3 ч назад»).
 * Хранится сырой ответ Open-Meteo, разбор тот же, что у свежего ответа.
 */
class ForecastCache(private val file: File) {

    data class Entry(val savedAt: Instant, val place: Place, val raw: String)

    /** Возвращает null, если файла нет или он испорчен: кэш никогда не должен ронять приложение. */
    fun load(): Entry? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            if (json.getInt("v") != VERSION) return null
            Entry(
                savedAt = Instant.ofEpochMilli(json.getLong("savedAtMs")),
                place = Place(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lon"),
                    name = if (json.isNull("name")) null else json.getString("name"),
                ),
                raw = json.getString("raw"),
            )
        } catch (e: JSONException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Запись через временный файл, чтобы обрыв посреди записи не оставил половину файла. */
    fun save(entry: Entry) {
        val json = JSONObject()
            .put("v", VERSION)
            .put("savedAtMs", entry.savedAt.toEpochMilli())
            .put("lat", entry.place.latitude)
            .put("lon", entry.place.longitude)
            .put("name", entry.place.name ?: JSONObject.NULL)
            .put("raw", entry.raw)
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.writeText(json.toString(), Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temp.delete()
        }
    }

    private companion object {
        const val VERSION = 1
    }
}
