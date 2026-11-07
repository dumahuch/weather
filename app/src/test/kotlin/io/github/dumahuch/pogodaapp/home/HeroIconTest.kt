package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroIconTest {

    @Test
    fun clearSkyHasNoIconBecauseSunAndMoonAreOnTheSky() {
        assertNull(heroIconFor(Condition.Clear, isDay = true))
        assertNull(heroIconFor(Condition.Clear, isDay = false))
    }

    @Test
    fun clearSkyWithStrongWindShowsOnlyTheWind() {
        assertEquals(HeroIcon.Wind, heroIconFor(Condition.Clear, isDay = true, windy = true))
    }

    @Test
    fun partlyCloudyIsACloudWithTheSunByDayAndTheMoonByNight() {
        assertEquals(HeroIcon.CloudSun, heroIconFor(Condition.Partly, isDay = true))
        assertEquals(HeroIcon.CloudMoon, heroIconFor(Condition.Partly, isDay = false))
    }

    @Test
    fun overcastIsTwoCloudsByDayAndByNight() {
        assertEquals(HeroIcon.Clouds, heroIconFor(Condition.Cloudy, isDay = true))
        assertEquals(HeroIcon.Clouds, heroIconFor(Condition.Cloudy, isDay = false))
    }

    @Test
    fun strongWindJoinsCloudsRainAndSnowButNotStormAndFog() {
        assertEquals(HeroIcon.CloudWind, heroIconFor(Condition.Partly, isDay = true, windy = true))
        assertEquals(HeroIcon.CloudWind, heroIconFor(Condition.Cloudy, isDay = false, windy = true))
        assertEquals(HeroIcon.RainWind, heroIconFor(Condition.Rain, isDay = true, windy = true))
        assertEquals(HeroIcon.SnowWind, heroIconFor(Condition.Snow, isDay = true, windy = true))
        assertEquals(HeroIcon.Storm, heroIconFor(Condition.Storm, isDay = true, windy = true))
        assertEquals(HeroIcon.Fog, heroIconFor(Condition.Fog, isDay = true, windy = true))
    }

    @Test
    fun everyOtherConditionHasItsOwnIcon() {
        for (day in listOf(true, false)) {
            val icons = Condition.entries.mapNotNull { heroIconFor(it, day) }
            assertEquals(Condition.entries.size - 1, icons.size)
            assertEquals(icons.size, icons.toSet().size)
        }
        assertEquals(HeroIcon.Rain, heroIconFor(Condition.Rain, isDay = true))
        assertEquals(HeroIcon.Storm, heroIconFor(Condition.Storm, isDay = false))
        assertEquals(HeroIcon.Snow, heroIconFor(Condition.Snow, isDay = true))
        assertEquals(HeroIcon.Fog, heroIconFor(Condition.Fog, isDay = false))
    }

    @Test
    fun strongWindStartsAtThirtyNineKilometresPerHour() {
        assertFalse(isStrongWind(null))
        assertFalse(isStrongWind(38.9))
        assertTrue(isStrongWind(39.0))
    }
}
