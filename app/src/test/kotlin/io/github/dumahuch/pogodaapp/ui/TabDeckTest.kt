package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabDeckTest {
    @Test
    fun keepsVisitedTabsAndCurrentInFixedOrder() {
        assertEquals(listOf(0), tabsToKeep(emptySet(), 0))
        assertEquals(listOf(0, 2), tabsToKeep(setOf(2), 0))
        assertEquals(listOf(0, 1, 2), tabsToKeep(setOf(2, 0), 1))
    }

    @Test
    fun warmsMissingTabsOneByOne() {
        val all = listOf(0, 1, 2)
        assertEquals(1, nextToWarm(setOf(0), all))
        assertEquals(2, nextToWarm(setOf(0, 1), all))
        assertNull(nextToWarm(setOf(0, 1, 2), all))
    }
}
