package io.github.dumahuch.pogodaapp.location

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {

    @Test
    fun samePointIsZero() {
        val point = GeoPoint(52.37, 9.74)
        assertEquals(0.0, distanceKm(point, point), 1e-9)
    }

    @Test
    fun hannoverToBerlinIsAbout249Kilometres() {
        val hannover = GeoPoint(52.3745, 9.7386)
        val berlin = GeoPoint(52.5200, 13.4050)
        assertEquals(249.0, distanceKm(hannover, berlin), 1.0)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertEquals(111.2, distanceKm(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 0.1)
    }

    @Test
    fun distanceIsSymmetricAndWorksAcrossTheDateLine() {
        val a = GeoPoint(-17.7, 179.9)
        val b = GeoPoint(-17.7, -179.9)
        assertEquals(distanceKm(a, b), distanceKm(b, a), 1e-9)
        assertEquals(21.2, distanceKm(a, b), 0.5)
    }
}
