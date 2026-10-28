package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormattingTest {

    @Test
    fun roundsHalfAwayFromZero() {
        assertEquals(3, roundTemperature(2.5))
        assertEquals(-3, roundTemperature(-2.5))
        assertEquals(2, roundTemperature(2.4))
        assertEquals(-2, roundTemperature(-2.4))
        assertEquals(13, roundTemperature(12.7))
        assertEquals(-1, roundTemperature(-0.5))
    }

    @Test
    fun smallNegativeValueIsZeroWithoutSign() {
        assertEquals(0, roundTemperature(-0.4))
        assertEquals("0", temperatureDigits(-0.4))
        assertEquals("0°", temperatureText(-0.4))
        assertEquals("0°", temperatureText(0.0))
    }

    @Test
    fun usesRealMinusSign() {
        assertEquals("−2", temperatureDigits(-2.0))
        assertEquals("−2°", temperatureText(-1.6))
        assertEquals("13°", temperatureText(12.7))
        assertTrue(!temperatureText(-5.0).contains("-"))
    }

    @Test
    fun heroDigitsShrinkForLongNumbers() {
        assertEquals(124, heroDigitsSize(5.0))
        assertEquals(124, heroDigitsSize(12.7))
        assertEquals(124, heroDigitsSize(-0.4))
        assertEquals(124, heroDigitsSize(99.0))
        assertEquals(112, heroDigitsSize(-2.0))
        assertEquals(112, heroDigitsSize(-9.4))
        assertEquals(94, heroDigitsSize(-12.0))
        assertEquals(94, heroDigitsSize(-40.0))
        assertEquals(94, heroDigitsSize(100.0))
    }

    @Test
    fun precipitationUsesLocaleSeparator() {
        assertEquals("–", precipitationText(0.0, Locale.forLanguageTag("ru")))
        assertEquals("0,3", precipitationText(0.3, Locale.forLanguageTag("ru")))
        assertEquals("0,3", precipitationText(0.3, Locale.forLanguageTag("de")))
        assertEquals("0.3", precipitationText(0.3, Locale.forLanguageTag("en")))
        assertEquals("12.0", precipitationText(12.0, Locale.forLanguageTag("en")))
    }

    @Test
    fun precipitationRoundsToOneDecimal() {
        assertEquals("0.1", precipitationText(0.06, Locale.forLanguageTag("en")))
        assertEquals("2.5", precipitationText(2.54, Locale.forLanguageTag("en")))
    }

    private val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun hourLabelHasTwoDigits() {
        assertEquals("05", hourLabel(Instant.parse("2026-10-28T04:00:00Z"), berlin, days))
        assertEquals("23", hourLabel(Instant.parse("2026-10-28T22:00:00Z"), berlin, days))
    }

    @Test
    fun midnightShowsTheNameOfTheNewDay() {
        // 23:00Z это 00:00 в Берлине уже в четверг, 29 октября 2026.
        assertEquals("Чт", hourLabel(Instant.parse("2026-10-28T23:00:00Z"), berlin, days))
        assertEquals("Пт", hourLabel(Instant.parse("2026-10-29T23:00:00Z"), berlin, days))
    }

    @Test
    fun hourLabelFollowsThePlaceZoneNotTheTelephone() {
        assertEquals("13", hourLabel(Instant.parse("2026-10-28T00:00:00Z"), ZoneId.of("Pacific/Auckland"), days))
    }

    @Test
    fun curveHeightRunsFromWarmHighToColdLow() {
        assertEquals(listOf(96f, 96f), HourlyGeometry.curveY(listOf(5, 5)))
        assertEquals(listOf(110f, 82f), HourlyGeometry.curveY(listOf(10, 14)))
        assertEquals(listOf(82f, 96f, 110f), HourlyGeometry.curveY(listOf(14, 12, 10)))
        assertEquals(emptyList<Float>(), HourlyGeometry.curveY(emptyList()))
    }

    @Test
    fun barHeightsScaleToTheLargestValueButNotBelowOneAndAHalfMillimetre() {
        assertEquals(listOf(0f, 0f), HourlyGeometry.barHeights(listOf(0.0, 0.0)))
        // Максимум 3,0 мм: столбик 3 мм достигает 26, а 1,5 мм вдвое ниже.
        assertEquals(listOf(0f, 13f, 26f), HourlyGeometry.barHeights(listOf(0.0, 1.5, 3.0)))
        // Шкала не меньше 1,5 мм: слабый дождь 0,75 мм это половина высоты, а не полная.
        assertEquals(13f, HourlyGeometry.barHeights(listOf(0.75, 0.0))[0], 0.001f)
    }

    @Test
    fun nonZeroPrecipitationAlwaysHasAVisibleBar() {
        assertEquals(3f, HourlyGeometry.barHeights(listOf(0.01, 0.0))[0], 0.001f)
    }
}
