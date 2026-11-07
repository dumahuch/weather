package io.github.dumahuch.pogodaapp.location

import io.github.dumahuch.pogodaapp.settings.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseLocationPromptTest {

    private class MemoryStorage : SettingsStorage {
        val values = HashMap<String, String>()
        var writes = 0
        override fun read(name: String): String? = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
            writes++
        }
    }

    @Test
    fun asksOnceWhenOnlyApproximateIsGranted() {
        val storage = MemoryStorage()
        val prompt = PreciseLocationPrompt(storage)
        assertTrue(prompt.shouldAsk(hasCoarse = true, hasFine = false))
        prompt.markAsked()
        assertFalse(prompt.shouldAsk(hasCoarse = true, hasFine = false))
        // «Перезапуск»: новый объект над тем же хранилищем помнит.
        assertFalse(PreciseLocationPrompt(storage).shouldAsk(hasCoarse = true, hasFine = false))
    }

    @Test
    fun doesNotAskWhenPreciseIsAlreadyGrantedOrNothingIsGranted() {
        val prompt = PreciseLocationPrompt(MemoryStorage())
        assertFalse(prompt.shouldAsk(hasCoarse = true, hasFine = true))
        // Без всякого доступа окно покажет обычный запрос по кнопке «Разрешить».
        assertFalse(prompt.shouldAsk(hasCoarse = false, hasFine = false))
    }

    @Test
    fun markingTwiceWritesOnce() {
        val storage = MemoryStorage()
        val prompt = PreciseLocationPrompt(storage)
        prompt.markAsked()
        prompt.markAsked()
        assertEquals(1, storage.writes)
        assertEquals("1", storage.values[PreciseLocationPrompt.KEY])
    }
}
