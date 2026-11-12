package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Загрузка кадров карты: запасные цвета DWD, отсутствие кадра, выбор прогона ICON-D2. */
class MapLoadersTest {

    private val now = Instant.parse("2026-11-09T19:52:00Z")
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private val xml = "<?xml version=\"1.0\"?><ServiceExceptionReport/>".toByteArray()

    @Test
    fun styledFrameIsUsedWhenServerAcceptsStyle() {
        val asked = mutableListOf<String>()
        val loader = RainLoader { url -> asked += url; png }
        val result = loader.load(Instant.parse("2026-11-09T19:50:00Z"), now)
        assertTrue(result is RainResult.Image && result.styled)
        assertEquals(1, asked.size)
        assertTrue(asked[0].contains("LAYERS=Niederschlagsradar"))
    }

    @Test
    fun refusedStyleFallsBackToDwdColoursAndIsNotAskedAgain() {
        val asked = mutableListOf<String>()
        val loader = RainLoader { url -> asked += url; if (url.contains("SLD_BODY")) xml else png }
        val first = loader.load(Instant.parse("2026-11-09T19:50:00Z"), now)
        assertTrue(first is RainResult.Image && !first.styled)
        val second = loader.load(Instant.parse("2026-11-09T19:55:00Z"), now)
        assertTrue(second is RainResult.Image && !second.styled)
        assertEquals(3, asked.size)
    }

    @Test
    fun missingTimeWithWorkingStyleIsMissing() {
        var calls = 0
        val loader = RainLoader { _ -> calls++; if (calls == 1) png else xml }
        loader.load(Instant.parse("2026-11-09T19:50:00Z"), now)
        assertEquals(RainResult.Missing, loader.load(Instant.parse("2026-11-09T21:00:00Z"), now))
        assertEquals(2, calls)
    }

    @Test
    fun laterHoursComeFromModelForTheFollowingHour() {
        val asked = mutableListOf<String>()
        RainLoader { url -> asked += url; png }.load(Instant.parse("2026-11-10T15:00:00Z"), now)
        assertTrue(asked[0].contains("LAYERS=Icon-eu_reg00625_fd_sl_TOTPREC01H"))
        assertTrue(asked[0].contains("TIME=2026-11-10T16%3A00%3A00.000Z"))
    }

    @Test
    fun networkErrorIsPassedUp() {
        val loader = RainLoader { _ -> throw IOException("нет сети") }
        assertThrows(IOException::class.java) { loader.load(now, now) }
    }

    @Test
    fun cloudsComeFromLatestRunThatHasTheHour() {
        val hour = Instant.parse("2026-11-10T21:00:00Z")
        val older = IconD2Frame(Instant.parse("2026-11-09T03:00:00Z"), 42)
        val listings = mutableListOf<String>()
        val downloads = mutableListOf<String>()
        val grib = Grib2Test.message(columns = 2, rows = 2, raw = intArrayOf(0, 50, 100, 100), scan = 64)
        val loader = CloudLoader(
            text = { url ->
                listings += url
                // Прогон 18 UTC ещё не выложил этот час, прогон 03 UTC (он считает на 45 часов) выложил.
                if (url.endsWith("/03/clct_mod/")) "<a href=\"${IconD2Files.fileName(older)}\">" else ""
            },
            bytes = { url -> downloads += url; grib },
            unpack = { it },
        )
        val grid = loader.load(hour, now)
        assertEquals(0.5f, grid!!.at(1, 0), 1e-6f)
        assertEquals(listOf("/18/clct_mod/", "/03/clct_mod/"), listings.map { it.takeLast(13) })
        assertEquals(listOf(IconD2Files.runFolder(older.run) + IconD2Files.fileName(older)), downloads)
        // Список папки держится 10 минут: второй раз не просится.
        loader.load(hour, now.plusSeconds(60))
        assertEquals(2, listings.size)
    }

    @Test
    fun noRunWithTheHourGivesNothing() {
        val loader = CloudLoader(text = { "" }, bytes = { error("не должно скачиваться") }, unpack = { it })
        assertNull(loader.load(Instant.parse("2026-11-10T21:00:00Z"), now))
    }

    @Test
    fun brokenFileIsDataError() {
        val frame = IconD2Frame(Instant.parse("2026-11-09T18:00:00Z"), 27)
        val loader = CloudLoader(
            text = { "<a href=\"${IconD2Files.fileName(frame)}\">" },
            bytes = { "<html>".toByteArray() },
            unpack = { it },
        )
        assertThrows(MapDataException::class.java) { loader.load(Instant.parse("2026-11-10T21:00:00Z"), now) }
    }

    @Test
    fun rainFramesAreLoadedAtTheSameTimeNotOneByOne() {
        // Каждая загрузка ждёт, пока начнётся вторая: при общей очереди (замке) этого не случилось бы.
        val bothStarted = CountDownLatch(2)
        var waitedInTime = true
        val loader = RainLoader { _ ->
            bothStarted.countDown()
            if (!bothStarted.await(5, TimeUnit.SECONDS)) waitedInTime = false
            png
        }
        val a = thread { loader.load(Instant.parse("2026-11-09T19:45:00Z"), now) }
        val b = thread { loader.load(Instant.parse("2026-11-09T19:50:00Z"), now) }
        a.join(10_000)
        b.join(10_000)
        assertTrue("кадры дождя грузятся по очереди", waitedInTime)
    }

    @Test
    fun cloudHoursAreLoadedAtTheSameTimeAndFolderListingIsAskedOnce() {
        val grib = Grib2Test.message(columns = 2, rows = 2, raw = intArrayOf(0, 50, 100, 100), scan = 64)
        val bothStarted = CountDownLatch(2)
        var waitedInTime = true
        var listings = 0
        val first = Instant.parse("2026-11-10T19:00:00Z")
        val second = Instant.parse("2026-11-10T20:00:00Z")
        val run = Instant.parse("2026-11-09T18:00:00Z")
        val names = listOf(first, second).map { hour ->
            IconD2Files.fileName(IconD2Frame(run, (hour.epochSecond - run.epochSecond).toInt() / 3600))
        }
        val loader = CloudLoader(
            text = { synchronized(this) { listings++ }; names.joinToString("") { "<a href=\"$it\">" } },
            bytes = {
                bothStarted.countDown()
                if (!bothStarted.await(5, TimeUnit.SECONDS)) waitedInTime = false
                grib
            },
            unpack = { it },
        )
        val a = thread { loader.load(first, now) }
        val b = thread { loader.load(second, now) }
        a.join(10_000)
        b.join(10_000)
        assertTrue("часы облаков грузятся по очереди", waitedInTime)
        assertEquals(1, listings)
    }
}
