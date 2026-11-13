package io.github.dumahuch.pogodaapp.widget

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetModelTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-12T15:20:00Z")
    private val start = Instant.parse("2026-11-12T00:00:00Z")

    private fun day(offset: Long, max: Double, min: Double, mm: Double = 0.0) = DayForecast(
        date = LocalDate.of(2026, 11, 12).plusDays(offset),
        condition = Condition.Cloudy,
        max = max,
        min = min,
        precipitationMm = mm,
        precipitationProbability = null,
        windMaxKmh = 10.0,
        gustMaxKmh = 20.0,
        uvMax = 1.0,
        sunrise = Instant.parse("2026-11-12T06:41:00Z"),
        sunset = Instant.parse("2026-11-12T15:36:00Z"),
    )

    private fun forecast(days: List<DayForecast>, windKmh: Double? = 12.0): Forecast {
        val hours = (0 until 48).map {
            HourPoint(start.plusSeconds(it * 3600L), 6.0, Condition.Cloudy, isDay = it % 24 in 7..16, precipitationMm = 0.0)
        }
        val details = hours.map {
            HourDetails(it.time, humidity = 78, pressureHpa = 1012.0, visibilityM = null, windKmh = windKmh, windDirection = null, gustKmh = null, uvIndex = null)
        }
        return Forecast(
            zone = zone,
            current = CurrentWeather(Instant.parse("2026-11-12T15:15:00Z"), 7.0, 4.0, Condition.Partly, isDay = true),
            today = DayRange(9.0, 3.0),
            hours = hours,
            days = days,
            hourDetails = details,
        )
    }

    private val place = Place(52.37, 9.73, "Ганновер")

    @Test
    fun takesCurrentValuesAndTodayRange() {
        val model = WidgetModels.of(place, forecast(listOf(day(0, 9.0, 3.0))), now)
        assertEquals("Ганновер", model.placeName)
        assertEquals(7.0, model.temperature, 0.0)
        assertEquals(4.0, model.feelsLike, 0.0)
        assertEquals(Condition.Partly, model.condition)
        assertEquals(9.0, model.high, 0.0)
        assertEquals(3.0, model.low, 0.0)
    }

    @Test
    fun hoursStartAtTheCurrentHourAndAreSix() {
        val model = WidgetModels.of(place, forecast(listOf(day(0, 9.0, 3.0))), now)
        assertEquals(WidgetModels.HOURS, model.hours.size)
        assertEquals(Instant.parse("2026-11-12T15:00:00Z"), model.hours.first().time)
        assertEquals(7.0, model.hours.first().temperature, 0.0)
    }

    @Test
    fun daysAreTheFollowingOnesWithoutToday() {
        val days = (0L..7L).map { day(it, 9.0 - it, 3.0 - it) }
        val model = WidgetModels.of(place, forecast(days), now)
        assertEquals(WidgetModels.DAYS, model.days.size)
        assertEquals(LocalDate.of(2026, 11, 13), model.days.first().date)
        assertEquals(model.days.size, model.ranges.size)
    }

    @Test
    fun metricsComeFromTheCurrentHourAndToday() {
        val model = WidgetModels.of(place, forecast(listOf(day(0, 9.0, 3.0, mm = 0.4))), now)
        assertEquals(12.0, model.windKmh!!, 0.0)
        assertEquals(78, model.humidity)
        assertEquals(0.4, model.precipitationMm!!, 0.0)
    }

    @Test
    fun forecastWithoutDaysStillGivesAModel() {
        val model = WidgetModels.of(place, forecast(emptyList(), windKmh = null), now)
        assertTrue(model.days.isEmpty())
        assertNull(model.precipitationMm)
        assertNull(model.windKmh)
    }

    @Test
    fun strongWindMakesTheIconWindy() {
        assertTrue(WidgetModels.of(place, forecast(emptyList(), windKmh = 60.0), now).windy)
        assertTrue(!WidgetModels.of(place, forecast(emptyList(), windKmh = 12.0), now).windy)
    }
}
