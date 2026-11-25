package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageBackTest {
    @Test
    fun `без жеста колода как была`() {
        assertEquals(-0.2f, deckShiftWith(0f, -0.2f), 0f)
        assertEquals(0f, deckAlphaWith(0f, 0f), 0f)
        assertEquals(1f, deckAlphaWith(0f, 1f), 0f)
    }

    @Test
    fun `окно за краем: колода на месте и видна`() {
        assertEquals(0f, deckShiftWith(1f, -0.2f), 0f)
        assertEquals(1f, deckAlphaWith(1f, 0f), 0f)
    }

    @Test
    fun `колода уже на месте жестом не двигается`() {
        assertEquals(0f, deckShiftWith(0.5f, 0f), 0f)
        assertEquals(1f, deckAlphaWith(0.5f, 1f), 0f)
    }

    @Test
    fun `на полпути колода посередине`() {
        assertEquals(-0.1f, deckShiftWith(0.5f, -0.2f), 1e-6f)
        assertEquals(0.5f, deckAlphaWith(0.5f, 0f), 1e-6f)
    }
}
