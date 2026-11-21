package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.HourDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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

    @Test
    fun pressureLevelBoundaries() {
        assertEquals(PressureLevel.Low, PressureLevel.ofHpa(999))
        assertEquals(PressureLevel.Normal, PressureLevel.ofHpa(1000))
        assertEquals(PressureLevel.Normal, PressureLevel.ofHpa(1013))
        assertEquals(PressureLevel.Normal, PressureLevel.ofHpa(1025))
        assertEquals(PressureLevel.High, PressureLevel.ofHpa(1026))
        assertEquals(PressureLevel.High, PressureLevel.ofHpa(1033))
        assertEquals(PressureLevel.Low, PressureLevel.ofHpa(970))
    }

    @Test
    fun pressureLevelAsContinuationOfTheTrendLine() {
        // «Стабильно · повышенное», «Steady · high», «Stabil · hoher Druck»: продолжение строчное, в немецком из двух слов
        assertEquals("повышенное", continuationCase("Повышенное", Locale.forLanguageTag("ru")))
        assertEquals("high", continuationCase("High", Locale.ENGLISH))
        assertEquals("hoher Druck", continuationCase("Hoher Druck", Locale.GERMAN))
    }
}
