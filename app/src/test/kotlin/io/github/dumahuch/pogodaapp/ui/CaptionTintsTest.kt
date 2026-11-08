package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Оттенки значков строки под температурой: заметны там, где небо даёт запас контраста, держат контраст значка (3 : 1)
 * и подстраиваются под худший цвет неба под строкой.
 */
class CaptionTintsTest {

    private val darkInk = Color(0xFF30363E)
    private val lightInk = Color(0xFFE8ECF1)

    /** Серое дневное небо со снимка Димы и ночное тёмное. */
    private val graySky = Color(0xFFACAFB0)
    private val nightSky = Color(0xFF2B3340)

    @Test
    fun humidityIsBlueWindIsTealAndPrecipitationIsBluishWhenTheSkyAllowsIt() {
        val sky = Color(0xFFE6EEF6)
        val ink = Color(0xFF101722)
        val drop = CaptionTints.icon(ink, 0f, CaptionHue.Humidity, sky)
        val wind = CaptionTints.icon(ink, 0f, CaptionHue.Wind, sky)
        val rain = CaptionTints.icon(ink, 0f, CaptionHue.Precipitation, sky)
        assertTrue("капля синяя", drop.blue - drop.red >= 0.12f)
        assertTrue("ветер бирюзовый: зелёного и синего больше красного", wind.green - wind.red >= 0.08f && wind.blue - wind.red >= 0.08f)
        assertTrue("осадки сине-фиолетовые", rain.blue - rain.green >= 0.08f)
    }

    @Test
    fun everyHueKeepsTheIconContrastOnTheGrayAndOnTheNightSky() {
        for ((ink, smoke, sky) in listOf(Triple(darkInk, 0f, graySky), Triple(lightInk, 1f, nightSky))) {
            for (hue in CaptionHue.entries) {
                val color = CaptionTints.icon(ink, smoke, hue, sky)
                val contrast = ExtremeTints.contrastRatio(color, sky)
                assertTrue("$hue: контраст $contrast", contrast >= ExtremeTints.MIN_ARROW_CONTRAST)
            }
        }
    }

    @Test
    fun theIconFallsBackToTheTextColourWhenNoTintHoldsTheContrast() {
        // Небо ровно того цвета, что и текст: никакой оттенок контраст не вернёт.
        assertEquals(darkInk, CaptionTints.icon(darkInk, 0f, CaptionHue.Humidity, darkInk))
    }

    @Test
    fun theTintSlidesFromTheDarkSetToTheLightSetWithSmoke() {
        val dark = CaptionTints.hue(CaptionHue.Humidity, 0f)
        val light = CaptionTints.hue(CaptionHue.Humidity, 1f)
        assertTrue("на тёмном небе оттенок светлее", light.red + light.green + light.blue > dark.red + dark.green + dark.blue)
    }
}
