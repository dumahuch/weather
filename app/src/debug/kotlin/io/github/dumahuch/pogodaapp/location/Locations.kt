package io.github.dumahuch.pogodaapp.location

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Источник места в проверочной сборке. Если в папке приложения лежит файл [OVERRIDE_FILE]
 * со строкой «широта,долгота», место берётся оттуда: так проверка на эмуляторе не зависит
 * от службы местоположения эмулятора, которой нужны сеть и спутники. Файла нет, значит работает
 * настоящее местоположение. В рабочей сборке этого кода нет.
 */
object Locations {
    const val OVERRIDE_FILE = "location_override.txt"

    fun create(context: Context): LocationSource {
        val real = AndroidLocationSource(context)
        val file = File(context.filesDir, OVERRIDE_FILE)
        return object : LocationSource {
            override suspend fun currentLocation(): GeoPoint? = readOverride(file) ?: real.currentLocation()
        }
    }

    internal fun readOverride(file: File): GeoPoint? {
        if (!file.isFile) return null
        val parts = try {
            file.readText(Charsets.UTF_8).trim().split(',')
        } catch (e: IOException) {
            return null
        }
        if (parts.size != 2) return null
        val latitude = parts[0].trim().toDoubleOrNull() ?: return null
        val longitude = parts[1].trim().toDoubleOrNull() ?: return null
        return GeoPoint(latitude, longitude)
    }
}
