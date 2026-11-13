package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Форма сугроба на карточке: высота по силе снега, края тоньше, низ по скруглению. */
class SnowDriftShapeTest {

    @Test
    fun heavierSnowGrowsTheDriftFasterAndItTakesFromHalfAMinuteToAMinuteAndAHalf() {
        assertEquals(90.0, SnowDriftShape.MAX_HEIGHT_DP / SnowDriftShape.growDpPerSecond(0.0), 1e-9)
        assertEquals(25.0, SnowDriftShape.MAX_HEIGHT_DP / SnowDriftShape.growDpPerSecond(1.0), 1e-9)
        assertEquals(SnowDriftShape.growDpPerSecond(1.0), SnowDriftShape.growDpPerSecond(3.0), 1e-9)
        assertEquals(SnowDriftShape.growDpPerSecond(0.0), SnowDriftShape.growDpPerSecond(-1.0), 1e-9)
        assertTrue(SnowDriftShape.growDpPerSecond(0.3) < SnowDriftShape.growDpPerSecond(0.6))
        // Средний снег (как на экране проверки): около минуты.
        val middle = SnowDriftShape.MAX_HEIGHT_DP / SnowDriftShape.growDpPerSecond(0.5)
        assertTrue(middle in 50.0..65.0)
    }

    @Test
    fun theDriftIsThinAtTheEdgesAndFullInTheMiddle() {
        val width = 350.0
        assertEquals(0.12, SnowDriftShape.taper(0.0, width), 1e-9)
        assertEquals(0.12, SnowDriftShape.taper(width, width), 1e-9)
        assertEquals(1.0, SnowDriftShape.taper(width / 2, width), 1e-9)
        assertEquals(SnowDriftShape.taper(30.0, width), SnowDriftShape.taper(width - 30.0, width), 1e-9)
        assertTrue(SnowDriftShape.taper(10.0, width) < SnowDriftShape.taper(30.0, width))
    }

    @Test
    fun aNarrowCardNeverGetsAFullHeightOrANegativeOne() {
        for (x in 0..40) {
            val v = SnowDriftShape.taper(x.toDouble(), 40.0)
            assertTrue("$x $v", v in 0.12..1.0)
        }
    }

    @Test
    fun theTopIsUnevenButNeverFlatOrAboveTheLimit() {
        for (seed in 0..20) {
            val values = (0..350).map { SnowDriftShape.profile(it.toDouble(), seed) }
            assertTrue(values.all { it in 0.25..1.0 })
            assertTrue(values.max() - values.min() > 0.1)
        }
    }

    @Test
    fun differentCardsGetDifferentDriftsAndTheSameSeedRepeats() {
        val a = (0..100).map { SnowDriftShape.profile(it.toDouble(), 1) }
        assertEquals(a, (0..100).map { SnowDriftShape.profile(it.toDouble(), 1) })
        assertTrue(a != (0..100).map { SnowDriftShape.profile(it.toDouble(), 2) })
    }

    @Test
    fun theBottomFollowsTheRoundedCorner() {
        val width = 350.0
        val radius = 22.0
        assertEquals(radius, SnowDriftShape.edgeDrop(0.0, width, radius), 1e-9)
        assertEquals(radius, SnowDriftShape.edgeDrop(width, width, radius), 1e-9)
        assertEquals(0.0, SnowDriftShape.edgeDrop(radius, width, radius), 1e-9)
        assertEquals(0.0, SnowDriftShape.edgeDrop(width / 2, width, radius), 1e-9)
        val drops = (0..22).map { SnowDriftShape.edgeDrop(it.toDouble(), width, radius) }
        assertTrue(drops.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun aSquareCardHasNoDropAndOutsidePointsAreClamped() {
        assertEquals(0.0, SnowDriftShape.edgeDrop(0.0, 100.0, 0.0), 1e-9)
        assertEquals(22.0, SnowDriftShape.edgeDrop(-5.0, 100.0, 22.0), 1e-9)
    }

    @Test
    fun meltingIsFasterThanTheSlowestGrowth() {
        assertTrue(SnowDriftShape.MELT_DP_PER_SECOND > SnowDriftShape.growDpPerSecond(0.0))
    }
}
