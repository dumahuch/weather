package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyThemeDriverTest {

    private val sampler = SkySampler(SkyTestData.maps)

    private fun driver() = SkyThemeDriver(sampler)

    private fun state(elevation: Double, solarHour: Double, condition: Condition) =
        SkyState.of(SunPosition.Sun(elevation, solarHour), condition)

    private val night = state(-40.0, 23.0, Condition.Clear)
    private val noon = state(26.0, 12.0, Condition.Clear)

    @Test
    fun firstPaletteIsFinalAndSettled() {
        val atNight = driver()
        assertFalse("ночью текст светлый", atNight.palette(atNight.measure(night, 0.5), 0.016).darkInk)
        assertTrue(atNight.settled)
        val atNoon = driver()
        assertTrue("ясным днём текст тёмный", atNoon.palette(atNoon.measure(noon, 0.5), 0.016).darkInk)
        assertTrue(atNoon.settled)
    }

    @Test
    fun switchingTextColorTakesAFewHundredMillis() {
        val d = driver()
        d.palette(d.measure(noon, 0.5), 0.016)
        val scores = d.measure(night, 0.5)
        val early = d.palette(scores, 0.05)
        assertTrue("сразу после смены текст ещё тёмный", early.darkInk)
        assertFalse("и переход не закончен", d.settled)
        val late = d.palette(scores, 1.0)
        assertFalse(late.darkInk)
        assertTrue(d.settled)
    }

    @Test
    fun cloudDriftIsSmoothedOnTheSameSky() {
        val d = driver()
        val look1 = noon.look(0.2)
        val look2 = noon.look(0.8)
        d.measure(noon, 0.2)
        val second = d.measure(noon, 0.8)
        val expected = sampler.backdrops(look1).blend(sampler.backdrops(look2), 0.4)
        assertEquals(expected.hero.mean.r, second.backdrops.hero.mean.r, 1e-9)
        assertEquals(expected.top.b, second.backdrops.top.b, 1e-9)
    }

    @Test
    fun smoothingIsNotAppliedAcrossDifferentWeather() {
        val d = driver()
        d.measure(noon, 0.5)
        val rainy = state(26.0, 12.0, Condition.Rain)
        val scores = d.measure(rainy, 0.5)
        val raw = sampler.backdrops(rainy.look(0.5))
        assertEquals(raw.hero.mean.r, scores.backdrops.hero.mean.r, 1e-9)
    }
}
