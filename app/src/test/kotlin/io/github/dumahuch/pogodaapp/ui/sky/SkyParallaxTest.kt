package io.github.dumahuch.pogodaapp.ui.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/** Небо следует за экраном, сообщившим последним, и возвращается к предыдущему, когда тот уходит. */
class SkyParallaxTest {

    @Test
    fun skyFallsBackToTheScreenBeneathWhenThePageLeaves() {
        val parallax = SkyParallax()
        val tab = SkyParallax.Source()
        val page = SkyParallax.Source()

        assertEquals(0f, parallax.scrollPx, 0f)
        parallax.report(tab, 120f)
        assertEquals(120f, parallax.scrollPx, 0f)
        parallax.report(page, 300f)
        assertEquals(300f, parallax.scrollPx, 0f)

        parallax.detach(page)
        assertEquals("после закрытия страницы небо возвращается к вкладке", 120f, parallax.scrollPx, 0f)
        parallax.detach(tab)
        assertEquals(0f, parallax.scrollPx, 0f)
    }

    @Test
    fun theScreenThatScrollsLastLeadsEvenIfAnotherOneWasAttachedLater() {
        val parallax = SkyParallax()
        val home = SkyParallax.Source()
        val forecast = SkyParallax.Source()
        parallax.report(home, 0f)
        parallax.report(forecast, 0f)

        parallax.report(home, 80f)
        assertEquals(80f, parallax.scrollPx, 0f)
        parallax.report(home, 95f)
        assertEquals(95f, parallax.scrollPx, 0f)
    }
}
