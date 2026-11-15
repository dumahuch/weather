package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/** Кадры дождя модели на диске: по прогонам, через полночь, старые прогоны и прошедшее время стираются. */
class ModelFrameDiskTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-11-12T22:00:00Z")
    private val run = Instant.parse("2026-11-12T18:00:00Z")
    private val tomorrow = Instant.parse("2026-11-13T15:30:00Z")
    private val levels = ByteArray(1000) { if (it in 300..400) 7 else 0 }
    private val packed = LevelPacking.pack(levels, 50)

    private fun runs(root: File) = root.list()!!.sorted()

    @Test
    fun packingRestoresLevelsExactly() {
        val any = ByteArray(1200 * 7) { (it * 37 % 256 xor it / 1200).toByte() }
        assertArrayEquals(any, LevelPacking.unpack(LevelPacking.pack(any, 1200), 1200, 7))
        assertNull(LevelPacking.unpack(LevelPacking.pack(any, 1200), 1200, 8))
    }

    @Test
    fun frameIsReadBackOnlyForItsRun() {
        val disk = ModelFrameDisk(folder.root)
        disk.write(run, tomorrow, packed, now)
        assertArrayEquals(levels, LevelPacking.unpack(disk.read(run, tomorrow, 50, 20)!!, 50, 20))
        assertNull(disk.read(run.plus(Duration.ofHours(3)), tomorrow, 50, 20))
        // Другой размер картинки: кадр не годится и удаляется.
        assertNull(disk.read(run, tomorrow, 51, 20))
        assertNull(disk.read(run, tomorrow, 50, 20))
    }

    @Test
    fun onlyLastRunsStayAndPastFramesGo() {
        val disk = ModelFrameDisk(folder.root)
        File(folder.root, "v0-2026111209").mkdirs()
        disk.write(run.minus(Duration.ofHours(3)), tomorrow, packed, now)
        disk.write(run.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2)), packed, now)
        disk.write(run, tomorrow, packed, now)
        assertEquals(listOf("v1-2026111215", "v1-2026111218"), runs(folder.root))
        // Прошедшее время показывает радар: кадр модели на него стёрт.
        assertNull(disk.read(run.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2)), 50, 20))
        assertTrue(disk.read(run.minus(Duration.ofHours(3)), tomorrow, 50, 20) != null)
        disk.write(run.plus(Duration.ofHours(3)), tomorrow, packed, now)
        assertEquals(listOf("v1-2026111218", "v1-2026111221"), runs(folder.root))
    }

    @Test
    fun nextDayDoesNotTouchFramesOfCurrentRun() {
        val disk = ModelFrameDisk(folder.root)
        disk.write(run, tomorrow, packed, now)
        disk.tidy(Instant.parse("2026-11-13T00:10:00Z"))
        assertTrue(disk.read(run, tomorrow, 50, 20) != null)
        disk.tidy(tomorrow.plus(Duration.ofHours(2)))
        assertFalse(File(folder.root, "v1-2026111218").listFiles()!!.isNotEmpty())
    }

    @Test
    fun hourlyFramesLieByRunAndKind() {
        val disk = ModelFrameDisk(folder.root)
        val frame = ByteArray(300) { (it % 7).toByte() }
        disk.writeFrame("temp", run, tomorrow, frame, now)
        disk.writeFrame("wind", run, tomorrow, ByteArray(40) { 3 }, now)
        assertArrayEquals(frame, disk.readFrame("temp", run, tomorrow, 300))
        assertEquals(40, disk.readFrame("wind", run, tomorrow, 40)!!.size)
        // Другого вида и другого размера кадр не читается; испорченный размер стирает файл.
        assertNull(disk.readFrame("clouds", run, tomorrow, 300))
        assertNull(disk.readFrame("temp", run, tomorrow, 301))
        assertNull(disk.readFrame("temp", run, tomorrow, 300))
        assertEquals(run, disk.newestRun("wind", tomorrow))
    }

    @Test
    fun newestRunIsTheLatestOneThatHasTheFrame() {
        val disk = ModelFrameDisk(folder.root)
        val frame = ByteArray(100) { 1 }
        val older = run.minus(Duration.ofHours(3))
        disk.writeFrame("clouds", older, tomorrow, frame, now)
        assertEquals(older, disk.newestRun("clouds", tomorrow))
        disk.writeFrame("clouds", run, tomorrow, frame, now)
        assertEquals(run, disk.newestRun("clouds", tomorrow))
        // У позднего прогона нет кадра на другое время: берётся то, где он есть.
        disk.writeFrame("clouds", older, tomorrow.plus(Duration.ofHours(1)), frame, now)
        assertEquals(older, disk.newestRun("clouds", tomorrow.plus(Duration.ofHours(1))))
        assertNull(disk.newestRun("wind", tomorrow))
        assertNull(ModelFrameDisk(File(folder.root, "нет")).newestRun("clouds", tomorrow))
    }

    @Test
    fun currentHourStaysAndOlderFramesGo() {
        val disk = ModelFrameDisk(folder.root)
        val frame = ByteArray(100) { 1 }
        val hour = Instant.parse("2026-11-12T22:00:00Z")
        disk.writeFrame("wind", run, hour, frame, now)
        disk.writeFrame("wind", run, hour.minus(Duration.ofHours(2)), frame, now)
        // В 22:40 час 22:00 ещё нужен (карта округляет время до ближайшего часа), 20:00 уже нет.
        disk.tidy(hour.plus(Duration.ofMinutes(40)))
        assertEquals(100, disk.readFrame("wind", run, hour, 100)!!.size)
        assertNull(disk.readFrame("wind", run, hour.minus(Duration.ofHours(2)), 100))
    }
}
