package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.dumahuch.pogodaapp.weather.Condition

class GlyphSceneTest {

    private val small = Condition.entries.flatMap { c ->
        listOf(true, false).flatMap { day -> listOf(true, false).map { wet -> GlyphScenes.small(c, day, wet) } }
    }

    private val all = HeroIcon.entries.map { it to GlyphScenes.of(it) } +
        listOf(true, false).map { null to GlyphScenes.clear(it) } +
        small.map { null to it }

    @Test
    fun everyIconFitsTheSquare() {
        for ((icon, scene) in all) {
            assertTrue("$icon: низ ${scene.bottom}", scene.bottom <= 24f)
            scene.front?.let {
                assertTrue("$icon: облако слева", it.left >= 0f)
                assertTrue("$icon: облако справа", it.right <= 24f)
                assertTrue("$icon: облако сверху", it.top >= 0f)
            }
        }
    }

    @Test
    fun theCloudIsWiderThanTallAndItsHeadIsLeftOfCentre() {
        val width = CloudShape.RIGHT - CloudShape.LEFT
        val height = CloudShape.BOTTOM - CloudShape.TOP
        assertEquals(1.48f, width / height, 0.05f)
        val head = CloudShape.circles.maxBy { it.r }
        assertTrue("голова левее середины", head.x < (CloudShape.LEFT + CloudShape.RIGHT) / 2)
    }

    @Test
    fun theCloudBoundsMatchItsCirclesAndBase() {
        assertEquals(CloudShape.TOP, CloudShape.circles.minOf { it.y - it.r }, 1e-4f)
        assertEquals(CloudShape.LEFT, CloudShape.circles.minOf { it.x - it.r }, 1e-4f)
        assertEquals(CloudShape.RIGHT, CloudShape.circles.maxOf { it.x + it.r }, 1e-4f)
        assertEquals(CloudShape.BOTTOM, CloudShape.base.bottom, 0f)
    }

    @Test
    fun rainHangsBelowTheCloudInShortDashes() {
        for (icon in listOf(HeroIcon.Rain, HeroIcon.RainWind)) {
            val scene = GlyphScenes.of(icon)
            val rain = scene.parts.filterIsInstance<GlyphPart.Rain>().single()
            for (d in rain.dashes) assertTrue("$icon: штрих короткий", d.y2 - d.y1 < 3f)
            assertTrue("$icon: свисает ниже облака", rain.bottom > scene.cloudBottom + 4f)
            assertTrue("$icon: начинается за облаком", rain.dashes.minOf { it.y1 } < scene.cloudBottom)
        }
    }

    @Test
    fun cloudsAreAlignedByTheBottomOfTheFrontCloud() {
        for (icon in HeroIcon.entries) {
            val scene = GlyphScenes.of(icon)
            val front = scene.front
            if (front != null) assertEquals(front.bottom, glyphCloudBottom(icon), 0f)
            else assertEquals(scene.bottom, glyphCloudBottom(icon), 0f)
        }
    }

    @Test
    fun overcastHasASmallerCloudBehindAndHigher() {
        val scene = GlyphScenes.of(HeroIcon.Clouds)
        val back = scene.parts.filterIsInstance<GlyphPart.BackCloud>().single().place
        val front = scene.front!!
        assertTrue(back.k < front.k)
        assertTrue(back.top < front.top)
    }

    @Test
    fun stormWithRainKeepsTheRainLeftOfTheBoltAndNoneOnTheRight() {
        val dry = GlyphScenes.of(HeroIcon.Storm).parts.filterIsInstance<GlyphPart.Bolt>().single()
        val wet = GlyphScenes.of(HeroIcon.StormRain)
        val bolt = wet.parts.filterIsInstance<GlyphPart.Bolt>().single()
        val rain = wet.parts.filterIsInstance<GlyphPart.Rain>().single()
        assertTrue("молния сдвинута вправо", bolt.points.minOf { it.first } > dry.points.minOf { it.first })
        val boltLeft = bolt.points.minOf { it.first }
        // Справа от молнии штрихов нет (решение Димы 08.11.2026), слева два ряда.
        assertTrue("штрихи только слева", rain.dashes.all { maxOf(it.x1, it.x2) < boltLeft })
        assertTrue("штрихи свисают ниже облака", rain.bottom > wet.cloudBottom + 3f)
    }

    @Test
    fun smallFogIsACloudWithTwoBars() {
        val scene = GlyphScenes.small(Condition.Fog, isDay = true, wet = false)
        assertTrue("у мелкого тумана есть облако", scene.front != null)
        assertEquals(2, scene.parts.filterIsInstance<GlyphPart.Fog>().single().bars.size)
    }

    @Test
    fun smallClearSkyIsTheSunWithRaysByDayAndTheMoonByNight() {
        assertTrue(GlyphScenes.small(Condition.Clear, true, false).parts.single().let { it is GlyphPart.Sun && it.rays })
        assertTrue(GlyphScenes.small(Condition.Clear, false, false).parts.single() is GlyphPart.Moon)
    }

    @Test
    fun smallStormFollowsThePrecipitationFlag() {
        val dry = GlyphScenes.small(Condition.Storm, true, wet = false)
        val wet = GlyphScenes.small(Condition.Storm, true, wet = true)
        assertTrue(dry.parts.none { it is GlyphPart.Rain })
        assertTrue(wet.parts.any { it is GlyphPart.Rain })
    }
}
