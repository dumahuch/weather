package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Капли на стекле карточки: набегают до предела, созревшие стекают со следом, след гаснет. */
class GlassRainTest {

    private fun rain(seconds: Double, strength: Double = 1.0, seed: Long = 3): GlassRain {
        val glass = GlassRain(350.0, 250.0, seed)
        var t = 0.0
        while (t < seconds) {
            glass.step(0.03, strength)
            t += 0.03
        }
        return glass
    }

    @Test
    fun theGlassStartsDryAndFillsUpToItsCapacityButNotBeyond() {
        assertTrue(GlassRain(350.0, 250.0, 1).beads.isEmpty())
        val glass = rain(30.0)
        val cap = glass.capacity(1.0)
        assertEquals(GlassRain.MAX_BEADS, cap)
        // Часть капель стекающие вобрали, остальные почти заполнили стекло.
        assertTrue("${glass.beads.size}", glass.beads.size in cap / 2..cap + 20)
    }

    @Test
    fun weakRainLeavesFewerDropsAndASmallCardHoldsFewer() {
        assertTrue(rain(30.0, 0.0).beads.size < rain(30.0, 1.0).beads.size)
        assertTrue(GlassRain(175.0, 125.0, 1).capacity(1.0) < GlassRain(350.0, 250.0, 1).capacity(1.0) / 3)
    }

    @Test
    fun waterRunsDownFromTheTopEdgeAndSoonerWhenTheRainIsHeavy() {
        fun fromEdge(strength: Double): Int {
            val glass = GlassRain(350.0, 250.0, 4)
            var count = 0
            var t = 0.0
            while (t < 20.0) {
                val before = glass.runners.toSet()
                glass.step(0.03, strength)
                count += glass.runners.count { it !in before && it.y < 8.0 }
                t += 0.03
            }
            return count
        }
        assertTrue(fromEdge(1.0) >= 6)
        assertTrue(fromEdge(1.0) > fromEdge(0.0))
    }

    @Test
    fun aTallCardIsCappedSoTheFrameStaysCheap() {
        assertTrue(GlassRain(360.0, 2000.0, 1).capacity(1.0) <= GlassRain.MAX_BEADS * 3 / 2)
    }

    @Test
    fun dropsStayOnTheGlassAndAreNotTooBig() {
        val glass = rain(20.0)
        for (b in glass.beads) {
            assertTrue(b.x in -10.0..360.0 && b.y in -10.0..260.0)
            assertTrue(b.r in 0.7..5.0)
        }
    }

    @Test
    fun ripeDropsRunDownAndLeaveAWetTrail() {
        val glass = rain(25.0)
        assertTrue(glass.runners.isNotEmpty() || glass.trails.isNotEmpty())
        val runner = glass.runners.firstOrNull() ?: return
        val before = runner.y
        repeat(100) { glass.step(0.03, 1.0) }
        assertTrue(runner.y > before || runner !in glass.runners)
        assertTrue(glass.trails.all { it.y2 >= it.y1 })
    }

    @Test
    fun theTrailFadesAndIsDroppedAfterItsLife() {
        val glass = GlassRain(350.0, 250.0, 1)
        assertEquals(1.0, glass.trailAlpha(0.0), 1e-9)
        assertEquals(0.0, glass.trailAlpha(GlassRain.TRAIL_LIFE), 1e-9)
        val wet = rain(25.0)
        repeat(((GlassRain.TRAIL_LIFE + 1) / 0.03).toInt()) { wet.step(0.03, 1.0) }
        assertTrue(wet.trails.all { it.age <= GlassRain.TRAIL_LIFE })
    }

    @Test
    fun aLongPauseIsCutSoNothingJumps() {
        val a = GlassRain(350.0, 250.0, 5)
        val b = GlassRain(350.0, 250.0, 5)
        a.step(10.0, 1.0)
        b.step(0.1, 1.0)
        assertEquals(b.beads.size, a.beads.size)
    }

    @Test
    fun theSameSeedGivesTheSameGlass() {
        val a = rain(10.0, seed = 9)
        val b = rain(10.0, seed = 9)
        assertEquals(a.beads.map { it.x to it.r }, b.beads.map { it.x to it.r })
    }
}
