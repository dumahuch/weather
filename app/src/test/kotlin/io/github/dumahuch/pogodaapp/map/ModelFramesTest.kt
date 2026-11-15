package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant

/** Кадры модели на диске по прогонам: без сети, пока прогон ещё последний; прежний кадр без сети; новый прогон заменяет старый. */
class ModelFramesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-11-10T19:52:00Z")
    private val hour = Instant.parse("2026-11-10T21:00:00Z")
    /** Время, когда последним был прогон 12 UTC (`older`). */
    private val seedNow = Instant.parse("2026-11-10T12:30:00Z")
    private val grib = Grib2Test.message(columns = 2, rows = 2, raw = intArrayOf(0, 50, 100, 100), scan = 64)

    /** Лучший прогон для [hour] сейчас (последний начавшийся) и более ранний. */
    private val best = IconD2Files.candidates(hour, now).first()
    private val older = IconD2Files.candidates(hour, now)[2]

    private class Net(var listingFails: Boolean = false, val listed: MutableSet<String> = mutableSetOf()) {
        var downloads = 0
        var listings = 0
    }

    private fun frames(net: Net, root: java.io.File = folder.root): ModelFrames {
        val listings = FolderListings { url ->
            net.listings++
            if (net.listingFails) throw IOException("нет сети")
            net.listed.filter { it.startsWith(url) }.joinToString("") { "<a href=\"${it.substringAfterLast('/')}\">" }
        }
        val bytes = { _: String -> net.downloads++; grib }
        return ModelFrames(
            fields = FieldLoader(listings, bytes, { it }),
            rain = ModelRainLoader(listings, bytes, { it }),
            disk = ModelFrameDisk(root),
        )
    }

    private fun listed(frame: IconD2Frame, field: String = IconD2Files.CLOUDS) =
        IconD2Files.runFolder(frame.run, field) + IconD2Files.fileName(frame, field)

    @Test
    fun frameIsDownloadedOnceAndThenComesFromDiskWithoutAnyRequest() {
        val net = Net(listed = mutableSetOf(listed(best)))
        val first = frames(net).clouds(hour, now)!!
        assertEquals(1, net.downloads)
        val listingsBefore = net.listings
        // Прогон начался меньше четырёх часов назад: новее на сервере ещё нет, ни список папки, ни файл не спрашиваются.
        val second = frames(net).clouds(hour, now.plusSeconds(600))!!
        assertArrayEquals(first, second)
        assertEquals(1, net.downloads)
        assertEquals(listingsBefore, net.listings)
    }

    @Test
    fun oldRunFrameIsUsedWhenThereIsNoNetwork() {
        // Кадр прогона 12 UTC лёг на диск в 12:30; в 19:52 он уже старше четырёх часов, но сети нет.
        frames(Net(listed = mutableSetOf(listed(older)))).clouds(hour, seedNow)
        assertEquals(older.run, ModelFrameDisk(folder.root).newestRun(ModelFrameDisk.CLOUDS, hour))
        val net = Net(listingFails = true)
        assertTrue(frames(net).clouds(hour, now)!!.isNotEmpty())
        assertEquals(0, net.downloads)
    }

    @Test
    fun noNetworkAndNothingOnDiskIsAnError() {
        val net = Net(listingFails = true)
        assertThrows(IOException::class.java) { frames(net).clouds(hour, now) }
        assertNull(frames(Net()).clouds(hour, now))
    }

    @Test
    fun newRunReplacesOldOneWhenItIsPublished() {
        frames(Net(listed = mutableSetOf(listed(older)))).clouds(hour, seedNow)
        assertEquals(older.run, ModelFrameDisk(folder.root).newestRun(ModelFrameDisk.CLOUDS, hour))
        // Старый кадр старше четырёх часов: идёт проверка списка, новый прогон выложен, кадр скачивается.
        val net = Net(listed = mutableSetOf(listed(best)))
        frames(net).clouds(hour, now)
        assertEquals(1, net.downloads)
        assertEquals(best.run, ModelFrameDisk(folder.root).newestRun(ModelFrameDisk.CLOUDS, hour))
    }

    @Test
    fun frameThatDoesNotChangeIsNotKeptByRun() {
        val net = Net(listed = mutableSetOf(listed(best)))
        frames(net).clouds(hour, now, keep = false)
        assertNull(ModelFrameDisk(folder.root).newestRun(ModelFrameDisk.CLOUDS, hour))
    }

    @Test
    fun windNeedsBothComponentsAndIsKeptWholeOnDisk() {
        val net = Net(listed = mutableSetOf(listed(best, IconD2Files.WIND_U)))
        assertNull(frames(net).field(HourlyField.Wind, hour, now))
        net.listed += listed(best, IconD2Files.WIND_V)
        val frame = frames(net).field(HourlyField.Wind, hour, now)!!
        assertEquals(HourlyField.Wind.length, frame.size)
        assertEquals(2, net.downloads)
        assertArrayEquals(frame, frames(Net(listingFails = true)).field(HourlyField.Wind, hour, now))
    }

    @Test
    fun preloadTaskGoesToTheMatchingLayer() {
        val net = Net(listed = mutableSetOf(listed(best)))
        frames(net).preload(PreloadTask(PreloadKind.Clouds, hour), now)
        assertEquals(best.run, ModelFrameDisk(folder.root).newestRun(ModelFrameDisk.CLOUDS, hour))
    }
}
