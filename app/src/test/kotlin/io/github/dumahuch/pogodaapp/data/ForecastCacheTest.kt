package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class ForecastCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cacheFile(): File = File(folder.root, "forecast_cache.json")

    private val entry = ForecastCache.Entry(
        savedAt = Instant.ofEpochMilli(1_793_186_400_123),
        place = Place(52.37, 9.74, "Hannover"),
        raw = "{\"timezone\":\"Europe/Berlin\",\"текст\":\"ü\"}",
    )

    @Test
    fun noFileGivesNull() {
        assertNull(ForecastCache(cacheFile()).load())
    }

    @Test
    fun saveThenLoadReturnsTheSameEntry() {
        val cache = ForecastCache(cacheFile())
        cache.save(entry)
        assertEquals(entry, ForecastCache(cacheFile()).load())
    }

    @Test
    fun placeWithoutNameIsKeptWithoutName() {
        val cache = ForecastCache(cacheFile())
        val unnamed = entry.copy(place = Place(1.5, -2.5, null))
        cache.save(unnamed)
        assertNull(cache.load()?.place?.name)
        assertEquals(unnamed, cache.load())
    }

    @Test
    fun secondSaveReplacesTheFirst() {
        val cache = ForecastCache(cacheFile())
        cache.save(entry)
        val newer = entry.copy(savedAt = Instant.ofEpochMilli(1_793_190_000_000), raw = "{}")
        cache.save(newer)
        assertEquals(newer, cache.load())
    }

    @Test
    fun noTemporaryFileIsLeftBehind() {
        ForecastCache(cacheFile()).save(entry)
        assertEquals(listOf("forecast_cache.json"), folder.root.list()?.toList())
    }

    @Test
    fun brokenFileGivesNullAndDoesNotThrow() {
        cacheFile().writeText("{ это не JSON")
        assertNull(ForecastCache(cacheFile()).load())
        cacheFile().writeText("")
        assertNull(ForecastCache(cacheFile()).load())
    }

    @Test
    fun fileWithMissingFieldsGivesNull() {
        cacheFile().writeText("{\"v\":1,\"lat\":1.0}")
        assertNull(ForecastCache(cacheFile()).load())
    }

    @Test
    fun fileOfAnotherFormatVersionGivesNull() {
        val cache = ForecastCache(cacheFile())
        cache.save(entry)
        cacheFile().writeText(cacheFile().readText().replace("\"v\":1", "\"v\":2"))
        assertNull(cache.load())
    }

    @Test
    fun savingIntoAMissingFolderDoesNotThrow() {
        val cache = ForecastCache(File(folder.root, "нет/такой/папки/cache.json"))
        cache.save(entry)
        assertFalse(File(folder.root, "нет").exists())
    }
}
