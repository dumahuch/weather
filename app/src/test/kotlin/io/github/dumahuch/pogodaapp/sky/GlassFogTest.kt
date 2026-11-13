package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Запотевшее стекло: когда появляется плёнка и как её стирает палец. */
class GlassFogTest {

    @Test
    fun fogDrizzleAndHumidAirFogTheGlassButClearWeatherDoesNot() {
        assertEquals(1.0, GlassFog.density(Condition.Fog, null, null, null), 1e-9)
        assertEquals(1.0, GlassFog.density(Condition.Fog, null, null, 150.0), 1e-9)
        assertEquals(0.6, GlassFog.density(Condition.Fog, null, null, 2000.0), 1e-9)
        assertEquals(0.55, GlassFog.density(Condition.Rain, 0.1, 60, null), 1e-9)
        assertEquals(0.0, GlassFog.density(Condition.Rain, 3.0, 60, null), 1e-9)
        assertEquals(0.0, GlassFog.density(Condition.Clear, null, 70, null), 1e-9)
        assertEquals(0.5, GlassFog.density(Condition.Cloudy, null, 99, null), 1e-9)
        assertTrue(GlassFog.density(Condition.Cloudy, null, 92, null) in 0.1..0.4)
        // Сильный дождь при сырости: плёнка от влажности остаётся.
        assertEquals(0.5, GlassFog.density(Condition.Rain, 3.0, 98, null), 1e-9)
        assertEquals(0.0, GlassFog.density(Condition.Storm, null, null, null), 1e-9)
    }

    @Test
    fun aFingerClearsItsPathSoftlyButNotTheRestOfTheGlass() {
        val glass = FogWipe(350.0, 250.0)
        glass.wipe(50.0, 100.0, 250.0, 100.0)
        assertTrue(glass.active)
        val clearest = 1f - FogWipe.MAX_CLEAR
        assertEquals(clearest, glass.cover(index(glass, 150.0, 100.0)), 1e-6f)
        assertEquals(clearest, glass.cover(index(glass, 60.0, 102.0)), 1e-6f)
        assertEquals(1f, glass.cover(index(glass, 150.0, 200.0)), 1e-6f)
        assertEquals(1f, glass.cover(index(glass, 320.0, 100.0)), 1e-6f)
        // След широкий: чисто на 5 dp от середины, а к краю плёнка густеет плавно, не обрывом.
        assertEquals(clearest, glass.cover(index(glass, 150.0, 100.0 + 5.0)), 1e-6f)
        val covers = (0..8).map { glass.cover(index(glass, 150.0, 100.0 + it * FogWipe.BRUSH_DP / 8)) }
        assertTrue(covers.zipWithNext().all { (a, b) -> b >= a - 0.12f })
        assertTrue("$covers", covers.zipWithNext().maxOf { (a, b) -> abs(b - a) } < 0.4f)
        assertTrue("$covers", covers.count { it in 0.2f..0.9f } >= 3)
        assertEquals(1f, covers.last(), 0.05f)
        // Граница неровная: на одном расстоянии от середины клетки вдоль следа не одинаковы.
        val edge = (60..240 step 2).map { glass.cover(index(glass, it.toDouble(), 100.0 + FogWipe.BRUSH_DP * 0.8)) }
        assertTrue(edge.max() - edge.min() > 0.05f)
    }

    @Test
    fun theWipedPathHoldsThenFogsUpFromTheEdgesInwardAndSmoothly() {
        val glass = FogWipe(350.0, 250.0)
        glass.wipe(100.0, 100.0, 200.0, 100.0)
        val centre = index(glass, 150.0, 100.0)
        val edge = index(glass, 150.0, 100.0 + FogWipe.BRUSH_DP * 0.5)
        val clearest = 1f - FogWipe.MAX_CLEAR
        run(glass, 2.0)
        assertEquals(clearest, glass.cover(centre), 1e-6f)
        val edgeAtStart = glass.cover(edge)
        // Дальше плёнка наползает с краёв: край затягивается раньше середины, след сужается.
        run(glass, 7.0)
        assertTrue("${glass.cover(edge)} ${glass.cover(centre)}", glass.cover(edge) > glass.cover(centre) + 0.2f)
        assertTrue(glass.cover(edge) > edgeAtStart)
        // Середина затягивается последней и плавно: за кадр меняется на доли процента.
        run(glass, 5.0)
        assertTrue("${glass.cover(centre)}", glass.cover(centre) > clearest)
        val before = glass.cover(centre)
        glass.step(0.016)
        assertTrue(abs(glass.cover(centre) - before) < 0.01f)
        assertTrue(glass.cover(centre) > before - 1e-6f)
        run(glass, 10.0)
        assertEquals(1f, glass.cover(centre), 1e-6f)
        assertEquals(1f, glass.cover(edge), 1e-6f)
        assertFalse(glass.active)
        assertFalse(glass.step(0.03))
    }

    @Test
    fun wipingAgainOverARecoveringTrailClearsItAgain() {
        val glass = FogWipe(350.0, 250.0)
        glass.wipe(100.0, 100.0, 200.0, 100.0)
        run(glass, 15.0)
        val centre = index(glass, 150.0, 100.0)
        val faded = glass.cover(centre)
        assertTrue(faded > 0.5f)
        glass.wipe(100.0, 100.0, 200.0, 100.0)
        assertEquals(1f - FogWipe.MAX_CLEAR, glass.cover(centre), 1e-6f)
    }

    @Test
    fun aTallCardKeepsTheGridSmallAndATouchOutsideDoesNothing() {
        val tall = FogWipe(360.0, 2400.0)
        assertTrue(tall.cols * tall.rows <= 41_000)
        assertTrue(FogWipe(350.0, 250.0).cell <= 2.0)
        val glass = FogWipe(100.0, 50.0)
        glass.wipe(-200.0, -200.0, -150.0, -180.0)
        assertFalse(glass.active)
    }

    private fun index(glass: FogWipe, x: Double, y: Double): Int = (y / glass.cell).toInt() * glass.cols + (x / glass.cell).toInt()

    private fun run(glass: FogWipe, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            glass.step(0.03)
            t += 0.03
        }
    }
}
