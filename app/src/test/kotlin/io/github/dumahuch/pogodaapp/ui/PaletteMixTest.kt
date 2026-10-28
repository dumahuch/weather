package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.Rgba
import io.github.dumahuch.pogodaapp.ui.sky.toColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PaletteMixTest {

    private val neutral = PogodaPalette.Neutral
    private val night = neutral.copy(ink = Color(244, 247, 251), darkInk = false, scrim = Color(10, 20, 40, 90))

    @Test
    fun endsAreTheArguments() {
        assertSame(neutral, neutral.mixWith(night, 0f))
        assertSame(night, neutral.mixWith(night, 1f))
    }

    @Test
    fun middleIsBetween() {
        val half = neutral.mixWith(night, 0.5f)
        assertTrue(half.ink.red > neutral.ink.red && half.ink.red < night.ink.red)
        assertTrue(half.scrim.alpha > 0f && half.scrim.alpha < night.scrim.alpha)
    }

    @Test
    fun iconsFollowTheCloserSide() {
        assertTrue(neutral.mixWith(night, 0.4f).darkInk)
        assertTrue(!neutral.mixWith(night, 0.6f).darkInk)
    }

    @Test
    fun colorBridgeRoundsAlphaToHundredths() {
        val a = Rgba(Rgb(10.4, 20.6, 300.0), 0.2049).toColor()
        val b = Rgba(Rgb(10.0, 21.0, 255.0), 0.2001).toColor()
        assertEquals(a, b)
        assertEquals(51, (a.alpha * 255).roundToInt())
    }
}
