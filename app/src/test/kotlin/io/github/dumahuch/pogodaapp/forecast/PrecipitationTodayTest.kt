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

/** Подпись осадков на главном экране: какие части суток до конца календарных суток места дождливы (порог 0,3 мм на часть). */
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

    private fun caption(rain: Map<Int, Double>, hour: Int, minute: Int = 0) = precipitationCaption(forecast(rain), local(hour, minute))

    private fun parts(vararg parts: DayPart) = PrecipitationCaption.Today(parts.toList())

    @Test
    fun partsOfTheDayHaveTheAgreedBoundaries() {
        assertEquals(DayPart.Night, DayPart.of(0))
        assertEquals(DayPart.Night, DayPart.of(5))
        assertEquals(DayPart.Morning, DayPart.of(6))
        assertEquals(DayPart.Morning, DayPart.of(10))
        assertEquals(DayPart.Day, DayPart.of(11))
        assertEquals(DayPart.Day, DayPart.of(14))
        assertEquals(DayPart.Afternoon, DayPart.of(15))
        assertEquals(DayPart.Afternoon, DayPart.of(17))
        assertEquals(DayPart.Evening, DayPart.of(18))
        assertEquals(DayPart.Evening, DayPart.of(23))
    }

    @Test
    fun rainInOnePartNamesThatPart() {
        assertEquals(parts(DayPart.Day), caption(mapOf(13 to 0.5), hour = 9))
        assertEquals(parts(DayPart.Afternoon), caption(mapOf(16 to 0.5), hour = 9))
        assertEquals(parts(DayPart.Evening), caption(mapOf(20 to 0.5), hour = 9))
    }

    @Test
    fun rainInTwoPartsNamesBothInOrder() {
        assertEquals(parts(DayPart.Morning, DayPart.Evening), caption(mapOf(21 to 0.4, 7 to 0.4), hour = 0))
    }

    @Test
    fun smallHoursOfOnePartAddUp() {
        // три часа по 0,1 мм в одной части дают 0,3 мм: этого хватает
        assertEquals(parts(DayPart.Day), caption(mapOf(11 to 0.1, 12 to 0.1, 13 to 0.1), hour = 8))
    }

    @Test
    fun aPartWithLessThanThreeTenthsOfAMillimetreIsIgnored() {
        assertEquals(PrecipitationCaption.None, caption(mapOf(13 to 0.29), hour = 8))
        assertEquals(PrecipitationCaption.None, caption(mapOf(13 to 0.1, 14 to 0.1), hour = 8))
        assertEquals(parts(DayPart.Day), caption(mapOf(13 to 0.3), hour = 8))
    }

    @Test
    fun rainOfTwoPartsIsNotMergedAcrossTheirBorder() {
        // 0,2 мм в последний час дня и 0,2 мм в первый час после обеда: ни одна часть не набрала 0,3 мм
        assertEquals(PrecipitationCaption.None, caption(mapOf(14 to 0.2, 15 to 0.2), hour = 8))
    }

    @Test
    fun rainThatAlreadyFellIsNotCounted() {
        assertEquals(PrecipitationCaption.None, caption(mapOf(8 to 1.2), hour = 12, minute = 20))
    }

    @Test
    fun onlyTheRestOfThePartCountsAfterItHasStarted() {
        // в 13:20 в части «днём» остались часы 13 и 14: утренние 11 и 12 не в счёт
        assertEquals(PrecipitationCaption.None, caption(mapOf(11 to 0.5, 12 to 0.5), hour = 13, minute = 20))
        assertEquals(parts(DayPart.Day), caption(mapOf(11 to 0.5, 13 to 0.3), hour = 13, minute = 20))
    }

    @Test
    fun theCurrentHourCountsAsAWhole() {
        assertEquals(parts(DayPart.Day), caption(mapOf(12 to 0.3), hour = 12, minute = 40))
    }

    @Test
    fun anHourThatEndedExactlyNowIsNotCounted() {
        assertEquals(PrecipitationCaption.None, caption(mapOf(11 to 0.3), hour = 12))
    }

    @Test
    fun fourOrMoreRainyPartsMeanAllDay() {
        val rain = mapOf(2 to 0.5, 8 to 0.5, 12 to 0.5, 16 to 0.5, 20 to 0.5)
        val all = caption(rain, hour = 0) as PrecipitationCaption.Today
        assertEquals(5, all.parts.size)
        assertTrue(all.allDay)
        val four = caption(rain - 2, hour = 0) as PrecipitationCaption.Today
        assertTrue(four.allDay)
        val three = caption(rain - 2 - 8, hour = 0) as PrecipitationCaption.Today
        assertFalse(three.allDay)
    }

    @Test
    fun dryTodayAndRainyTomorrowMorningSaysTomorrowMorning() {
        assertEquals(PrecipitationCaption.TomorrowMorning, caption(mapOf(24 + 8 to 0.5), hour = 12))
    }

    @Test
    fun tomorrowsRainOutsideTheMorningIsNotMentioned() {
        assertEquals(PrecipitationCaption.None, caption(mapOf(24 + 2 to 2.0, 24 + 13 to 2.0), hour = 12))
    }

    @Test
    fun tomorrowMorningNeedsThreeTenthsToo() {
        assertEquals(PrecipitationCaption.None, caption(mapOf(24 + 8 to 0.29), hour = 12))
    }

    @Test
    fun rainTodayWinsOverTomorrowMorning() {
        assertEquals(parts(DayPart.Evening), caption(mapOf(20 to 0.5, 24 + 8 to 2.0), hour = 12))
    }

    @Test
    fun theDayEndsAtLocalMidnightNotAtUtcMidnight() {
        // 23:30 по местному времени 28 октября (22:30 UTC): час с 00:00 местного 29 октября уже завтра, и это ночь, а не утро
        assertEquals(PrecipitationCaption.None, caption(mapOf(24 to 1.0), hour = 23, minute = 30))
        assertEquals(parts(DayPart.Evening), caption(mapOf(23 to 1.0), hour = 23, minute = 30))
    }

    @Test
    fun noHoursMeansNoRain() {
        val empty = forecast(emptyMap()).copy(hours = emptyList())
        assertEquals(PrecipitationCaption.None, precipitationCaption(empty, local(12, 0)))
    }
}
