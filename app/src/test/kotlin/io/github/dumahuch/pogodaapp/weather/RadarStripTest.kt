package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Карточка «Ближайшие 2 часа»: кадры от текущего, первый дождь, его конец и сумма; два часа в окне фразы. */
class RadarStripTest {

    private val now = Instant.parse("2026-11-22T14:02:00Z")

    private fun frames(vararg mmPerHour: Double, from: Instant = Instant.parse("2026-11-22T13:55:00Z")) =
        mmPerHour.mapIndexed { i, v -> RadarFrame(from.plusSeconds(i * 300L), v) }

    @Test
    fun stripStartsAtTheCurrentFrameAndHoldsTwoHours() {
        val all = frames(*DoubleArray(40) { 0.0 })
        val strip = RadarStrip.of(all.shuffled(), now)
        assertEquals(RadarStrip.FRAMES, strip.size)
        assertEquals(Instant.parse("2026-11-22T14:05:00Z"), strip.first().time)
        assertTrue(strip.zipWithNext().all { (a, b) -> a.time < b.time })
    }

    @Test
    fun spellIsTheFirstRunAboveTheThresholdWithItsEndAndTotal() {
        // Кадры с 13:55, текущий 14:05 (0,2, ниже порога); дождь в кадрах 14:10–14:20 по 1,2 мм/ч (три по пять минут = 0,3 мм),
        // потом сухо и снова дождь (второй дождь в сумму не идёт).
        val strip = RadarStrip.of(frames(0.0, 0.0, 0.2, 1.2, 1.2, 1.2, 0.1, 3.0), now)
        val spell = RadarStrip.spell(strip)!!
        assertEquals(Instant.parse("2026-11-22T14:20:00Z"), spell.end)
        assertEquals(0.3, spell.totalMm, 1e-9)
        assertNull(RadarStrip.spell(RadarStrip.of(frames(0.0, 0.1, 0.3, 0.0), now)))
    }

    @Test
    fun headlineLooksTwoHoursAheadNotOne() {
        // Дождь в кадре 15:35 (начало 15:30, через 88 минут): уведомление (окно час) молчит, карточка его называет.
        val values = DoubleArray(30) { 0.0 }.also { it[20] = 2.0; it[21] = 2.0 }
        val all = frames(*values)
        assertEquals(RainOutlook.Dry, RainSoon.outlook(all, now))
        val outlook = RainSoon.outlook(all, now, RadarStrip.SPAN)
        assertTrue("$outlook", outlook is RainOutlook.Starts && outlook.inMinutes == 88)
    }
}
