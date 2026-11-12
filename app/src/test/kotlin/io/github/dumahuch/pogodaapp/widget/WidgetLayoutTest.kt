package io.github.dumahuch.pogodaapp.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {

    @Test
    fun cellsFollowTheAndroidSizeRule() {
        assertEquals(1, WidgetLayouts.cells(40))
        assertEquals(2, WidgetLayouts.cells(110))
        assertEquals(3, WidgetLayouts.cells(180))
        assertEquals(4, WidgetLayouts.cells(250))
        assertEquals(5, WidgetLayouts.cells(320))
    }

    @Test
    fun launcherRoundingDoesNotChangeTheCellCount() {
        assertEquals(4, WidgetLayouts.cells(245))
        assertEquals(4, WidgetLayouts.cells(262))
        assertEquals(2, WidgetLayouts.cells(104))
        assertEquals(1, WidgetLayouts.cells(10))
    }

    @Test
    fun layoutByCells() {
        assertEquals(WidgetLayout.Tiny, WidgetLayouts.pick(40, 40))
        assertEquals(WidgetLayout.Narrow, WidgetLayouts.pick(110, 40))
        assertEquals(WidgetLayout.Narrow, WidgetLayouts.pick(180, 40))
        assertEquals(WidgetLayout.Strip, WidgetLayouts.pick(250, 40))
        assertEquals(WidgetLayout.Square, WidgetLayouts.pick(110, 110))
        assertEquals(WidgetLayout.Square, WidgetLayouts.pick(180, 250))
        assertEquals(WidgetLayout.Wide, WidgetLayouts.pick(250, 110))
        assertEquals(WidgetLayout.Wide, WidgetLayouts.pick(250, 180))
        assertEquals(WidgetLayout.Large, WidgetLayouts.pick(250, 250))
        assertEquals(WidgetLayout.Large, WidgetLayouts.pick(320, 320))
    }

    @Test
    fun narrowAndTallWidgetsFallBackToTheNearestSmallerOne() {
        assertEquals(WidgetLayout.Tiny, WidgetLayouts.pick(40, 320))
        assertEquals(WidgetLayout.Tiny, WidgetLayouts.pick(40, 110))
    }

    @Test
    fun designSizeFollowsTheMockupGrid() {
        assertEquals(214f, WidgetLayout.Tiny.designWidth, 0f)
        assertEquals(444f, WidgetLayout.Square.designWidth, 0f)
        assertEquals(904f, WidgetLayout.Large.designWidth, 0f)
        assertEquals(904f, WidgetLayout.Large.designHeight, 0f)
        assertEquals(214f, WidgetLayout.Strip.designHeight, 0f)
    }
}
