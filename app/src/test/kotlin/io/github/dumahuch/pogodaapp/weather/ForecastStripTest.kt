package io.github.dumahuch.pogodaapp.weather

import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ForecastStripTest {

    private fun hannover(): Forecast {
        val url = checkNotNull(javaClass.classLoader?.getResource("open-meteo/hannover.json"))
        return OpenMeteoParser.parse(url.readText(Charsets.UTF_8))
    }

    private fun at(text: String): Instant = Instant.parse(text)

    @Test
    fun freshDataStartsWithTheCurrentValues() {
        val forecast = hannover()
        val strip = forecast.strip(at("2026-10-28T11:20:00Z"))
        assertEquals(48, strip.size)
        // Первая точка берёт температуру из блока «сейчас» (12,7), а не из почасового ряда (12,5), чтобы числа не расходились.
        assertEquals(12.7, strip.first().temperature, 0.0)
        assertEquals(forecast.current.condition, strip.first().condition)
        assertEquals(forecast.hours[0].time, strip.first().time)
        assertEquals(forecast.hours[1], strip[1])
    }

    @Test
    fun staleDataStartsAtTheHourOfNow() {
        val forecast = hannover()
        val strip = forecast.strip(at("2026-10-28T14:30:00Z"))
        assertEquals(forecast.hours[3].time, strip.first().time)
        assertEquals(forecast.hours[3].temperature, strip.first().temperature, 0.0)
        assertEquals(45, strip.size)
    }

    @Test
    fun nowBeforeTheFirstHourStartsAtTheBeginning() {
        val forecast = hannover()
        val strip = forecast.strip(at("2026-10-28T09:00:00Z"))
        assertEquals(forecast.hours[0].time, strip.first().time)
    }

    @Test
    fun noMoreThanRequestedCount() {
        val strip = hannover().strip(at("2026-10-28T11:20:00Z"), count = 5)
        assertEquals(5, strip.size)
    }

    @Test
    fun endOfDataGivesAShorterStrip() {
        val strip = hannover().strip(at("2026-10-30T10:30:00Z"))
        assertEquals(1, strip.size)
        assertEquals(at("2026-10-30T10:00:00Z"), strip.first().time)
    }

    @Test
    fun halfHourTimeZonesAlignWithLocalHours() {
        // В Индии часы начинаются в :30 по Гринвичу. «Сейчас» 12:15 по местному (06:45Z) относится к часу 12:00 (06:30Z).
        val zone = ZoneId.of("Asia/Kolkata")
        val first = at("2026-10-28T06:30:00Z")
        val hours = (0 until 4).map { i ->
            HourPoint(first.plusSeconds(i * 3600L), 20.0 + i, Condition.Cloudy, true, 0.0)
        }
        val current = CurrentWeather(at("2026-10-28T06:45:00Z"), 25.0, 24.0, Condition.Clear, true)
        val forecast = Forecast(zone, current, DayRange(30.0, 20.0), hours)
        val strip = forecast.strip(at("2026-10-28T06:50:00Z"))
        assertEquals(25.0, strip.first().temperature, 0.0)
        assertEquals(Condition.Clear, strip.first().condition)
        assertEquals(4, strip.size)
    }

    @Test
    fun emptyHoursGiveEmptyStrip() {
        val forecast = hannover().copy(hours = emptyList())
        assertTrue(forecast.strip(at("2026-10-28T11:20:00Z")).isEmpty())
    }

    @Test
    fun hoursOfADayAreOnlyThatLocalDay() {
        val url = checkNotNull(javaClass.classLoader?.getResource("open-meteo/hannover-15d.json"))
        val forecast = OpenMeteoParser.parse(url.readText(Charsets.UTF_8))
        val day = forecast.days[3].date
        val hours = forecast.hoursOf(day)
        assertEquals(24, hours.size)
        assertTrue(hours.all { it.time.atZone(forecast.zone).toLocalDate() == day })
        assertTrue(forecast.hoursOf(day.plusDays(100)).isEmpty())
    }
}
