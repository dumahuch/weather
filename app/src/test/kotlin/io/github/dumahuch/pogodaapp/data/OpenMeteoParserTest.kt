package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OpenMeteoParserTest {

    private fun fixture(name: String): String {
        val url = checkNotNull(javaClass.classLoader?.getResource("open-meteo/$name")) { "нет файла $name" }
        return url.readText(Charsets.UTF_8)
    }

    /** Короткий ответ на четыре часа: 0:00, 1:00, 2:00, 3:00 по Гринвичу. */
    private fun small(
        precipitation: String = "[9.0,0.4,0.0,2.5]",
        times: String = "[0,3600,7200,10800]",
        zone: String = "UTC",
        extra: String = "",
    ) = """{"timezone":"$zone","current":{"time":3600,"temperature_2m":1.0,"apparent_temperature":0.5,"weather_code":3,"is_day":1},""" +
        """"hourly":{"time":$times,"temperature_2m":[1.0,2.0,3.0,4.0],"weather_code":[0,3,61,95],"is_day":[1,1,0,0],"precipitation":$precipitation},""" +
        """"daily":{"temperature_2m_max":[4.0],"temperature_2m_min":[1.0]}$extra}"""

    @Test
    fun hannoverCurrentWeather() {
        val forecast = OpenMeteoParser.parse(fixture("hannover.json"))
        assertEquals(ZoneId.of("Europe/Berlin"), forecast.zone)
        assertEquals(Instant.ofEpochSecond(1793186100), forecast.current.time)
        assertEquals(12.7, forecast.current.temperature, 0.0)
        assertEquals(10.8, forecast.current.feelsLike, 0.0)
        assertEquals(Condition.Clear, forecast.current.condition)
        assertTrue(forecast.current.isDay)
    }

    @Test
    fun hannoverTodayRange() {
        val forecast = OpenMeteoParser.parse(fixture("hannover.json"))
        assertEquals(13.5, forecast.today.max, 0.0)
        assertEquals(8.3, forecast.today.min, 0.0)
    }

    @Test
    fun hannoverGivesFortyEightHours() {
        // В ответе 49 записей: последняя нужна только для осадков 48-го часа и в ленту не попадает.
        val forecast = OpenMeteoParser.parse(fixture("hannover.json"))
        assertEquals(48, forecast.hours.size)
        val first = forecast.hours.first()
        assertEquals(Instant.ofEpochSecond(1793185200), first.time)
        assertEquals(12.5, first.temperature, 0.0)
        assertEquals(Condition.Clear, first.condition)
        assertEquals(Condition.Partly, forecast.hours[2].condition)
        assertTrue(forecast.hours[4].isDay)
        assertFalse(forecast.hours[5].isDay)
        assertEquals(Instant.ofEpochSecond(1793185200L + 47 * 3600), forecast.hours.last().time)
    }

    @Test
    fun sydneyIsNightInSouthernZone() {
        val forecast = OpenMeteoParser.parse(fixture("sydney.json"))
        assertEquals(ZoneId.of("Australia/Sydney"), forecast.zone)
        assertFalse(forecast.current.isDay)
        assertEquals(Condition.Clear, forecast.current.condition)
        assertEquals(24.0, forecast.today.max, 0.0)
    }

    @Test
    fun precipitationOfAnHourComesFromTheNextRecord() {
        // В ответе осадки записаны за час, который закончился в указанное время.
        // Точка 0:00 описывает час 0:00–1:00, поэтому берёт значение записи 1:00.
        val forecast = OpenMeteoParser.parse(small())
        assertEquals(listOf(0.4, 0.0, 2.5), forecast.hours.map { it.precipitationMm })
    }

    @Test
    fun lastRecordIsOnlyUsedForPrecipitation() {
        val forecast = OpenMeteoParser.parse(small())
        assertEquals(3, forecast.hours.size)
        assertEquals(listOf(0L, 3600L, 7200L), forecast.hours.map { it.time.epochSecond })
        assertEquals(listOf(Condition.Clear, Condition.Cloudy, Condition.Rain), forecast.hours.map { it.condition })
    }

    @Test
    fun missingPrecipitationCountsAsZero() {
        val forecast = OpenMeteoParser.parse(small(precipitation = "[0.0,null,1.0,null]"))
        assertEquals(listOf(0.0, 1.0, 0.0), forecast.hours.map { it.precipitationMm })
    }

    @Test
    fun serviceErrorBecomesForecastExceptionWithReason() {
        val error = assertThrows(ForecastException::class.java) { OpenMeteoParser.parse(fixture("error-400.json")) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Latitude must be in range"))
    }

    @Test
    fun brokenJsonBecomesForecastException() {
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse("это не JSON") }
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse("") }
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse("{\"timezone\":\"UTC\"") }
    }

    @Test
    fun missingBlocksBecomeForecastException() {
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse("{}") }
        assertThrows(ForecastException::class.java) {
            OpenMeteoParser.parse(small().replace("\"current\"", "\"other\""))
        }
        assertThrows(ForecastException::class.java) {
            OpenMeteoParser.parse(small().replace("\"daily\"", "\"other\""))
        }
    }

    @Test
    fun unknownTimeZoneBecomesForecastException() {
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse(small(zone = "Mars/Olympus")) }
    }

    @Test
    fun tooFewHoursBecomeForecastException() {
        assertThrows(ForecastException::class.java) {
            OpenMeteoParser.parse(small(times = "[0]", precipitation = "[0.0]"))
        }
    }

    @Test
    fun arraysOfDifferentLengthBecomeForecastException() {
        assertThrows(ForecastException::class.java) { OpenMeteoParser.parse(small(precipitation = "[0.0,1.0]")) }
    }

    @Test
    fun oldFormatWithoutDailyFieldsGivesNoDays() {
        val forecast = OpenMeteoParser.parse(fixture("hannover.json"))
        assertTrue(forecast.days.isEmpty())
    }

    @Test
    fun fifteenDayFixtureGivesFourteenDays() {
        val forecast = OpenMeteoParser.parse(fixture("hannover-15d.json"))
        assertEquals(Forecast.DAYS_SHOWN, forecast.days.size)
        // Часы: 15 суток по 24 часа, последняя запись нужна только для осадков и в ленту не идёт.
        assertEquals(15 * 24 - 1, forecast.hours.size)
        val first = forecast.days.first()
        assertEquals(LocalDate.of(2026, 10, 28), first.date)
        assertEquals(Condition.Clear, first.condition)
        assertEquals(13.5, first.max, 0.0)
        assertEquals(8.3, first.min, 0.0)
        assertEquals(5, first.precipitationProbability)
        assertEquals(31.0, first.gustMaxKmh, 0.0)
        assertEquals(
            Instant.parse("2026-10-28T06:11:00Z"),
            first.sunrise,
        )
        assertEquals(Instant.parse("2026-10-28T15:49:00Z"), first.sunset)
    }

    @Test
    fun daysAreConsecutive() {
        val days = OpenMeteoParser.parse(fixture("hannover-15d.json")).days
        for (i in 1 until days.size) {
            assertEquals(days[i - 1].date.plusDays(1), days[i].date)
        }
    }

    @Test
    fun missingProbabilityStaysEmpty() {
        val days = OpenMeteoParser.parse(fixture("hannover-15d.json")).days
        assertEquals(null, days.last().precipitationProbability)
        assertEquals(85, days[1].precipitationProbability)
    }

    @Test
    fun dayPrecipitationMatchesHourlyOnes() {
        val forecast = OpenMeteoParser.parse(fixture("hannover-15d.json"))
        val day = forecast.days[1]
        assertEquals(day.precipitationMm, forecast.hoursOf(day.date).sumOf { it.precipitationMm }, 0.05)
    }

    @Test
    fun sunTimesInIsoFormAreReadInThePlaceZone() {
        val json = small(
            extra = "",
        ).replace(
            """"daily":{"temperature_2m_max":[4.0],"temperature_2m_min":[1.0]}""",
            """"daily":{"time":[0],"weather_code":[3],"temperature_2m_max":[4.0],"temperature_2m_min":[1.0],""" +
                """"sunrise":["1970-01-01T07:20"],"sunset":[null]}""",
        ).replace(""""timezone":"UTC"""", """"timezone":"Europe/Berlin"""")
        val day = OpenMeteoParser.parse(json).days.single()
        assertEquals(Instant.parse("1970-01-01T06:20:00Z"), day.sunrise)
        assertEquals(null, day.sunset)
        assertEquals(0.0, day.windMaxKmh, 0.0)
        assertEquals(0.0, day.precipitationMm, 0.0)
    }

    @Test
    fun daysStopAtTheFirstDayWithoutTemperature() {
        val json = small().replace(
            """"daily":{"temperature_2m_max":[4.0],"temperature_2m_min":[1.0]}""",
            """"daily":{"time":[0,86400,172800],"weather_code":[3,3,3],""" +
                """"temperature_2m_max":[4.0,null,5.0],"temperature_2m_min":[1.0,0.0,1.0]}""",
        )
        assertEquals(1, OpenMeteoParser.parse(json).days.size)
    }
}
