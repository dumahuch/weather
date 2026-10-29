package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Скачивание ответа одним запросом: общее для прогноза и качества воздуха (Open-Meteo) и пыльцы (DWD). */
internal object HttpJson {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    fun get(url: String, userAgent: String, source: String = "Open-Meteo"): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
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

    private fun reasonOf(body: String): String = try {
        JSONObject(body).optString("reason", body.take(120))
    } catch (e: JSONException) {
        body.take(120)
    }
}
