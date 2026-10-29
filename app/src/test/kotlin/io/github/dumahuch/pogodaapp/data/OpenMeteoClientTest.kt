package io.github.dumahuch.pogodaapp.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale

class OpenMeteoClientTest {

    private lateinit var server: HttpServer
    @Volatile private var status = 200
    @Volatile private var body = "{}"
    @Volatile private var lastQuery: String? = null
    @Volatile private var lastUserAgent: String? = null
    private var stopped = false

    private val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}/v1/forecast"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/v1/forecast") { exchange: HttpExchange ->
            lastQuery = exchange.requestURI.rawQuery
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stopServer() {
        stopOnce()
    }

    private fun stopOnce() {
        if (!stopped) {
            stopped = true
            server.stop(0)
        }
    }

    @Test
    fun urlHasRoundedCoordinatesAndAllRequestedFields() {
        val url = OpenMeteoClient().buildUrl(52.3651, 9.7449)
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=52.37&longitude=9.74"))
        for (part in listOf(
            "current=temperature_2m,apparent_temperature,weather_code,is_day",
            "hourly=temperature_2m,weather_code,precipitation,is_day",
            "daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunrise,sunset",
            "wind_speed_unit=kmh",
            "timezone=auto",
            "timeformat=unixtime",
            "forecast_days=15",
        )) {
            assertTrue("в адресе нет $part: $url", url.contains(part))
        }
    }

    @Test
    fun coordinatesUseDotEvenInGermanLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val url = OpenMeteoClient().buildUrl(-33.8688, 151.2093)
            assertTrue(url, url.contains("latitude=-33.87&longitude=151.21"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun returnsTheBodyAndSendsTheUserAgent() = runTest {
        body = "{\"ok\":\"да\"}"
        val raw = OpenMeteoClient(baseUrl, "TestAgent/1").fetchRaw(52.3651, 9.7449)
        assertEquals("{\"ok\":\"да\"}", raw)
        assertEquals("TestAgent/1", lastUserAgent)
        assertTrue(lastQuery.orEmpty(), lastQuery.orEmpty().startsWith("latitude=52.37&longitude=9.74&"))
    }

    @Test
    fun errorStatusBecomesForecastExceptionWithTheServiceReason() = runTest {
        status = 400
        body = "{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90°. Given: 95.0.\"}"
        val error = runCatching { OpenMeteoClient(baseUrl).fetchRaw(95.0, 0.0) }.exceptionOrNull()
        assertTrue(error.toString(), error is ForecastException)
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("400"))
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("Latitude must be in range"))
    }

    @Test
    fun serverErrorWithAPlainBodyBecomesForecastException() = runTest {
        status = 503
        body = "Service Unavailable"
        val error = runCatching { OpenMeteoClient(baseUrl).fetchRaw(1.0, 1.0) }.exceptionOrNull()
        assertTrue(error.toString(), error is ForecastException)
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("503"))
    }

    @Test
    fun unreachableServerBecomesForecastException() = runTest {
        val url = baseUrl
        stopOnce()
        val error = runCatching { OpenMeteoClient(url).fetchRaw(1.0, 1.0) }.exceptionOrNull()
        assertTrue(error.toString(), error is ForecastException)
    }

    @Test
    fun parserAcceptsWhatTheFixtureLooksLike() = runTest {
        val url = checkNotNull(javaClass.classLoader?.getResource("open-meteo/hannover.json"))
        body = url.readText(Charsets.UTF_8)
        val raw = OpenMeteoClient(baseUrl).fetchRaw(52.37, 9.74)
        assertEquals(48, OpenMeteoParser.parse(raw).hours.size)
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse("{") }
    }
}
