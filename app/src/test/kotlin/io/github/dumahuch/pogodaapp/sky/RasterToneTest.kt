package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterToneTest {

    private fun stormyNight(): SkyLook {
        val sun = SunPosition.atSolarHour(48.0, Instant.parse("2026-07-01T12:00:00Z"), 1.0)
        return SkyState.of(sun, Condition.Storm).look(0.5)
    }

    @Test
    fun toneOfAFullyOpaqueColourIsTheOldWholeFrameFinish() {
        val look = stormyNight()
        val a = doubleArrayOf(120.0, 140.0, 160.0)
        val b = a.copyOf()
        RasterKit.finish(look, a)
        RasterKit.tone(look, b, 1.0)
        for (i in 0..2) assertEquals(a[i], b[i], 1e-9)
        assertTrue("гроза ночью темнее исходного цвета", a[0] < 120.0)
    }

    @Test
    fun toneOfAHalfTransparentColourIsHalfOfTheToneOfTheOpaqueOne() {
        val look = stormyNight()
        val full = doubleArrayOf(120.0, 140.0, 160.0)
        val half = doubleArrayOf(60.0, 70.0, 80.0)
        RasterKit.tone(look, full, 1.0)
        RasterKit.tone(look, half, 0.5)
        for (i in 0..2) assertEquals(full[i] / 2, half[i], 1e-9)
    }

    @Test
    fun theTonePaintsNothingWhereNothingIsDrawn() {
        val look = stormyNight()
        val none = doubleArrayOf(0.0, 0.0, 0.0)
        RasterKit.tone(look, none, 0.0)
        for (i in 0..2) assertEquals(0.0, none[i], 0.0)
    }
}
