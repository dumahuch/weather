package io.github.dumahuch.pogodaapp.update

import io.github.dumahuch.pogodaapp.settings.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UpdateStoreTest {

    private class MemoryStorage : SettingsStorage {
        val values = HashMap<String, String>()
        override fun read(name: String): String? = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
        }
    }

    @Test
    fun autoCheckIsOnByDefaultAndRemembered() {
        val storage = MemoryStorage()
        assertTrue(UpdateStore(storage).auto)
        UpdateStore(storage).auto = false
        assertFalse(UpdateStore(storage).auto)
    }

    @Test
    fun rememberedOfferAndTimesSurviveRestart() {
        val storage = MemoryStorage()
        val offer = ReleaseOffer("0.8.69", "https://github.com/x", "ab".repeat(32), 37_740_652)
        val at = Instant.parse("2026-11-19T17:20:00Z")
        UpdateStore(storage).apply {
            this.offer = offer
            checkedAt = at
            notified = "0.8.69"
            started = "0.8.69"
        }
        val again = UpdateStore(storage)
        assertEquals(offer, again.offer)
        assertEquals(at, again.checkedAt)
        assertEquals("0.8.69", again.notified)
        assertEquals("0.8.69", again.started)
    }

    @Test
    fun clearedOrBrokenValuesReadAsNothing() {
        val storage = MemoryStorage()
        val store = UpdateStore(storage)
        assertNull(store.offer)
        assertNull(store.checkedAt)
        store.started = "0.8.69"
        store.started = null
        assertNull(store.started)
        storage.values[UpdateStore.OFFER] = "0.8.69\nтолько две части"
        assertNull(store.offer)
        storage.values[UpdateStore.CHECKED] = "вчера"
        assertNull(store.checkedAt)
    }
}
