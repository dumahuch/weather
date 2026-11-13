package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Туман на небе: виден только у настоящего тумана, узор полос без шва и с мягкими краями. */
class SkyFogTest {

    @Test
    fun onlyRealFogShowsOnTheSky() {
        assertEquals(1.0, SkyFog.amount(0.9), 1e-9)
        assertEquals(0.0, SkyFog.amount(0.15), 1e-9)
        assertEquals(0.0, SkyFog.amount(0.0), 1e-9)
    }

    @Test
    fun fogColourFollowsTheHorizonSoNightFogStaysDark() {
        val night = SkyFog.color(Rgb(30.0, 36.0, 48.0))
        val day = SkyFog.color(Rgb(200.0, 208.0, 214.0))
        assertTrue(night.r < 70 && day.r > 200)
    }

    @Test
    fun theBandPatternHasSoftEdgesNoSeamAndRealClumps() {
        val w = 256
        val h = 64
        val t = SkyFog.texture(w, h, 7)
        assertTrue(t.all { it in 0f..1f })
        for (x in 0 until w) {
            assertEquals(0f, t[x], 1e-6f)
            assertEquals(0f, t[(h - 1) * w + x], 1e-6f)
        }
        val middle = (0 until w).map { t[h / 2 * w + it] }
        assertTrue("${middle.min()} ${middle.max()}", middle.max() - middle.min() > 0.3f)
        // Шов: разница между последним и первым столбцом не больше, чем между соседями внутри узора.
        val steps = (1 until w).maxOf { x -> (0 until h).maxOf { y -> abs(t[y * w + x] - t[y * w + x - 1]) } }
        val seam = (0 until h).maxOf { y -> abs(t[y * w] - t[y * w + w - 1]) }
        assertTrue("$seam $steps", seam <= steps * 1.5f)
    }
}
