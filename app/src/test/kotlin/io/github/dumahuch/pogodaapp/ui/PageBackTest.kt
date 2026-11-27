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
    fun `окно за краем, колода на месте и видна`() {
        assertEquals(0f, deckShiftWith(1f, -0.2f), 0f)
        assertEquals(1f, deckAlphaWith(1f, 0f), 0f)
    }

    @Test
    fun `колода уже на месте жестом не двигается`() {
        assertEquals(0f, deckShiftWith(0.5f, 0f), 0f)
        assertEquals(1f, deckAlphaWith(0.5f, 1f), 0f)
    }

    @Test
    fun `на полпути к трети колода посередине`() {
        assertEquals(-0.1f, deckShiftWith(0.15f, -0.2f), 1e-6f)
        assertEquals(0.5f, deckAlphaWith(0.15f, 0f), 1e-6f)
    }

    @Test
    fun `после трети пути колода уже на месте`() {
        assertEquals(0f, deckShiftWith(0.4f, -0.2f), 0f)
        assertEquals(1f, deckAlphaWith(0.4f, 0f), 0f)
    }

    @Test
    fun `сдвиг колоды в пикселях целый и ровно ноль на месте`() {
        assertEquals(-288, deckOffsetPx(0f, -0.2f, 1440))
        assertEquals(0, deckOffsetPx(1f, -0.2f, 1440))
        assertEquals(0, deckOffsetPx(0.4f, -0.2f, 1440))
        assertEquals(-144, deckOffsetPx(0.15f, -0.2f, 1440))
    }
}
