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
}
