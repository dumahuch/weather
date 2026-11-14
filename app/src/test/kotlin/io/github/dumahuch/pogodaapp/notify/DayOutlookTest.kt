package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayOutlookTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 11, 14)

    private fun forecastAt(hour: Int): Pair<Forecast, java.time.Instant> {
        val now = ZonedDateTime.of(day.atStartOfDay(), zone).withHour(hour).withMinute(10).toInstant()
        val hours = (0..23).map { h ->
            HourPoint(ZonedDateTime.of(day.atStartOfDay(), zone).withHour(h).toInstant(), h.toDouble(), Condition.Cloudy, true, 0.0)
        }
        return Forecast(zone, CurrentWeather(now, 7.0, 5.0, Condition.Cloudy, true), DayRange(12.0, 2.0), hours) to now
    }

    @Test
    fun earlyMorningShowsAllThreeParts() {
        val (forecast, now) = forecastAt(7)
        val parts = DayOutlook.remaining(forecast, now)
        assertEquals(listOf(DayStop.Morning, DayStop.Day, DayStop.Evening), parts.map { it.stop })
        assertEquals(listOf(9.0, 14.0, 20.0), parts.map { it.temperature })
    }

    @Test
    fun passedPartsDisappear() {
        val (forecast, now) = forecastAt(15)
        assertEquals(listOf(DayStop.Evening), DayOutlook.remaining(forecast, now).map { it.stop })
    }

    @Test
    fun partStartingThisHourIsAlreadyPassed() {
        val (forecast, now) = forecastAt(14)
        assertEquals(listOf(DayStop.Evening), DayOutlook.remaining(forecast, now).map { it.stop })
    }

    @Test
    fun lateEveningLeavesNothing() {
        val (forecast, now) = forecastAt(21)
        assertTrue(DayOutlook.remaining(forecast, now).isEmpty())
    }
}
