package io.github.dumahuch.pogodaapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyPlanTest {
    private val dh = 0.72f

    /** Клетка Pixel 62×94 dp в единицах макета: 214 × 324. */
    private val pixelHeight = 324f

    private fun check(plan: TinyPlan.Plan, iconHeight: Float) {
        val digitsTop = plan.digitsBase - plan.digitSize * dh
        // Цифра (со значком) ниже города и выше максимума с минимумом, подписи на своих полях.
        assertTrue("город над значком", plan.iconTop >= plan.cityBase)
        assertTrue("цифра над максимумом и минимумом", plan.digitsBase <= plan.hiLoBase - TinyPlan.LABEL * dh)
        assertEquals(plan.iconTop + iconHeight, digitsTop, 0.01f)
        assertEquals(TinyPlan.PAD + TinyPlan.LABEL * dh, plan.cityBase, 0.01f)
        assertEquals(pixelHeight - TinyPlan.PAD, plan.hiLoBase, 0.01f)
    }

    @Test
    fun skyDigitsAreCenteredAtFullSize() {
        val plan = TinyPlan.plan(pixelHeight, 150f, 0f, dh)
        check(plan, 0f)
        assertEquals(150f, plan.digitSize, 0.01f)
        val center = (plan.iconTop + plan.digitsBase) / 2
        assertEquals(pixelHeight / 2, center, 0.01f)
    }

    @Test
    fun iconTakesRoomFromDigitsInsteadOfOverlappingLabels() {
        val plan = TinyPlan.plan(pixelHeight, 150f, 66f, dh)
        check(plan, 66f)
        assertTrue(plan.digitSize < 150f)
        assertTrue(plan.digitSize > 100f)
    }

    @Test
    fun narrowWidthKeepsTheSmallerDigits() {
        val plan = TinyPlan.plan(pixelHeight, 90f, 0f, dh)
        assertEquals(90f, plan.digitSize, 0.01f)
        check(plan, 0f)
    }

    @Test
    fun shortCanvasShrinksDigitsButKeepsOrder() {
        val plan = TinyPlan.plan(214f, 150f, 66f, dh)
        assertTrue(plan.digitSize >= 60f)
        assertTrue(plan.iconTop >= plan.cityBase)
    }
}
