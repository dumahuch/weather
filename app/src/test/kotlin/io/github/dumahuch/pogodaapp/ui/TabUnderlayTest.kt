package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabUnderlayTest {
    @Test
    fun `колода сверху только когда на экране вкладка`() {
        assertTrue(tabsAreOnTop(ScreenKey(Layer.Tab, 0)))
        assertTrue(tabsAreOnTop(ScreenKey(Layer.Tab, 3, isMap = true)))
        assertFalse(tabsAreOnTop(ScreenKey(Layer.Metric, 0, metric = 2)))
        assertFalse(tabsAreOnTop(ScreenKey(Layer.Day, 2, day = 20000L)))
        assertFalse(tabsAreOnTop(ScreenKey(Layer.Places)))
        assertFalse(tabsAreOnTop(ScreenKey(Layer.Settings)))
    }

    @Test
    fun `колода начинает на своём месте или под страницей`() {
        val shown = TabUnderlay(visible = true)
        assertEquals(1f, shown.alpha.value, 0f)
        assertEquals(0f, shown.shift.value, 0f)
        val hidden = TabUnderlay(visible = false)
        assertEquals(0f, hidden.alpha.value, 0f)
        assertEquals(-1f / UNDER_SHIFT, hidden.shift.value, 0f)
    }
}
