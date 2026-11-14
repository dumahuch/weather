package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Контур капли: без хвоста круг, с хвостом снизу круглая и широкая, сверху сужается; симметрична. */
class DropOutlineTest {

    @Test
    fun withoutATailItIsACircle() {
        val p = DropOutline.points(0.0)
        for (i in 0 until DropOutline.POINTS) assertEquals(1.0, hypot(p[2 * i], p[2 * i + 1]), 1e-9)
    }

    @Test
    fun theTopIsAtMinusOneTheBottomAtPlusOneAndTheWidestPartIsExactlyOne() {
        for (tail in listOf(0.0, 0.5, 1.0, 1.4)) {
            val p = DropOutline.points(tail)
            assertEquals(0.0, p[0], 1e-9)
            assertEquals(-1.0, p[1], 1e-9)
            val bottom = DropOutline.POINTS / 2
            assertEquals(0.0, p[2 * bottom], 1e-9)
            assertEquals(1.0, p[2 * bottom + 1], 1e-9)
            assertEquals(1.0, (0 until DropOutline.POINTS).maxOf { abs(p[2 * it]) }, 1e-9)
        }
    }

    @Test
    fun aDropWithATailIsWiderBelowThanAboveTheCentre() {
        for (tail in listOf(0.3, 0.8, 1.4)) {
            val p = DropOutline.points(tail)
            val upper = (0 until DropOutline.POINTS).filter { p[2 * it + 1] < -0.2 }.sumOf { abs(p[2 * it]) }
            val lower = (0 until DropOutline.POINTS).filter { p[2 * it + 1] > 0.2 }.sumOf { abs(p[2 * it]) }
            assertTrue("$tail $upper $lower", lower > upper * 1.2)
        }
    }

    @Test
    fun theLongerTheTailTheNarrowerTheTop() {
        fun upperWidth(tail: Double): Double {
            val p = DropOutline.points(tail)
            return (0 until DropOutline.POINTS).filter { p[2 * it + 1] < -0.5 }.maxOf { abs(p[2 * it]) }
        }
        assertTrue(upperWidth(0.4) > upperWidth(0.9))
        assertTrue(upperWidth(0.9) > upperWidth(1.4))
    }

    @Test
    fun theOutlineIsMirrorSymmetric() {
        val p = DropOutline.points(1.1)
        for (i in 1 until DropOutline.POINTS) {
            val j = DropOutline.POINTS - i
            assertEquals(p[2 * i], -p[2 * j], 1e-9)
            assertEquals(p[2 * i + 1], p[2 * j + 1], 1e-9)
        }
    }
}
