package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightningTest {

    private fun strikes(seed: Long, count: Int): List<Strike> {
        val storm = Lightning(seed)
        return List(count) { storm.next() }
    }

    @Test
    fun firstStrikeComesSoonThenPausesStayInRange() {
        val all = strikes(1, 500)
        assertEquals(Lightning.FIRST_GAP_SECONDS, all.first().gapSeconds, 0.0)
        for (strike in all.drop(1)) {
            assertTrue("пауза ${strike.gapSeconds}", strike.gapSeconds in Lightning.MIN_GAP_SECONDS..Lightning.MAX_GAP_SECONDS)
        }
    }

    @Test
    fun atMostTwoFlashesPerSecond() {
        for (strike in strikes(2, 500)) {
            assertTrue(strike.pulses.size in 1..2)
            assertTrue("разряд ${strike.duration} с", strike.duration < 1.2)
        }
    }

    @Test
    fun aboutHalfTheStrikesShowABoltAndNeverTheSameTwiceInARow() {
        val all = strikes(3, 2000)
        val bolts = all.filter { it.bolt >= 0 }
        val share = bolts.size / all.size.toDouble()
        assertTrue("доля молний $share", share in 0.58..0.72)
        for (strike in bolts) assertTrue(strike.bolt in 0 until Lightning.BOLT_COUNT)
        for (i in 1 until bolts.size) assertNotEquals(bolts[i - 1].bolt, bolts[i].bolt)
        assertEquals(Lightning.BOLT_COUNT, bolts.map { it.bolt }.toSet().size)
    }

    @Test
    fun lightRisesFastAndFadesOut() {
        val strike = Strike(0.0, listOf(Strike.Pulse(0.0, 0.9)), bolt = 2, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        assertNull(strike.frame(-0.01))
        assertEquals(0.0, strike.frame(0.0)!!.flash, 1e-9)
        assertEquals(0.9, strike.frame(Lightning.RISE_SECONDS)!!.flash, 1e-9)
        assertTrue(strike.frame(0.1)!!.flash < 0.3)
        assertTrue(strike.frame(strike.duration - 0.001)!!.flash < 0.01)
        assertNull(strike.frame(strike.duration + 0.01))
    }

    @Test
    fun secondFlashDipsInBetween() {
        val strike = Strike(0.0, listOf(Strike.Pulse(0.0, 1.0), Strike.Pulse(0.12, 0.8)), bolt = -1, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        val dip = strike.light(0.119)
        assertTrue("провал $dip", dip < 0.4)
        assertEquals(0.8, strike.light(0.12 + Lightning.RISE_SECONDS), 1e-9)
    }

    @Test
    fun boltOutlastsTheFlashAndIsAbsentWithoutBolt() {
        val pulses = listOf(Strike.Pulse(0.0, 0.8))
        val withBolt = Strike(0.0, pulses, bolt = 0, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        val cloudOnly = Strike(0.0, pulses, bolt = -1, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        // Молния полной силы держится через 0,15 с, когда вспышка уже почти погасла.
        assertEquals(0.8, withBolt.frame(Lightning.BOLT_RISE + 0.05)!!.bolt, 1e-9)
        assertTrue(withBolt.frame(0.15)!!.bolt > 0.7)
        assertTrue(withBolt.frame(0.15)!!.flash < 0.1)
        assertTrue(withBolt.frame(withBolt.duration - 0.001)!!.bolt < 0.02)
        assertEquals(0.0, cloudOnly.frame(0.05)!!.bolt, 0.0)
    }

    @Test
    fun boltFlickersOnSecondStrike() {
        val strike = Strike(0.0, listOf(Strike.Pulse(0.0, 1.0), Strike.Pulse(0.5, 0.9)), bolt = 1, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        val dip = strike.boltLight(0.49)
        assertTrue("провал $dip", dip < 0.1)
        assertEquals(0.9, strike.boltLight(0.5 + Lightning.BOLT_RISE + 0.05), 1e-9)
    }

    @Test
    fun boltIsVisibleForAtLeastTenFramesOfAnAverageFrameRate() {
        val strike = Strike(0.0, listOf(Strike.Pulse(0.0, 0.8)), bolt = 0, boltX = 0.5, mirrored = false, boltDepth = 0.5, boltScale = 1.0, boltTilt = 0.0, glowX = 0.5, glowY = 0.3)
        val frame = 1.0 / 38
        val visible = generateSequence(0) { it + 1 }.takeWhile { it * frame < strike.duration }.count { strike.frame(it * frame)!!.bolt >= 0.25 }
        assertTrue("кадров с молнией $visible", visible >= 10)
    }

    @Test
    fun placesStayOnScreen() {
        for (strike in strikes(4, 500)) {
            assertTrue(strike.boltX in 0.2..0.8)
            assertTrue(strike.boltScale in 0.8..1.2)
            assertTrue(strike.boltDepth in Lightning.MIN_DEPTH..Lightning.MAX_DEPTH)
            assertTrue(Math.abs(strike.boltTilt) <= Lightning.MAX_TILT)
            assertTrue(strike.glowX in 0.0..1.0)
            assertTrue(strike.glowY in 0.0..0.5)
            for (pulse in strike.pulses) assertTrue(pulse.peak in 0.2..1.0)
        }
    }

    @Test
    fun sameSeedSameStorm() {
        val a = strikes(7, 50)
        val b = strikes(7, 50)
        for (i in a.indices) {
            assertEquals(a[i].gapSeconds, b[i].gapSeconds, 0.0)
            assertEquals(a[i].bolt, b[i].bolt)
        }
    }

    @Test
    fun fileNames() {
        assertEquals("lightning/bolt_01.webp", Lightning.file(0))
        assertEquals("lightning/bolt_06.webp", Lightning.file(Lightning.BOLT_COUNT - 1))
    }
}
