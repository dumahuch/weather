package io.github.dumahuch.pogodaapp.ui.sunmoon

import io.github.dumahuch.pogodaapp.forecast.LightPhase
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Кривая «Солнце и Луна» (колокол с плоскими хвостами), метки и цвета полосы света. */
class SunMoonShapeTest {

    @Test
    fun bellRisesFromTheEndsToTheTopAndItsTailsLieJustBelowTheHorizon() {
        assertEquals(0f, BellShape.lift(BellShape.LEFT), 1e-4f)
        assertEquals(0f, BellShape.lift(BellShape.RIGHT), 1e-4f)
        assertEquals(1f, BellShape.lift((BellShape.LEFT + BellShape.RIGHT) / 2f), 1e-4f)
        // Хвосты ниже горизонта, но неглубоко, и у краёв карточки почти плоские.
        val edge = BellShape.lift(0f)
        assertTrue("$edge", edge < 0f && edge > -BellShape.TAIL - 1e-4f)
        assertTrue(BellShape.lift(0.05f) - edge < 0.03f)
        // Симметрия и подъём без провалов до вершины.
        assertEquals(BellShape.lift(0.3f), BellShape.lift(0.7f), 1e-4f)
        val rising = (0..50).map { BellShape.lift(it / 100f) }
        assertTrue(rising.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun markerRunsFromSunriseToSunset() {
        assertEquals(BellShape.LEFT, BellShape.xAt(0f), 1e-6f)
        assertEquals(BellShape.RIGHT, BellShape.xAt(1f), 1e-6f)
        assertEquals(BellShape.RIGHT, BellShape.xAt(1.5f), 1e-6f)
    }

    @Test
    fun markerIconsReadOnTheirFill() {
        val sun = contrast(Rgb.hex(SunMoonColors.SUN_ICON), Rgb.hex(SunMoonColors.SUN))
        val moon = contrast(Rgb.hex(SunMoonColors.MOON_ICON), Rgb.hex(SunMoonColors.MOON_FILL))
        assertTrue("солнце $sun", sun >= 4.5)
        assertTrue("луна $moon", moon >= 4.5)
        // Метка в белом ободке отличается от него.
        assertTrue(contrast(Rgb.hex(SunMoonColors.SUN), Rgb.White) >= 1.5)
        assertTrue(contrast(Rgb.hex(SunMoonColors.MOON_FILL), Rgb.White) >= 3.0)
    }

    @Test
    fun neighbouringLightPhasesAreDistinguishable() {
        val colors = LightPhase.entries.map { Rgb.hex(SunMoonColors.of(it)) }
        colors.zipWithNext().forEach { (a, b) -> assertTrue("${contrast(a, b)}", contrast(a, b) >= 1.25) }
    }
}
