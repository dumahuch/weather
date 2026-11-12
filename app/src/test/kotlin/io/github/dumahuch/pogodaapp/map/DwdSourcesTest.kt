package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant

/** Адреса DWD: кадр осадков (WMS) и файлы облачности ICON-D2. */
class DwdSourcesTest {

    private fun params(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }

    @Test
    fun radarRequestAsksForPngInMapProjection() {
        val url = DwdWms.mapUrl(RainSource.Radar, Instant.parse("2026-11-09T19:50:00Z"))
        assertTrue(url.startsWith("https://maps.dwd.de/geoserver/dwd/wms?"))
        val p = params(url)
        assertEquals("Niederschlagsradar", p["LAYERS"])
        assertEquals("EPSG:3857", p["CRS"])
        assertEquals("2026-11-09T19:50:00.000Z", p["TIME"])
        assertEquals("image/png", p["FORMAT"])
        assertEquals(MapArea.RAIN_WIDTH.toString(), p["WIDTH"])
        assertEquals(MapArea.heightFor(MapArea.RAIN_WIDTH).toString(), p["HEIGHT"])
        assertEquals("", p["STYLES"])
        assertEquals(4, p.getValue("BBOX").split(',').size)
    }

    @Test
    fun requestNeverCarriesOwnStyle() {
        // Сервер DWD запрещает свои стили («Dynamic style usage is forbidden»), цвета меняет RainPalette.
        val p = params(DwdWms.mapUrl(RainSource.Model, Instant.parse("2026-11-10T16:00:00Z")))
        assertEquals("Icon-eu_reg00625_fd_sl_TOTPREC01H", p["LAYERS"])
        assertFalse(p.containsKey("SLD_BODY"))
        assertEquals("", p["STYLES"])
    }

    @Test
    fun pngIsRecognised() {
        assertTrue(DwdWms.isPng(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
        assertFalse(DwdWms.isPng("<?xml version=\"1.0\"?><ServiceExceptionReport/>".toByteArray()))
        assertFalse(DwdWms.isPng(ByteArray(0)))
    }

    @Test
    fun iconFileNames() {
        val frame = IconD2Frame(Instant.parse("2026-11-09T18:00:00Z"), 5)
        assertEquals("https://opendata.dwd.de/weather/nwp/icon-d2/grib/18/clct_mod/", IconD2Files.runFolder(frame.run))
        assertEquals(
            "icon-d2_germany_regular-lat-lon_single-level_2026110918_005_2d_clct_mod.grib2.bz2",
            IconD2Files.fileName(frame),
        )
    }

    @Test
    fun pastHourUsesRunJustBeforeIt() {
        val now = Instant.parse("2026-11-09T19:52:00Z")
        val first = IconD2Files.candidates(Instant.parse("2026-11-09T10:00:00Z"), now).first()
        assertEquals(IconD2Frame(Instant.parse("2026-11-09T09:00:00Z"), 1), first)
    }

    @Test
    fun futureHourTriesLatestRunsFirst() {
        val now = Instant.parse("2026-11-09T19:52:00Z")
        val list = IconD2Files.candidates(Instant.parse("2026-11-10T21:00:00Z"), now)
        assertEquals(IconD2Frame(Instant.parse("2026-11-09T18:00:00Z"), 27), list[0])
        assertEquals(IconD2Frame(Instant.parse("2026-11-09T15:00:00Z"), 30), list[1])
        // В конце прогон 03 UTC; все прогоны считают до 48 часов.
        assertEquals(IconD2Frame(Instant.parse("2026-11-09T03:00:00Z"), 42), list.last())
        assertEquals(5, list.size)
    }

    @Test
    fun tomorrowAfternoonFallsBackToTheLongRunWhenTheLatestRunIsNotOutYet() {
        val now = Instant.parse("2026-11-12T15:47:00Z")
        val list = IconD2Files.candidates(Instant.parse("2026-11-13T16:00:00Z"), now)
        // Прогон 15 UTC (25 часов), если он уже выложен; иначе более ранние, в конце прогон 03 UTC (37 часов).
        assertEquals(IconD2Frame(Instant.parse("2026-11-12T15:00:00Z"), 25), list.first())
        assertEquals(IconD2Frame(Instant.parse("2026-11-12T03:00:00Z"), 37), list.last())
        assertEquals(5, list.size)
    }

    @Test
    fun longRunBeforeThreeInTheMorningIsYesterdaysRun() {
        val now = Instant.parse("2026-11-12T01:30:00Z")
        val list = IconD2Files.candidates(Instant.parse("2026-11-12T23:00:00Z"), now)
        assertEquals(IconD2Frame(Instant.parse("2026-11-11T03:00:00Z"), 44), list.last())
    }

    @Test
    fun hourBeyondTwoDaysHasNoRun() {
        val now = Instant.parse("2026-11-12T15:47:00Z")
        assertEquals(emptyList<IconD2Frame>(), IconD2Files.candidates(Instant.parse("2026-11-14T16:00:00Z"), now))
        assertEquals(48, IconD2Files.candidates(Instant.parse("2026-11-14T15:00:00Z"), now).first().lead)
    }

    @Test
    fun hourBeyondModelHasNoCandidates() {
        val now = Instant.parse("2026-11-09T19:52:00Z")
        assertTrue(IconD2Files.candidates(Instant.parse("2026-11-12T00:00:00Z"), now).isEmpty())
    }

    @Test
    fun listingLookup() {
        val frame = IconD2Frame(Instant.parse("2026-11-09T18:00:00Z"), 5)
        val listing = "<a href=\"${IconD2Files.fileName(frame)}\">x</a>"
        assertTrue(IconD2Files.listed(listing, frame))
        assertFalse(IconD2Files.listed(listing, frame.copy(lead = 6)))
    }
}
