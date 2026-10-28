package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkyTimeOfDayTest {

    private fun weights(elevation: Double, hour: Double) = SkyTimeOfDay.weights(elevation, hour)

    @Test
    fun weightsAlwaysSumToOne() {
        for (hour in 0..48) {
            for (elevation in -40..70 step 3) {
                val w = weights(elevation.toDouble(), hour / 2.0)
                assertEquals("e=$elevation h=${hour / 2.0}", 1.0, w.total, 1e-9)
            }
        }
    }

    @Test
    fun highSunInTheMorningIsMorningAndInTheAfternoonIsAfternoon() {
        assertEquals(1.0, weights(30.0, 9.0)[SkyScene.Morning], 1e-9)
        assertEquals(1.0, weights(30.0, 15.0)[SkyScene.Afternoon], 1e-9)
    }

    @Test
    fun deepNightIsNightOnBothBranches() {
        assertEquals(1.0, weights(-30.0, 3.0)[SkyScene.Night], 1e-9)
        assertEquals(1.0, weights(-30.0, 22.0)[SkyScene.Night], 1e-9)
    }

    @Test
    fun sunriseGoesNightDawnMorning() {
        assertTrue(weights(-12.0, 6.0)[SkyScene.Night] > 0 && weights(-12.0, 6.0)[SkyScene.Dawn] > 0)
        assertEquals(1.0, weights(-3.0, 7.0)[SkyScene.Dawn], 1e-9)
        assertTrue(weights(5.0, 8.0)[SkyScene.Dawn] > 0 && weights(5.0, 8.0)[SkyScene.Morning] > 0)
    }

    @Test
    fun sunsetGoesAfternoonSunsetDuskNight() {
        assertTrue(weights(8.0, 15.5)[SkyScene.Afternoon] > 0 && weights(8.0, 15.5)[SkyScene.Sunset] > 0)
        assertEquals(1.0, weights(0.0, 17.0)[SkyScene.Sunset], 1e-9)
        assertTrue(weights(-5.0, 17.5)[SkyScene.Sunset] > 0 && weights(-5.0, 17.5)[SkyScene.Dusk] > 0)
        assertTrue(weights(-12.0, 18.5)[SkyScene.Dusk] > 0 && weights(-12.0, 18.5)[SkyScene.Night] > 0)
    }

    @Test
    fun branchesBlendAroundSolarNoon() {
        val before = weights(30.0, 11.0)
        val during = weights(30.0, 12.0)
        val after = weights(30.0, 13.0)
        assertEquals(1.0, before[SkyScene.Morning], 1e-9)
        assertTrue(during[SkyScene.Morning] > 0 && during[SkyScene.Afternoon] > 0)
        assertEquals(1.0, after[SkyScene.Afternoon], 1e-9)
    }

    @Test
    fun strongestKeepsTwoScenesAndTheTotal() {
        // Зимний полдень на высокой широте: формулы смешивают три картины.
        val w = weights(5.0, 12.0)
        assertTrue("нужны три картины для проверки", w.visible().size >= 3)
        val two = w.strongest(2)
        assertEquals(2, two.visible(0.0).size)
        assertEquals(w.total, two.total, 1e-9)
    }

    @Test
    fun panFollowsTheDominantScene() {
        assertEquals(SkyScene.Morning.pan, weights(30.0, 9.0).pan, 1e-9)
        assertEquals(SkyScene.Afternoon.pan, weights(30.0, 15.0).pan, 1e-9)
        val mid = weights(5.0, 8.0).pan
        assertTrue(mid > SkyScene.Dawn.pan && mid < SkyScene.Morning.pan)
    }

    @Test
    fun everyPictureIsDominantAtSomeTimeOfTheDay() {
        val day = Instant.parse("2026-10-28T12:00:00Z")
        for (scene in SkyScene.entries) {
            val hour = SkyState.dominantSolarHour(scene, 49.44, day)
            val sun = SunPosition.atSolarHour(49.44, day, hour)
            val weight = SkyTimeOfDay.weights(sun.elevation, sun.solarHour)[scene]
            assertTrue("$scene: вес $weight в $hour ч", weight > 0.95)
        }
    }

    @Test
    fun dominantHoursFollowTheDay() {
        val day = Instant.parse("2026-10-28T12:00:00Z")
        val hours = listOf(SkyScene.Dawn, SkyScene.Morning, SkyScene.Afternoon, SkyScene.Sunset, SkyScene.Dusk)
            .map { SkyState.dominantSolarHour(it, 49.44, day) }
        assertEquals("утренние и вечерние картины идут по порядку: $hours", hours.sorted(), hours)
    }
}
