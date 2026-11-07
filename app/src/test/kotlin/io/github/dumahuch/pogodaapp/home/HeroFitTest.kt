package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Деление свободного места «Главной» (числа в dp при плотности 1). С step-40 место над цифрами не зарезервировано,
 * растёт только строка цифр (112 dp), и на Pixel 6 Pro свободно около 253 dp (было 155 с пустой полосой 98 dp).
 */
class HeroFitTest {

    private val rows = HeroFit.Rows(row = 42f, more = 38f, gap = 8f, lead = 16f)
    private val edge = 12f
    private val hero = 112f

    /** Свободное место под блоком: должно оставаться не меньше наименьшего промежутка. */
    private fun below(free: Float, plan: HeroFit.Plan): Float {
        val growth = (plan.scale - 1f) * hero
        return free - plan.top - growth - plan.alerts
    }

    @Test
    fun withoutAlertsTheBlockFillsTheSpaceUpToTheLimitAndTheRestSplitsEvenly() {
        val plan = HeroFit.plan(free = 253f, count = 0, rows = rows, edge = edge, heroBase = hero)
        assertEquals(0, plan.shown)
        assertFalse(plan.more)
        assertEquals(0f, plan.alerts, 0f)
        assertEquals("на Pixel 6 Pro блок упирается в предел", HeroFit.MAX_SCALE, plan.scale, 1e-4f)
        val growth = (plan.scale - 1f) * hero
        assertEquals((253f - growth) / 2, plan.top, 0.01f)
        assertEquals("над и под блоком поровну", plan.top, below(253f, plan), 0.01f)
    }

    @Test
    fun onAShorterScreenTheBlockTakesItsShareAndTheRestSplitsEvenly() {
        val plan = HeroFit.plan(140f, 0, rows, edge, hero)
        // Рост 60 % от (140 − 24) = 69,6, это меньше предела (78,4).
        assertEquals(1f + 69.6f / hero, plan.scale, 1e-4f)
        assertEquals((140f - 69.6f) / 2, plan.top, 0.01f)
        assertEquals(plan.top, below(140f, plan), 0.01f)
    }

    @Test
    fun theWidthLimitCapsTheGrowth() {
        val capped = HeroFit.plan(253f, 0, rows, edge, hero, maxScale = 1.3f)
        assertEquals(1.3f, capped.scale, 1e-4f)
        assertEquals((253f - 0.3f * hero) / 2, capped.top, 0.01f)
        // Предел по ширине выше общего предела ничего не добавляет.
        val loose = HeroFit.plan(253f, 0, rows, edge, hero, maxScale = 3f)
        assertEquals(HeroFit.MAX_SCALE, loose.scale, 1e-4f)
    }

    @Test
    fun moreAlertsMeanSmallerBlock() {
        val none = HeroFit.plan(140f, 0, rows, edge, hero)
        val one = HeroFit.plan(140f, 1, rows, edge, hero)
        val two = HeroFit.plan(140f, 2, rows, edge, hero)
        val three = HeroFit.plan(140f, 3, rows, edge, hero)
        assertTrue(none.scale > one.scale)
        assertTrue(one.scale > two.scale)
        assertTrue(two.scale > three.scale)
        assertEquals(2, two.shown)
        assertFalse(two.more)
    }

    @Test
    fun oneAlertKeepsTheBlockAlmostAsLargeAndMovesItUp() {
        val none = HeroFit.plan(140f, 0, rows, edge, hero)
        val one = HeroFit.plan(140f, 1, rows, edge, hero)
        assertEquals(1, one.shown)
        // Одна плашка (16 + 42 = 58) оставляет 58 из 116: блок теряет только то, что не помещается (11,6 из 69,6).
        assertEquals(1f + 58f / hero, one.scale, 1e-4f)
        assertTrue("почти прежний размер", none.scale - one.scale < 0.11f)
        assertTrue("сдвинут вверх", one.top < none.top)
        assertEquals("сверху наименьший промежуток", edge, one.top, 0.01f)
        assertEquals("снизу наименьший промежуток", edge, below(140f, one), 0.01f)
    }

    @Test
    fun onAPixelWithRoomAlertsDoNotShrinkTheBlockAtAll() {
        // Pixel 6 Pro, 253 dp: плашки (до трёх с «Ещё N») сдвигают блок, но не уменьшают.
        val none = HeroFit.plan(253f, 0, rows, edge, hero)
        for (count in 1..3) {
            val plan = HeroFit.plan(253f, count, rows, edge, hero)
            assertTrue("count=$count масштаб ${plan.scale}", plan.scale >= 1f)
            assertTrue("count=$count снизу ${below(253f, plan)}", below(253f, plan) >= edge - 0.01f)
            assertTrue("count=$count сверху ${plan.top}", plan.top >= edge - 0.01f && plan.top <= none.top + 0.01f)
        }
        val one = HeroFit.plan(253f, 1, rows, edge, hero)
        assertEquals("одна плашка размер не меняет", none.scale, one.scale, 1e-6f)
        assertTrue("и не сдвигает, пока места хватает", one.top <= none.top && one.top > edge)
    }

    @Test
    fun withRoomToSpareAlertsDoNotShrinkTheBlockAtAll() {
        val none = HeroFit.plan(500f, 0, rows, edge, hero)
        for (count in 1..5) {
            val plan = HeroFit.plan(500f, count, rows, edge, hero)
            assertEquals("count=$count", none.scale, plan.scale, 1e-6f)
            assertTrue("count=$count снизу ${below(500f, plan)}", below(500f, plan) >= edge - 0.01f)
            assertTrue("count=$count сверху ${plan.top}", plan.top >= edge - 0.01f && plan.top <= none.top + 0.01f)
        }
    }

    @Test
    fun threeAlertsAreAllShownAndTheBlockShrinksToMakeRoom() {
        val plan = HeroFit.plan(170f, 3, rows, edge, hero)
        assertEquals(3, plan.shown)
        assertFalse(plan.more)
        // Три плашки (16 + 3 * 42 + 2 * 8 = 158) не влезают в 146: блок отдаёт недостающие 12.
        assertEquals(158f, plan.alerts, 0f)
        assertEquals(1f - 12f / hero, plan.scale, 1e-4f)
        assertTrue("блок меньше прежнего", plan.scale < 1f)
        assertTrue("не меньше предела", plan.scale >= HeroFit.MIN_SCALE)
        assertEquals("сверху наименьший промежуток", edge, plan.top, 0.01f)
        assertEquals("снизу наименьший промежуток", edge, below(170f, plan), 0.01f)
    }

    @Test
    fun morePlatesGoUnderMoreAfterThree() {
        val plan = HeroFit.plan(175f, 5, rows, edge, hero)
        assertEquals(3, plan.shown)
        assertTrue(plan.more)
        assertEquals(16f + 3 * 42f + 2 * 8f + 8f + 38f, plan.alerts, 0f)
        assertTrue("блок сжат", plan.scale < 1f)
        assertTrue(plan.scale >= HeroFit.MIN_SCALE)
        assertEquals(edge, below(175f, plan), 0.01f)

        val four = HeroFit.plan(175f, 4, rows, edge, hero)
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
        // Свободно 30: даже сжатый до предела блок отдаёт 56, всего 62; три плашки (158) не влезают, одна и «Ещё» (104) тоже, но меньше одной не бывает.
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
        val plan = HeroFit.plan(170f, 2, rows, edge, hero)
        // Под блоком остаётся не меньше наименьшего промежутка, а над ним остальное.
        val below = below(170f, plan)
        assertTrue("снизу $below", below >= edge - 0.01f)
        assertTrue("сверху ${plan.top}", plan.top >= edge)
    }

    @Test
    fun withThreeAlertsTheBlockIsNeverLargerThanUsualAndEverythingFits() {
        // Свободное место от узкого до того, где три плашки ещё не помещаются без сжатия блока: все три видны целиком.
        var free = 130f
        while (free <= 182f) {
            val plan = HeroFit.plan(free, 3, rows, edge, hero)
            assertEquals("free=$free", 3, plan.shown)
            assertTrue("free=$free масштаб ${plan.scale}", plan.scale <= 1f)
            assertTrue("free=$free масштаб ${plan.scale}", plan.scale >= HeroFit.MIN_SCALE)
            assertTrue("free=$free снизу ${below(free, plan)}", below(free, plan) >= edge - 0.01f)
            assertTrue("free=$free сверху ${plan.top}", plan.top >= edge - 0.01f)
            free += 4f
        }
    }

    @Test
    fun withoutAlertsTheBlockReachesTheNewLimitOnAnAmpleScreen() {
        val plan = HeroFit.plan(400f, 0, rows, edge, hero)
        assertEquals(1.7f, HeroFit.MAX_SCALE, 0f)
        assertEquals(HeroFit.MAX_SCALE, plan.scale, 1e-4f)
        val usual = HeroFit.plan(253f, 0, rows, edge, hero)
        assertTrue("на Pixel 6 Pro без предупреждений блок заметно крупнее прежнего", usual.scale > 1.6f)
    }
}
