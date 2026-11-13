package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrecipClockTest {
    private val step = 30_000_000L

    @Test
    fun theFirstFrameStartsTheClockEvenWithHugeFrameTimes() {
        val clock = PrecipClock(step, 3.0)
        assertEquals(3.0, clock.seconds(Long.MAX_VALUE / 2)!!, 1e-9)
    }

    @Test
    fun theClockAdvancesAsFramesComeInAndSkipsTheFastOnes() {
        val clock = PrecipClock(step, 3.0)
        val start = 5_000_000_000_000L
        clock.seconds(start)
        assertNull(clock.seconds(start + 16_000_000L))
        assertEquals(3.033, clock.seconds(start + 33_000_000L)!!, 1e-9)
        assertNull(clock.seconds(start + 50_000_000L))
        assertEquals(3.066, clock.seconds(start + 66_000_000L)!!, 1e-9)
    }

    @Test
    fun aSlowFrameStillMovesTheClockByTheRealElapsedTime() {
        val clock = PrecipClock(step, 0.0)
        clock.seconds(1_000L)
        assertEquals(2.0, clock.seconds(1_000L + 2_000_000_000L)!!, 1e-9)
    }

    @Test
    fun aFrameExactlyOneStepLaterCounts() {
        val clock = PrecipClock(step, 0.0)
        clock.seconds(0L)
        assertNotNull(clock.seconds(step))
    }
}
