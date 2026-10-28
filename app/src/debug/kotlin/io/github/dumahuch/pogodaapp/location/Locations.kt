package io.github.dumahuch.pogodaapp.location

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/**
 * Источник места в проверочной сборке. Если в папке приложения лежит файл [OVERRIDE_FILE]
 * со строкой «широта,долгота», место берётся оттуда: так проверка на эмуляторе не зависит
 * от службы местоположения эмулятора, которой нужны сеть и спутники. Файла нет, значит работает
 * настоящее местоположение. Файл [DELAY_FILE] с числом миллисекунд замедляет определение свежего места:
 * так проверка на эмуляторе успевает увидеть индикатор обновления протягиванием. В рабочей сборке этого кода нет.
 */
object Locations {
    const val OVERRIDE_FILE = "location_override.txt"
    const val DELAY_FILE = "location_delay_ms.txt"

    fun create(context: Context): LocationSource {
        val real = AndroidLocationSource(context)
        val file = File(context.filesDir, OVERRIDE_FILE)
        val delayFile = File(context.filesDir, DELAY_FILE)
        return object : LocationSource {
            override fun lastKnown(): GeoPoint? = readOverride(file) ?: real.lastKnown()

            override suspend fun fresh(): GeoPoint? {
                readDelay(delayFile)?.let { delay(it) }
                return readOverride(file) ?: real.fresh()
            }
        }
    }

    /** Пауза в миллисекундах из файла [DELAY_FILE]; нет файла или в нём не положительное число, значит паузы нет. */
    internal fun readDelay(file: File): Long? {
        if (!file.isFile) return null
        val text = try {
            file.readText(Charsets.UTF_8).trim()
        } catch (e: IOException) {
            return null
        }
        return text.toLongOrNull()?.takeIf { it > 0 }
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
