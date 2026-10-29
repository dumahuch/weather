package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цвета стекла, добавленные в 0.2.2 (блик по кромке, тень кнопок, «дымчатость»): значения на концах сверяются
 * с формулами макета (`themeVars`: `--glass-hi`, `--btn-hi`, `--shadow`; `--smoke` в виде «Авто» равен положению текста).
 */
class SkyGlassPaletteTest {

    private val sampler = SkySampler(SkyTestData.maps)

    private fun scores(elevation: Double, solarHour: Double): InkScores {
        val sky = SkyState.of(SunPosition.Sun(elevation, solarHour), Condition.Clear)
        return SkyThemeDriver(sampler).measure(sky, 0.5)
    }

    private val noon = scores(26.0, 12.0)

    @Test
    fun smokeFollowsTheTextSwitch() {
        for (k in listOf(0.0, 0.25, 0.5, 0.8, 1.0)) {
            assertEquals(k, SkyTheme.palette(noon, k).smoke, 1e-12)
        }
    }

    @Test
    fun highlightsAndShadowAtBothEndsMatchTheMockup() {
        val light = SkyTheme.palette(noon, 0.0)
        assertEquals(0.30, light.glassHighlight.alpha, 1e-12)
        assertEquals(0.8, light.buttonHighlight.alpha, 1e-12)
        assertEquals(0.16, light.shadow.alpha, 1e-12)
        assertEquals(20.0, light.shadow.rgb.r, 1e-9)
        assertEquals(34.0, light.shadow.rgb.g, 1e-9)
        assertEquals(56.0, light.shadow.rgb.b, 1e-9)

        val dark = SkyTheme.palette(noon, 1.0)
        assertEquals(0.10, dark.glassHighlight.alpha, 1e-12)
        assertEquals(0.2, dark.buttonHighlight.alpha, 1e-12)
        assertEquals(0.34, dark.shadow.alpha, 1e-12)
        assertEquals(0.0, dark.shadow.rgb.r, 1e-9)
    }

    @Test
    fun glassHighlightIsWhiteTintedByTheSkyGlassColour() {
        for (k in listOf(0.0, 1.0)) {
            val palette = SkyTheme.palette(noon, k)
            val tint = palette.glass.rgb
            val expected = mix(Rgb.White, tint, 0.1)
            assertEquals(expected.r, palette.glassHighlight.rgb.r, 1e-9)
            assertEquals(expected.g, palette.glassHighlight.rgb.g, 1e-9)
            assertEquals(expected.b, palette.glassHighlight.rgb.b, 1e-9)
        }
    }

    @Test
    fun theMiddleOfTheSwitchIsBetweenTheEnds() {
        val half = SkyTheme.palette(noon, 0.5)
        assertTrue(half.shadow.alpha in 0.16..0.34)
        assertTrue(half.glassHighlight.alpha in 0.10..0.30)
    }
}
