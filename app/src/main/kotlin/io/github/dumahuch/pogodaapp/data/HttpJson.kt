package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Скачивание ответа одним запросом: общее для прогноза и качества воздуха (Open-Meteo) и пыльцы (DWD). */
internal object HttpJson {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Предел размера ответа: настоящие ответы (прогноз на 15 суток с подробностями) занимают сотни килобайт. */
    internal const val MAX_BODY_BYTES = 4 * 1024 * 1024

    /** Предел времени на весь ответ целиком: таймаут чтения не спасает от сервера, который отдаёт по байту. */
    private const val TOTAL_TIMEOUT_MS = 30_000L

    private const val CHUNK_BYTES = 8 * 1024

    fun get(url: String, userAgent: String, source: String = "Open-Meteo"): String {
        val address = URI(url)
        // Только https. Исключение одно: адрес самого устройства (127.0.0.1), на нём в тестах работает пробный сервер.
        if (address.scheme != "https" && !(address.scheme == "http" && address.host == LOOPBACK)) {
            throw ForecastException("$source: адрес без защищённого соединения отклонён")
        }
        val deadline = System.nanoTime() + TOTAL_TIMEOUT_MS * NANOS_PER_MILLI
        val connection = address.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (connection.contentLengthLong > MAX_BODY_BYTES) throw ForecastException("$source: ответ слишком большой")
            val body = stream?.use { String(readBounded(it, MAX_BODY_BYTES, deadline), Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) {
                throw ForecastException("$source ответил кодом $status: ${reasonOf(body)}")
            }
            return body
        } catch (e: IOException) {
            throw ForecastException("Нет связи с $source: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Читает поток целиком, но не больше [maxBytes] и не позже [deadlineNanos] (по `System.nanoTime()`).
     * Превышение даёт [ForecastException]: приложение не должно ни упасть от памяти, ни зависнуть на чужом сервере.
     */
    internal fun readBounded(stream: InputStream, maxBytes: Int, deadlineNanos: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            if (System.nanoTime() - deadlineNanos > 0) throw ForecastException("Ответ идёт слишком долго")
            val count = stream.read(chunk)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > maxBytes) throw ForecastException("Ответ слишком большой")
            out.write(chunk, 0, count)
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val LOOPBACK = "127.0.0.1"

    private fun reasonOf(body: String): String = try {
        JSONObject(body).optString("reason", body.take(120))
    } catch (e: JSONException) {
        body.take(120)
    }
}
