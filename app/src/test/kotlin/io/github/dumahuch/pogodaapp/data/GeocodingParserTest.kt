package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingParserTest {

    private val answer = """
        {"results":[
          {"id":2910831,"name":"Hannover","latitude":52.37052,"longitude":9.73322,"elevation":55.0,
           "feature_code":"PPLA","country_code":"DE","admin1":"Niedersachsen","country":"Deutschland","postcodes":["30159"]},
          {"name":"Hanover","latitude":43.70231,"longitude":-72.28870,"country_code":"US","admin1":"New Hampshire","country":"United States"},
          {"name":"Остров","latitude":10.5,"longitude":20.5},
          {"name":"","latitude":1.0,"longitude":2.0},
          {"name":"Без координат","country_code":"DE"},
          {"name":"Без долготы","latitude":1.0}
        ],"generationtime_ms":0.41}
    """.trimIndent()

    @Test
    fun parsesNameRegionCountryAndPoint() {
        val first = GeocodingParser.parse(answer).first()
        assertEquals("Hannover", first.name)
        assertEquals("Niedersachsen", first.admin1)
        assertEquals("Deutschland", first.country)
        assertEquals("DE", first.countryCode)
        assertEquals(52.37052, first.latitude, 0.0)
        assertEquals(9.73322, first.longitude, 0.0)
    }

    @Test
    fun keepsTheOrderOfTheService() {
        assertEquals(listOf("Hannover", "Hanover", "Остров"), GeocodingParser.parse(answer).map { it.name })
    }

    @Test
    fun missingRegionAndCountryBecomeNull() {
        val island = GeocodingParser.parse(answer).last()
        assertNull(island.admin1)
        assertNull(island.country)
        assertNull(island.countryCode)
    }

    @Test
    fun skipsEntriesWithoutNameOrCoordinates() {
        assertEquals(3, GeocodingParser.parse(answer).size)
    }

    @Test
    fun answerWithoutResultsKeyIsAnEmptyListNotAnError() {
        assertTrue(GeocodingParser.parse("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test(expected = ForecastException::class)
    fun garbageIsAnError() {
        GeocodingParser.parse("это не JSON")
    }

    @Test
    fun urlEncodesTheQueryAndAsksForTheLanguage() {
        val client = OpenMeteoGeocodingClient(baseUrl = "https://example.test/v1/search")
        assertEquals(
            "https://example.test/v1/search?name=M%C3%BCnchen&count=12&language=ru&format=json",
            client.buildUrl("  München ", "ru"),
        )
        assertTrue(client.buildUrl("Bad Homburg", "de").contains("name=Bad+Homburg&"))
        assertTrue(client.buildUrl("30159", "en").contains("name=30159&"))
    }
}
