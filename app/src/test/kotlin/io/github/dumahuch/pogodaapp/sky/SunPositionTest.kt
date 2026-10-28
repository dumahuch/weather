package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Высота солнца сверяется с независимой библиотекой astral 3.2 (Python, без поправки на рефракцию):
 * значения получены командой `elevation(Observer(lat, lon), момент, with_refraction=False)`.
 * Формулы приближённые (около угловой минуты), допуск 0,05°.
 */
class SunPositionTest {

    private class Case(val latitude: Double, val longitude: Double, val time: String, val expected: Double, val name: String)

    private val cases = listOf(
        Case(49.44, 7.77, "2026-10-28T11:00:00Z", 27.297, "Кайзерслаутерн, около полудня"),
        Case(49.44, 7.77, "2026-10-28T16:00:00Z", 1.311, "Кайзерслаутерн, закат"),
        Case(49.44, 7.77, "2026-10-28T04:00:00Z", -21.700, "Кайзерслаутерн, ночь"),
        Case(52.37, 9.74, "2026-06-21T10:30:00Z", 59.358, "Ганновер, лето"),
        Case(55.75, 37.62, "2026-12-21T09:00:00Z", 10.596, "Москва, зимнее солнцестояние"),
        Case(-33.87, 151.21, "2026-01-15T02:00:00Z", 77.237, "Сидней, южное полушарие"),
        Case(0.0, 0.0, "2026-03-20T12:00:00Z", 88.141, "экватор в равноденствие"),
        Case(64.0, -22.0, "2026-12-21T13:00:00Z", 2.413, "Исландия, зима"),
    )

    @Test
    fun elevationMatchesAnIndependentLibrary() {
        for (c in cases) {
            val sun = SunPosition.at(c.latitude, c.longitude, Instant.parse(c.time))
            assertEquals("${c.name}: высота солнца", c.expected, sun.elevation, 0.05)
        }
    }

    @Test
    fun solarHourFollowsLongitudeAndEquationOfTime() {
        // 28 октября уравнение времени около +16 минут, долгота 7,77° даёт ещё 31 минуту:
        // 11:00 по Гринвичу это около 11:47 по солнцу.
        val sun = SunPosition.at(49.44, 7.77, Instant.parse("2026-10-28T11:00:00Z"))
        assertEquals(11.79, sun.solarHour, 0.1)
    }

    @Test
    fun solarHourStaysWithinTheDay() {
        for (hour in 0 until 24) {
            val sun = SunPosition.at(10.0, 170.0, Instant.parse("2026-03-01T00:00:00Z").plusSeconds(hour * 3600L))
            assertTrue("солнечный час ${sun.solarHour}", sun.solarHour >= 0.0 && sun.solarHour < 24.0)
        }
    }

    @Test
    fun solarHourSettingGivesSymmetricElevation() {
        val day = Instant.parse("2026-10-28T12:00:00Z")
        val morning = SunPosition.atSolarHour(49.44, day, 9.0)
        val afternoon = SunPosition.atSolarHour(49.44, day, 15.0)
        assertEquals(morning.elevation, afternoon.elevation, 1e-9)
        val noon = SunPosition.atSolarHour(49.44, day, 12.0)
        assertTrue(noon.elevation > morning.elevation)
        // Склонение солнца в конце октября около −13°: полуденная высота 90 − 49,44 − 13,2.
        assertEquals(27.4, noon.elevation, 0.5)
    }
}
