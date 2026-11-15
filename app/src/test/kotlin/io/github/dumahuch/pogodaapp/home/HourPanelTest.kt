package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HourPanelTest {
    private val time = Instant.parse("2026-11-15T15:00:00Z")

    private fun details(
        humidity: Int? = 78,
        pressure: Double? = 1012.4,
        wind: Double? = 22.3,
        direction: Int? = 180,
        gust: Double? = 41.0,
        feels: Double? = 9.6,
        dew: Double? = 7.1,
    ) = HourDetails(time, humidity, pressure, null, wind, direction, gust, null, feels, dew)

    @Test
    fun valuesAreRoundedAsShown() {
        val panel = hourPanelOf(details())!!
        assertEquals(1012, panel.pressureHpa)
        assertEquals(78, panel.humidityPercent)
        assertEquals(22, panel.windKmh)
        assertEquals(4, panel.windStep)
        assertEquals(CompassPoint.South, panel.windFrom)
        assertEquals(41, panel.gustKmh)
        assertEquals(9.6, panel.feelsLikeC!!, 0.0)
        assertEquals(7.1, panel.dewPointC!!, 0.0)
    }

    @Test
    fun calmWindHasNoDirection() {
        val panel = hourPanelOf(details(wind = 0.4, gust = 3.0))!!
        assertEquals(0, panel.windStep)
        assertNull(panel.windFrom)
        assertNull(panel.gustKmh)
    }

    @Test
    fun gustsAreShownOnlyWhenWorthMentioning() {
        assertNull(hourPanelOf(details(wind = 10.0, gust = 15.0))!!.gustKmh)
        assertNull(hourPanelOf(details(wind = 30.0, gust = 32.0))!!.gustKmh)
        assertEquals(45, hourPanelOf(details(wind = 22.0, gust = 45.0))!!.gustKmh)
    }

    @Test
    fun missingValuesLeaveRowsEmptyAndNothingAtAllGivesNoPanel() {
        val partial = hourPanelOf(details(feels = null, dew = null, pressure = null))!!
        assertNull(partial.feelsLikeC)
        assertNull(partial.pressureHpa)
        assertNotNull(partial.windKmh)

        assertNull(hourPanelOf(null))
        val empty = details(
            humidity = null, pressure = null, wind = null, direction = null, gust = null, feels = null, dew = null,
        )
        assertNull(hourPanelOf(empty))
    }
}
