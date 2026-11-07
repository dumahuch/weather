package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Деление свободного места «Главной» (числа в dp при плотности 1, Pixel 6 Pro: свободно около 155 dp). */
class HeroFitTest {

    private val rows = HeroFit.Rows(row = 42f, more = 38f, gap = 8f, lead = 16f)
    private val edge = 12f
    private val hero = 210f

    /** Свободное место под блоком: должно оставаться не меньше наименьшего промежутка. */
    private fun below(free: Float, plan: HeroFit.Plan): Float {
        val growth = (plan.scale - 1f) * hero
        return free - plan.top - growth - plan.alerts
    }

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
        val three = HeroFit.plan(155f, 3, rows, edge, hero)
        assertTrue(none.scale > one.scale)
        assertTrue(one.scale > two.scale)
        assertTrue(two.scale > three.scale)
        assertEquals(2, two.shown)
        assertFalse(two.more)
    }

    @Test
    fun threeAlertsAreAllShownAndTheBlockShrinksToMakeRoom() {
        val plan = HeroFit.plan(155f, 3, rows, edge, hero)
        assertEquals(3, plan.shown)
        assertFalse(plan.more)
        // Три плашки (16 + 3 * 42 + 2 * 8 = 158) не влезают в 131: блок отдаёт недостающие 27.
        assertEquals(158f, plan.alerts, 0f)
        assertEquals(1f - 27f / hero, plan.scale, 1e-4f)
        assertTrue("блок меньше прежнего", plan.scale < 1f)
        assertTrue("не меньше предела", plan.scale >= HeroFit.MIN_SCALE)
        assertEquals("сверху наименьший промежуток", edge, plan.top, 0.01f)
        assertEquals("снизу наименьший промежуток", edge, below(155f, plan), 0.01f)
    }

    @Test
    fun morePlatesGoUnderMoreAfterThree() {
        val plan = HeroFit.plan(155f, 5, rows, edge, hero)
        assertEquals(3, plan.shown)
        assertTrue(plan.more)
        assertEquals(16f + 3 * 42f + 2 * 8f + 8f + 38f, plan.alerts, 0f)
        assertTrue("блок сжат", plan.scale < 1f)
        assertTrue(plan.scale >= HeroFit.MIN_SCALE)
        assertEquals(edge, below(155f, plan), 0.01f)

        val four = HeroFit.plan(155f, 4, rows, edge, hero)
        assertEquals(3, four.shown)
        assertTrue(four.more)
    }

    @Test
    fun bigScreenShowsThreeAndKeepsTheBlockAtMaxSize() {
        val plan = HeroFit.plan(600f, 5, rows, edge, hero)
        assertEquals(3, plan.shown)
        assertTrue(plan.more)
        assertEquals(HeroFit.MAX_SCALE, plan.scale, 1e-4f)

        val three = HeroFit.plan(600f, 3, rows, edge, hero)
        assertEquals(3, three.shown)
        assertFalse(three.more)
    }

    @Test
    fun fewerPlatesFitWhenEvenTheSmallestBlockLeavesNoRoomForThree() {
        // Свободно 30: даже сжатый до предела блок отдаёт 105, всего 111; три плашки (158) не влезают, одна и «Ещё» (104) да.
        val plan = HeroFit.plan(30f, 3, rows, edge, hero)
        assertEquals(1, plan.shown)
        assertTrue(plan.more)
        assertTrue(plan.scale >= HeroFit.MIN_SCALE)
        assertTrue(plan.scale < 1f)
        assertEquals(edge, plan.top, 0f)

        val single = HeroFit.plan(30f, 1, rows, edge, hero)
        assertEquals(1, single.shown)
        assertFalse(single.more)
    }

    @Test
    fun theBlockNeverShrinksBelowTheTechnicalMinimum() {
        val plan = HeroFit.plan(10f, 3, rows, edge, hero)
        assertEquals(1, plan.shown)
        assertEquals(HeroFit.MIN_SCALE, plan.scale, 1e-6f)
    }

    @Test
    fun withoutAHeroBaseNothingScales() {
        // Блоку нечего отдавать: три плашки (158) не влезают в 131, две и «Ещё 1» (154) тоже, одна и «Ещё 2» (104) да.
        val plan = HeroFit.plan(155f, 3, rows, edge, heroBase = 0f)
        assertEquals(1f, plan.scale, 0f)
        assertEquals(1, plan.shown)
        assertTrue(plan.more)
    }

    @Test
    fun alertsTakeTheSpaceBelowFirstThenAbove() {
        val plan = HeroFit.plan(155f, 2, rows, edge, hero)
        // Под блоком остаётся не меньше наименьшего промежутка, а над ним остальное.
        val below = below(155f, plan)
        assertTrue("снизу $below", below >= edge - 0.01f)
        assertTrue("сверху ${plan.top}", plan.top >= edge)
    }

    @Test
    fun withThreeAlertsTheBlockIsNeverLargerThanUsualAndEverythingFits() {
        // Свободное место от узкого до обычного (Pixel 6 Pro около 155): три плашки всегда видны целиком, блок не больше прежнего.
        var free = 90f
        while (free <= 155f) {
            val plan = HeroFit.plan(free, 3, rows, edge, hero)
            assertEquals("free=$free", 3, plan.shown)
            assertTrue("free=$free масштаб ${plan.scale}", plan.scale <= 1f)
            assertTrue("free=$free масштаб ${plan.scale}", plan.scale >= HeroFit.MIN_SCALE)
            assertTrue("free=$free снизу ${below(free, plan)}", below(free, plan) >= edge - 0.01f)
            assertTrue("free=$free сверху ${plan.top}", plan.top >= edge - 0.01f)
            free += 5f
        }
    }

    @Test
    fun withoutAlertsTheBlockReachesTheNewLimitOnAnAmpleScreen() {
        val plan = HeroFit.plan(400f, 0, rows, edge, hero)
        assertEquals(1.4f, HeroFit.MAX_SCALE, 0f)
        assertEquals(HeroFit.MAX_SCALE, plan.scale, 1e-4f)
        val usual = HeroFit.plan(155f, 0, rows, edge, hero)
        assertTrue("на Pixel 6 Pro без предупреждений блок заметно крупнее прежнего", usual.scale > 1.3f)
    }
}
