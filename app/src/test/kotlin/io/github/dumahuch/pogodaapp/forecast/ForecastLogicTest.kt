package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class ForecastLogicTest {

    private val base = Instant.parse("2026-10-28T10:00:00Z")

    private fun day(max: Double, min: Double, sunrise: Instant? = null, sunset: Instant? = null) = DayForecast(
        date = LocalDate.of(2026, 10, 28),
        condition = Condition.Clear,
        max = max,
        min = min,
        precipitationMm = 0.0,
        precipitationProbability = null,
        windMaxKmh = 0.0,
        gustMaxKmh = 0.0,
        uvMax = 0.0,
        sunrise = sunrise,
        sunset = sunset,
    )

    private fun hours(vararg mm: Double, condition: Condition = Condition.Rain) = mm.mapIndexed { i, value ->
        HourPoint(base.plusSeconds(i * 3600L), 10.0, condition, true, value)
    }

    @Test
    fun scaleIsAtLeastFromMinusTwoToThirteen() {
        val spans = TemperatureRange.layout(listOf(day(13.0, -2.0)))
        assertEquals(0f, spans.single().start, 0.001f)
        assertEquals(1f, spans.single().width, 0.001f)
    }

    @Test
    fun scaleGrowsForColdAndWarmDays() {
        val spans = TemperatureRange.layout(listOf(day(30.0, 20.0), day(0.0, -12.0)))
        // Шкала от −12 до 30, 42 градуса.
        assertEquals(10f / 42f, spans[0].width, 0.001f)
        assertEquals(32f / 42f, spans[0].start, 0.001f)
        assertEquals(0f, spans[1].start, 0.001f)
    }

    @Test
    fun barNeverNarrowerThanSixPercentAndStaysInside() {
        val spans = TemperatureRange.layout(listOf(day(13.0, 13.0)))
        assertEquals(0.06f, spans.single().width, 0.0001f)
        assertTrue(spans.single().start + spans.single().width <= 1.0001f)
    }

    @Test
    fun noDaysNoBars() {
        assertTrue(TemperatureRange.layout(emptyList()).isEmpty())
    }

    @Test
    fun dryHoursGiveNoOutlook() {
        assertEquals(PrecipitationOutlook.None, PrecipitationOutlooks.of(hours(0.0, 0.0, 0.0)))
    }

    @Test
    fun rainNowEndsAtTheFirstDryHour() {
        val outlook = PrecipitationOutlooks.of(hours(0.4, 0.2, 0.0, 0.0))
        assertEquals(PrecipitationOutlook.Ongoing(PrecipitationKind.Rain, base.plusSeconds(2 * 3600)), outlook)
    }

    @Test
    fun rainUntilTheEndOfTheRangeHasNoEnd() {
        val outlook = PrecipitationOutlooks.of(hours(0.4, 0.2))
        assertEquals(PrecipitationOutlook.Ongoing(PrecipitationKind.Rain, null), outlook)
    }

    @Test
    fun rainLaterStartsAtTheFirstWetHour() {
        val outlook = PrecipitationOutlooks.of(hours(0.0, 0.0, 0.3, 0.0))
        assertEquals(PrecipitationOutlook.Starts(PrecipitationKind.Rain, base.plusSeconds(2 * 3600)), outlook)
    }

    @Test
    fun kindFollowsTheCondition() {
        assertEquals(PrecipitationKind.Snow, PrecipitationKind.of(Condition.Snow))
        assertEquals(PrecipitationKind.Storm, PrecipitationKind.of(Condition.Storm))
        assertEquals(PrecipitationKind.Drizzle, PrecipitationKind.of(Condition.Fog))
        assertEquals(PrecipitationKind.Generic, PrecipitationKind.of(Condition.Cloudy))
    }

    @Test
    fun totalIsTheSumOfHours() {
        assertEquals(0.9, PrecipitationOutlooks.totalMm(hours(0.4, 0.0, 0.5)), 0.0001)
    }

    @Test
    fun dayPrecipitationTextHidesZerosAndUsesTheLocale() {
        assertEquals("", dayPrecipitationText(0.0, Locale.GERMANY))
        assertEquals("", dayPrecipitationText(0.04, Locale.GERMANY))
        assertEquals("0,1", dayPrecipitationText(0.05, Locale.GERMANY))
        assertEquals("12.3", dayPrecipitationText(12.34, Locale.US))
    }

    @Test
    fun uvLevelsFollowTheWhoScale() {
        assertEquals(UvLevel.Low, UvLevel.of(0))
        assertEquals(UvLevel.Low, UvLevel.of(2))
        assertEquals(UvLevel.Moderate, UvLevel.of(3))
        assertEquals(UvLevel.Moderate, UvLevel.of(5))
        assertEquals(UvLevel.High, UvLevel.of(6))
        assertEquals(UvLevel.High, UvLevel.of(7))
        assertEquals(UvLevel.VeryHigh, UvLevel.of(8))
        assertEquals(UvLevel.VeryHigh, UvLevel.of(10))
        assertEquals(UvLevel.Extreme, UvLevel.of(11))
    }

    @Test
    fun daylightIsTheDistanceBetweenSunriseAndSunset() {
        val rise = Instant.parse("2026-10-28T06:11:00Z")
        val set = Instant.parse("2026-10-28T15:49:00Z")
        assertEquals(Duration.ofMinutes(9 * 60 + 38), daylight(day(1.0, 0.0, rise, set)))
    }

    @Test
    fun daylightIsEmptyWithoutBothTimesOrWhenReversed() {
        val rise = Instant.parse("2026-10-28T06:11:00Z")
        assertNull(daylight(day(1.0, 0.0, rise, null)))
        assertNull(daylight(day(1.0, 0.0, null, rise)))
        assertNull(daylight(day(1.0, 0.0, rise, rise.minusSeconds(60))))
    }
}
