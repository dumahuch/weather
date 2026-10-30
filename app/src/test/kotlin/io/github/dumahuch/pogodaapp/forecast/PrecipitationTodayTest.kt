package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Подпись осадков на главном экране: «Ожидаются осадки» или «Без осадков» до конца календарных суток места. */
class PrecipitationTodayTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** Ночь с 25 на 26 октября 2026 уже зимнее время: 28 октября местное время UTC+1. */
    private val midnight28 = Instant.parse("2026-10-27T23:00:00Z")

    /** Прогноз на 48 часов с начала 28 октября по местному времени; [rain] ключи это часы от начала суток 28 октября. */
    private fun forecast(rain: Map<Int, Double>): Forecast {
        val hours = (0 until 48).map { hour ->
            HourPoint(
                time = midnight28.plus(Duration.ofHours(hour.toLong())),
                temperature = 8.0,
                condition = Condition.Cloudy,
                isDay = hour in 8..17,
                precipitationMm = rain[hour] ?: 0.0,
            )
        }
        return Forecast(
            zone = zone,
            current = CurrentWeather(midnight28.plus(Duration.ofHours(12)), 8.0, 6.0, Condition.Cloudy, true),
            today = DayRange(10.0, 5.0),
            hours = hours,
        )
    }

    private fun local(hour: Int, minute: Int = 0): Instant = midnight28.plus(Duration.ofMinutes(hour * 60L + minute))

    @Test
    fun rainLaterTodayIsExpected() {
        val forecast = forecast(mapOf(15 to 0.4))
        assertTrue(precipitationExpectedToday(forecast, local(12, 20)))
        assertEquals(0.4, precipitationLeftToday(forecast, local(12, 20)), 1e-9)
    }

    @Test
    fun rainThatAlreadyFellIsNotCounted() {
        assertFalse(precipitationExpectedToday(forecast(mapOf(8 to 1.2)), local(12, 20)))
    }

    @Test
    fun theCurrentHourCountsAsAWhole() {
        assertTrue(precipitationExpectedToday(forecast(mapOf(12 to 0.3)), local(12, 40)))
    }

    @Test
    fun anHourThatEndedExactlyNowIsNotCounted() {
        assertFalse(precipitationExpectedToday(forecast(mapOf(11 to 0.3)), local(12, 0)))
    }

    @Test
    fun traceAmountsBelowTheDisplayThresholdAreDry() {
        // дважды по 0,02 мм = 0,04 мм: в строке дня такое показывается пустым, значит и здесь «без осадков»
        assertFalse(precipitationExpectedToday(forecast(mapOf(14 to 0.02, 15 to 0.02)), local(12, 0)))
        assertTrue(precipitationExpectedToday(forecast(mapOf(14 to 0.03, 15 to 0.02)), local(12, 0)))
    }

    @Test
    fun tomorrowsRainIsNotTodays() {
        assertFalse(precipitationExpectedToday(forecast(mapOf(30 to 2.0)), local(12, 0)))
    }

    @Test
    fun theDayEndsAtLocalMidnightNotAtUtcMidnight() {
        // 23:30 по местному времени 28 октября (22:30 UTC): час с 00:00 местного 29 октября уже завтра
        assertFalse(precipitationExpectedToday(forecast(mapOf(24 to 1.0)), local(23, 30)))
        assertTrue(precipitationExpectedToday(forecast(mapOf(23 to 1.0)), local(23, 30)))
    }

    @Test
    fun noHoursMeansNoRain() {
        val empty = forecast(emptyMap()).copy(hours = emptyList())
        assertFalse(precipitationExpectedToday(empty, local(12, 0)))
    }
}
