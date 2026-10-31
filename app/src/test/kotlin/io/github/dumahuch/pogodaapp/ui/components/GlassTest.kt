package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Стекло 0.2.2: числа размытия, тени и блика сверяются с макетом (`design/mockup/src/style.css`). */
class GlassTest {

    @Test
    fun blurNoiseAndColourFollowTheMockupFormulas() {
        assertEquals(0.10f, GlassBlur.noise(0f), 1e-6f)
        assertEquals(0.16f, GlassBlur.noise(1f), 1e-6f)
        assertEquals(1f, GlassBlur.brightness(0f), 1e-6f)
        assertEquals(0.85f, GlassBlur.brightness(1f), 1e-6f)
        assertEquals(1.6f, GlassBlur.saturation(0f), 1e-6f)
        assertEquals(2.0f, GlassBlur.saturation(1f), 1e-6f)
        assertEquals(10f, GlassBlur.Radius.value, 0f)
    }

    @Test
    fun colourMatrixIsTheCssSaturateTimesBrightness() {
        val identity = GlassBlur.colorMatrix(1f, 1f).values
        val expected = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        for (i in expected.indices) assertEquals("элемент $i", expected[i], identity[i], 1e-5f)

        // CSS saturate(s): строка красного (0,213 + 0,787 s, 0,715 − 0,715 s, 0,072 − 0,072 s).
        val s = 1.6f
        val b = 0.85f
        val m = GlassBlur.colorMatrix(s, b).values
        assertEquals(b * (0.213f + 0.787f * s), m[0], 1e-4f)
        assertEquals(b * (0.715f - 0.715f * s), m[1], 1e-4f)
        assertEquals(b * (0.072f - 0.072f * s), m[2], 1e-4f)
        assertEquals(b * (0.213f - 0.213f * s), m[5], 1e-4f)
        assertEquals(b * (0.715f + 0.285f * s), m[6], 1e-4f)
        assertEquals(1f, m[18], 1e-6f) // прозрачность не трогаем
        assertEquals(0f, m[19], 1e-6f)
    }

    @Test
    fun maskRadiusMatchesSkiaSigmaConversion() {
        assertEquals(0f, maskRadiusForSigma(0f), 0f)
        assertEquals(0f, maskRadiusForSigma(0.5f), 0f)
        // сигма = 0,57735 · радиус + 0,5
        for (sigma in listOf(0.75f, 1.3f, 5f, 35f)) {
            val radius = maskRadiusForSigma(sigma)
            assertEquals(sigma, 0.57735f * radius + 0.5f, 1e-4f)
        }
    }

    @Test
    fun growingAnOutlineMovesEveryEdgeAndCorner() {
        val rect = Outline.Rectangle(Rect(10f, 20f, 110f, 80f)).grown(-3f) as Outline.Rectangle
        assertEquals(Rect(13f, 23f, 107f, 77f), rect.rect)

        val rounded = Outline.Rounded(RoundRect(0f, 0f, 100f, 50f, CornerRadius(26f, 26f))).grown(-30f) as Outline.Rounded
        assertEquals(30f, rounded.roundRect.left, 0f)
        assertEquals(70f, rounded.roundRect.right, 0f)
        // углы не уходят в минус
        assertEquals(0f, rounded.roundRect.topLeftCornerRadius.x, 0f)
        val bigger = Outline.Rounded(RoundRect(0f, 0f, 100f, 50f, CornerRadius(22f, 22f))).grown(2f) as Outline.Rounded
        assertEquals(24f, bigger.roundRect.topLeftCornerRadius.x, 0f)
        assertEquals(-2f, bigger.roundRect.top, 0f)
    }

    // ---- Сверка с макетом ----

    private val css: String? by lazy {
        listOf("../design/mockup/src/style.css", "design/mockup/src/style.css")
            .map(::File).firstOrNull { it.isFile }?.readText()
    }

    private fun parseLayers(text: String): List<ShadowLayer> {
        val layer = Regex("""(?:inset\s+)?([-\d.px\s]+?)\s*hsl\(\s*[\d.]+\s+[\d.]+%\s+[\d.]+%\s*/\s*([\d.]+)\)""")
        return layer.findAll(text).map { match ->
            val numbers = match.groupValues[1].trim().split(Regex("\\s+")).map { it.removeSuffix("px").toFloat() }
            ShadowLayer(numbers[0], numbers[1], numbers[2], numbers.getOrElse(3) { 0f }, match.groupValues[2].toFloat())
        }.toList()
    }

    private fun valueOf(name: String): String {
        val source = requireNotNull(css)
        val start = source.indexOf("$name:") + name.length + 1
        val end = source.indexOfAny(charArrayOf(';', '}'), start)
        return source.substring(start, end)
    }

    private fun assertSameLayers(expected: List<ShadowLayer>, actual: List<ShadowLayer>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            assertEquals("слой $i, сдвиг x", e.dx, a.dx, 1e-6f)
            assertEquals("слой $i, сдвиг y", e.dy, a.dy, 1e-6f)
            assertEquals("слой $i, размытие", e.blur, a.blur, 1e-6f)
            assertEquals("слой $i, расширение", e.spread, a.spread, 1e-6f)
            assertEquals("слой $i, прозрачность", e.alpha, a.alpha, 1e-6f)
        }
    }

    @Test
    fun cardShadowIsTheMockupSevenLayers() {
        assumeTrue("нет макета рядом", css != null)
        val fromCss = parseLayers(valueOf("--sh3d"))
        assertEquals(7, fromCss.size)
        assertSameLayers(fromCss, CardShadow)
    }

    @Test
    fun cardRimIsTheMockupInsetLayers() {
        assumeTrue("нет макета рядом", css != null)
        val fromCss = parseLayers(valueOf("--rim"))
        assertEquals(5, fromCss.size)
        assertSameLayers(fromCss, CardRim)
    }

    @Test
    fun shadowAndRimColoursAreTheMockupHsl() {
        assertSameColour(hsl(205f, 0.20f, 0.10f), CardShadowColor)
        assertSameColour(hsl(205f, 0.20f, 0.90f), CardRimColor)
    }

    @Test
    fun liftedCardsGetTheirOwnStrongerShadowOnTopOfTheMockupOne() {
        val mockupAlpha = CardShadow.sumOf { it.alpha.toDouble() }
        val liftAlpha = CardLift.Shadow.sumOf { it.alpha.toDouble() }
        assertTrue("добавка слабее тени макета: $liftAlpha против $mockupAlpha", liftAlpha >= 0.8)
        // Тень падает вниз и вширь не раздувается: расширение отрицательное или нулевое.
        for (layer in CardLift.Shadow) {
            assertTrue(layer.dy >= 0f)
            assertTrue(layer.spread <= 0f)
        }
    }

    @Test
    fun veilLightensLightGlassAndDarkensSmokyGlass() {
        val light = CardLift.veil(0f)
        val smoky = CardLift.veil(1f)
        // Цвет хранит каналы в 8 битах, поэтому допуск в половину ступени (1/255).
        assertEquals(1f, light.red, 0.005f)
        assertEquals(CardLift.VEIL_LIGHT, light.alpha, 0.005f)
        assertEquals(0f, smoky.red, 0.005f)
        assertEquals(CardLift.VEIL_SMOKY, smoky.alpha, 0.005f)
        val half = CardLift.veil(0.5f)
        assertTrue(half.red in 0.4f..0.6f)
        assertTrue(half.alpha in CardLift.VEIL_SMOKY..CardLift.VEIL_LIGHT)
        // Вне диапазона значение прижимается к краю.
        assertEquals(light, CardLift.veil(-3f))
        assertEquals(smoky, CardLift.veil(7f))
    }

    @Test
    fun liftedRimIsThinAndFaint() {
        assertTrue(CardLift.RIM_ALPHA in 0.2f..0.4f)
    }

    private fun hsl(h: Float, s: Float, l: Float): Color {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color((r + m), (g + m), (b + m))
    }

    private fun assertSameColour(expected: Color, actual: Color) {
        assertTrue(
            "ждали $expected, получили $actual",
            listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue)
                .all { abs(it * 255f).roundToInt() <= 1 },
        )
    }
}
