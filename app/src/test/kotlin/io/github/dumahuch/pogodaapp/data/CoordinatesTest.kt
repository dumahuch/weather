package io.github.dumahuch.pogodaapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CoordinatesTest {

    @Test
    fun keepsThreeDecimals() {
        assertEquals("52.365", Coordinates.param(52.3651))
        assertEquals("9.745", Coordinates.param(9.7449))
        assertEquals("-33.869", Coordinates.param(-33.8688))
        assertEquals("151.209", Coordinates.param(151.2093))
    }

    @Test
    fun padsAndKeepsZeroAndEdges() {
        assertEquals("0.000", Coordinates.param(0.0))
        assertEquals("52.370", Coordinates.param(52.37))
        assertEquals("90.000", Coordinates.param(90.0))
        assertEquals("-180.000", Coordinates.param(-180.0))
    }

    @Test
    fun usesDotInGermanLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("52.365", Coordinates.param(52.3651))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun everyClientSendsThreeDecimals() {
        val urls = listOf(
            OpenMeteoClient().buildUrl(52.3651, 9.7449),
            OpenMeteoAirQualityClient().buildUrl(52.3651, 9.7449),
            BrightSkyAlertsClient().buildUrl(52.3651, 9.7449),
        )
        for (url in urls) {
            assertTrue(url, url.contains("=52.365&") && url.contains("=9.745"))
            assertTrue(url, !url.contains("52.37") && !url.contains("9.74&"))
        }
    }
}
