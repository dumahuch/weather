package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.AirLevel
import io.github.dumahuch.pogodaapp.weather.AirQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AirQualityTest {

    private val noon = Instant.parse("2026-10-29T12:20:00Z")
    private val hour = Instant.parse("2026-10-29T12:00:00Z").epochSecond

    private fun response(times: List<Long>, values: String) =
        """{"latitude":52.38,"hourly":{"time":${times.joinToString(prefix = "[", postfix = "]")},"european_aqi":$values}}"""

    @Test
    fun takesTheHourThatContainsNow() {
        val json = response(listOf(hour - 3600, hour, hour + 3600), "[30,24.4,60]")
        assertEquals(AirQuality(24), AirQualityParser.parse(json, noon))
    }

    @Test
    fun roundsToTheNearestWholeNumber() {
        val json = response(listOf(hour), "[24.6]")
        assertEquals(25, AirQualityParser.parse(json, noon)?.eaqi)
    }

    @Test
    fun skipsEmptyValuesAndKeepsTheLatestKnownOne() {
        val json = response(listOf(hour - 3600, hour), "[18,null]")
        assertEquals(AirQuality(18), AirQualityParser.parse(json, noon))
    }

    @Test
    fun allValuesEmptyGivesNothing() {
        val json = response(listOf(hour - 3600, hour), "[null,null]")
        assertNull(AirQualityParser.parse(json, noon))
    }

    @Test
    fun onlyFutureHoursGiveNothing() {
        val json = response(listOf(hour + 3600), "[40]")
        assertNull(AirQualityParser.parse(json, noon))
    }

    @Test
    fun brokenOrForeignAnswerGivesNothing() {
        assertNull(AirQualityParser.parse("не json", noon))
        assertNull(AirQualityParser.parse("""{"error":true,"reason":"out of range"}""", noon))
        assertNull(AirQualityParser.parse("""{"hourly":{"time":[1]}}""", noon))
    }

    @Test
    fun levelsFollowTheEuropeanScale() {
        assertEquals(AirLevel.Good, AirQuality(0).level)
        assertEquals(AirLevel.Good, AirQuality(19).level)
        assertEquals(AirLevel.Fair, AirQuality(20).level)
        assertEquals(AirLevel.Moderate, AirQuality(40).level)
        assertEquals(AirLevel.Poor, AirQuality(60).level)
        assertEquals(AirLevel.VeryPoor, AirQuality(80).level)
        assertEquals(AirLevel.ExtremelyPoor, AirQuality(100).level)
        assertEquals(AirLevel.ExtremelyPoor, AirQuality(140).level)
    }

    @Test
    fun urlAsksForHourlyIndexInUnixTime() {
        val url = OpenMeteoAirQualityClient().buildUrl(52.3651, 9.7449)
        assertTrue(
            url,
            url.startsWith("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=52.37&longitude=9.74"),
        )
        for (part in listOf("hourly=european_aqi", "timezone=auto", "timeformat=unixtime", "forecast_days=2")) {
            assertTrue("в адресе нет $part: $url", url.contains(part))
        }
    }
}
