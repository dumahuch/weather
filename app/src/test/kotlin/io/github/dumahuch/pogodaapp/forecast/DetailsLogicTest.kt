package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class DetailsLogicTest {

    private val start = Instant.parse("2026-10-29T00:00:00Z")

    private fun hours(pressures: List<Double?>) = pressures.mapIndexed { i, p ->
        HourDetails(start.plusSeconds(i * 3600L), 70, p, 20000.0, 10.0, 200, 20.0, 1.0)
    }

    @Test
    fun trendBoundaries() {
        assertEquals(PressureTrend.FallingFast, PressureTrend.ofChange(-3.0))
        assertEquals(PressureTrend.FallingFast, PressureTrend.ofChange(-6.5))
        assertEquals(PressureTrend.Falling, PressureTrend.ofChange(-2.9))
        assertEquals(PressureTrend.Falling, PressureTrend.ofChange(-1.1))
        assertEquals(PressureTrend.Steady, PressureTrend.ofChange(-1.0))
        assertEquals(PressureTrend.Steady, PressureTrend.ofChange(0.0))
        assertEquals(PressureTrend.Steady, PressureTrend.ofChange(1.0))
        assertEquals(PressureTrend.Rising, PressureTrend.ofChange(1.1))
        assertEquals(PressureTrend.Rising, PressureTrend.ofChange(4.0))
    }

    @Test
    fun trendLooksThreeHoursAhead() {
        // давление падает на 1,5 гПа в час: через три часа минус 4,5
        val details = hours(List(24) { 1020.0 - it * 1.5 })
        val now = start.plusSeconds(10 * 3600L + 1200)
        assertEquals(PressureTrend.FallingFast, PressureOutlook.trend(details, now))
    }

    @Test
    fun trendIsEmptyWithoutDataThreeHoursAhead() {
        val details = hours(List(12) { 1015.0 })
        assertNull(PressureOutlook.trend(details, start.plusSeconds(10 * 3600L)))
        assertNull(PressureOutlook.trend(emptyList(), start))
        assertNull(PressureOutlook.trend(hours(listOf(null, null, null, null, null)), start))
    }

    @Test
    fun seriesTakesSixHoursBeforeAndTwelveAfter() {
        val details = hours(List(48) { 1000.0 + it })
        val now = start.plusSeconds(20 * 3600L)
        val series = PressureOutlook.series(details, now)
        assertEquals(19, series.size)
        assertEquals(1014.0, series.first(), 0.0)
        assertEquals(1032.0, series.last(), 0.0)
    }

    @Test
    fun seriesIsShortenedAtTheEdgesOfTheData() {
        val details = hours(List(48) { 1000.0 + it })
        // в 02:30 до «сейчас» есть только два часа
        assertEquals(2 + 1 + 12, PressureOutlook.series(details, start.plusSeconds(2 * 3600L + 1800)).size)
        // у конца данных после «сейчас» остаётся мало
        assertEquals(6 + 1 + 2, PressureOutlook.series(details, start.plusSeconds(45 * 3600L)).size)
        assertTrue(PressureOutlook.series(emptyList(), start).isEmpty())
    }

    @Test
    fun seriesSkipsHoursWithoutPressure() {
        val details = hours(listOf(1000.0, null, 1002.0, 1003.0))
        assertEquals(listOf(1000.0, 1002.0, 1003.0), PressureOutlook.series(details, start.plusSeconds(3600)))
    }

    @Test
    fun compassPointsCoverTheCircle() {
        assertEquals(CompassPoint.North, CompassPoint.of(0))
        assertEquals(CompassPoint.North, CompassPoint.of(22))
        assertEquals(CompassPoint.NorthEast, CompassPoint.of(23))
        assertEquals(CompassPoint.East, CompassPoint.of(90))
        assertEquals(CompassPoint.South, CompassPoint.of(180))
        assertEquals(CompassPoint.SouthWest, CompassPoint.of(225))
        assertEquals(CompassPoint.West, CompassPoint.of(270))
        assertEquals(CompassPoint.NorthWest, CompassPoint.of(315))
        assertEquals(CompassPoint.North, CompassPoint.of(338))
        assertEquals(CompassPoint.North, CompassPoint.of(360))
        assertEquals(CompassPoint.South, CompassPoint.of(-180))
    }

    @Test
    fun visibilityIsShownInKilometres() {
        val de = Locale.GERMANY
        assertEquals("24", visibilityText(24140.0, de))
        assertEquals("10", visibilityText(9960.0, de))
        assertEquals("8", visibilityText(8000.0, de))
        assertEquals("3,5", visibilityText(3500.0, de))
        assertEquals("0,4", visibilityText(400.0, de))
        assertEquals("0,2", visibilityText(200.0, de))
        assertEquals("0", visibilityText(20.0, de))
        assertEquals("3.5", visibilityText(3500.0, Locale.US))
    }

    private fun day(date: String, rise: String?, set: String?) = DayForecast(
        date = LocalDate.parse(date),
        condition = Condition.Clear,
        max = 10.0,
        min = 2.0,
        precipitationMm = 0.0,
        precipitationProbability = null,
        windMaxKmh = 10.0,
        gustMaxKmh = 20.0,
        uvMax = 1.0,
        sunrise = rise?.let { Instant.parse(it) },
        sunset = set?.let { Instant.parse(it) },
    )

    private val today = day("2026-10-29", "2026-10-29T06:20:00Z", "2026-10-29T15:40:00Z")
    private val tomorrow = day("2026-10-30", "2026-10-30T06:22:00Z", "2026-10-30T15:38:00Z")

    @Test
    fun sunIsHalfwayAtSolarNoon() {
        val position = arcPosition(Instant.parse("2026-10-29T11:00:00Z"), today, tomorrow)!!
        assertTrue(position.daytime)
        assertEquals(0.5, position.fraction, 1e-9)
    }

    @Test
    fun nightAfterSunsetRunsToTomorrowsSunrise() {
        // ночь от 15:40 до 06:22 следующего дня: 14 ч 42 мин; прошло 3 ч 40 мин
        val position = arcPosition(Instant.parse("2026-10-29T19:20:00Z"), today, tomorrow)!!
        assertEquals(false, position.daytime)
        assertEquals(220.0 / 882.0, position.fraction, 1e-9)
    }

    @Test
    fun nightAfterSunsetWithoutTomorrowUsesTodaysSunrisePlusADay() {
        val position = arcPosition(Instant.parse("2026-10-29T19:20:00Z"), today, null)!!
        // до 06:20 завтра: ночь 14 ч 40 мин = 880 мин, прошло 220
        assertEquals(220.0 / 880.0, position.fraction, 1e-9)
    }

    @Test
    fun nightBeforeSunriseRunsFromYesterdaysSunset() {
        // вчерашний закат принимается за 15:40 − 24 ч, то есть вчера 15:40; до 06:20 сегодня это 14 ч 40 мин (880 мин);
        // в 03:00 прошло 11 ч 20 мин (680 мин)
        val position = arcPosition(Instant.parse("2026-10-29T03:00:00Z"), today, tomorrow)!!
        assertEquals(false, position.daytime)
        assertEquals(680.0 / 880.0, position.fraction, 1e-9)
    }

    @Test
    fun arcIsMissingWithoutSunriseOrSunset() {
        assertNull(arcPosition(Instant.parse("2026-10-29T11:00:00Z"), day("2026-10-29", null, null), null))
        assertNull(arcPosition(Instant.parse("2026-10-29T11:00:00Z"), day("2026-10-29", "2026-10-29T06:20:00Z", null), null))
        // закат раньше восхода: данные не годятся
        assertNull(
            arcPosition(
                Instant.parse("2026-10-29T11:00:00Z"),
                day("2026-10-29", "2026-10-29T15:40:00Z", "2026-10-29T06:20:00Z"),
                null,
            ),
        )
        assertNotNull(arcPosition(Instant.parse("2026-10-29T11:00:00Z"), today, null))
    }
}
