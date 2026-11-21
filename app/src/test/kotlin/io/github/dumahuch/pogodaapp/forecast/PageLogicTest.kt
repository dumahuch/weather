package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.AirHour
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLogicTest {

    private val start = Instant.parse("2026-11-21T00:00:00Z")
    private fun at(h: Int) = start.plusSeconds(h * 3600L)
    private fun pressure(vararg hpa: Double) = hpa.mapIndexed { h, p -> HourDetails(at(h), null, p, null, null, null, null, null) }
    private fun uv(vararg values: Double) = values.mapIndexed { h, u -> HourDetails(at(h), null, null, null, null, null, null, u) }

    @Test
    fun pressureTrendOverTheDay() {
        assertEquals(PressureTrend.Steady, PressureDay.trend(pressure(1013.0, 1014.0, 1014.9)))
        assertEquals(PressureTrend.Rising, PressureDay.trend(pressure(1013.0, 1015.0)))
        assertEquals(PressureTrend.Falling, PressureDay.trend(pressure(1013.0, 1011.0)))
        assertEquals(PressureTrend.FallingFast, PressureDay.trend(pressure(1013.0, 1005.0)))
        assertNull(PressureDay.trend(pressure(1013.0)))
    }

    @Test
    fun pressureExtremesAndNoon() {
        val hours = pressure(*DoubleArray(24) { 1010.0 + it })
        assertEquals(TimedValue(1033.0, at(23)), PressureDay.highest(hours))
        assertEquals(TimedValue(1010.0, at(0)), PressureDay.lowest(hours))
        assertEquals(1022.0, PressureDay.noon(hours, ZoneOffset.UTC)!!, 0.0)
    }

    @Test
    fun uvProtectionCoversTheHoursFromThreeByTheShownNumber() {
        val hours = uv(0.0, 1.0, 2.4, 2.6, 5.0, 3.1, 2.2, 0.0)
        // 2,6 показывается как 3: с этого часа защита нужна; 3,1 последний такой час, защита до его конца.
        assertEquals(at(3) to at(6), UvDay.protection(hours))
        assertNull(UvDay.protection(uv(0.0, 1.0, 2.4)))
        assertEquals(TimedValue(5.0, at(4)), UvDay.peak(hours))
    }

    @Test
    fun dailyPollutantIsTheMeanAndOzoneTheBestEightHours() {
        val date = LocalDate.of(2026, 11, 21)
        val hours = (0 until 24).map { h -> AirHour(at(h), 20 + h, pm25 = 10.0 + h % 2, o3 = if (h in 10..17) 90.0 else 30.0) }
        assertEquals(10.5, AirDay.daily(hours, date, ZoneOffset.UTC, Pollutant.Pm25)!!, 1e-9)
        assertEquals(90.0, AirDay.daily(hours, date, ZoneOffset.UTC, Pollutant.O3)!!, 1e-9)
        assertNull(AirDay.daily(hours.take(3), date, ZoneOffset.UTC, Pollutant.Pm25))
        assertEquals(43, AirDay.worst(hours, date, ZoneOffset.UTC))
        assertEquals(listOf(date), AirDay.dates(hours, ZoneOffset.UTC, date))
        assertEquals(Pollutant.O3, AirDay.main(mapOf(Pollutant.Pm25 to 0.7, Pollutant.O3 to 0.9)))
    }
}
