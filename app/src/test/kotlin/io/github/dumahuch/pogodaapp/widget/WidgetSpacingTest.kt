package io.github.dumahuch.pogodaapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSpacingTest {

    @Test
    fun slackIsSplitBetweenBlocks() {
        val tops = WidgetSpacing.tops(start = 10f, total = 100f, heights = listOf(20f, 20f, 20f))
        assertEquals(listOf(10f, 60f, 110f), tops)
    }

    @Test
    fun oneBlockIsCentred() {
        assertEquals(listOf(40f), WidgetSpacing.tops(start = 10f, total = 100f, heights = listOf(40f)))
    }

    @Test
    fun blocksThatDoNotFitGoOneAfterAnother() {
        assertEquals(listOf(0f, 60f), WidgetSpacing.tops(start = 0f, total = 50f, heights = listOf(60f, 60f)))
    }

    @Test
    fun noBlocksNoTops() {
        assertTrue(WidgetSpacing.tops(0f, 100f, emptyList()).isEmpty())
    }

    @Test
    fun rowsFitTheSpaceAndTheLimit() {
        assertEquals(3, WidgetSpacing.rows(200f, 62f, 5))
        assertEquals(5, WidgetSpacing.rows(900f, 62f, 5))
        assertEquals(0, WidgetSpacing.rows(10f, 62f, 5))
    }
}
