package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** «Скоро дождь»: решения по кадрам радара (порог 0,5 мм/ч, окно час, шаг пять минут). */
class RainSoonTest {

    private val now = Instant.parse("2026-11-08T12:02:00Z")

    /** Кадры с шагом пять минут, первый на 12:05 (он описывает 12:00–12:05, то есть «сейчас»), плюс час истории. */
    private fun frames(vararg mm: Double): List<RadarFrame> {
        val first = Instant.parse("2026-11-08T12:05:00Z")
        return mm.mapIndexed { index, value -> RadarFrame(first.plus(Duration.ofMinutes(5L * index)), value) }
    }

    private fun dry(count: Int) = DoubleArray(count) { 0.0 }

    @Test
    fun dryHourGivesDry() {
        assertEquals(RainOutlook.Dry, RainSoon.outlook(frames(*dry(16)), now))
    }

    @Test
    fun rainRightNowGivesRaining() {
        assertEquals(RainOutlook.Raining, RainSoon.outlook(frames(0.6, 0.0, 0.0), now))
    }

    @Test
    fun belowTheThresholdIsNotRain() {
        assertEquals(RainOutlook.Dry, RainSoon.outlook(frames(0.0, 0.4, 0.49, 0.3, *dry(10)), now))
    }

    @Test
    fun rainStartingLaterReportsMinutesToTheStartOfItsFrame() {
        // Кадр 12:20 описывает 12:15–12:20; от 12:02 до 12:15 тринадцать минут.
        val outlook = RainSoon.outlook(frames(0.0, 0.0, 0.0, 1.2, 1.5, *dry(5)), now)
        assertEquals(RainOutlook.Starts(13), outlook)
    }

    @Test
    fun aLoneWeakFrameIsNoise() {
        assertEquals(RainOutlook.Dry, RainSoon.outlook(frames(0.0, 0.0, 0.7, 0.0, *dry(10)), now))
    }

    @Test
    fun twoWeakFramesInARowCount() {
        assertEquals(RainOutlook.Starts(8), RainSoon.outlook(frames(0.0, 0.0, 0.6, 0.7, *dry(10)), now))
    }

    @Test
    fun aLoneStrongFrameCounts() {
        assertEquals(RainOutlook.Starts(8), RainSoon.outlook(frames(0.0, 0.0, 1.0, 0.0, *dry(10)), now))
    }

    @Test
    fun rainBeyondTheHourIsIgnored() {
        // Кадр 13:10 описывает 13:05–13:10: начало за границей часа (13:02).
        assertEquals(RainOutlook.Dry, RainSoon.outlook(frames(*dry(13), 2.0, 2.0), now))
        // Кадр 13:05 описывает 13:00–13:05: начало внутри часа.
        assertEquals(RainOutlook.Starts(58), RainSoon.outlook(frames(*dry(12), 2.0, 2.0), now))
    }

    @Test
    fun onlyFutureFramesCountAndHistoryIsSkipped() {
        val history = listOf(
            RadarFrame(Instant.parse("2026-11-08T11:50:00Z"), 5.0),
            RadarFrame(Instant.parse("2026-11-08T11:55:00Z"), 5.0),
        )
        assertEquals(RainOutlook.Dry, RainSoon.outlook(history + frames(*dry(14)), now))
    }

    @Test
    fun staleOrEmptyDataIsUnknown() {
        assertEquals(RainOutlook.Unknown, RainSoon.outlook(emptyList(), now))
        val old = listOf(RadarFrame(Instant.parse("2026-11-08T11:00:00Z"), 3.0))
        assertEquals(RainOutlook.Unknown, RainSoon.outlook(old, now))
    }

    @Test
    fun unsortedFramesAreHandled() {
        val shuffled = frames(0.0, 0.0, 1.0, 1.0, *dry(5)).reversed()
        assertEquals(RainOutlook.Starts(8), RainSoon.outlook(shuffled, now))
    }

    @Test
    fun startsNotifiesOnceUntilItIsDryAgain() {
        val first = RainSoon.decide(RainOutlook.Starts(13), notified = false)
        assertEquals(13, first.notifyInMinutes)
        assertTrue(first.notified)
        assertFalse(first.cancel)
        val again = RainSoon.decide(RainOutlook.Starts(8), notified = true)
        assertNull(again.notifyInMinutes)
        assertTrue(again.notified)
        val rain = RainSoon.decide(RainOutlook.Raining, notified = true)
        assertNull(rain.notifyInMinutes)
        assertTrue(rain.cancel)
        assertTrue(rain.notified)
        val dryAgain = RainSoon.decide(RainOutlook.Dry, notified = true)
        assertTrue(dryAgain.cancel)
        assertFalse(dryAgain.notified)
        assertEquals(5, RainSoon.decide(RainOutlook.Starts(5), notified = false).notifyInMinutes)
    }

    @Test
    fun unknownChangesNothing() {
        val decision = RainSoon.decide(RainOutlook.Unknown, notified = true)
        assertNull(decision.notifyInMinutes)
        assertFalse(decision.cancel)
        assertTrue(decision.notified)
    }

    @Test
    fun minutesRoundUpToFive() {
        assertEquals(10, RainSoon.roundedMinutes(6))
        assertEquals(10, RainSoon.roundedMinutes(10))
        assertEquals(15, RainSoon.roundedMinutes(11))
        assertEquals(60, RainSoon.roundedMinutes(58))
    }
}
