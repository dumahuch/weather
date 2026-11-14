package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SummaryNoticeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 14)
    private val tomorrow = today.plusDays(1)
    private val now = ZonedDateTime.of(2026, 11, 14, 20, 0, 0, 0, zone).toInstant()

    private fun day(date: LocalDate, max: Double, min: Double, condition: Condition = Condition.Cloudy) =
        DayForecast(date, condition, max, min, 0.0, null, 10.0, 20.0, 1.0, null, null)

    /** Часы завтрашних суток: [condition] весь день, осадки [rain] (час → мм). */
    private fun hours(condition: Condition, rain: Map<Int, Double> = emptyMap()): List<HourPoint> =
        (0..23).map { h ->
            HourPoint(ZonedDateTime.of(tomorrow.atStartOfDay(), zone).withHour(h).toInstant(), 8.0, condition, h in 7..17, rain[h] ?: 0.0)
        }

    private fun forecast(todayMax: Double, tomorrowMax: Double, hours: List<HourPoint>) = Forecast(
        zone,
        CurrentWeather(now, 7.0, 5.0, Condition.Cloudy, false),
        DayRange(todayMax, 2.0),
        hours,
        listOf(day(today, todayMax, 2.0), day(tomorrow, tomorrowMax, 3.0)),
    )

    @Test
    fun sameTemperatureIsSaid() {
        val outlook = SummaryNotice.outlook(forecast(10.0, 10.4, hours(Condition.Cloudy)), now)!!
        assertEquals(Versus.Same, outlook.versus)
        assertEquals(Condition.Cloudy, outlook.dominant)
        assertNull(outlook.rain)
        assertEquals(10.4, outlook.high, 0.0)
        assertEquals(3.0, outlook.low, 0.0)
    }

    @Test
    fun warmerAndColderCarryTheDifferenceInDegrees() {
        val warmer = SummaryNotice.outlook(forecast(10.0, 14.2, hours(Condition.Clear)), now)!!
        assertEquals(Versus.Warmer, warmer.versus)
        assertEquals(4, warmer.versusDegrees)
        val colder = SummaryNotice.outlook(forecast(10.0, 6.0, hours(Condition.Clear)), now)!!
        assertEquals(Versus.Colder, colder.versus)
        assertEquals(4, colder.versusDegrees)
    }

    @Test
    fun mixedWeatherHasNoDominantCondition() {
        val mixed = hours(Condition.Cloudy).map { if (it.time.atZone(zone).hour % 2 == 0) it.copy(condition = Condition.Clear) else it }
        assertNull(SummaryNotice.outlook(forecast(10.0, 10.0, mixed), now)!!.dominant)
    }

    @Test
    fun rainWindowRunsFromFirstToLastWetHour() {
        val rain = mapOf(14 to 0.6, 15 to 1.2, 17 to 0.4, 3 to 0.05)
        val outlook = SummaryNotice.outlook(forecast(10.0, 10.0, hours(Condition.Rain, rain)), now)!!
        val window = outlook.rain!!
        assertEquals(PrecipitationKind.Rain, window.kind)
        assertEquals(14, window.from.atZone(zone).hour)
        assertEquals(18, window.to.atZone(zone).hour)
        assertEquals(2.25, window.totalMm, 0.001)
    }

    @Test
    fun traceAmountsAreNotRain() {
        val outlook = SummaryNotice.outlook(forecast(10.0, 10.0, hours(Condition.Cloudy, mapOf(10 to 0.1, 11 to 0.1))), now)!!
        assertNull(outlook.rain)
    }

    @Test
    fun snowIsNamedBySnowKind() {
        val outlook = SummaryNotice.outlook(forecast(0.0, 0.0, hours(Condition.Snow, mapOf(9 to 0.8, 10 to 0.8))), now)!!
        assertEquals(PrecipitationKind.Snow, outlook.rain!!.kind)
    }

    @Test
    fun noTomorrowInOldForecastMeansNoSummary() {
        val old = forecast(10.0, 10.0, hours(Condition.Cloudy)).copy(days = emptyList())
        assertNull(SummaryNotice.outlook(old, now))
        assertNotNull(SummaryNotice.outlook(forecast(10.0, 10.0, hours(Condition.Cloudy)), now))
    }

    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 14, h, m, 0, 0, zone)

    @Test
    fun dueFromTheChosenTimeUntilThreeHoursLater() {
        val eight = 20 * 60
        assertFalse(SummaryNotice.isDue(at(19, 59), eight, null))
        assertTrue(SummaryNotice.isDue(at(20, 0), eight, null))
        assertTrue(SummaryNotice.isDue(at(22, 59), eight, null))
        assertFalse(SummaryNotice.isDue(at(23, 1), eight, null))
    }

    @Test
    fun notDueTwiceInADay() {
        assertFalse(SummaryNotice.isDue(at(20, 30), 20 * 60, today))
        assertTrue(SummaryNotice.isDue(at(20, 30), 20 * 60, today.minusDays(1)))
    }

    @Test
    fun nextRunIsTodayThenNowThenTomorrow() {
        val eight = 20 * 60
        assertEquals(Duration.ofHours(2), SummaryNotice.delayUntilNext(at(18, 0), eight, null))
        assertEquals(Duration.ZERO, SummaryNotice.delayUntilNext(at(20, 40), eight, null))
        assertEquals(Duration.ofHours(23), SummaryNotice.delayUntilNext(at(21, 0), eight, today))
        assertEquals(Duration.ofHours(20) + Duration.ofMinutes(30), SummaryNotice.delayUntilNext(at(23, 30), eight, null))
    }
}
