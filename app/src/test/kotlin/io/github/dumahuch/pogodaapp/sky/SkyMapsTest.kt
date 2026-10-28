package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SkyMapsTest {

    private val maps = SkyTestData.maps

    @Test
    fun fileHasTwelveMapsOfTheDeclaredSize() {
        assertEquals(192, maps.width)
        assertEquals(128, maps.height)
        val expected = 4 + 1 + 1 + 2 + 2 + 12L * 192 * 128 * 4
        assertEquals(expected, File("src/main/assets/${SkyMaps.ASSET_PATH}").length())
    }

    private fun meanLuminance(scene: SkyScene): Double {
        val layer = maps.layer(scene, clouds = false)
        var sum = 0.0
        for (i in layer.indices step 4) {
            sum += luminance(Rgb((layer[i].toInt() and 0xFF).toDouble(), (layer[i + 1].toInt() and 0xFF).toDouble(), (layer[i + 2].toInt() and 0xFF).toDouble()))
        }
        return sum / (layer.size / 4)
    }

    @Test
    fun nightIsDarkerThanDayAndTheSkyWithoutCloudsIsOpaque() {
        assertTrue(meanLuminance(SkyScene.Night) < meanLuminance(SkyScene.Dusk))
        assertTrue(meanLuminance(SkyScene.Dusk) < meanLuminance(SkyScene.Afternoon))
        for (scene in SkyScene.entries) {
            val layer = maps.layer(scene, clouds = false)
            for (i in 3 until layer.size step 4) assertEquals("${scene.fileName}: небо без облаков прозрачно в точке $i", 255, layer[i].toInt() and 0xFF)
        }
    }

    @Test
    fun cloudMapsHaveBothClearSkyAndClouds() {
        for (scene in SkyScene.entries) {
            val layer = maps.layer(scene, clouds = true)
            var transparent = 0
            var solid = 0
            for (i in 3 until layer.size step 4) {
                val a = layer[i].toInt() and 0xFF
                if (a < 16) transparent++
                if (a > 200) solid++
                // Цвет умножен на прозрачность, поэтому ни одна составляющая не больше прозрачности.
                for (c in 0..2) assertTrue(scene.fileName, (layer[i - 3 + c].toInt() and 0xFF) <= a + 1)
            }
            assertTrue("${scene.fileName}: просвета нет", transparent > 500)
            assertTrue("${scene.fileName}: облаков нет", solid > 500)
        }
    }

    @Test
    fun brokenFilesAreRejected() {
        val text = "NOPE".toByteArray()
        try {
            SkyMaps.read(ByteArrayInputStream(text))
            throw AssertionError("файл без заголовка должен отклоняться")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("карт"))
        }
    }
}
