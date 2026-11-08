package io.github.dumahuch.pogodaapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroWidthTest {

    @Test
    fun theWidestTwoDigitNumberFitsTheFullLimitOnAPhoneWithNormalFont() {
        // Pixel 6 Pro, 411 dp: «66°» при цифрах 124 sp и значке 72 dp помещается и при предельном масштабе.
        val scale = HeroWidth.maxScale("66", 124, 411f, 1f)
        assertTrue("масштаб $scale", scale >= HeroFit.MAX_SCALE)
    }

    @Test
    fun aNegativeNumberAlsoFitsTheFullLimit() {
        val scale = HeroWidth.maxScale("−88", 94, 411f, 1f)
        assertTrue("масштаб $scale", scale >= HeroFit.MAX_SCALE)
    }

    @Test
    fun aLargeSystemFontLimitsTheGrowth() {
        val normal = HeroWidth.maxScale("66", 124, 411f, 1f)
        val large = HeroWidth.maxScale("66", 124, 411f, 1.3f)
        assertTrue("крупный шрифт даёт меньший предел: $large < $normal", large < normal)
        assertTrue("но не меньше обычного размера", large >= 1f)
    }

    @Test
    fun aNarrowScreenLimitsTheGrowth() {
        assertTrue(HeroWidth.maxScale("66", 124, 240f, 1f) < HeroFit.MAX_SCALE)
    }

    @Test
    fun theScaleNeverDropsBelowTheNormalSize() {
        assertEquals(1f, HeroWidth.maxScale("−88", 94, 150f, 2f), 0f)
    }

    @Test
    fun widthOfDigitsFollowsTheMeasuredAdvances() {
        // Две цифры по 0,62 минус два межбуквенных интервала по 0,035.
        assertEquals(1.17, HeroWidth.digitsEm("12"), 1e-9)
        // Минус 0,54 и две цифры.
        assertEquals(0.54 + 2 * 0.62 - 3 * 0.035, HeroWidth.digitsEm("−12"), 1e-9)
    }

    @Test
    fun theIconGrowsWithTheDigitsAndKeepsTheOldSizeAtScaleOne() {
        assertEquals(72f, HeroWidth.glyphDp(124, 1f), 1e-3f)
        assertEquals(72f * 1.3f, HeroWidth.glyphDp(124, 1.3f), 1e-3f)
        // Мелкие цифры «−12» (94 sp) дают значок в той же доле.
        assertEquals(72f * 94 / 124, HeroWidth.glyphDp(94, 1f), 1e-3f)
    }

    @Test
    fun centeringCountsTheDegreeFullyAndHalfOfTheWiderIcon() {
        assertEquals(20f, HeroWidth.centeringSide(degree = 20f, glyph = 0f), 1e-4f)
        assertEquals(20f, HeroWidth.centeringSide(degree = 20f, glyph = 15f), 1e-4f)
        assertEquals(20f + (94f - 20f) * HeroWidth.ICON_CENTER_WEIGHT, HeroWidth.centeringSide(20f, 94f), 1e-4f)
    }

    @Test
    fun theGrownBlockStillFitsOnThePhoneAtTheLimit() {
        // Значок и цифры растут вместе: на 411 dp «66°» при предельном масштабе помещается с запасом 8 dp по краям.
        val width = 411.0
        val scale = HeroWidth.maxScale("66", 124, 411f, 1f).coerceAtMost(HeroFit.MAX_SCALE).toDouble()
        val text = 124.0 * scale * (HeroWidth.digitsEm("66") + HeroWidth.DEGREE_SCALE * HeroWidth.DEGREE_GAP_EM)
        val degree = 124.0 * scale * (0.45 * HeroWidth.DEGREE_SCALE - HeroWidth.DEGREE_PULL_EM)
        val glyph = HeroWidth.glyphDp(124, scale.toFloat()).toDouble()
        val centering = HeroWidth.centeringSide(degree.toFloat(), glyph.toFloat())
        val left = (width - (text + centering)) / 2
        val right = width - (left + text + maxOf(degree, glyph))
        assertTrue("слева $left", left >= 8.0 - 1e-6)
        assertTrue("справа $right", right >= 8.0 - 1e-6)
    }
}
