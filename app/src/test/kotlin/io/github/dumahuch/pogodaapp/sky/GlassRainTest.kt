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
            // Валик у кромки считается по высоте: длинный вмещает объём больше одной капли.
            if (b.pooled) assertTrue(glass.halfHeight(b) in 0.5..GlassRain.POOL_HEIGHT_LIMIT) else assertTrue(b.r in 0.7..5.0)
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
            if (b.pooled) {
                // Валик у кромки лежит на ней и шире капли, но боком кромку не пересекает.
                val half = glass.halfWidth(b)
                assertTrue("${b.x} ${b.r}", b.x >= half - 1e-6 && b.x <= 350.0 - half + 1e-6)
                continue
            }
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
            assertEquals(250.0, b.y + GlassRain.POOL_SIT * glass.halfHeight(b), 1e-6)
            assertTrue("${glass.halfHeight(b)}", glass.halfHeight(b) < GlassRain.POOL_HEIGHT_LIMIT)
            // Валик ниже и шире круглой капли такого объёма.
            assertTrue(glass.halfWidth(b) >= b.r && glass.halfHeight(b) <= b.r)
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
        for (b in button.beads) assertTrue(b.x >= button.halfWidth(b) - 1e-6 && b.x <= 52.0 - button.halfWidth(b) + 1e-6)
    }

    @Test
    fun touchingDropsMergeIntoOneAndFlattenForAMoment() {
        val glass = GlassRain(350.0, 250.0, 1)
        glass.beads += GlassRain.Bead(100.0, 100.0, 3.0)
        glass.beads += GlassRain.Bead(104.0, 100.0, 3.0)
        glass.beads += GlassRain.Bead(200.0, 100.0, 3.0)
        val volume = 3.0 * 3.0 * 3.0 * 2
        glass.step(0.1, 0.0)
        glass.step(0.1, 0.0)
        val merged = glass.beads.single { it.x in 95.0..110.0 && it.y in 97.0..103.0 }
        assertTrue(glass.beads.any { it.x in 199.0..201.0 })
        assertTrue("${merged.r}", merged.r in 3.5..Math.cbrt(volume) + 0.01)
        assertEquals(102.0, merged.x, 0.01)
        assertTrue(merged.squash > 0.0 && merged.squashSideways)
        repeat(4) { glass.step(0.1, 0.0) }
        assertTrue("${merged.squash}", merged.squash < 0.95)
    }

    @Test
    fun anOverheavyDropFallsAwayInsteadOfStayingHuge() {
        val glass = GlassRain(350.0, 250.0, 1)
        glass.beads += GlassRain.Bead(100.0, 100.0, 4.4)
        glass.beads += GlassRain.Bead(105.0, 100.0, 4.4)
        glass.step(0.1, 0.0)
        glass.step(0.1, 0.0)
        assertTrue(glass.beads.none { it.r >= 5.0 })
        assertTrue(glass.runners.any { it.x in 95.0..110.0 })
    }

    @Test
    fun touchingWaterAtTheEdgeJoinsIntoOneWiderCushion() {
        val glass = GlassRain(350.0, 250.0, 1)
        val a = GlassRain.Bead(100.0, 249.0, 2.0).also { it.pooled = true; it.span = 4.6 }
        val b = GlassRain.Bead(104.0, 249.0, 2.0).also { it.pooled = true; it.span = 4.6 }
        glass.beads += a
        glass.beads += b
        val width = glass.halfWidth(a)
        glass.step(0.1, 0.0)
        glass.step(0.1, 0.0)
        val joined = glass.beads.filter { it.pooled }
        assertEquals(1, joined.size)
        assertTrue(glass.halfWidth(joined.single()) > width)
        assertEquals(250.0, joined.single().y + GlassRain.POOL_SIT * glass.halfHeight(joined.single()), 1e-6)
    }

    @Test
    fun aCushionSwellsIntoARoundDropBeforeItBreaksOff() {
        val glass = GlassRain(350.0, 250.0, 1)
        val small = GlassRain.Bead(100.0, 249.0, 2.0).also { it.pooled = true; it.span = 4.6 }
        val heavy = GlassRain.Bead(100.0, 249.0, 4.4).also { it.pooled = true; it.span = 4.4 }
        assertTrue(glass.halfWidth(small) / glass.halfHeight(small) > glass.halfWidth(heavy) / glass.halfHeight(heavy))
        assertTrue(glass.halfWidth(heavy) / glass.halfHeight(heavy) < 1.5)
    }

    @Test
    fun restingDropsDifferInShape() {
        val glass = rain(20.0)
        val shapes = glass.beads.filter { !it.pooled }.map { Triple((it.form * 4).toInt(), (it.wide * 8).toInt(), (it.tilt / 5).toInt()) }.toSet()
        assertTrue("${shapes.size}", shapes.size >= 8)
        assertTrue(glass.beads.all { it.tilt in -14.0..14.0 && it.wide in 0.9..1.3 && it.form in 0.2..1.5 })
    }

    @Test
    fun aSmallFlatGlassHasNoRunningDropsAndFewerDrops() {
        val bar = GlassRain(300.0, 56.0, 4)
        var t = 0.0
        while (t < 60.0) {
            bar.step(0.03, 1.0)
            assertTrue(bar.runners.all { it.dripping })
            t += 0.03
        }
        assertTrue(bar.compact)
        assertTrue(bar.beads.isNotEmpty())
        assertTrue(bar.beads.count { it.pooled } >= 1)
        val area = 300.0 * 56.0 / (350.0 * 250.0)
        assertTrue(bar.capacity(1.0) < GlassRain.MAX_BEADS * area)
        assertTrue(bar.beads.size <= bar.capacity(1.0) + 5)
    }

    @Test
    fun aTallCardIsNotCompact() {
        assertTrue(GlassRain(350.0, 250.0, 1).compact.not())
        assertTrue(GlassRain(52.0, 52.0, 1).compact)
    }

    @Test
    fun waterFromAboveOnASmallGlassStaysAsADropAtTheTopEdge() {
        val bar = GlassRain(300.0, 56.0, 4)
        bar.receive(100.0, 4.0, 0.1)
        repeat(6) { bar.step(0.05, 0.0) }
        assertTrue(bar.runners.isEmpty())
        assertTrue(bar.beads.any { it.x in 99.0..101.0 && it.y < 8.0 })
    }
}
