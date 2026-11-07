package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphSceneTest {

    private val all = HeroIcon.entries.map { it to GlyphScenes.of(it) } +
        listOf(true, false).map { null to GlyphScenes.clear(it) }

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
}
