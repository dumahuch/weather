package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlacesLogicTest {

    private fun suggestion(name: String, code: String?, lat: Double, lon: Double, admin1: String? = null, country: String? = null) =
        PlaceSuggestion(name, admin1, country, code, lat, lon)

    private val hannover = suggestion("Hannover", "DE", 52.3705, 9.7332)
    private val hanoverUs = suggestion("Hanover", "US", 43.7023, -72.2887)
    private val hannoverCopy = suggestion("Hannover", "DE", 52.3720, 9.7350)
    private val hannoverOther = suggestion("Hannover", "US", 40.0, -80.0)

    @Test
    fun placesOfTheHomeCountryComeFirstAndOrderInsideGroupsIsKept() {
        val ranked = PlacesLogic.rank(listOf(hanoverUs, hannoverOther, hannover), "DE")
        assertEquals(listOf(hannover, hanoverUs, hannoverOther), ranked)
    }

    @Test
    fun homeCountryCodeIsCaseInsensitive() {
        assertEquals(hanoverUs, PlacesLogic.rank(listOf(hannover, hanoverUs), "us").first())
    }

    @Test
    fun withoutHomeCountryTheServiceOrderStays() {
        assertEquals(listOf(hanoverUs, hannover), PlacesLogic.rank(listOf(hanoverUs, hannover), null))
        assertEquals(listOf(hanoverUs, hannover), PlacesLogic.rank(listOf(hanoverUs, hannover), " "))
    }

    @Test
    fun sameNameWithinOneKilometreIsShownOnce() {
        assertEquals(listOf(hannover), PlacesLogic.rank(listOf(hannover, hannoverCopy), "DE"))
    }

    @Test
    fun sameNameFarApartIsKept() {
        assertEquals(2, PlacesLogic.rank(listOf(hannover, hannoverOther), null).size)
    }

    @Test
    fun findsPinnedPlaceWithinTwoKilometres() {
        val pinned = listOf(PinnedPlace.of(hannover))
        assertNotNull(PlacesLogic.findSame(pinned, 52.3800, 9.7400))
        assertNull(PlacesLogic.findSame(pinned, 52.4500, 9.7332))
        assertNull(PlacesLogic.findSame(emptyList(), 52.3705, 9.7332))
    }

    @Test
    fun labelJoinsThreeLevelsWithoutRepeats() {
        assertEquals("Hannover · Niedersachsen · Deutschland", placeLabel("Hannover", "Niedersachsen", "Deutschland"))
        assertEquals("Berlin · Deutschland", placeLabel("Berlin", "Berlin", "Deutschland"))
        assertEquals("Hannover", placeLabel("Hannover", null, " "))
    }

    @Test
    fun regionLineHasNoName() {
        assertEquals("Niedersachsen · Deutschland", placeRegionLine("Hannover", "Niedersachsen", "Deutschland"))
        assertEquals("Deutschland", placeRegionLine("Berlin", "Berlin", "Deutschland"))
        assertEquals("", placeRegionLine("Hannover", null, null))
    }

    @Test
    fun idIsRoundedToHundredthsOfADegreeWithADotWhateverTheLanguage() {
        assertEquals("52.37_9.74", placeId(52.3745, 9.7386))
        assertEquals("-33.87_151.21", placeId(-33.8688, 151.2093))
    }

    @Test
    fun pinnedPlaceFromSuggestionAndFromPoint() {
        val fromSuggestion = PinnedPlace.of(hannover)
        assertEquals("52.37_9.73", fromSuggestion.id)
        assertEquals("Hannover", fromSuggestion.name)
        val fromPoint = PinnedPlace.ofPoint(GeoPoint(48.1374, 11.5755), "München")
        assertEquals("48.14_11.58", fromPoint.id)
        assertNull(fromPoint.admin1)
        assertNull(fromPoint.country)
        assertEquals(GeoPoint(48.1374, 11.5755), fromPoint.point)
    }
}
