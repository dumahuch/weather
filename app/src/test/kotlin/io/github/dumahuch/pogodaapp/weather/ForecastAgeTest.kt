package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ForecastAgeTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** Ответ, полученный 12.11 в 20:15: вечер, 7°, в ряду часов температура идёт вниз к ночи и вверх к утру. */
    private fun forecast(withDays: Boolean = true): Forecast {
        val start = Instant.parse("2026-11-12T00:00:00Z")
        val hours = (0 until 72).map {
            HourPoint(start.plusSeconds(it * 3600L), temperature = it.toDouble(), condition = Condition.Cloudy, isDay = it % 24 in 7..16, precipitationMm = 0.0, cloudCover = 50)
        }
        val details = hours.map {
            HourDetails(it.time, humidity = 80, pressureHpa = null, visibilityM = null, windKmh = null, windDirection = null, gustKmh = null, uvIndex = null, feelsLikeC = it.temperature - 2.0)
        }
        val days = if (withDays) {
            (0 until 3).map {
                DayForecast(LocalDate.of(2026, 11, 12).plusDays(it.toLong()), Condition.Cloudy, max = 10.0 + it, min = 1.0 + it, precipitationMm = 0.0, precipitationProbability = null, windMaxKmh = 5.0, gustMaxKmh = 9.0, uvMax = 1.0, sunrise = null, sunset = null)
            }
        } else {
            emptyList()
        }
        return Forecast(
            zone = zone,
            current = CurrentWeather(Instant.parse("2026-11-12T20:15:00Z"), temperature = 7.0, feelsLike = 5.0, condition = Condition.Clear, isDay = false, cloudCover = 10, drizzle = true),
            today = DayRange(11.0, 6.0),
            hours = hours,
            days = days,
            hourDetails = details,
        )
    }

    @Test
    fun freshResponseIsUnchanged() {
        val f = forecast()
        assertSame(f, ForecastAge.asOf(f, Instant.parse("2026-11-12T21:15:00Z")))
    }

    @Test
    fun responseFromTheFutureCountsAsFresh() {
        val f = forecast()
        assertSame(f, ForecastAge.asOf(f, Instant.parse("2026-11-12T20:00:00Z")))
        assertEquals(Duration.ZERO, ForecastAge.of(f, Instant.parse("2026-11-12T20:00:00Z")))
    }

    @Test
    fun overnightUsesTheHourOfNowAndTodaysRange() {
        val adjusted = ForecastAge.asOf(forecast(), Instant.parse("2026-11-13T07:40:00Z"))!!
        // Час 07:00 13.11 это индекс 31 в ряду.
        assertEquals(31.0, adjusted.current.temperature, 0.0)
        assertEquals(29.0, adjusted.current.feelsLike, 0.0)
        assertEquals(Condition.Cloudy, adjusted.current.condition)
        assertTrue(adjusted.current.isDay)
        assertEquals(false, adjusted.current.drizzle)
        assertEquals(50, adjusted.current.cloudCover)
        assertEquals(DayRange(11.0, 2.0), adjusted.today)
    }

    @Test
    fun withoutFeelsLikeDetailsUsesTemperature() {
        val f = forecast().copy(hourDetails = emptyList())
        val adjusted = ForecastAge.asOf(f, Instant.parse("2026-11-13T07:40:00Z"))!!
        assertEquals(31.0, adjusted.current.feelsLike, 0.0)
    }

    @Test
    fun oldFormatWithoutDaysTakesRangeFromHours() {
        val adjusted = ForecastAge.asOf(forecast(withDays = false), Instant.parse("2026-11-13T07:40:00Z"))!!
        // Сутки 13.11 в ряду это индексы 24..47.
        assertEquals(DayRange(47.0, 24.0), adjusted.today)
    }

    @Test
    fun adjustedForecastStaysAdjustedWhenAdjustedAgain() {
        val now = Instant.parse("2026-11-13T07:40:00Z")
        val once = ForecastAge.asOf(forecast(), now)!!
        assertSame(once, ForecastAge.asOf(once, now))
    }

    @Test
    fun olderThanADayHasNoData() {
        assertNull(ForecastAge.asOf(forecast(), Instant.parse("2026-11-13T20:16:00Z")))
    }

    @Test
    fun exactlyADayStillShows() {
        val adjusted = ForecastAge.asOf(forecast(), Instant.parse("2026-11-13T20:15:00Z"))!!
        assertEquals(44.0, adjusted.current.temperature, 0.0)
    }

    @Test
    fun noHoursMeansNoData() {
        val f = forecast().copy(hours = emptyList(), hourDetails = emptyList())
        assertNull(ForecastAge.asOf(f, Instant.parse("2026-11-13T07:40:00Z")))
    }

    @Test
    fun todayWithoutDayOrHoursMeansNoData() {
        val f = forecast(withDays = false)
        // Ряд кончается 14.11 23:00; «сейчас» в пределах суток от ответа, но сутки 13.11 есть, а вот сутки без часов — нет.
        val short = f.copy(hours = f.hours.filter { it.time < Instant.parse("2026-11-13T00:00:00Z") })
        assertNull(ForecastAge.asOf(short, Instant.parse("2026-11-13T07:40:00Z")))
    }
}
