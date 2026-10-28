package io.github.dumahuch.pogodaapp.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocationsOverrideTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(text: String?): File {
        val file = File(folder.root, Locations.OVERRIDE_FILE)
        if (text != null) file.writeText(text)
        return file
    }

    @Test
    fun noFileMeansNoOverride() {
        assertNull(Locations.readOverride(file(null)))
    }

    @Test
    fun readsLatitudeAndLongitude() {
        assertEquals(GeoPoint(52.3745, 9.7386), Locations.readOverride(file("52.3745,9.7386\n")))
        assertEquals(GeoPoint(-33.87, 151.21), Locations.readOverride(file(" -33.87 , 151.21 ")))
    }

    @Test
    fun brokenContentMeansNoOverride() {
        assertNull(Locations.readOverride(file("")))
        assertNull(Locations.readOverride(file("52.37")))
        assertNull(Locations.readOverride(file("52.37,9.74,10")))
        assertNull(Locations.readOverride(file("north,east")))
    }

    private fun delayFile(text: String?): File {
        val file = File(folder.root, Locations.DELAY_FILE)
        if (text != null) file.writeText(text)
        return file
    }

    @Test
    fun noDelayFileMeansNoDelay() {
        assertNull(Locations.readDelay(delayFile(null)))
    }

    @Test
    fun readsTheDelayInMilliseconds() {
        assertEquals(12000L, Locations.readDelay(delayFile("12000\n")))
    }

    @Test
    fun brokenOrNonPositiveDelayMeansNoDelay() {
        assertNull(Locations.readDelay(delayFile("")))
        assertNull(Locations.readDelay(delayFile("долго")))
        assertNull(Locations.readDelay(delayFile("0")))
        assertNull(Locations.readDelay(delayFile("-5")))
    }
}
