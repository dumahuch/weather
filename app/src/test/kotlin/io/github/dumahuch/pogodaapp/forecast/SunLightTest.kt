package io.github.dumahuch.pogodaapp.forecast

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint

/**
 * Свет суток сверяется с независимой библиотекой PyEphem 4 (центр диска, без рефракции, `pressure = 0`, горизонт по
 * границе): время до полутора минут, азимут восхода и высота в полдень до половины градуса.
 */
class SunLightTest {

    private fun utc(text: String): Instant = Instant.parse(text)

    private fun near(expected: String, actual: Instant?, toleranceSeconds: Long = 90) {
        assertNotNull("нет события, ожидалось $expected", actual)
        val delta = Duration.between(utc(expected), actual).abs().seconds
        assertTrue("ожидалось $expected, получилось $actual", delta <= toleranceSeconds)
    }

    @Test
    fun hannoverInLateNovemberMatchesPyEphem() {
        val day = SunLight.day(LocalDate.of(2026, 11, 21), ZoneId.of("Europe/Berlin"), 52.374, 9.738)
        near("2026-11-21T04:51:08Z", day.rising[SunEdge.Astronomical])
        near("2026-11-21T05:31:37Z", day.rising[SunEdge.Nautical])
        near("2026-11-21T06:13:50Z", day.rising[SunEdge.Civil])
        near("2026-11-21T06:28:30Z", day.rising[SunEdge.Blue])
        near("2026-11-21T06:52:34Z", day.sunrise)
        near("2026-11-21T07:49:52Z", day.rising[SunEdge.Golden])
        near("2026-11-21T14:23:27Z", day.setting[SunEdge.Golden])
        near("2026-11-21T15:20:44Z", day.sunset)
        near("2026-11-21T15:44:47Z", day.setting[SunEdge.Blue])
        near("2026-11-21T15:59:27Z", day.setting[SunEdge.Civil])
        near("2026-11-21T16:41:39Z", day.setting[SunEdge.Nautical])
        near("2026-11-21T17:22:05Z", day.setting[SunEdge.Astronomical])
        near("2026-11-21T11:06:54Z", day.noon, toleranceSeconds = 120)
        assertEquals(17.66, day.noonElevation, 0.5)
        assertEquals(122.65, day.riseAzimuth!!, 0.5)
        assertEquals(237.21, day.setAzimuth!!, 0.5)
    }

    @Test
    fun spansFollowTheEdges() {
        val day = SunLight.day(LocalDate.of(2026, 11, 21), ZoneId.of("Europe/Berlin"), 52.374, 9.738)
        assertEquals(day.rising[SunEdge.Civil], day.morningBlue!!.from)
        assertEquals(day.rising[SunEdge.Blue], day.morningBlue!!.to)
        assertEquals(day.rising[SunEdge.Blue], day.morningGolden!!.from)
        assertEquals(day.setting[SunEdge.Golden], day.eveningGolden!!.from)
        assertEquals(day.setting[SunEdge.Blue], day.eveningBlue!!.from)
        assertEquals(day.setting[SunEdge.Civil], day.eveningBlue!!.to)
        assertEquals(day.sunset, day.eveningTwilight(SunEdge.Civil)!!.from)
        assertEquals(day.setting[SunEdge.Civil], day.eveningTwilight(SunEdge.Nautical)!!.from)
        assertEquals(day.rising[SunEdge.Astronomical], day.morningTwilight(SunEdge.Astronomical)!!.from)
        assertEquals(day.rising[SunEdge.Nautical], day.morningTwilight(SunEdge.Astronomical)!!.to)
        assertEquals(Duration.between(day.sunrise, day.sunset), day.daylight)
    }

    @Test
    fun sydneyInJuneMatchesPyEphem() {
        val day = SunLight.day(LocalDate.of(2026, 6, 21), ZoneId.of("Australia/Sydney"), -33.87, 151.21)
        near("2026-06-20T20:59:58Z", day.sunrise)
        near("2026-06-21T06:53:48Z", day.sunset)
        near("2026-06-20T19:30:30Z", day.rising[SunEdge.Astronomical])
        near("2026-06-21T06:15:30Z", day.setting[SunEdge.Golden])
        assertEquals(32.69, day.noonElevation, 0.5)
        assertEquals(62.01, day.riseAzimuth!!, 0.5)
    }

    @Test
    fun tromsoInMarchMatchesPyEphem() {
        val day = SunLight.day(LocalDate.of(2026, 3, 2), ZoneId.of("Europe/Oslo"), 69.65, 18.96)
        near("2026-03-02T06:05:39Z", day.sunrise)
        near("2026-03-02T15:48:38Z", day.sunset)
        near("2026-03-02T02:42:02Z", day.rising[SunEdge.Astronomical])
        near("2026-03-02T19:13:49Z", day.setting[SunEdge.Astronomical])
        assertEquals(108.71, day.riseAzimuth!!, 0.5)
        assertEquals(251.76, day.setAzimuth!!, 0.5)
    }

    @Test
    fun polarNightHasNoSunriseButTwilight() {
        val day = SunLight.day(LocalDate.of(2026, 12, 21), ZoneId.of("Europe/Oslo"), 69.65, 18.96)
        assertNull(day.sunrise)
        assertNull(day.sunset)
        assertNull(day.daylight)
        assertNull(day.riseAzimuth)
        assertNotNull(day.rising[SunEdge.Nautical])
        assertTrue(day.noonElevation < 0)
    }

    @Test
    fun whiteNightHasNoAstronomicalNight() {
        val day = SunLight.day(LocalDate.of(2026, 6, 21), ZoneId.of("Europe/Berlin"), 52.374, 9.738)
        assertNull(day.rising[SunEdge.Astronomical])
        assertNull(day.setting[SunEdge.Astronomical])
        assertNull(day.morningTwilight(SunEdge.Astronomical))
        assertNotNull(day.sunrise)
    }

    @Test
    fun lightPhaseFollowsTheElevation() {
        assertEquals(LightPhase.Night, LightPhase.of(-20.0))
        assertEquals(LightPhase.Astronomical, LightPhase.of(-15.0))
        assertEquals(LightPhase.Nautical, LightPhase.of(-8.0))
        assertEquals(LightPhase.Blue, LightPhase.of(-5.0))
        assertEquals(LightPhase.Golden, LightPhase.of(-4.0))
        assertEquals(LightPhase.Golden, LightPhase.of(5.9))
        assertEquals(LightPhase.Day, LightPhase.of(6.0))
    }

    @Test
    fun stripCoversTheWholeDayInTenMinuteSteps() {
        val zone = ZoneId.of("Europe/Berlin")
        val strip = SunLight.strip(LocalDate.of(2026, 11, 21), zone, 52.374, 9.738)
        assertEquals(144, strip.size)
        assertEquals(LightPhase.Night, strip.first())
        assertEquals(LightPhase.Day, strip[12 * 6])
        // День перевода часов: 25 часов.
        assertEquals(150, SunLight.strip(LocalDate.of(2026, 10, 25), zone, 52.374, 9.738).size)
    }

    @Test
    fun runSaysDayOrNightAndHowMuchHasPassed() {
        val zone = ZoneOffset.ofHours(1)
        val date = LocalDate.of(2026, 11, 21)
        val days = (-1L..1L).map { SunLight.day(date.plusDays(it), zone, 52.374, 9.738) }
        val (yesterday, today, tomorrow) = days
        val midday = SunLight.run(today.noon, yesterday, today, tomorrow)!!
        assertTrue(midday.daytime)
        assertEquals(0.5, midday.fraction, 0.03)
        val evening = SunLight.run(utc("2026-11-21T21:00:00Z"), yesterday, today, tomorrow)!!
        assertFalse(evening.daytime)
        assertEquals(today.sunset, evening.from)
        assertEquals(tomorrow.sunrise, evening.to)
        val morning = SunLight.run(utc("2026-11-21T03:00:00Z"), yesterday, today, tomorrow)!!
        assertFalse(morning.daytime)
        assertEquals(yesterday.sunset, morning.from)
        assertEquals(today.sunrise, morning.to)
        assertTrue(morning.fraction in 0.5..1.0)
    }

    @Test
    fun nextShootingEventIsTheNearestAhead() {
        val day = SunLight.day(LocalDate.of(2026, 11, 21), ZoneId.of("Europe/Berlin"), 52.374, 9.738)
        assertEquals(ShootingEvent.BlueHour, SunLight.nextShootingEvent(utc("2026-11-21T03:00:00Z"), day)!!.first)
        assertEquals(ShootingEvent.GoldenHour to day.setting[SunEdge.Golden], SunLight.nextShootingEvent(utc("2026-11-21T12:00:00Z"), day))
        assertEquals(ShootingEvent.Sunset, SunLight.nextShootingEvent(utc("2026-11-21T15:00:00Z"), day)!!.first)
        assertNull(SunLight.nextShootingEvent(utc("2026-11-21T20:00:00Z"), day))
    }

    @Test
    fun compassPointsRoundToTheNearestOfEight() {
        assertEquals(0, compassPoint(10.0))
        assertEquals(3, compassPoint(124.0))
        assertEquals(5, compassPoint(236.0))
        assertEquals(0, compassPoint(350.0))
        assertEquals(6, compassPoint(270.0))
    }

    private fun hour(at: String, cover: Int?) = HourPoint(utc(at), 5.0, Condition.Cloudy, true, 0.0, cover)

    private fun layers(at: String, low: Int?, mid: Int?, high: Int?) = HourDetails(
        utc(at), null, null, null, null, null, null, null,
        cloudLowPercent = low, cloudMidPercent = mid, cloudHighPercent = high,
    )

    @Test
    fun cloudsDuringASpanAverageTheTouchedHoursAndNameTheMainLayer() {
        val span = Span(utc("2026-11-21T14:23:00Z"), utc("2026-11-21T15:44:00Z"))
        val hours = listOf(hour("2026-11-21T13:00:00Z", 100), hour("2026-11-21T14:00:00Z", 30), hour("2026-11-21T15:00:00Z", 40), hour("2026-11-21T16:00:00Z", 100))
        val details = listOf(
            layers("2026-11-21T14:00:00Z", 5, 10, 30),
            layers("2026-11-21T15:00:00Z", 0, 20, 40),
            layers("2026-11-21T16:00:00Z", 100, 0, 0),
        )
        assertEquals(SpanClouds(35, CloudLayer.High), SpanCloudsLogic.during(span, hours, details))
        // Почти ясно: слой не называется.
        val clear = listOf(hour("2026-11-21T14:00:00Z", 4), hour("2026-11-21T15:00:00Z", 6))
        assertEquals(SpanClouds(5, null), SpanCloudsLogic.during(span, clear, details))
        // Слоёв нет (старый прогноз): только общая облачность.
        assertEquals(SpanClouds(35, null), SpanCloudsLogic.during(span, hours, emptyList()))
        // Облачности нет вовсе.
        assertNull(SpanCloudsLogic.during(span, listOf(hour("2026-11-21T14:00:00Z", null)), details))
    }
}
