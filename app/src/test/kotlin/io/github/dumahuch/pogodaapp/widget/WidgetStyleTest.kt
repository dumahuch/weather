package io.github.dumahuch.pogodaapp.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleTest {

    @Test
    fun unknownBackgroundKeyMeansTheSky() {
        assertEquals(WidgetBackground.Sky, WidgetBackground.fromKey(null))
        assertEquals(WidgetBackground.Sky, WidgetBackground.fromKey("???"))
        assertEquals(WidgetBackground.Glass, WidgetBackground.fromKey("glass"))
        assertEquals(WidgetBackground.None, WidgetBackground.fromKey("none"))
    }

    @Test
    fun detailByIndexFallsBackToUsual() {
        assertEquals(WidgetDetail.Minimum, WidgetDetail.fromIndex(0))
        assertEquals(WidgetDetail.Normal, WidgetDetail.fromIndex(1))
        assertEquals(WidgetDetail.Full, WidgetDetail.fromIndex(2))
        assertEquals(WidgetDetail.Normal, WidgetDetail.fromIndex(7))
        assertEquals(WidgetDetail.Normal, WidgetDetail.fromIndex(-1))
    }

    @Test
    fun defaultsAreTheSkyAndUsualDetail() {
        val style = WidgetStyle()
        assertEquals(WidgetBackground.Sky, style.background)
        assertEquals(WidgetDetail.Normal, style.detail)
        assertEquals(WidgetStyle.DEFAULT_SHADE, style.shade)
    }
}
