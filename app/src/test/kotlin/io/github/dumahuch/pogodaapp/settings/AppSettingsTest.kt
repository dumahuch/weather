package io.github.dumahuch.pogodaapp.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    private class MemoryStorage(initial: Map<String, String> = emptyMap()) : SettingsStorage {
        val values = HashMap(initial)
        var writes = 0
        override fun read(name: String): String? = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
            writes++
        }
    }

    @Test
    fun cardShadowIsSoftByDefault() {
        assertEquals(CardShadow.Soft, AppSettings(MemoryStorage()).cardShadow.value)
    }

    @Test
    fun unknownStoredValueFallsBackToSoft() {
        assertEquals(CardShadow.Soft, CardShadow.fromKey("banana"))
        assertEquals(CardShadow.Soft, CardShadow.fromKey(null))
        assertEquals(CardShadow.Soft, CardShadow.fromKey(""))
        val storage = MemoryStorage(mapOf(AppSettings.CARD_SHADOW to "banana"))
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun chosenShadowIsStoredAndComesBackAfterRestart() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        first.setCardShadow(CardShadow.Strong)
        assertEquals(CardShadow.Strong, first.cardShadow.value)
        assertEquals("strong", storage.values[AppSettings.CARD_SHADOW])
        // «Перезапуск»: новый объект над тем же хранилищем.
        assertEquals(CardShadow.Strong, AppSettings(storage).cardShadow.value)
        first.setCardShadow(CardShadow.Soft)
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun choosingTheSameShadowAgainWritesNothing() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        settings.setCardShadow(CardShadow.Soft)
        assertEquals(0, storage.writes)
        settings.setCardShadow(CardShadow.Strong)
        settings.setCardShadow(CardShadow.Strong)
        assertEquals(1, storage.writes)
    }

    @Test
    fun everyShadowHasItsOwnKeyThatReadsBack() {
        val keys = CardShadow.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (shadow in CardShadow.entries) assertEquals(shadow, CardShadow.fromKey(shadow.key))
        assertTrue(CardShadow.entries.size == 2)
    }
}
