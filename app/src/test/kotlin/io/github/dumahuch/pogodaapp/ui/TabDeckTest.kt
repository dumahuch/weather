package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabDeckTest {
    @Test
    fun keepsVisitedTabsAndCurrentInFixedOrder() {
        val all = listOf(0, 1, 2)
        assertEquals(listOf(0), tabsToKeep(emptySet(), 0, all))
        assertEquals(listOf(0, 2), tabsToKeep(setOf(2), 0, all))
        assertEquals(listOf(0, 1, 2), tabsToKeep(setOf(2, 0), 1, all))
    }

    @Test
    fun mapKeepsBuiltTabsButAddsNothing() {
        val all = listOf(0, 1, 2)
        // Карта (3) не из колоды: собранные вкладки остаются, сама карта в колоду не попадает.
        assertEquals(listOf(0, 1, 2), tabsToKeep(setOf(0, 1, 2), 3, all))
        assertEquals(emptyList<Int>(), tabsToKeep(emptySet(), 3, all))
    }

    @Test
    fun paintsAheadOnlyTabThatIsNotOnScreen() {
        assertEquals(2, offscreenTab(active = 0, painting = 2))
        assertNull(offscreenTab(active = 2, painting = 2))
        assertNull(offscreenTab(active = 0, painting = null))
    }

    @Test
    fun warmsMissingTabsOneByOne() {
        val all = listOf(0, 1, 2)
        assertEquals(1, nextToWarm(setOf(0), all))
        assertEquals(2, nextToWarm(setOf(0, 1), all))
        assertNull(nextToWarm(setOf(0, 1, 2), all))
    }
}
