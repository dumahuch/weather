package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Капли на стекле карточки: набегают до предела, созревшие стекают со следом, след гаснет. Стекло вертикальное: капли не
 * висят обрезанными у кромок, а вода внизу уходит на карточку ниже или собирается у кромки и отрывается.
 */
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

    @Test
    fun dropsNeverSitCutByTheEdgesOrCrossTheirOwnRoundedCorners() {
        val glass = rain(40.0)
        for (b in glass.beads) {
            assertTrue("${b.x} ${b.y} ${b.r}", b.x >= b.r - 1e-6 && b.x <= 350.0 - b.r + 1e-6)
            assertTrue("${b.x} ${b.y} ${b.r}", b.y >= b.r - 1e-6 && b.y <= 250.0 - b.r + 1e-6)
            assertTrue("${b.x} ${b.y} ${b.r}", glass.fits(b.x, b.y, b.r - 1e-6))
        }
        assertFalse(glass.fits(3.0, 3.0, 3.0))
        assertTrue(glass.fits(175.0, 125.0, 3.0))
    }

    @Test
    fun runnersKeepAwayFromTheSides() {
        val glass = GlassRain(350.0, 250.0, 6)
        var t = 0.0
        while (t < 40.0) {
            glass.step(0.03, 1.0)
            for (r in glass.runners) assertTrue("${r.x}", r.x >= r.r - 1e-6 && r.x <= 350.0 - r.r + 1e-6)
            t += 0.03
        }
    }

    @Test
    fun waterCollectsAtTheBottomEdgeWhenNothingIsBelow() {
        val glass = rain(60.0)
        val pooled = glass.beads.filter { it.pooled }
        assertTrue(pooled.isNotEmpty())
        for (b in pooled) {
            assertEquals(250.0, b.y + b.r, 1e-6)
            assertTrue("${b.r}", b.r < GlassRain.POOL_LIMIT)
        }
        assertTrue(glass.runners.all { it.y < 250.0 + it.r * 3 })
    }

    @Test
    fun aHeavyDropAtTheEdgeBreaksOffAndFallsAway() {
        val glass = GlassRain(350.0, 250.0, 2)
        // Сначала капля у кромки, потом ей добавляют воды, пока не оторвётся.
        glass.receive(120.0, 4.0, 0.0)
        var falling = false
        var t = 0.0
        while (t < 120.0 && !falling) {
            glass.step(0.03, 0.0)
            if (glass.beads.none { it.pooled && it.x in 100.0..140.0 }) falling = glass.runners.any { it.dripping }
            if (glass.runners.none { it.x in 100.0..140.0 && it.y > 200.0 }) glass.receive(120.0, 4.5, 0.0)
            t += 0.03
        }
        assertTrue(falling)
        repeat(200) { glass.step(0.03, 0.0) }
        assertTrue(glass.runners.none { it.dripping && it.y > 250.0 + it.r * 3 })
    }

    @Test
    fun waterThatReachesTheBottomGoesToTheCardBelowWhenThereIsOne() {
        val glass = GlassRain(350.0, 250.0, 3)
        val given = ArrayList<Pair<Double, Double>>()
        glass.spill = { x, r ->
            given += x to r
            true
        }
        var t = 0.0
        while (t < 60.0) {
            glass.step(0.03, 1.0)
            t += 0.03
        }
        assertTrue(given.isNotEmpty())
        assertTrue(given.all { (x, r) -> x in r..350.0 - r && r >= 3.0 })
        assertTrue(glass.beads.none { it.pooled })
    }

    @Test
    fun waterFromAboveLandsOnTheTopEdgeAndRunsDownAfterItsFall() {
        val glass = GlassRain(350.0, 250.0, 8)
        glass.receive(100.0, 4.0, 0.25)
        repeat(4) { glass.step(0.05, 0.0) }
        assertTrue(glass.runners.none { it.x in 99.0..101.0 })
        repeat(2) { glass.step(0.05, 0.0) }
        val landed = glass.runners.single { it.x in 99.0..101.0 }
        assertTrue(landed.y < 12.0 && landed.r > 3.9)
        repeat(20) { glass.step(0.05, 0.0) }
        assertTrue(glass.runners.none { it === landed } || landed.y > 12.0)
    }

    @Test
    fun theStretchOfARunnerIsZeroWhenItStandsAndGrowsWithSpeed() {
        val glass = GlassRain(350.0, 250.0, 1)
        val standing = GlassRain.Runner(100.0, 50.0, 4.0, 1.0, 0.0).also { it.moving = false }
        assertEquals(0.0, glass.stretch(standing), 1e-9)
        val slow = GlassRain.Runner(100.0, 50.0, 4.0, 1.0, 0.0).also { it.v = 20.0 }
        val fast = GlassRain.Runner(100.0, 50.0, 4.0, 1.0, 0.0).also { it.v = 100.0 }
        assertTrue(glass.stretch(slow) in 0.0..glass.stretch(fast))
        assertEquals(1.0, glass.stretch(GlassRain.Runner(1.0, 1.0, 4.0, 1.0, 0.0).also { it.v = 9999.0 }), 1e-9)
    }

    @Test
    fun aNarrowButtonKeepsItsDropsInsideToo() {
        val button = GlassRain(52.0, 52.0, 2)
        var t = 0.0
        while (t < 30.0) {
            button.step(0.03, 1.0)
            t += 0.03
        }
        for (b in button.beads) assertTrue(b.x >= b.r - 1e-6 && b.x <= 52.0 - b.r + 1e-6)
    }
}
