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

    @Test
    fun beaufortBoundaries() {
        val expected = mapOf(
            0 to 0, 1 to 1, 5 to 1, 6 to 2, 11 to 2, 12 to 3, 19 to 3, 20 to 4, 28 to 4, 29 to 5, 38 to 5,
            39 to 6, 49 to 6, 50 to 7, 61 to 7, 62 to 8, 74 to 8, 75 to 9, 88 to 9, 89 to 10, 102 to 10,
            103 to 11, 117 to 11, 118 to 12, 200 to 12,
        )
        expected.forEach { (kmh, bft) -> assertEquals("$kmh км/ч", bft, beaufortOf(kmh)) }
    }

    @Test
    fun arcShrinksByTheOverflowPlusGap() {
        // текущая высота 100, не хватает 30 (slack −30), зазор 6: целимся в 100 − 30 − 6 = 64
        assertEquals(64, fitArcHeight(currentPx = 100, slackPx = -30, gapPx = 6, minPx = 40, naturalPx = 120))
    }

    @Test
    fun arcGrowsBackButNeverAboveNatural() {
        assertEquals(120, fitArcHeight(currentPx = 64, slackPx = 200, gapPx = 6, minPx = 40, naturalPx = 120))
        assertEquals(90, fitArcHeight(currentPx = 64, slackPx = 32, gapPx = 6, minPx = 40, naturalPx = 120))
    }

    @Test
    fun arcNeverShrinksBelowMinimum() {
        assertEquals(40, fitArcHeight(currentPx = 100, slackPx = -500, gapPx = 6, minPx = 40, naturalPx = 120))
        // если естественная высота меньше минимума, берём её
        assertEquals(30, fitArcHeight(currentPx = 30, slackPx = -500, gapPx = 6, minPx = 40, naturalPx = 30))
    }

    @Test
    fun smoothingKeepsEndsAndFlattensNoise() {
        val raw = listOf(1000.0, 1004.0, 1000.0, 1004.0, 1000.0)
        val smooth = smoothSeries(raw)
        assertEquals(raw.size, smooth.size)
        assertEquals(1000.0, smooth.first(), 1e-9)
        assertEquals(1000.0, smooth.last(), 1e-9)
        assertTrue(smooth[1] < raw[1] && smooth[2] > raw[2])
        assertEquals(raw.take(2), smoothSeries(raw.take(2)))
    }

    @Test
    fun smoothingKeepsAStraightLine() {
        val line = List(6) { 1000.0 + it }
        smoothSeries(line).forEachIndexed { i, v -> assertEquals(line[i], v, 1e-9) }
    }

    @Test
    fun tangentsAreZeroAtExtremaAndKeepSlopeOnLines() {
        val m = monotoneTangents(doubleArrayOf(0.0, 2.0, 1.0, 1.0, 3.0))
        assertEquals(0.0, m[1], 0.0)   // максимум
        assertEquals(0.0, m[2], 0.0)   // начало плато
        assertEquals(0.0, m[3], 0.0)
        val line = monotoneTangents(doubleArrayOf(0.0, 1.0, 2.0, 3.0))
        line.forEach { assertEquals(1.0, it, 1e-9) }
    }

    // --- Строка порывов с пояснением (0.5.0, вариант А) ---

    @Test
    fun gustWordAppearsFromTwentyKmhOnly() {
        assertNull(gustBeaufort(meanKmh = 5, gustKmh = 19))
        assertEquals(4, gustBeaufort(meanKmh = 5, gustKmh = 20))
        assertEquals(5, gustBeaufort(meanKmh = 5, gustKmh = 29))
    }

    @Test
    fun gustWordIsSkippedWhenTheStepIsNotAboveTheMeanWind() {
        // средний ветер 22 км/ч это тоже ступень 4, порыв 25 км/ч не выше
        assertNull(gustBeaufort(meanKmh = 22, gustKmh = 25))
        // порыв 30 км/ч уже ступень 5 против четвёртой
        assertEquals(5, gustBeaufort(meanKmh = 22, gustKmh = 30))
    }

    @Test
    fun gustWordIsSkippedWhenTheGustIsWeakerThanTheMeanWind() {
        assertNull(gustBeaufort(meanKmh = 40, gustKmh = 21))
    }

    @Test
    fun withoutMeanWindOnlyTheTwentyKmhConditionApplies() {
        assertEquals(4, gustBeaufort(meanKmh = null, gustKmh = 20))
        assertNull(gustBeaufort(meanKmh = null, gustKmh = 19))
    }

    // --- График давления (вариант Б) ---

    @Test
    fun axisCoversTheSeriesWithAtMostTwoIntervals() {
        val axis = pressureAxis(listOf(1012.1, 1013.0, 1016.0))
        assertEquals(PressureAxis(min = 1012, max = 1016, step = 2), axis)
        assertEquals(listOf(1012, 1014, 1016), axis.ticks)
    }

    @Test
    fun flatSeriesStillGetsTwoGridLines() {
        val axis = pressureAxis(listOf(1015.2, 1015.2, 1015.4))
        assertEquals(PressureAxis(min = 1015, max = 1016, step = 1), axis)
    }

    @Test
    fun seriesOnAWholeNumberDoesNotCollapseTheAxis() {
        assertEquals(PressureAxis(min = 1015, max = 1016, step = 1), pressureAxis(listOf(1015.0, 1015.0)))
    }

    @Test
    fun widerSeriesGetsAWiderStepAndStaysInside() {
        val values = listOf(998.4, 1004.0, 1011.7)
        val axis = pressureAxis(values)
        assertTrue(axis.min <= values.min())
        assertTrue(axis.max >= values.max())
        assertTrue((axis.max - axis.min) / axis.step <= 2)
        assertEquals(20, axis.step)
    }

    @Test
    fun axisOfAnEmptySeriesIsHarmless() {
        assertEquals(listOf(0, 1), pressureAxis(emptyList()).ticks)
    }

    @Test
    fun pointsCarryTheirTimeAndSkipHoursWithoutPressure() {
        val details = hours(listOf(1000.0, null, 1002.0, 1003.0))
        val points = PressureOutlook.points(details, start.plusSeconds(3600))
        assertEquals(listOf(start, start.plusSeconds(7200), start.plusSeconds(10800)), points.map { it.time })
        assertEquals(listOf(1000.0, 1002.0, 1003.0), points.map { it.hpa })
    }

    @Test
    fun nowIndexPointsAtTheLastPointNotAfterNow() {
        val details = hours(List(48) { 1000.0 + it })
        val now = start.plusSeconds(20 * 3600L + 1200)
        val points = PressureOutlook.points(details, now)
        assertEquals(6, PressureOutlook.nowIndex(points, now))
        assertEquals(0, PressureOutlook.nowIndex(points, start.minusSeconds(3600)))
        assertEquals(0, PressureOutlook.nowIndex(emptyList(), now))
    }
}
