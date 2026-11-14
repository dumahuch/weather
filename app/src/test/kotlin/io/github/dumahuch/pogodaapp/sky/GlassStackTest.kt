package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Вода с нижней кромки падает на ближайшую карточку снизу, если она рядом и под этой точкой. */
class GlassStackTest {

    private fun pane(left: Double, top: Double, width: Double, height: Double, density: Double = 1.0) =
        GlassStack.Pane().also {
            it.left = left
            it.top = top
            it.density = density
            it.glass = GlassRain(width, height, 1)
        }

    @Test
    fun waterFallsOnTheCardJustBelowAtTheSameHorizontalPlace() {
        val stack = GlassStack()
        val upper = pane(20.0, 100.0, 300.0, 100.0)
        val lower = pane(20.0, 212.0, 300.0, 80.0)
        stack.add(upper)
        stack.add(lower)
        assertTrue(stack.spill(upper, 150.0, 4.0))
        // Через падение через просвет 12 dp вода уже на верхней кромке нижней карточки в той же точке.
        repeat(5) { lower.glass!!.step(0.04, 0.0) }
        assertTrue(lower.glass!!.runners.any { kotlin.math.abs(it.x - 150.0) < 1.0 && it.y < 12.0 })
    }

    @Test
    fun theLowestCardOfTheStackKeepsItsWater() {
        val stack = GlassStack()
        val upper = pane(20.0, 100.0, 300.0, 100.0)
        val lower = pane(20.0, 212.0, 300.0, 80.0)
        stack.add(upper)
        stack.add(lower)
        assertFalse(stack.spill(lower, 150.0, 4.0))
    }

    @Test
    fun aBigGapAHigherCardOrAnotherColumnTakesNothing() {
        val stack = GlassStack()
        val upper = pane(20.0, 100.0, 300.0, 100.0)
        stack.add(upper)
        val far = pane(20.0, 200.0 + GlassStack.MAX_GAP_DP + 5, 300.0, 80.0)
        stack.add(far)
        assertFalse(stack.spill(upper, 150.0, 4.0))
        stack.remove(far)
        val above = pane(20.0, 0.0, 300.0, 80.0)
        stack.add(above)
        assertFalse(stack.spill(upper, 150.0, 4.0))
        stack.remove(above)
        val aside = pane(330.0, 212.0, 100.0, 80.0)
        stack.add(aside)
        assertFalse(stack.spill(upper, 150.0, 4.0))
        stack.remove(aside)
        val besideAtSameHeight = pane(330.0, 100.0, 100.0, 100.0)
        stack.add(besideAtSameHeight)
        assertFalse(stack.spill(upper, 310.0, 4.0))
    }

    @Test
    fun theNearestOfSeveralCardsBelowWinsAndAPaneWithoutGlassIsSkipped() {
        val stack = GlassStack()
        val upper = pane(0.0, 0.0, 300.0, 100.0)
        val near = pane(0.0, 108.0, 300.0, 50.0)
        val farther = pane(0.0, 120.0, 300.0, 50.0)
        val dry = pane(0.0, 104.0, 300.0, 50.0).also { it.glass = null }
        listOf(upper, farther, dry, near).forEach(stack::add)
        assertTrue(stack.spill(upper, 100.0, 4.0))
        repeat(5) { near.glass!!.step(0.04, 0.0) }
        assertTrue(near.glass!!.runners.isNotEmpty())
        assertTrue(farther.glass!!.runners.isEmpty())
    }

    @Test
    fun theDensityOfTheScreenIsTakenIntoAccount() {
        val stack = GlassStack()
        // 3 px на dp: карточки в пикселях окна, просвет 12 dp = 36 px.
        val upper = pane(60.0, 300.0, 300.0, 100.0, 3.0)
        val lower = pane(60.0, 300.0 + 300.0 + 36.0, 300.0, 80.0, 3.0)
        stack.add(upper)
        stack.add(lower)
        assertTrue(stack.spill(upper, 150.0, 4.0))
        // Точка у самого края нижней карточки: капле там не уместиться, воду она не берёт.
        assertFalse(stack.spill(upper, 0.5, 4.0))
        assertEquals(300.0, lower.glass!!.widthDp, 1e-9)
    }
}
