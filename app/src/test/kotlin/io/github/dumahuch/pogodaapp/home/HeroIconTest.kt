package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroIconTest {

    @Test
    fun clearSkyHasNoIconBecauseSunAndMoonAreOnTheSky() {
        assertNull(heroIconFor(Condition.Clear))
    }

    @Test
    fun partlyCloudyIsCloudWithoutSunOrMoon() {
        assertEquals(HeroIcon.Cloud, heroIconFor(Condition.Partly))
    }

    @Test
    fun everyOtherConditionHasItsOwnIcon() {
        val icons = Condition.entries.mapNotNull(::heroIconFor)
        assertEquals(Condition.entries.size - 1, icons.size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(HeroIcon.Rain, heroIconFor(Condition.Rain))
        assertEquals(HeroIcon.Storm, heroIconFor(Condition.Storm))
        assertEquals(HeroIcon.Snow, heroIconFor(Condition.Snow))
        assertEquals(HeroIcon.Fog, heroIconFor(Condition.Fog))
        assertEquals(HeroIcon.Clouds, heroIconFor(Condition.Cloudy))
    }
}
