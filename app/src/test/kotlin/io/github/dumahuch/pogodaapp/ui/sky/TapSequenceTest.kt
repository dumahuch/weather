package io.github.dumahuch.pogodaapp.ui.sky

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSequenceTest {

    private fun TapSequence.tapsAt(vararg times: Long): List<Boolean> = times.map { tap(it) }

    @Test
    fun seventhQuickTapOpens() {
        val taps = TapSequence()
        val results = taps.tapsAt(0, 300, 600, 900, 1200, 1500, 1800)
        assertEquals6False(results)
        assertTrue(results.last())
    }

    private fun assertEquals6False(results: List<Boolean>) {
        results.dropLast(1).forEach { assertFalse(it) }
    }

    @Test
    fun sixTapsAreNotEnough() {
        val results = TapSequence().tapsAt(0, 200, 400, 600, 800, 1000)
        assertFalse(results.any { it })
    }

    @Test
    fun longPauseStartsOver() {
        val taps = TapSequence()
        taps.tapsAt(0, 200, 400, 600, 800, 1000)
        // Пауза дольше допустимой: счёт идёт заново, и седьмое нажатие со старого счёта не засчитывается.
        assertFalse(taps.tap(5000))
        val rest = taps.tapsAt(5200, 5400, 5600, 5800, 6000)
        assertFalse(rest.any { it })
        assertTrue(taps.tap(6200))
    }

    @Test
    fun countStartsOverAfterSuccess() {
        val taps = TapSequence()
        val first = taps.tapsAt(0, 100, 200, 300, 400, 500, 600)
        assertTrue(first.last())
        val second = taps.tapsAt(700, 800, 900, 1000, 1100, 1200, 1300)
        assertEquals6False(second)
        assertTrue(second.last())
    }
}
