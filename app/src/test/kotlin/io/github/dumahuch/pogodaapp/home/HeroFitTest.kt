package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Деление свободного места «Главной» (числа в dp при плотности 1, Pixel 6 Pro: свободно около 155 dp). */
class HeroFitTest {

    private val rows = HeroFit.Rows(row = 46f, more = 38f, gap = 8f, lead = 16f)
    private val edge = 12f
    private val hero = 210f

    @Test
    fun withoutAlertsTheBlockGrowsButNotTooMuch() {
        val plan = HeroFit.plan(free = 155f, count = 0, rows = rows, edge = edge, heroBase = hero)
        assertEquals(0, plan.shown)
        assertFalse(plan.more)
        assertEquals(0f, plan.alerts, 0f)
        assertTrue("блок растёт", plan.scale > 1f)
        assertTrue("не больше предела", plan.scale <= HeroFit.MAX_SCALE + 1e-6f)
        // Рост больше вверх: над блоком меньше места, чем было (77,5), под ним тоже меньше, но строки сдвигаются мало.
        val growth = (plan.scale - 1f) * hero
        assertEquals(155f / 2 - growth * 2 / 3, plan.top, 0.01f)
    }

    @Test
    fun moreAlertsMeanSmallerBlock() {
        val none = HeroFit.plan(155f, 0, rows, edge, hero)
        val one = HeroFit.plan(155f, 1, rows, edge, hero)
        val two = HeroFit.plan(155f, 2, rows, edge, hero)
        assertTrue(none.scale > one.scale)
        assertTrue(one.scale > two.scale)
        assertTrue(two.scale >= 1f)
        assertEquals(2, two.shown)
        assertFalse(two.more)
    }

    @Test
    fun alertsThatDoNotFitGoUnderMore() {
        val plan = HeroFit.plan(155f, 5, rows, edge, hero)
        // Две строки (16 + 46 + 8 + 46 = 116) влезают в 131, две строки и «Ещё» (170) нет: одна строка и «Ещё 4».
        assertEquals(1, plan.shown)
        assertTrue(plan.more)
        assertEquals(16f + 46f + 8f + 38f, plan.alerts, 0f)
        assertEquals("блок не меньше прежнего", 1f, plan.scale, 0.2f)
        assertTrue(plan.scale >= 1f)
    }

    @Test
    fun bigScreenShowsEverything() {
        val plan = HeroFit.plan(600f, 5, rows, edge, hero)
        assertEquals(5, plan.shown)
        assertFalse(plan.more)
        assertEquals(HeroFit.MAX_SCALE, plan.scale, 1e-4f)
    }

    @Test
    fun noRoomAtAllStillShowsOneAndKeepsTheBlockAtNormalSize() {
        val plan = HeroFit.plan(30f, 3, rows, edge, hero)
        assertEquals(1, plan.shown)
        assertTrue(plan.more)
        assertEquals(1f, plan.scale, 0f)
        assertEquals(edge, plan.top, 0f)

        val single = HeroFit.plan(30f, 1, rows, edge, hero)
        assertEquals(1, single.shown)
        assertFalse(single.more)
    }

    @Test
    fun alertsTakeTheSpaceBelowFirstThenAbove() {
        val plan = HeroFit.plan(155f, 2, rows, edge, hero)
        val growth = (plan.scale - 1f) * hero
        // Под блоком остаётся не меньше наименьшего промежутка, а над ним остальное.
        val below = 155f - plan.top - growth - plan.alerts
        assertTrue("снизу $below", below >= edge - 0.01f)
        assertTrue("сверху ${plan.top}", plan.top >= edge)
    }
}
