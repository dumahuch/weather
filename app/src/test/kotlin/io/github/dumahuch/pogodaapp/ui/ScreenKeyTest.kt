package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenKeyTest {
    private val home = ScreenKey(Layer.Tab, 0)
    private val details = ScreenKey(Layer.Tab, 1)
    private val forecast = ScreenKey(Layer.Tab, 2)
    private val map = ScreenKey(Layer.Tab, 3, isMap = true)
    private val day = ScreenKey(Layer.Day, 2)
    private val places = ScreenKey(Layer.Places)
    private val settings = ScreenKey(Layer.Settings)

    @Test
    fun tabsSwitchInstantly() {
        assertEquals(TransitionKind.None, transitionKind(home, forecast))
        assertEquals(TransitionKind.None, transitionKind(forecast, details))
        assertEquals(TransitionKind.None, transitionKind(forecast, map))
        assertEquals(TransitionKind.None, transitionKind(map, home))
    }

    @Test
    fun newTabComesFromItsSideOfTheBar() {
        assertEquals(108, tabShift(fromIndex = 0, toIndex = 2, widthPx = 1080))
        assertEquals(-108, tabShift(fromIndex = 3, toIndex = 1, widthPx = 1080))
        assertEquals(0, tabShift(fromIndex = 2, toIndex = 2, widthPx = 1080))
        assertEquals(0, tabShift(fromIndex = 0, toIndex = 1, widthPx = 0))
    }

    @Test
    fun deeperWindowsPushAndPop() {
        assertEquals(TransitionKind.Push, transitionKind(forecast, day))
        assertEquals(TransitionKind.Pop, transitionKind(day, forecast))
        assertEquals(TransitionKind.Push, transitionKind(home, places))
        assertEquals(TransitionKind.Pop, transitionKind(settings, home))
    }

    @Test
    fun sameWindowAndOddPairs() {
        assertEquals(TransitionKind.None, transitionKind(home, home))
        assertEquals(TransitionKind.Fade, transitionKind(places, settings))
    }
}
