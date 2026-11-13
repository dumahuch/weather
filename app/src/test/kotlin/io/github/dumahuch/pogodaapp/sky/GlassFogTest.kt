package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun aFingerClearsItsPathButNotTheRestOfTheGlass() {
        val glass = FogWipe(350.0, 250.0)
        glass.wipe(50.0, 100.0, 250.0, 100.0)
        assertTrue(glass.active)
        assertEquals(0f, glass.cover(index(glass, 150.0, 100.0)), 1e-6f)
        assertEquals(0f, glass.cover(index(glass, 60.0, 102.0)), 1e-6f)
        assertEquals(1f, glass.cover(index(glass, 150.0, 200.0)), 1e-6f)
        assertEquals(1f, glass.cover(index(glass, 320.0, 100.0)), 1e-6f)
        // Край следа мягкий: между чистым и нетронутым.
        val edge = glass.cover(index(glass, 150.0, 100.0 + FogWipe.BRUSH_DP * 0.75))
        assertTrue("$edge", edge > 0f && edge < 1f)
    }

    @Test
    fun theWipedPathStaysClearForAFewSecondsThenFogsUpAgainSlowly() {
        val glass = FogWipe(350.0, 250.0)
        glass.wipe(100.0, 100.0, 100.0, 100.0)
        val i = index(glass, 100.0, 100.0)
        run(glass, 4.0)
        assertEquals(0f, glass.cover(i), 1e-6f)
        run(glass, 10.0)
        assertTrue(glass.cover(i) in 0.2f..0.8f)
        run(glass, 20.0)
        assertEquals(1f, glass.cover(i), 1e-6f)
        assertFalse(glass.active)
        assertFalse(glass.step(0.03))
    }

    @Test
    fun aTallCardKeepsTheGridSmallAndATouchOutsideDoesNothing() {
        val tall = FogWipe(360.0, 2400.0)
        assertTrue(tall.cols * tall.rows <= 12_500)
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
