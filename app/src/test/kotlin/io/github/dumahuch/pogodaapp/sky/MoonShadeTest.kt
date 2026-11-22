package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Маска фазы луны: где светло, где почти прозрачно, мягкая граница и пепельный свет только у тонкой луны. */
class MoonShadeTest {

    private fun alpha(u: Double, v: Double, lit: Double, right: Boolean = true) = MoonShade.at(u, v, lit, right).alpha

    @Test
    fun fullMoonIsLitEverywhereUpToTheLimb() {
        for (u in listOf(-0.98, -0.5, 0.0, 0.5, 0.98)) assertEquals("u=$u", 1.0, alpha(u, 0.0, 1.0), 1e-9)
        assertEquals(1.0, alpha(0.0, 0.98, 1.0), 1e-9)
    }

    @Test
    fun quarterIsLitOnTheLightSideAndAlmostInvisibleOnTheOther() {
        assertTrue(alpha(0.8, 0.0, 0.5) > 0.99)
        assertTrue("тёмная половина почти прозрачна: ${alpha(-0.8, 0.0, 0.5)}", alpha(-0.8, 0.0, 0.5) < 0.02)
        assertTrue("слева свет у убывающей", alpha(-0.8, 0.0, 0.5, right = false) > 0.99)
    }

    @Test
    fun terminatorIsSoftNotACut() {
        // Поперёк границы у четверти яркость растёт постепенно: несколько промежуточных значений, без скачка.
        val steps = (0..20).map { alpha(it / 20.0 * 0.5, 0.0, 0.5) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a - 1e-9 })
        assertTrue(steps.zipWithNext().all { (a, b) -> b - a < 0.25 })
        assertTrue("есть полутень", steps.count { it in 0.15..0.85 } >= 3)
    }

    @Test
    fun earthshineFadesFromCrescentToGibbous() {
        val crescent = alpha(-0.6, 0.0, 0.05)
        val quarter = alpha(-0.8, 0.0, 0.5)
        assertTrue("у серпа тёмная часть чуть видна: $crescent", crescent in 0.04..0.1)
        assertTrue("к четверти слабее: $quarter", quarter < crescent)
        val darkTint = MoonShade.at(-0.6, 0.0, 0.05, true)
        assertTrue("тёмная часть синее", darkTint.b > darkTint.r)
    }

    @Test
    fun glowOutsideTheDiscOnlyOnTheLitSideAndWeakerForThinMoon() {
        assertEquals(1.0, alpha(1.1, 0.0, 1.0), 1e-9)
        assertEquals(1.0, alpha(-1.1, 0.0, 1.0), 1e-9)
        assertEquals("у четверти нет свечения со стороны тени", 0.0, alpha(-1.1, 0.0, 0.5), 1e-9)
        assertTrue(alpha(1.1, 0.0, 0.25) < alpha(1.1, 0.0, 0.75))
    }

    @Test
    fun maskCoversThePictureAndKeyRoundTrips() {
        val mask = MoonShade.mask(0.5, true)
        assertEquals(MoonShade.SIDE * MoonShade.SIDE, mask.size)
        val mid = MoonShade.SIDE / 2
        val right = mask[mid * MoonShade.SIDE + mid + MoonShade.SIDE / 5] ushr 24
        val left = mask[mid * MoonShade.SIDE + mid - MoonShade.SIDE / 5] ushr 24
        assertTrue("справа светло ($right), слева прозрачно ($left)", right > 240 && left < 8)
        val key = MoonShade.key(0.437, false)
        assertEquals(0.438, MoonShade.litOf(key), 1e-9)
        assertTrue(MoonShade.key(0.437, true) != key)
    }
}
