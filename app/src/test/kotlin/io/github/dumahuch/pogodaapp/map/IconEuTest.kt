package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** ICON-EU после конца ICON-D2: имена файлов, выбор прогона, дождь за час и облака. */
class IconEuTest {

    private val now = Instant.parse("2026-11-14T20:30:00Z")
    private val run = Instant.parse("2026-11-14T18:00:00Z")

    @Test
    fun fileNamesFollowDwd() {
        assertEquals("https://opendata.dwd.de/weather/nwp/icon-eu/grib/18/tot_prec/", IconEuFiles.folder(run, IconEuFiles.RAIN))
        assertEquals(
            "icon-eu_europe_regular-lat-lon_single-level_2026111418_053_TOT_PREC.grib2.bz2",
            IconEuFiles.fileName(run, 53, IconEuFiles.RAIN),
        )
        assertEquals(
            "icon-eu_europe_regular-lat-lon_single-level_2026111418_005_CLCT_MOD.grib2.bz2",
            IconEuFiles.fileName(run, 5, IconEuFiles.CLOUDS),
        )
    }

    @Test
    fun onlyLongRunsWithinHourlyFiles() {
        // Конец «Послезавтра»: прогон 18 UTC даёт +76 ч, прогон 12 UTC уже +82 (там файлы через 3 часа).
        assertEquals(listOf(run), IconEuFiles.runs(Instant.parse("2026-11-17T22:00:00Z"), now))
        assertEquals(
            listOf(run, Instant.parse("2026-11-14T12:00:00Z"), Instant.parse("2026-11-14T06:00:00Z")),
            IconEuFiles.runs(Instant.parse("2026-11-16T20:00:00Z"), now),
        )
    }

    @Test
    fun frameBetweenHoursBelongsToHourItLiesIn() {
        assertEquals(Instant.parse("2026-11-17T22:00:00Z"), IconEuRainModel.frameEnd(Instant.parse("2026-11-17T22:00:00Z")))
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), IconEuRainModel.frameEnd(Instant.parse("2026-11-17T22:15:00Z")))
    }

    /** Файл накопления на +[lead] ч, одинакового по всей сетке 25 × 12 (сотые мм). */
    private fun rainFile(lead: Int, hundredths: Int): ByteArray = Grib2Test.message(
        columns = 25,
        rows = 12,
        raw = IntArray(25 * 12) { hundredths },
        scan = 64,
        validEnd = LocalDateTime.ofInstant(run.plusSeconds(lead * 3600L), ZoneOffset.UTC),
    )

    private fun rainLoader(files: Map<String, ByteArray>, downloads: MutableList<String>) = ModelRainLoader(
        listings = FolderListings { url -> if (url.endsWith("/18/tot_prec/")) files.keys.joinToString("") { "<a href=\"$it\">" } else "" },
        bytes = { url -> downloads += url; files.getValue(url.substringAfterLast('/')) },
        unpack = { it },
        width = 16,
        height = 12,
        model = IconEuRainModel,
    )

    private fun middle(levels: ByteArray) = levels[6 * 16 + 8].toInt() and 0xFF

    @Test
    fun hourlyRainIsDifferenceOfNeighbourHours() {
        val files = mapOf(
            IconEuFiles.fileName(run, 51, IconEuFiles.RAIN) to rainFile(51, 1000),
            IconEuFiles.fileName(run, 52, IconEuFiles.RAIN) to rainFile(52, 1250),
        )
        val downloads = mutableListOf<String>()
        val loader = rainLoader(files, downloads)
        // 21:00 + 52 ч: 12,50 − 10,00 мм за час = 2,5 мм/ч.
        val at = Instant.parse("2026-11-16T22:00:00Z")
        val frame = loader.load(at, now)!!
        assertEquals(run, frame.run)
        assertEquals(RainPalette.levelOf(2.5).toDouble(), middle(frame.levels).toDouble(), 1.0)
        assertEquals(run, loader.runFor(at, now))
        assertEquals(2, downloads.size)
        // 21:30 лежит в том же часе: файлы из запаса.
        assertEquals(middle(frame.levels), middle(loader.load(Instant.parse("2026-11-16T21:30:00Z"), now)!!.levels))
        assertEquals(2, downloads.size)
    }

    @Test
    fun firstHourOfRunNeedsNoZeroFile() {
        val files = mapOf(IconEuFiles.fileName(run, 1, IconEuFiles.RAIN) to rainFile(1, 300))
        val downloads = mutableListOf<String>()
        val frame = rainLoader(files, downloads).load(Instant.parse("2026-11-14T19:00:00Z"), now)!!
        assertEquals(RainPalette.levelOf(3.0).toDouble(), middle(frame.levels).toDouble(), 1.0)
        assertEquals(1, downloads.size)
    }

    @Test
    fun missingHourGivesNothing() {
        assertNull(rainLoader(emptyMap(), mutableListOf()).load(Instant.parse("2026-11-16T22:00:00Z"), now))
    }

    @Test
    fun cloudsAfterIconD2ComeFromIconEu() {
        val hour = Instant.parse("2026-11-17T20:00:00Z")
        val name = IconEuFiles.fileName(run, 74, IconEuFiles.CLOUDS)
        val downloads = mutableListOf<String>()
        val loader = CloudLoader(
            listings = FolderListings { url -> if (url.endsWith("/icon-eu/grib/18/clct_mod/")) "<a href=\"$name\">" else "" },
            bytes = { url -> downloads += url; Grib2Test.message(columns = 2, rows = 2, raw = intArrayOf(0, 50, 100, 100), scan = 64) },
            unpack = { it },
        )
        assertEquals(0.5f, loader.load(hour, now)!!.at(1, 0), 1e-6f)
        assertEquals(listOf(IconEuFiles.folder(run, IconEuFiles.CLOUDS) + name), downloads)
    }
}
