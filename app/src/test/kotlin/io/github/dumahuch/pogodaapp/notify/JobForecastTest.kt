package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class JobForecastTest {
    private class Counting(private val fail: Boolean = false) : ForecastSource {
        var calls = 0

        override suspend fun fetchRaw(latitude: Double, longitude: Double): String {
            calls++
            if (fail) throw ForecastException("нет сети")
            return "raw-$latitude-$longitude"
        }
    }

    @Test
    fun `три потребителя одного места делают один запрос`() = runBlocking {
        val source = Counting()
        val forecast = JobForecast(source)
        val a = forecast.raw(52.37, 9.73)
        val b = forecast.raw(52.37, 9.73)
        val c = forecast.raw(52.37, 9.73)
        assertEquals(1, source.calls)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `другое место запрашивается заново`() = runBlocking {
        val source = Counting()
        val forecast = JobForecast(source)
        forecast.raw(52.37, 9.73)
        forecast.raw(50.11, 8.68)
        assertEquals(2, source.calls)
    }

    @Test
    fun `сбой запоминается и повторных попыток в том же запуске нет`() = runBlocking {
        val source = Counting(fail = true)
        val forecast = JobForecast(source)
        repeat(3) {
            try {
                forecast.raw(52.37, 9.73)
                fail("ожидали ForecastException")
            } catch (e: ForecastException) {
                // так и должно быть
            }
        }
        assertEquals(1, source.calls)
    }
}
