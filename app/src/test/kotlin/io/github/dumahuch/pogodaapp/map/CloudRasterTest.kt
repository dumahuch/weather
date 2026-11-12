package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Облачность в картинку: порог ясного неба, проекция, пропуски. */
class CloudRasterTest {

    private fun grid(values: FloatArray, columns: Int, rows: Int) =
        LatLonGrid(columns, rows, west = 0.0, south = 40.0, stepLon = 10.0, stepLat = 10.0, values = values)

    @Test
    fun clearSkyIsTransparentOvercastIsMostlyOpaque() {
        assertEquals(0f, CloudRaster.alphaOf(0f), 0f)
        assertEquals(0f, CloudRaster.alphaOf(0.04f), 0f)
        assertEquals(0f, CloudRaster.alphaOf(Float.NaN), 0f)
        assertTrue(CloudRaster.alphaOf(1f) in 0.6f..0.8f)
        assertTrue(CloudRaster.alphaOf(0.5f) < CloudRaster.alphaOf(0.9f))
    }

    @Test
    fun percentValuesAreScaled() {
        val g = CloudRaster.normalize(grid(floatArrayOf(0f, 50f, 100f, 25f), 2, 2))
        assertEquals(0.5f, g.at(1, 0), 1e-6f)
        val unchanged = grid(floatArrayOf(0f, 0.5f, 1f, 0.25f), 2, 2)
        assertTrue(CloudRaster.normalize(unchanged) === unchanged)
    }

    @Test
    fun samplingInterpolates() {
        val g = grid(floatArrayOf(0f, 1f, 0f, 1f), 2, 2)
        assertEquals(0.5f, CloudRaster.sample(g, 0.5, 0.5), 1e-6f)
    }

    @Test
    fun overcastGridFillsPicturePixelsOutsideStayEmpty() {
        val g = grid(FloatArray(4) { 1f }, 2, 2)
        // Сетка 0…10° в.д., 40…50° с.ш.; картинка шире сетки на восток.
        val pixels = CloudRaster.render(g, GeoBox(west = 0.0, south = 40.0, east = 20.0, north = 50.0), 20, 10)
        assertTrue(pixels[5 * 20 + 2] ushr 24 > 150)
        assertEquals(0, pixels[5 * 20 + 17])
    }

    @Test
    fun rowsFollowMercatorLatitude() {
        // Облака только в северной строке сетки (50° с.ш.): в картинке они должны быть вверху, не в середине.
        val g = grid(floatArrayOf(0f, 0f, 1f, 1f), 2, 2)
        val pixels = CloudRaster.render(g, GeoBox(west = 0.0, south = 40.0, east = 10.0, north = 50.0), 4, 100)
        val top = pixels[1 * 4 + 1] ushr 24
        val bottom = pixels[98 * 4 + 1] ushr 24
        assertTrue(top > bottom)
    }

    @Test
    fun alphaOnlyKeepsTheImageAndShrinksItFourTimes() {
        val g = grid(floatArrayOf(0f, 0.5f, 1f, 0.25f), 2, 2)
        val box = GeoBox(west = 0.0, south = 40.0, east = 10.0, north = 50.0)
        val pixels = CloudRaster.render(g, box, 16, 16)
        val alpha = CloudRaster.alphaOnly(pixels)
        assertEquals(pixels.size, alpha.size)
        assertTrue(pixels.any { it != 0 })
        assertTrue(CloudRaster.fromAlpha(alpha).contentEquals(pixels))
    }
}
