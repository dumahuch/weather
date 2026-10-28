package io.github.dumahuch.pogodaapp.ui.sky

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameStatsTest {

    private val second = 1_000_000_000L

    @Test
    fun countsSlowFramesAgainstBudget() {
        val stats = FrameStats(budgetMillis = 16.67)
        repeat(98) { stats.add(it * 10_000_000L, 8.0) }
        stats.add(980_000_000L, 30.0)
        stats.add(990_000_000L, 17.0)
        val summary = stats.summary(1 * second)
        assertEquals(100, summary.frames)
        assertEquals(2, summary.slow)
        assertEquals(8.0, summary.median, 1e-9)
        assertEquals(8.0, summary.p95, 1e-9)
        assertEquals(30.0, summary.longest, 1e-9)
    }

    @Test
    fun framesPerSecondUsesLastFiveSeconds() {
        val stats = FrameStats(budgetMillis = 16.67)
        // Старые кадры (дальше пяти секунд назад) в частоту не входят.
        repeat(50) { stats.add(it * 10_000_000L, 5.0) }
        // Двадцать кадров в секунду на протяжении последних пяти секунд.
        repeat(100) { stats.add(20 * second + it * 50_000_000L, 5.0) }
        val summary = stats.summary(25 * second)
        assertEquals(20.0, summary.perSecond, 0.5)
        assertEquals(150, summary.frames)
    }

    @Test
    fun keepsOnlyLastFramesButCountsAll() {
        val stats = FrameStats(budgetMillis = 10.0, capacity = 10)
        repeat(5) { stats.add(it * 1_000L, 50.0) }
        repeat(10) { stats.add(10_000L + it * 1_000L, 2.0) }
        val summary = stats.summary(30_000L)
        assertEquals(15, summary.frames)
        assertEquals(5, summary.slow)
        // Медленные кадры давно вытеснены из окна последних десяти.
        assertEquals(2.0, summary.longest, 1e-9)
    }

    @Test
    fun emptyAndReset() {
        val stats = FrameStats(budgetMillis = 10.0)
        assertEquals(0, stats.summary(0L).frames)
        stats.add(0L, 20.0)
        stats.reset()
        val summary = stats.summary(1L)
        assertEquals(0, summary.frames)
        assertEquals(0, summary.slow)
        assertEquals(0.0, summary.longest, 0.0)
    }
}
