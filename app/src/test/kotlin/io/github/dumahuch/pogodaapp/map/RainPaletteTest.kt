package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Перекраска дождя DWD в нашу гамму: все ступени сохранены, лишнее прозрачно. */
class RainPaletteTest {

    private fun px(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun recolor(source: RainSource, vararg pixels: Int): IntArray = pixels.copyOf().also { RainPalette.recolor(source, it) }

    private fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF

    @Test
    fun everyRadarStepGetsItsOwnColour() {
        val steps = listOf(
            px(51, 255, 255), px(26, 204, 154), px(1, 153, 52), px(77, 179, 27), px(153, 204, 1), px(204, 230, 1), px(255, 255, 1),
            px(255, 196, 1), px(255, 137, 1), px(255, 69, 1), px(254, 0, 0), px(229, 0, 76), px(204, 0, 152), px(102, 0, 203), px(0, 0, 254),
        )
        val out = recolor(RainSource.Radar, *steps.toIntArray())
        assertEquals(15, out.toSet().size)
        assertTrue(out.all { (it ushr 24) > 0 })
    }

    @Test
    fun everyModelStepGetsItsOwnColour() {
        val steps = listOf(
            px(192, 192, 192), px(254, 254, 192), px(254, 254, 91), px(207, 242, 0), px(160, 214, 37), px(54, 201, 105), px(0, 215, 215),
            px(0, 166, 221), px(0, 0, 254), px(151, 48, 194), px(217, 38, 199), px(254, 0, 0), px(160, 0, 0), px(96, 96, 96),
        )
        val out = recolor(RainSource.Model, *steps.toIntArray())
        assertEquals(14, out.toSet().size)
    }

    @Test
    fun sameRainGivesSameColourInRadarAndModel() {
        // 10-15 мм/ч у радара и 10-15 мм за час у модели: одна сила, один цвет.
        val radar = recolor(RainSource.Radar, px(255, 137, 1)).single()
        val model = recolor(RainSource.Model, px(207, 242, 0)).single()
        assertEquals(radar, model)
    }

    @Test
    fun colourGrowsFromLightBlueThroughBlueToViolet() {
        val weak = RainPalette.legendColor(0.1)
        val medium = RainPalette.legendColor(5.0)
        val strong = RainPalette.legendColor(20.0)
        assertEquals(0x8FD3FF, weak)
        assertEquals(0x3D56FF, medium)
        assertEquals(0x9A4DFF, strong)
        assertEquals(0xE04DFF, RainPalette.legendColor(100.0))
        assertEquals(0xFFB3F0, RainPalette.legendColor(500.0))
        assertEquals(0x4AA3FF, RainPalette.legendColor(1.0))
        // Между опорными точками цвет идёт плавно (синий канал выше у слабого дождя, чем у среднего).
        assertTrue(channel(RainPalette.legendColor(2.0), 0) in channel(medium, 0)..channel(RainPalette.legendColor(1.0), 0))
    }

    @Test
    fun weakRainIsMoreTransparentThanStrong() {
        val weak = RainPalette.mapColor(0.14) ushr 24
        val strong = RainPalette.mapColor(30.0) ushr 24
        assertTrue(weak < strong)
        assertEquals((0.85 * 255), strong.toDouble(), 1.0)
    }

    @Test
    fun noDataContoursAndSoftEdgesBecomeTransparent() {
        val out = recolor(
            RainSource.Radar,
            px(255, 255, 255, 0), // прозрачный фон
            px(126, 126, 126, 77), // «нет данных»
            px(251, 0, 255), // контур
            px(251, 0, 255, 120), // сглаженный контур
            px(51, 255, 255, 200), // не полностью непрозрачный пиксель
        )
        assertTrue(out.all { it == 0 })
    }

    @Test
    fun sameColourMeansDifferentRainInRadarAndModel() {
        // Красный (254,0,0) у радара 30-45 мм/ч, у модели 100-130 мм за час.
        val radar = recolor(RainSource.Radar, px(254, 0, 0)).single()
        val model = recolor(RainSource.Model, px(254, 0, 0)).single()
        assertTrue(radar != model)
    }

    @Test
    fun repeatedPixelsKeepTheirColour() {
        val a = px(77, 179, 27)
        val out = recolor(RainSource.Radar, a, a, a, px(0, 0, 0, 0), a)
        assertEquals(out[0], out[1])
        assertEquals(out[0], out[4])
        assertEquals(0, out[3])
    }

    @Test
    fun legendPositionsAreLogarithmic() {
        assertEquals(0.0, RainPalette.legendPosition(0.1), 1e-9)
        assertEquals(1.0, RainPalette.legendPosition(100.0), 1e-9)
        assertEquals(1.0 / 3.0, RainPalette.legendPosition(1.0), 1e-9)
        assertEquals(0.0, RainPalette.legendPosition(0.01), 1e-9)
    }
}
