package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Солнце и луна на небе: что показывается, где лежит и когда прячется за грядами. */
class CelestialTest {

    private val width = 412.0
    private val height = 892.0

    private fun sun(elevation: Double, solarHour: Double) = SunPosition.Sun(elevation, solarHour)

    private fun moonSky(elevation: Double, hourAngle: Double, cycle: Double, southern: Boolean = false) =
        MoonSky(Moon.Position(elevation, hourAngle), Moon.Phase(0.5, cycle), southern)

    private fun disc(body: SkyBody) = Celestial.discs(listOf(body), width, height).single()

    @Test
    fun sunIsShownOnlyAboveTheHorizonWithMargin() {
        assertEquals(1, Celestial.bodies(sun(26.0, 12.0), null).size)
        assertEquals(1, Celestial.bodies(sun(-5.0, 17.5), null).size)
        assertTrue("глубокой ночью солнца нет", Celestial.bodies(sun(-40.0, 0.0), null).isEmpty())
    }

    @Test
    fun sunAlphaDimsOnlyTheSunNotTheMoon() {
        assertEquals(1.0, Celestial.bodies(sun(26.0, 12.0), null).single().alpha, 0.0)
        assertEquals(0.3, Celestial.bodies(sun(26.0, 12.0), null, MoonSky.DEFAULT_PEAK, 0.3).single().alpha, 1e-9)
        val night = Celestial.bodies(sun(-30.0, 22.0), moonSky(30.0, 0.0, 0.5), MoonSky.DEFAULT_PEAK, 0.3).single()
        assertTrue("луна своей яркости: ${night.alpha}", night.moon && night.alpha > 0.7)
    }

    @Test
    fun moonIsShownAtNightAndInTwilightButNotByDay() {
        val full = moonSky(30.0, 0.0, 0.5)
        assertTrue("днём луны нет", Celestial.bodies(sun(26.0, 12.0), full).none { it.moon })
        val dusk = Celestial.bodies(sun(-2.0, 17.3), full).single { it.moon }
        assertTrue("в сумерках луна проступает", dusk.alpha > 0 && dusk.alpha < Celestial.MOON_MAX_ALPHA)
        assertEquals(Celestial.MOON_MAX_ALPHA, Celestial.bodies(sun(-30.0, 22.0), full).single { it.moon }.alpha, 1e-9)
    }

    @Test
    fun moonBelowTheHorizonOrAtNewMoonIsNotShown() {
        assertTrue(Celestial.bodies(sun(-30.0, 22.0), moonSky(-30.0, 3.0, 0.5)).isEmpty())
        assertTrue("новолуние без луны", Celestial.bodies(sun(-30.0, 22.0), moonSky(30.0, 0.0, 0.01)).isEmpty())
    }

    @Test
    fun moonPictureFollowsThePhaseAndTheHemisphere() {
        fun body(cycle: Double, southern: Boolean) = Celestial.bodies(sun(-30.0, 22.0), moonSky(30.0, 0.0, cycle, southern)).single()
        val waxing = body(0.25, false)
        assertEquals("moon_quarter_waxing", waxing.file)
        assertTrue(waxing.lightOnRight && !waxing.mirrored)
        val waning = body(0.75, false)
        assertEquals("moon_quarter_waning", waning.file)
        assertTrue(!waning.lightOnRight && !waning.mirrored)
        val south = body(0.25, true)
        assertTrue("на юге растущая освещена слева, картинка отражена", !south.lightOnRight && south.mirrored)
        assertEquals("moon_full", body(0.5, false).file)
        assertEquals("moon_gibbous_waxing", body(0.375, false).file)
        assertEquals("moon_crescent_waning", body(0.875, false).file)
    }

    @Test
    fun higherSunSitsHigherOnTheScreen() {
        val low = disc(Celestial.bodies(sun(10.0, 14.0), null).single())
        val high = disc(Celestial.bodies(sun(50.0, 12.0), null).single())
        assertTrue("выше над горизонтом значит выше на экране", high.cy < low.cy)
    }

    @Test
    fun winterSunInDiStandsInTheUpperPartOfTheScreen() {
        // Декабрьский полдень в Ганновере 14°, ноябрьский 21°: выше 35 % высоты экрана, но не выше верхней границы.
        for (elevation in listOf(14.0, 21.0, 61.0)) {
            val d = disc(SkyBody("sun", false, elevation, 0.0, 1.0, 1.0, true))
            assertTrue("$elevation°: центр на ${d.cy} из $height", d.cy < 0.35 * height && d.cy - d.radius > 0)
        }
        val low = disc(SkyBody("sun", false, 3.0, 0.0, 1.0, 1.0, true))
        val high = disc(SkyBody("sun", false, 30.0, 0.0, 1.0, 1.0, true))
        assertTrue("выше над горизонтом всё равно выше", high.cy < low.cy)
    }

    @Test
    fun schematicHeightDoesNotDependOnTheSeason() {
        // Полдень в декабре (14°) и июне (61°) стоит на одной высоте, как и восход и середина утра.
        fun cy(elevation: Double, peak: Double) = disc(Celestial.bodies(sun(elevation, 12.0), null, peak).single()).cy
        assertEquals(cy(14.2, 14.2), cy(61.0, 61.0), 1e-6)
        assertEquals(cy(7.1, 14.2), cy(30.5, 61.0), 1e-6)
        assertTrue("полдень выше середины утра", cy(14.2, 14.2) < cy(7.1, 14.2))
        // На горизонте диск у гряд, ниже горизонта высота настоящая.
        val horizon = disc(Celestial.bodies(sun(0.0, 6.0), null, 61.0).single())
        assertEquals(790.0 * DiArt.place(DiArt.Layer.Sky, width, height, 0.5, 0.0).scale, horizon.cy, 1e-6)
    }

    @Test
    fun moonPeakIsTheHighestPointOfTheDayAroundTheMoment() {
        val time = Instant.parse("2026-11-17T18:00:00Z")
        val moon = Celestial.moonSky(52.37, 9.73, time)
        assertTrue("полная дуга луны не ниже порога и не выше зенита: ${moon.peak}", moon.peak in 10.0..90.0)
        assertTrue("высота сейчас не больше наибольшей", moon.position.elevation <= moon.peak + 1e-9)
    }

    @Test
    fun testMoonShowsAnyPhaseAtAnyTimeOfDay() {
        for ((cycle, file) in listOf(0.125 to "moon_crescent_waxing", 0.25 to "moon_quarter_waxing", 0.375 to "moon_gibbous_waxing", 0.5 to "moon_full",
            0.625 to "moon_gibbous_waning", 0.75 to "moon_quarter_waning", 0.875 to "moon_crescent_waning")) {
            val bodies = Celestial.bodies(sun(26.0, 12.0), Celestial.testMoon(52.37, cycle))
            val moon = bodies.single { it.moon }
            assertEquals(file, moon.file)
            assertEquals(Celestial.MOON_MAX_ALPHA, moon.alpha, 1e-9)
        }
        val inSky = disc(Celestial.bodies(sun(-30.0, 22.0), Celestial.testMoon(52.37, 0.5)).single())
        assertTrue("луна проверки на экране", inSky.cx in 0.0..width && inSky.cy in 0.0..height / 2)
    }

    @Test
    fun solarHourInstantGivesThatSolarHour() {
        val day = Instant.parse("2026-11-06T09:30:00Z")
        for (hour in listOf(2.0, 12.0, 21.5)) {
            val sun = SunPosition.at(52.37, 9.73, Celestial.instantAtSolarHour(9.73, day, hour))
            val diff = Math.abs(sun.solarHour - hour).let { minOf(it, 24 - it) }
            assertTrue("час $hour: получилось ${sun.solarHour}", diff < 0.5)
        }
    }

    @Test
    fun sunMovesFromLeftToRightDuringTheDayAndStaysOnTheScreen() {
        val xs = listOf(5.0, 8.0, 12.0, 16.0, 19.0).map { hour -> disc(Celestial.bodies(sun(20.0, hour), null).single()).cx }
        assertTrue("слева направо $xs", xs.zipWithNext().all { (a, b) -> a < b })
        assertTrue("диск целиком на экране по ширине", xs.all { it - 0.1 * width >= 0 && it + 0.1 * width <= width })
        val moonRise = disc(Celestial.bodies(sun(-30.0, 22.0), moonSky(10.0, -6.0, 0.5)).single())
        val moonSet = disc(Celestial.bodies(sun(-30.0, 22.0), moonSky(10.0, 6.0, 0.5)).single())
        assertTrue("луна идёт с востока (слева) на запад", moonRise.cx < width / 2 && moonSet.cx > width / 2)
    }

    @Test
    fun sunGoesBehindTheRidgesAtTheHorizonInDi() {
        // Самая низкая вершина гряды far на холсте: 823 из 1536 (ridges.json), ниже неё её закрывает mid.
        val scale = DiArt.place(DiArt.Layer.Sky, width, height, 0.5, 0.0).scale
        val ridgeTop = 823.0 * scale
        val hidden = disc(SkyBody("sun", false, Celestial.SUN_HIDDEN_BELOW, 6.0, 1.0, 1.0, true))
        assertTrue("при ${Celestial.SUN_HIDDEN_BELOW}° диск целиком под гребнями", hidden.cy - hidden.radius >= ridgeTop)
        val rising = disc(SkyBody("sun", false, 0.0, -6.0, 1.0, 1.0, true))
        assertTrue("на горизонте диск наполовину над гребнем", rising.cy - rising.radius < ridgeTop && rising.cy > 700 * scale)
        val noon = disc(Celestial.bodies(sun(26.0, 12.0), null).single())
        assertTrue("днём диск над гребнями", noon.cy + noon.radius < 727.0 * scale)
    }

    @Test
    fun blendPaintsTheLitPartOfTheDiscAndLeavesTheFarSkyAlone() {
        val sunDisc = disc(Celestial.bodies(sun(30.0, 12.0), null).single())
        val inside = doubleArrayOf(20.0, 40.0, 200.0)
        Celestial.blend(listOf(sunDisc), sunDisc.cx, sunDisc.cy, inside)
        assertTrue("центр солнца тёплый и светлый", inside[0] > 200 && inside[1] > 120 && inside[2] < 80)
        val far = doubleArrayOf(20.0, 40.0, 200.0)
        Celestial.blend(listOf(sunDisc), sunDisc.cx + 4 * sunDisc.radius, sunDisc.cy, far)
        assertEquals(20.0, far[0], 0.0)
        // Растущая четверть освещена справа: слева от центра диска цвет не меняется, справа меняется.
        val quarter = disc(Celestial.bodies(sun(-30.0, 22.0), moonSky(40.0, 0.0, 0.25)).single())
        val dark = doubleArrayOf(10.0, 10.0, 30.0)
        Celestial.blend(listOf(quarter), quarter.cx - 0.6 * quarter.radius, quarter.cy, dark)
        assertEquals(10.0, dark[0], 0.0)
        val lit = doubleArrayOf(10.0, 10.0, 30.0)
        Celestial.blend(listOf(quarter), quarter.cx + 0.6 * quarter.radius, quarter.cy, lit)
        assertTrue(lit[0] > 150)
    }

    @Test
    fun moonPositionFollowsRiseAndSet() {
        val zone = ZoneId.of("Europe/Berlin")
        val events = Moon.eventsOn(LocalDate.of(2026, 11, 17), zone, 52.37, 9.73)
        val riseAt = events.rise!!
        val setAt = events.set!!
        val rise = Moon.positionAt(riseAt, 52.37, 9.73)
        val set = Moon.positionAt(setAt, 52.37, 9.73)
        // Порог восхода: параллакс и рефракция дают около 0,1° над математическим горизонтом.
        assertEquals("высота на восходе", 0.1, rise.elevation, 0.3)
        assertEquals("высота на заходе", 0.1, set.elevation, 0.3)
        assertTrue("на восходе луна на востоке", rise.hourAngleHours < -3 && rise.hourAngleHours > -12)
        assertTrue("на заходе луна на западе", set.hourAngleHours > 3 && set.hourAngleHours < 12)
        val transit = Moon.positionAt(Instant.ofEpochSecond((riseAt.epochSecond + setAt.epochSecond) / 2), 52.37, 9.73)
        assertTrue("посередине между восходом и заходом луна высоко", transit.elevation > 15)
        assertEquals(0.0, transit.hourAngleHours, 1.5)
    }
}
