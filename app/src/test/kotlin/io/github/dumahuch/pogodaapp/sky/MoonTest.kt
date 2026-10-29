package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Луна сверяется с независимой библиотекой PyEphem 4 (Python): освещённая доля `Moon(время).phase`,
 * восход и заход `Observer.next_rising/next_setting` по верхнему краю диска с поправкой −34′ без добавочной атмосферы
 * (pressure = 0, horizon = −0:34). Расхождения при выборе значений были до 1,5 минуты по времени и до 0,2 % по освещённости.
 */
class MoonTest {

    private class IllumCase(val time: String, val expected: Double)

    private val illuminations = listOf(
        IllumCase("2026-10-29T12:00:00Z", 0.8510),
        IllumCase("2026-11-05T12:00:00Z", 0.1445),
        IllumCase("2026-11-09T07:02:03Z", 0.0019),
        IllumCase("2026-11-17T11:47:45Z", 0.5013),
        IllumCase("2026-11-24T14:53:29Z", 0.9981),
        IllumCase("2026-12-01T20:00:00Z", 0.4389),
    )

    @Test
    fun illuminationMatchesAnIndependentLibrary() {
        for (c in illuminations) {
            val phase = Moon.phaseAt(Instant.parse(c.time))
            assertEquals("освещённая доля ${c.time}", c.expected, phase.illumination, 0.01)
        }
    }

    @Test
    fun cycleIsZeroAtNewMoonAndHalfAtFullMoon() {
        // Новолуние 9 ноября 2026 в 07:02 UTC, полнолуние 24 ноября в 14:53 (PyEphem next_new_moon, next_full_moon).
        val newMoon = Moon.phaseAt(Instant.parse("2026-11-09T07:02:03Z")).cycle
        assertTrue("цикл в новолуние $newMoon", newMoon < 0.01 || newMoon > 0.99)
        val full = Moon.phaseAt(Instant.parse("2026-11-24T14:53:29Z")).cycle
        assertEquals(0.5, full, 0.01)
        val firstQuarter = Moon.phaseAt(Instant.parse("2026-11-17T11:47:45Z")).cycle
        assertEquals(0.25, firstQuarter, 0.01)
    }

    @Test
    fun phaseNamesFollowTheCycle() {
        fun name(cycle: Double) = Moon.Phase(0.0, cycle).name
        assertEquals(Moon.PhaseName.New, name(0.0))
        assertEquals(Moon.PhaseName.New, name(0.97))
        assertEquals(Moon.PhaseName.WaxingCrescent, name(0.125))
        assertEquals(Moon.PhaseName.FirstQuarter, name(0.25))
        assertEquals(Moon.PhaseName.WaxingGibbous, name(0.375))
        assertEquals(Moon.PhaseName.Full, name(0.5))
        assertEquals(Moon.PhaseName.WaningGibbous, name(0.625))
        assertEquals(Moon.PhaseName.LastQuarter, name(0.75))
        assertEquals(Moon.PhaseName.WaningCrescent, name(0.875))
    }

    @Test
    fun waxingMeansTheFirstHalfOfTheCycle() {
        assertTrue(Moon.Phase(0.3, 0.2).waxing)
        assertTrue(!Moon.Phase(0.3, 0.8).waxing)
        assertEquals(85, Moon.Phase(0.851, 0.62).percent)
    }

    private class EventCase(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val zone: String,
        val date: String,
        val rise: String?,
        val set: String?,
    )

    private val events = listOf(
        EventCase("Ганновер, 29 октября", 52.37, 9.74, "Europe/Berlin", "2026-10-29", "2026-10-29T17:34:26Z", "2026-10-29T10:58:40Z"),
        EventCase("Ганновер, 5 ноября", 52.37, 9.74, "Europe/Berlin", "2026-11-05", "2026-11-05T01:51:38Z", "2026-11-05T14:03:05Z"),
        EventCase("Ганновер, 13 ноября", 52.37, 9.74, "Europe/Berlin", "2026-11-13", "2026-11-13T11:09:16Z", "2026-11-13T17:54:30Z"),
        EventCase("Ганновер, 20 ноября", 52.37, 9.74, "Europe/Berlin", "2026-11-20", "2026-11-20T13:09:55Z", "2026-11-20T01:18:04Z"),
        EventCase("Москва, лето", 55.75, 37.62, "Europe/Moscow", "2026-06-21", "2026-06-21T09:00:50Z", "2026-06-20T21:05:28Z"),
        EventCase("Сидней, южное полушарие", -33.87, 151.21, "Australia/Sydney", "2026-01-15", "2026-01-14T15:20:52Z", "2026-01-15T06:32:08Z"),
        EventCase("Стамбул", 41.0, 29.0, "Europe/Istanbul", "2026-08-12", "2026-08-12T02:27:41Z", "2026-08-12T17:05:04Z"),
    )

    @Test
    fun riseAndSetMatchAnIndependentLibrary() {
        for (c in events) {
            val result = Moon.eventsOn(LocalDate.parse(c.date), ZoneId.of(c.zone), c.latitude, c.longitude)
            check(c.name, "восход", c.rise, result.rise)
            check(c.name, "заход", c.set, result.set)
        }
    }

    private fun check(name: String, what: String, expected: String?, actual: Instant?) {
        if (expected == null) {
            assertNull("$name: $what должен отсутствовать", actual)
            return
        }
        assertNotNull("$name: нет значения для «$what»", actual)
        val minutes = (actual!!.epochSecond - Instant.parse(expected).epochSecond) / 60.0
        assertTrue("$name: $what расходится на $minutes мин", kotlin.math.abs(minutes) <= 3.0)
    }

    @Test
    fun dayWithoutRiseHasNoRise() {
        // 1 декабря 2026 в Кайзерслаутерне по PyEphem восхода в эти местные сутки нет, заход есть.
        val result = Moon.eventsOn(LocalDate.parse("2026-12-01"), ZoneId.of("Europe/Berlin"), 49.44, 7.77)
        assertNull(result.rise)
        assertNotNull(result.set)
    }

    @Test
    fun riseComesBeforeSetWhenBothHappenWithAWaxingMoonInTheEvening() {
        // Растущая луна восходит днём и заходит ночью (13 ноября 2026: восход 12:09, заход 18:54 по местному времени).
        val result = Moon.eventsOn(LocalDate.parse("2026-11-13"), ZoneId.of("Europe/Berlin"), 52.37, 9.74)
        assertTrue(result.rise!!.isBefore(result.set!!))
    }
}
