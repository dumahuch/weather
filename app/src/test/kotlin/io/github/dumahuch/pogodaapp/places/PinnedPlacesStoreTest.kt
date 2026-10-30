package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.weather.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class PinnedPlacesStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val hannover = PinnedPlace("52.37_9.73", "Hannover", "Niedersachsen", "Deutschland", 52.3705, 9.7332)
    private val phonePlace = PinnedPlace("48.14_11.58", "München", null, null, 48.1374, 11.5755)

    @Test
    fun listSurvivesSavingAndLoading() {
        val file = File(folder.root, "pinned.json")
        FilePinnedPlacesStorage(file).save(listOf(hannover, phonePlace))
        assertEquals(listOf(hannover, phonePlace), FilePinnedPlacesStorage(file).load())
    }

    @Test
    fun missingFileGivesAnEmptyList() {
        assertTrue(FilePinnedPlacesStorage(File(folder.root, "none.json")).load().isEmpty())
    }

    @Test
    fun brokenFileGivesAnEmptyListAndDoesNotCrash() {
        val file = File(folder.root, "pinned.json").apply { writeText("{{{ не JSON") }
        assertTrue(FilePinnedPlacesStorage(file).load().isEmpty())
    }

    @Test
    fun fileOfAnotherVersionIsIgnored() {
        val file = File(folder.root, "pinned.json").apply { writeText("""{"v":99,"places":[]}""") }
        assertTrue(FilePinnedPlacesStorage(file).load().isEmpty())
    }

    @Test
    fun entryWithoutRequiredFieldMakesTheWholeFileEmptyNotHalfLoaded() {
        val file = File(folder.root, "pinned.json").apply { writeText("""{"v":1,"places":[{"id":"x","name":"A"}]}""") }
        assertTrue(FilePinnedPlacesStorage(file).load().isEmpty())
    }

    @Test
    fun savingLeavesNoTemporaryFile() {
        val file = File(folder.root, "pinned.json")
        FilePinnedPlacesStorage(file).save(listOf(hannover))
        assertFalse(File(folder.root, "pinned.json.tmp").exists())
    }

    @Test
    fun memoryStorageKeepsWhatWasSaved() {
        val storage = MemoryPinnedPlacesStorage(listOf(hannover))
        assertEquals(listOf(hannover), storage.load())
        storage.save(emptyList())
        assertTrue(storage.load().isEmpty())
    }

    @Test
    fun everyPlaceHasItsOwnForecastFileAndDeletingRemovesOnlyThatOne() {
        val caches = PinnedForecastCaches(folder.root)
        val entry = ForecastCache.Entry(Instant.parse("2026-10-30T10:00:00Z"), Place(52.37, 9.73, "Hannover"), "{}")
        caches.cacheFor("52.37_9.73").save(entry)
        caches.cacheFor("48.14_11.58").save(entry.copy(place = Place(48.14, 11.58, "München")))

        assertEquals("Hannover", caches.cacheFor("52.37_9.73").load()?.place?.name)
        assertEquals("München", caches.cacheFor("48.14_11.58").load()?.place?.name)

        caches.delete("52.37_9.73")
        assertNull(caches.cacheFor("52.37_9.73").load())
        assertNotNull(caches.cacheFor("48.14_11.58").load())
    }

    @Test
    fun deletingAnUnknownPlaceIsHarmless() {
        PinnedForecastCaches(folder.root).delete("0.00_0.00")
    }
}
