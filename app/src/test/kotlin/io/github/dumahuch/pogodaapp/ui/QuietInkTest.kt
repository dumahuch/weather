package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Приглушённый цвет второстепенного текста: слабее основного там, где есть запас контраста, и не ниже порога читаемости. */
class QuietInkTest {

    private val lightInk = Color(0xFFF4F7FB)
    private val darkInk = Color(0xFF101722)

    private val nightSky = Color(0xFF0B1426)
    private val daySky = Color(0xFFD9E8F5)
    private val midSky = Color(0xFF4F7FA8)

    private fun contrast(a: Color, b: Color) = ExtremeTints.contrastRatio(a, b)

    @Test
    fun darkSkyGivesQuietLightText() {
        val quiet = QuietInk.text(lightInk, nightSky)
        assertTrue("слабее основного цвета", contrast(quiet, nightSky) < contrast(lightInk, nightSky))
        assertTrue("контраст ${contrast(quiet, nightSky)}", contrast(quiet, nightSky) >= QuietInk.TEXT_CONTRAST)
    }

    @Test
    fun lightSkyGivesQuietDarkText() {
        val quiet = QuietInk.text(darkInk, daySky)
        assertTrue("слабее основного цвета", contrast(quiet, daySky) < contrast(darkInk, daySky))
        assertTrue("контраст ${contrast(quiet, daySky)}", contrast(quiet, daySky) >= QuietInk.TEXT_CONTRAST)
    }

    @Test
    fun textNeverFallsBelowReadabilityWhenInkIsReadable() {
        for (backdrop in listOf(nightSky, daySky, midSky, Color(0xFF1F2E4A), Color(0xFFB8C8D8))) {
            for (ink in listOf(lightInk, darkInk)) {
                val base = contrast(ink, backdrop)
                val quiet = contrast(QuietInk.text(ink, backdrop), backdrop)
                assertTrue("фон $backdrop, текст $ink: $quiet", quiet >= minOf(base, QuietInk.TEXT_CONTRAST) - 1e-3f)
            }
        }
    }

    @Test
    fun inkStaysWhenThereIsNoRoomToFade() {
        // Светлый текст на светлом небе: контраст и так ниже порога, ослаблять нечего.
        assertEquals(lightInk, QuietInk.text(lightInk, daySky))
    }

    @Test
    fun fadedIconIsWeakerThanTextButStillVisible() {
        val text = QuietInk.text(lightInk, nightSky)
        val icon = QuietInk.fadedIcon(lightInk, nightSky)
        assertTrue(contrast(icon, nightSky) < contrast(text, nightSky))
        assertTrue("контраст значка ${contrast(icon, nightSky)}", contrast(icon, nightSky) >= QuietInk.ICON_CONTRAST)
    }
}
