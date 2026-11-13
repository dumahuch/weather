package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Брызги над кромкой: появляются по ширине и силе дождя, взлетают, падают и гаснут. */
class EdgeSplashTest {

    /** Сколько всплесков началось за [seconds]: новый после шага имеет возраст ровно в один шаг. */
    private fun splashes(width: Double, strength: Double, seconds: Double = 20.0): Int {
        val field = EdgeSplash(width, 5)
        var total = 0
        var t = 0.0
        while (t < seconds) {
            field.step(0.03, strength)
            total += field.splashes.count { it.age < 0.045 }
            t += 0.03
        }
        return total
    }

    @Test
    fun aWiderEdgeAndHeavierRainGiveMoreSplashes() {
        assertTrue(splashes(350.0, 1.0) > splashes(100.0, 1.0) * 2)
        assertTrue(splashes(350.0, 1.0) > splashes(350.0, 0.0))
        assertTrue(splashes(350.0, 0.0) > 0)
    }

    @Test
    fun aSplashFliesUpThenFallsAndFadesOut() {
        val field = EdgeSplash(300.0, 1)
        repeat(30) { field.step(0.03, 1.0) }
        val splash = field.splashes.first()
        assertTrue(splash.x in 10.0..290.0)
        assertEquals(splash.vx.size, splash.vy.size)
        // Сразу после удара частицы ещё у кромки, чуть позже над ней.
        splash.age = 0.0
        assertEquals(0.0, splash.height(0), 1e-9)
        splash.age = 0.08
        assertTrue((0 until splash.vy.size).all { splash.height(it) > 0.0 })
        splash.age = splash.life
        assertEquals(0.0, splash.alpha(), 1e-9)
    }

    @Test
    fun splashesDieAndTheirNumberStaysBounded() {
        val field = EdgeSplash(350.0, 2)
        var most = 0
        repeat(2000) {
            field.step(0.03, 1.0)
            most = maxOf(most, field.splashes.size)
            assertTrue(field.splashes.all { it.age < it.life })
        }
        assertTrue("$most", most in 1..40)
    }

    @Test
    fun aLongPauseIsCutSoNothingJumps() {
        val field = EdgeSplash(350.0, 3)
        field.step(30.0, 1.0)
        assertTrue(field.splashes.size <= 40)
        assertTrue(field.splashes.all { it.age <= 0.1 + 1e-9 })
    }
}
