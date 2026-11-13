package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Иней: с какой температуры появляется и как растёт от краёв ветками. */
class GlassFrostTest {

    @Test
    fun frostStartsAtMinusThreeAsARimAndCoversTheGlassByMinusTen() {
        assertEquals(0.0, GlassFrost.coverage(null), 1e-9)
        assertEquals(0.0, GlassFrost.coverage(2.0), 1e-9)
        assertEquals(0.0, GlassFrost.coverage(-2.9), 1e-9)
        assertEquals(0.25, GlassFrost.coverage(-3.0), 1e-9)
        assertEquals(1.0, GlassFrost.coverage(-10.0), 1e-9)
        assertEquals(1.0, GlassFrost.coverage(-25.0), 1e-9)
        assertTrue(GlassFrost.coverage(-5.0) < GlassFrost.coverage(-7.0))
    }

    private val pattern = FrostPattern(175, 125, 2.0, 1)

    private fun at(x: Double, y: Double) = pattern.threshold[(y / 2).toInt() * 175 + (x / 2).toInt()]

    @Test
    fun theEdgesFreezeFirstAndTheMiddleLast() {
        assertTrue(pattern.threshold.all { it in 0f..1f })
        assertTrue(at(1.0, 125.0) < 0.1f)
        assertTrue(at(175.0, 125.0) > 0.7f)
        // При кайме (−3 °C) середина чистая, по краям иней.
        val rim = pattern.threshold.count { it <= 0.25f }.toDouble() / pattern.threshold.size
        assertTrue("$rim", rim in 0.25..0.7)
        // Матовое стекло (−10 °C): замёрзло всё.
        assertTrue(pattern.threshold.all { it <= 1f })
    }

    @Test
    fun crystalBranchesReachAheadOfTheEvenFrost() {
        val lines = pattern.crystal.count { it > 0f }
        assertTrue("$lines", lines > 400)
        // Где прошла ветка, иней приходит раньше, чем в среднем на том же расстоянии от края.
        val onBranch = pattern.threshold.indices.filter { pattern.crystal[it] > 0f }.map { pattern.threshold[it] }.average()
        val all = pattern.threshold.average()
        assertTrue("$onBranch $all", onBranch < all * 0.8)
    }

    @Test
    fun theSameSeedGivesTheSamePatternAndAnotherGivesAnother() {
        val again = FrostPattern(175, 125, 2.0, 1)
        assertTrue(again.threshold.contentEquals(pattern.threshold))
        assertTrue(!FrostPattern(175, 125, 2.0, 2).crystal.contentEquals(pattern.crystal))
    }
}
