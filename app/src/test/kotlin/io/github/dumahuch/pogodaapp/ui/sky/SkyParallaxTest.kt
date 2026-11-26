package io.github.dumahuch.pogodaapp.ui.sky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Небо следует за экраном, сообщившим последним; при смене экрана оно не прыгает, а остаётся где было (дальше его ведёт
 * [SkyParallax.settle]); когда поправка погашена, небо на прокрутке верхнего экрана.
 */
class SkyParallaxTest {

    @Test
    fun skyFallsBackToTheScreenBeneathWhenThePageLeaves() {
        val parallax = SkyParallax()
        val tab = SkyParallax.Source()
        val page = SkyParallax.Source()

        assertEquals(0f, parallax.scrollPx, 0f)
        parallax.report(tab, 120f)
        parallax.finishSettling()
        assertEquals(120f, parallax.scrollPx, 0f)
        parallax.report(page, 300f)
        parallax.finishSettling()
        assertEquals(300f, parallax.scrollPx, 0f)

        parallax.detach(page)
        parallax.finishSettling()
        assertEquals("после закрытия страницы небо возвращается к вкладке", 120f, parallax.scrollPx, 0f)
        parallax.detach(tab)
        parallax.finishSettling()
        assertEquals(0f, parallax.scrollPx, 0f)
    }

    @Test
    fun skyDoesNotJumpWhenThePageOpensOrCloses() {
        val parallax = SkyParallax()
        val tab = SkyParallax.Source()
        val page = SkyParallax.Source()
        parallax.report(tab, 300f)
        parallax.finishSettling()

        // Страница открылась с прокруткой 0: небо остаётся на 300, а не прыгает в 0.
        parallax.report(page, 0f)
        assertEquals(300f, parallax.scrollPx, 0f)
        // Страницу прокрутили на 120: небо идёт вместе с пальцем от того места, где оно было.
        parallax.report(page, 120f)
        assertEquals(420f, parallax.scrollPx, 0f)
        // Страница закрылась: небо там же, где было, и только потом едет к 300.
        parallax.detach(page)
        assertEquals(420f, parallax.scrollPx, 0f)
        parallax.finishSettling()
        assertEquals(300f, parallax.scrollPx, 0f)
    }

    @Test
    fun theScreenThatScrollsLastLeadsEvenIfAnotherOneReportedLater() {
        val parallax = SkyParallax()
        val home = SkyParallax.Source()
        val forecast = SkyParallax.Source()
        parallax.report(home, 0f)
        parallax.report(forecast, 0f)

        parallax.report(home, 80f)
        parallax.finishSettling()
        assertEquals(80f, parallax.scrollPx, 0f)
        parallax.report(home, 95f)
        assertEquals(95f, parallax.scrollPx, 0f)
    }
}
