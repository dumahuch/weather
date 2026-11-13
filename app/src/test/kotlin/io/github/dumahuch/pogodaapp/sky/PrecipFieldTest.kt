package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Слой дождя и снега: условия по погоде, число частиц, глубина, ветер и то, что частицы не выходят за поле. */
class PrecipFieldTest {

    private val width = 390.0
    private val height = 844.0

    private fun spec(condition: Condition, mm: Double? = null, wind: Double = 10.0, from: Int? = 270) =
        PrecipSpec.of(condition, mm, wind, from)

    @Test
    fun onlyRainStormAndSnowGetALayer() {
        assertNull(spec(Condition.Clear))
        assertNull(spec(Condition.Partly))
        assertNull(spec(Condition.Cloudy))
        assertNull(spec(Condition.Fog))
        assertEquals(PrecipKind.Rain, spec(Condition.Rain)?.kind)
        assertEquals(PrecipKind.Rain, spec(Condition.Storm)?.kind)
        assertEquals(PrecipKind.Snow, spec(Condition.Snow)?.kind)
    }

    @Test
    fun strengthFollowsPrecipitationAndStormIsNeverWeak() {
        val drizzle = spec(Condition.Rain, 0.1)!!.strength
        val shower = spec(Condition.Rain, 2.0)!!.strength
        val downpour = spec(Condition.Rain, 6.0)!!.strength
        assertTrue(drizzle < shower && shower < downpour)
        assertEquals(1.0, downpour, 1e-9)
        assertEquals(0.5, spec(Condition.Rain)!!.strength, 1e-9)
        assertTrue(spec(Condition.Storm, 0.1)!!.strength >= 0.75)
    }

    @Test
    fun windFromTheWestLeansRightAndFromTheEastLeft() {
        assertTrue(spec(Condition.Rain, wind = 20.0, from = 270)!!.rainTilt > 0.1)
        assertTrue(spec(Condition.Rain, wind = 20.0, from = 90)!!.rainTilt < -0.1)
        assertEquals(0.0, spec(Condition.Rain, wind = 20.0, from = 0)!!.rainTilt, 1e-9)
        assertTrue(spec(Condition.Snow, wind = 14.0, from = 270)!!.snowDrift > 0.0)
        assertTrue(spec(Condition.Snow, wind = 14.0, from = 90)!!.snowDrift < 0.0)
    }

    @Test
    fun calmAirDoesNotTiltTheRainAndAGaleIsLimited() {
        assertEquals(0.0, spec(Condition.Rain, wind = 0.0)!!.rainTilt, 1e-9)
        assertEquals(0.5, spec(Condition.Rain, wind = 120.0)!!.rainTilt, 1e-9)
    }

    @Test
    fun unknownDirectionGivesTheDefaultLean() {
        assertTrue(spec(Condition.Rain, wind = 20.0, from = null)!!.rainTilt > 0.0)
    }

    @Test
    fun stormWindBlowsHarderThanLightWind() {
        assertTrue(spec(Condition.Rain, wind = 55.0)!!.rainTilt > spec(Condition.Rain, wind = 10.0)!!.rainTilt)
    }

    @Test
    fun moreStrengthMoreParticlesUpToTheCap() {
        val weak = RainField.create(0.0, width, height).drops.size
        val strong = RainField.create(1.0, width, height).drops.size
        assertTrue("$weak < $strong", weak < strong)
        assertTrue(strong <= RainField.MAX_DROPS)
        val flakesWeak = SnowField.create(0.0, width, height).flakes.size
        val flakesStrong = SnowField.create(1.0, width, height).flakes.size
        assertTrue(flakesWeak < flakesStrong)
        assertTrue(flakesStrong <= SnowField.MAX_FLAKES)
    }

    @Test
    fun aBiggerFieldHoldsMoreParticlesAndAnEmptyOneStillHasSome() {
        assertTrue(RainField.create(0.5, 2 * width, height).drops.size > RainField.create(0.5, width, height).drops.size)
        assertTrue(RainField.create(0.5, 1.0, 1.0).drops.isNotEmpty())
        assertTrue(SnowField.create(0.5, 1.0, 1.0).flakes.isNotEmpty())
    }

    @Test
    fun theSameSeedGivesTheSameField() {
        val a = RainField.create(0.6, width, height).drops
        val b = RainField.create(0.6, width, height).drops
        assertEquals(a.map { it.x0 to it.speed }, b.map { it.x0 to it.speed })
        assertTrue(RainField.create(0.6, width, height, seed = 3).drops.map { it.x0 } != a.map { it.x0 })
    }

    @Test
    fun nearRainIsFasterLongerThickerAndBrighterThanFarRain() {
        val drops = RainField.create(1.0, width, height).drops
        val far = drops.filter { it.depth <= 0.2 }
        val near = drops.filter { it.depth >= 1.0 }
        assertTrue(far.isNotEmpty() && near.isNotEmpty())
        assertTrue(far.maxOf { it.speed } < near.minOf { it.speed })
        assertTrue(far.maxOf { it.length } < near.minOf { it.length })
        assertTrue(far.maxOf { it.width } < near.minOf { it.width })
        assertTrue(far.maxOf { it.alpha } < near.maxOf { it.alpha })
    }

    @Test
    fun rainStreakLengthIsThePathOfTheExposure() {
        for (drop in RainField.create(1.0, width, height).drops) {
            val ratio = drop.length / drop.speed * 19.0
            assertTrue("$ratio", ratio in 0.79..1.21)
        }
    }

    @Test
    fun speedsAreInTheRangeOfRealRain() {
        // 350–1100 dp/с: на экране в 844 dp дальняя капля пролетает его за 2–3 секунды, ближняя за секунду (быстрее было неспокойно).
        for (drop in RainField.create(1.0, width, height).drops) assertTrue(drop.speed in 300.0..1300.0)
    }

    @Test
    fun rainHeadsStayNearTheFieldForAWholeHour() {
        val field = RainField.create(1.0, width, height)
        val pose = Pose()
        for (tilt in listOf(-0.5, 0.0, 0.5)) for (t in listOf(0.0, 1.3, 47.0, 3600.0)) for (drop in field.drops) {
            field.head(drop, t, tilt, pose)
            assertTrue("y=${pose.y}", pose.y >= -drop.length - 80 && pose.y <= height + drop.length + 80)
            assertTrue("x=${pose.x}", pose.x >= -120 && pose.x <= width + 120)
        }
    }

    @Test
    fun rainFallsDownwardWithTime() {
        val field = RainField.create(0.3, width, height)
        val drop = field.drops.first { it.depth >= 1.0 }
        val a = Pose()
        val b = Pose()
        field.head(drop, 0.0, 0.0, a)
        field.head(drop, 0.01, 0.0, b)
        assertTrue(b.y > a.y)
    }

    @Test
    fun aLeaningRainMovesSidewaysAndAVerticalOneDoesNot() {
        val field = RainField.create(0.3, width, height)
        val drop = field.drops.first { it.depth >= 1.0 }
        val a = Pose()
        val b = Pose()
        field.head(drop, 0.0, 0.0, a)
        field.head(drop, 0.2, 0.0, b)
        assertEquals(a.x, b.x, 1e-9)
        field.head(drop, 0.2, 0.4, b)
        assertTrue(abs(b.x - a.x) > 1.0)
    }

    @Test
    fun gustsStayAroundOneAndTheirShiftGrowsWithTime() {
        for (i in 0..600) assertTrue(Gusts.rainFactor(i / 10.0) in 0.49..1.51)
        // Накопленный сдвиг совпадает с численным интегралом множителя.
        var sum = 0.0
        val step = 0.001
        var t = 0.0
        while (t < 20.0) {
            sum += Gusts.rainFactor(t + step / 2) * step
            t += step
        }
        assertEquals(sum, Gusts.rainShift(20.0), 0.01)
        assertEquals(0.0, Gusts.rainShift(0.0), 1e-9)
        assertEquals(0.0, Gusts.snowShift(0.0), 1e-9)
        assertTrue(Gusts.snowShift(30.0) > Gusts.snowShift(10.0))
    }

    @Test
    fun farSnowIsSmallerAndSlowerThanNearSnow() {
        val flakes = SnowField.create(1.0, width, height).flakes
        val far = flakes.filter { it.depth <= 0.15 }
        val near = flakes.filter { it.depth >= 1.0 }
        assertTrue(far.maxOf { it.radius } < near.minOf { it.radius })
        assertTrue(far.maxOf { it.fall } < near.minOf { it.fall })
        // Снег падает в разы медленнее дождя.
        assertTrue(near.maxOf { it.fall } < RainField.create(1.0, width, height).drops.minOf { it.speed })
    }

    @Test
    fun crystalsAreOnlyOnTheBiggestFlakes() {
        val flakes = SnowField.create(1.0, width, height).flakes
        assertTrue(flakes.any { it.crystal })
        assertTrue(flakes.filter { it.crystal }.all { it.radius > 5.0 })
    }

    @Test
    fun snowSwaysSideToSideAndStaysInsideTheField() {
        val field = SnowField.create(1.0, width, height)
        val pose = Pose()
        for (drift in listOf(-40.0, 0.0, 40.0)) for (t in listOf(0.0, 2.0, 90.0, 5000.0)) for (flake in field.flakes) {
            field.centre(flake, t, drift, pose)
            assertTrue("x=${pose.x}", pose.x >= -100 && pose.x <= width + 100)
            assertTrue("y=${pose.y}", pose.y >= -3 * flake.radius - 30 && pose.y <= height + 3 * flake.radius + 30)
        }
        val flake = field.flakes.first { it.depth >= 0.75 }
        val xs = (0..200).map { i -> field.centre(flake, i / 10.0, 0.0, pose); pose.x }
        assertTrue(xs.max() - xs.min() > 2.0)
    }

    @Test
    fun snowDriftsWithTheWindAndMoreForNearFlakes() {
        val field = SnowField.create(0.4, width, height)
        val far = field.flakes.first { it.depth <= 0.15 }
        val near = field.flakes.first { it.depth >= 1.0 }
        val a = Pose()
        val b = Pose()
        val period = width + 200
        fun shift(flake: Flake): Double {
            field.centre(flake, 4.0, 0.0, a)
            field.centre(flake, 4.0, 2.0, b)
            val d = b.x - a.x
            return if (d < -period / 2) d + period else d
        }
        assertTrue(shift(near) > shift(far))
        assertTrue(shift(far) > 0.0)
    }

    @Test
    fun aBigScreenKeepsEveryLayerAndTheCap() {
        val rain = RainField.create(1.0, 600.0, 1400.0)
        val snow = SnowField.create(1.0, 600.0, 1400.0)
        assertTrue(rain.drops.size <= RainField.MAX_DROPS)
        assertTrue(snow.flakes.size <= SnowField.MAX_FLAKES)
        assertEquals(4, rain.drops.map { it.depth }.toSet().size)
        assertEquals(5, snow.flakes.map { it.depth }.toSet().size)
    }

    @Test
    fun strengthStepsAreFifths() {
        assertEquals(0.0, strengthStep(-1.0), 1e-9)
        assertEquals(0.2, strengthStep(0.13), 1e-9)
        assertEquals(0.6, strengthStep(0.55), 1e-9)
        assertEquals(1.0, strengthStep(7.0), 1e-9)
        assertNotNull(PrecipSpec(PrecipKind.Rain, 0.0, 0.0, 1.0))
    }
}
