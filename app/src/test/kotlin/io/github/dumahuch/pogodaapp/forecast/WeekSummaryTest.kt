package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekSummaryTest {

    private val start = LocalDate.of(2026, 11, 26)

    private fun day(
        index: Int,
        max: Double = 2.0,
        min: Double = -3.0,
        condition: Condition = Condition.Cloudy,
        mm: Double = 0.0,
        probability: Int? = 0,
    ) = DayForecast(
        date = start.plusDays(index.toLong()),
        condition = condition,
        max = max,
        min = min,
        precipitationMm = mm,
        precipitationProbability = probability,
        windMaxKmh = 0.0,
        gustMaxKmh = 0.0,
        uvMax = 0.0,
        sunrise = null,
        sunset = null,
    )

    private fun week(build: (Int) -> DayForecast) = (0 until 10).map(build)

    @Test
    fun tooFewDaysGiveNoSummary() {
        assertNull(WeekSummaries.of(listOf(day(0), day(1))))
    }

    @Test
    fun onlyTheFirstSevenDaysCount() {
        // Дождь на восьмой день в неделю не входит.
        val summary = WeekSummaries.of(week { if (it == 7) day(it, condition = Condition.Rain) else day(it) })!!
        assertTrue(summary.precipitation is WeekPrecipitation.Dry)
    }

    @Test
    fun anOvercastDryWeekIsDryAndCloudy() {
        val summary = WeekSummaries.of(week { day(it) })!!
        assertEquals(WeekPrecipitation.Dry(DrySky.Cloudy), summary.precipitation)
    }

    @Test
    fun aMostlyClearDryWeekIsSunny() {
        val summary = WeekSummaries.of(week { day(it, condition = if (it < 5) Condition.Clear else Condition.Partly) })!!
        assertEquals(WeekPrecipitation.Dry(DrySky.Clear), summary.precipitation)
    }

    @Test
    fun aMixedDryWeekHasNoWordAboutTheSky() {
        val summary = WeekSummaries.of(week { day(it, condition = if (it % 2 == 0) Condition.Clear else Condition.Cloudy) })!!
        assertEquals(WeekPrecipitation.Dry(DrySky.Plain), summary.precipitation)
    }

    @Test
    fun aRainyDayWithLowProbabilityIsOnlyPossible() {
        val summary = WeekSummaries.of(week { day(it, condition = if (it == 4) Condition.Rain else Condition.Cloudy, probability = if (it == 4) 38 else 0) })!!
        val wet = summary.precipitation as WeekPrecipitation.Wet
        assertEquals(WeekPrecipKind.Rain, wet.kind)
        assertEquals(listOf(start.plusDays(4)), wet.dates)
        assertTrue(wet.possible)
    }

    @Test
    fun aLikelyRainIsNotJustPossible() {
        val summary = WeekSummaries.of(week { day(it, condition = if (it == 2) Condition.Rain else Condition.Cloudy, probability = if (it == 2) 80 else 0) })!!
        assertEquals(false, (summary.precipitation as WeekPrecipitation.Wet).possible)
    }

    @Test
    fun aDayWithoutProbabilityDataCountsAsLikely() {
        val summary = WeekSummaries.of(week { day(it, condition = if (it == 2) Condition.Rain else Condition.Cloudy, probability = null) })!!
        assertEquals(false, (summary.precipitation as WeekPrecipitation.Wet).possible)
    }

    @Test
    fun snowDaysGiveSnowAndMixedKindsGiveGenericPrecipitation() {
        val snow = WeekSummaries.of(week { day(it, condition = if (it == 1) Condition.Snow else Condition.Cloudy) })!!
        assertEquals(WeekPrecipKind.Snow, (snow.precipitation as WeekPrecipitation.Wet).kind)
        val mixed = WeekSummaries.of(
            week { day(it, condition = when (it) { 1 -> Condition.Snow; 3 -> Condition.Rain; else -> Condition.Cloudy }) },
        )!!
        assertEquals(WeekPrecipKind.Mixed, (mixed.precipitation as WeekPrecipitation.Wet).kind)
    }

    @Test
    fun aCloudyDayWithAMillimetreOfWaterCountsAsWet() {
        val summary = WeekSummaries.of(week { day(it, mm = if (it == 3) 1.0 else 0.4) })!!
        val wet = summary.precipitation as WeekPrecipitation.Wet
        assertEquals(WeekPrecipKind.Mixed, wet.kind)
        assertEquals(listOf(start.plusDays(3)), wet.dates)
    }

    @Test
    fun rangeIsTheColdestMorningAndTheWarmestDayOfTheWeek() {
        val summary = WeekSummaries.of(week { day(it, max = if (it == 4) 4.4 else 1.0, min = if (it == 1) -4.4 else -2.0) })!!
        assertEquals(-4, summary.lowC)
        assertEquals(4, summary.highC)
    }

    @Test
    fun warmingAndCoolingAreFoundByThreeDayAverages() {
        val warmer = WeekSummaries.of(week { day(it, max = if (it < 3) 0.0 else 4.0) })!!
        assertEquals(TemperatureTrend.Warmer, warmer.trend)
        val colder = WeekSummaries.of(week { day(it, max = if (it < 3) 6.0 else 1.0) })!!
        assertEquals(TemperatureTrend.Colder, colder.trend)
        val steady = WeekSummaries.of(week { day(it, max = 2.0 + it % 2) })!!
        assertEquals(TemperatureTrend.Steady, steady.trend)
    }
}
