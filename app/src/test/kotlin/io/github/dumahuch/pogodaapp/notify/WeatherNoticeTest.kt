package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class WeatherNoticeTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-07T15:00:00Z")

    @Test
    fun statusBarWinsOverShadeAndNothingMeansNoNotification() {
        assertNull(WeatherNotice.channelFor(NotificationPrefs()))
        assertNull(WeatherNotice.channelFor(NotificationPrefs(placeOnLockScreen = true)))
        assertEquals(NoticeChannel.Shade, WeatherNotice.channelFor(NotificationPrefs(inShade = true)))
        assertEquals(NoticeChannel.StatusBar, WeatherNotice.channelFor(NotificationPrefs(inStatusBar = true)))
        assertEquals(NoticeChannel.StatusBar, WeatherNotice.channelFor(NotificationPrefs(inShade = true, inStatusBar = true)))
    }

    @Test
    fun placeIsMarkedOnlyWhenOlderThanADay() {
        assertNull(WeatherNotice.placeSince(now.minus(Duration.ofHours(23)), now, berlin))
        assertNull(WeatherNotice.placeSince(now.minus(Duration.ofHours(24)), now, berlin))
        // Дата по местному времени: 22:59 UTC это 23:59 5 ноября в Берлине, 23:00 UTC уже полночь 6 ноября.
        assertEquals(LocalDate.of(2026, 11, 5), WeatherNotice.placeSince(Instant.parse("2026-11-05T22:59:00Z"), now, berlin))
        assertEquals(LocalDate.of(2026, 11, 6), WeatherNotice.placeSince(Instant.parse("2026-11-05T23:00:00Z"), now, berlin))
    }

    @Test
    fun cachedForecastIsFreshForOneHour() {
        assertTrue(WeatherNotice.fresh(now, now))
        assertTrue(WeatherNotice.fresh(now.minus(Duration.ofMinutes(60)), now))
        assertFalse(WeatherNotice.fresh(now.minus(Duration.ofMinutes(61)), now))
        assertTrue(WeatherNotice.fresh(now.plus(Duration.ofMinutes(4)), now))
        assertFalse(WeatherNotice.fresh(now.plus(Duration.ofMinutes(6)), now))
    }

    @Test
    fun precipitationContinuesInLowerCaseExceptGermanNouns() {
        val ru = Locale.forLanguageTag("ru")
        assertEquals("осадки днём", WeatherNotice.precipitationContinuation("Осадки днём", ru))
        assertEquals("без осадков", WeatherNotice.precipitationContinuation("Без осадков", ru))
        assertEquals("precipitation all day", WeatherNotice.precipitationContinuation("Precipitation all day", Locale.ENGLISH))
        assertEquals("Niederschlag abends", WeatherNotice.precipitationContinuation("Niederschlag abends", Locale.GERMAN))
        assertEquals("Kein Niederschlag", WeatherNotice.precipitationContinuation("Kein Niederschlag", Locale.GERMAN))
    }
}
