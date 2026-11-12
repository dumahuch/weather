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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Кадры карты на диске: запись и чтение, испорченные файлы, очистка суток. */
class FrameDiskCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3)
    private val hour = Instant.parse("2026-11-12T14:00:00Z")

    private fun cache(day: String = "2026-11-12") = FrameDiskCache(File(folder.root, day))

    @Test
    fun rainFrameIsReadBack() {
        val name = FrameDiskCache.rainName(Instant.parse("2026-11-12T14:05:00Z"))
        cache().writeRain(name, png)
        assertArrayEquals(png, cache().readRain(name))
    }

    @Test
    fun missingFrameIsNull() {
        assertNull(cache().readRain("radar-1.png"))
        assertNull(cache().readClouds(hour, 10))
    }

    @Test
    fun brokenRainFileIsDroppedAndAskedAgain() {
        val name = "radar-5.png"
        cache().writeRain(name, "<xml/>".toByteArray())
        assertNull(cache().readRain(name))
        assertFalse(File(File(folder.root, "2026-11-12"), name).exists())
    }

    @Test
    fun cloudsAreStoredPacked() {
        val alpha = ByteArray(100_000) { (it / 1000).toByte() }
        cache().writeClouds(hour, alpha)
        assertArrayEquals(alpha, cache().readClouds(hour, alpha.size))
        val stored = File(File(folder.root, "2026-11-12"), "clouds-${hour.epochSecond}.z")
        assertTrue(stored.length() < alpha.size / 10)
    }

    @Test
    fun cloudsOfWrongSizeOrBrokenAreDropped() {
        val alpha = ByteArray(1000) { 7 }
        cache().writeClouds(hour, alpha)
        assertNull(cache().readClouds(hour, 5000))
        assertNull(cache().readClouds(hour, 1000))
        val file = File(File(folder.root, "2026-11-12"), "clouds-${hour.epochSecond}.z")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertNull(cache().readClouds(hour, 1000))
        assertFalse(file.exists())
    }

    @Test
    fun otherDaysArePurgedTodayStays() {
        cache("2026-11-11").writeRain("radar-1.png", png)
        cache("2026-11-12").writeRain("radar-2.png", png)
        FrameDiskCache.purgeOtherDays(folder.root, LocalDate.parse("2026-11-12"))
        assertNull(cache("2026-11-11").readRain("radar-1.png"))
        assertArrayEquals(png, cache("2026-11-12").readRain("radar-2.png"))
    }

    @Test
    fun purgeAllEmptiesTheRoot() {
        cache("2026-11-12").writeRain("radar-2.png", png)
        FrameDiskCache.purgeAll(folder.root)
        assertEquals(0, folder.root.listFiles()!!.size)
        FrameDiskCache.purgeAll(File(folder.root, "no-such-folder"))
    }

    @Test
    fun cleanupComesAtFiveToMidnightLocalTime() {
        val zone = ZoneId.of("Europe/Berlin")
        val evening = ZonedDateTime.of(2026, 11, 12, 18, 55, 0, 0, zone)
        assertEquals(Duration.ofHours(5), FrameDiskCache.untilCleanup(evening))
        val late = ZonedDateTime.of(2026, 11, 12, 23, 56, 0, 0, zone)
        assertEquals(Duration.ofHours(23).plusMinutes(59), FrameDiskCache.untilCleanup(late))
        val exact = ZonedDateTime.of(2026, 11, 12, 23, 55, 0, 0, zone)
        assertEquals(Duration.ofDays(1), FrameDiskCache.untilCleanup(exact))
    }
}
