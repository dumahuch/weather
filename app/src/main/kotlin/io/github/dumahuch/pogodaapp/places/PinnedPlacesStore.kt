package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.data.ForecastCache
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Где хранится список закреплённых мест. Реализация на файле ниже, в тестах хватает памяти. */
interface PinnedPlacesStorage {
    /** Сохранённый список; пустой, если ничего нет или хранилище испорчено (ошибка хранилища не должна ронять приложение). */
    fun load(): List<PinnedPlace>

    fun save(places: List<PinnedPlace>)
}

/** Список в одном JSON-файле. Запись через временный файл, как у [ForecastCache]. */
class FilePinnedPlacesStorage(private val file: File) : PinnedPlacesStorage {

    override fun load(): List<PinnedPlace> {
        if (!file.isFile) return emptyList()
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            if (json.getInt("v") != VERSION) return emptyList()
            val array = json.getJSONArray("places")
            val list = ArrayList<PinnedPlace>(array.length())
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                list += PinnedPlace(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    admin1 = item.optString("admin1").ifEmpty { null },
                    country = item.optString("country").ifEmpty { null },
                    latitude = item.getDouble("lat"),
                    longitude = item.getDouble("lon"),
                )
            }
            list
        } catch (e: JSONException) {
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    override fun save(places: List<PinnedPlace>) {
        val array = JSONArray()
        for (place in places) {
            array.put(
                JSONObject()
                    .put("id", place.id)
                    .put("name", place.name)
                    .put("admin1", place.admin1 ?: "")
                    .put("country", place.country ?: "")
                    .put("lat", place.latitude)
                    .put("lon", place.longitude),
            )
        }
        val json = JSONObject().put("v", VERSION).put("places", array)
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

/** Хранилище, которое ничего не пишет на диск: для тестов и для сборки без файловой папки. */
class MemoryPinnedPlacesStorage(initial: List<PinnedPlace> = emptyList()) : PinnedPlacesStorage {
    private var saved: List<PinnedPlace> = initial

    override fun load(): List<PinnedPlace> = saved

    override fun save(places: List<PinnedPlace>) {
        saved = places
    }
}

/** Сохранённые прогнозы закреплённых мест: у каждого места свой файл рядом с прогнозом текущего. */
class PinnedForecastCaches(private val directory: File) {

    fun cacheFor(id: String): ForecastCache = ForecastCache(fileOf(id))

    /** Убирает файл прогноза места, которое открепили. */
    fun delete(id: String) {
        fileOf(id).delete()
    }

    private fun fileOf(id: String) = File(directory, "forecast_cache_$id.json")
}
