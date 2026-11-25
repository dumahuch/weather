package io.github.dumahuch.pogodaapp.settings

import io.github.dumahuch.pogodaapp.weather.AlertFilter
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AppSettingsTest {

    private class MemoryStorage(initial: Map<String, String> = emptyMap()) : SettingsStorage {
        val values = HashMap(initial)
        var writes = 0
        override fun read(name: String): String? = values[name]
        override fun write(name: String, value: String) {
            values[name] = value
            writes++
        }
    }

    @Test
    fun cardShadowIsSoftByDefault() {
        assertEquals(CardShadow.Soft, AppSettings(MemoryStorage()).cardShadow.value)
    }

    @Test
    fun unknownStoredValueFallsBackToSoft() {
        assertEquals(CardShadow.Soft, CardShadow.fromKey("banana"))
        assertEquals(CardShadow.Soft, CardShadow.fromKey(null))
        assertEquals(CardShadow.Soft, CardShadow.fromKey(""))
        val storage = MemoryStorage(mapOf(AppSettings.CARD_SHADOW to "banana"))
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun chosenShadowIsStoredAndComesBackAfterRestart() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        first.setCardShadow(CardShadow.Strong)
        assertEquals(CardShadow.Strong, first.cardShadow.value)
        assertEquals("strong", storage.values[AppSettings.CARD_SHADOW])
        // «Перезапуск»: новый объект над тем же хранилищем.
        assertEquals(CardShadow.Strong, AppSettings(storage).cardShadow.value)
        first.setCardShadow(CardShadow.Soft)
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun mapPreloadIsAlwaysByDefaultAndWifiOnlyIsRemembered() {
        val storage = MemoryStorage()
        assertEquals(MapPreload.Always, AppSettings(storage).mapPreload.value)
        AppSettings(storage).setMapPreload(MapPreload.WifiOnly)
        assertEquals("wifi", storage.values[AppSettings.MAP_PRELOAD])
        assertEquals(MapPreload.WifiOnly, AppSettings(storage).mapPreload.value)
        assertEquals(MapPreload.Always, AppSettings(MemoryStorage(mapOf(AppSettings.MAP_PRELOAD to "banana"))).mapPreload.value)
    }

    @Test
    fun choosingTheSameShadowAgainWritesNothing() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        settings.setCardShadow(CardShadow.Soft)
        assertEquals(0, storage.writes)
        settings.setCardShadow(CardShadow.Strong)
        settings.setCardShadow(CardShadow.Strong)
        assertEquals(1, storage.writes)
    }

    @Test
    fun everyShadowHasItsOwnKeyThatReadsBack() {
        val keys = CardShadow.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (shadow in CardShadow.entries) assertEquals(shadow, CardShadow.fromKey(shadow.key))
        assertTrue(CardShadow.entries.size == 2)
    }

    @Test
    fun iconStyleIsGlassByDefaultAndUnknownFallsBackToGlass() {
        assertEquals(WeatherIconStyle.Glass, AppSettings(MemoryStorage()).iconStyle.value)
        assertEquals(WeatherIconStyle.Glass, WeatherIconStyle.fromKey("banana"))
        assertEquals(WeatherIconStyle.Glass, WeatherIconStyle.fromKey(null))
        val storage = MemoryStorage(mapOf(AppSettings.ICON_STYLE to "banana"))
        assertEquals(WeatherIconStyle.Glass, AppSettings(storage).iconStyle.value)
    }

    @Test
    fun chosenIconStyleIsStoredAndComesBackAfterRestart() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        first.setIconStyle(WeatherIconStyle.Outline)
        assertEquals("outline", storage.values[AppSettings.ICON_STYLE])
        assertEquals(WeatherIconStyle.Outline, AppSettings(storage).iconStyle.value)
        first.setIconStyle(WeatherIconStyle.Outline)
        assertEquals(1, storage.writes)
        // Значки не трогают другие настройки.
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun noisyNotificationsAreOffByDefault() {
        val prefs = AppSettings(MemoryStorage()).notifications.value
        // Предупреждения и вечерняя сводка включены, «Скоро дождь» и «Пыльца» нет (решение Димы 25.11.2026).
        assertEquals(NotificationPrefs(inShade = true, inStatusBar = true, placeOnLockScreen = true, alerts = true, summary = true), prefs)
        assertFalse(prefs.rainSoon)
        assertFalse(prefs.pollen)
        assertEquals(NotificationPrefs.SUMMARY_DEFAULT_MINUTES, prefs.summaryMinutes)
        assertEquals(20 * 60, prefs.summaryMinutes)
        assertTrue(prefs.wanted)
        assertEquals(DEFAULT_ENABLED, prefs.enabledCount)
    }

    @Test
    fun storedChoiceBeatsTheDefault() {
        val storage = MemoryStorage()
        storage.write(NotificationToggle.RainSoon.key, "1")
        storage.write(NotificationToggle.Alerts.key, "0")
        val prefs = AppSettings(storage).notifications.value
        assertTrue(prefs.rainSoon)
        assertFalse(prefs.alerts)
        assertTrue(prefs.inShade)
        assertFalse(prefs.pollen)
    }

    @Test
    fun notificationTogglesAreStoredSeparatelyAndComeBackAfterRestart() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        first.setNotification(NotificationToggle.StatusBar, false)
        first.setNotification(NotificationToggle.LockScreenPlace, false)
        first.setNotification(NotificationToggle.StatusBar, false)
        assertEquals(2, storage.writes)
        val restored = AppSettings(storage).notifications.value
        assertEquals(NotificationPrefs(true, false, false, true, false, false, true), restored)
        assertTrue(restored.wanted)
        first.setNotification(NotificationToggle.Shade, false)
        assertFalse(AppSettings(storage).notifications.value.wanted)
        // Уведомление не трогает другие настройки.
        assertEquals(CardShadow.Soft, AppSettings(storage).cardShadow.value)
    }

    @Test
    fun shadeAloneIsEnoughForTheNotification() {
        assertTrue(NotificationPrefs(inShade = true).wanted)
        assertFalse(NotificationPrefs(placeOnLockScreen = true).wanted)
    }

    @Test
    fun rainSoonAloneNeedsTheBackgroundJobAndRemembersWhetherItWasReported() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        NotificationToggle.entries.forEach { settings.setNotification(it, false) }
        settings.setNotification(NotificationToggle.RainSoon, true)
        val restored = AppSettings(storage).notifications.value
        assertEquals(NotificationPrefs(rainSoon = true), restored)
        assertTrue(restored.background)
        assertFalse(restored.wanted)
        assertFalse(settings.rainSoonNotified())
        settings.setRainSoonNotified(true)
        assertTrue(AppSettings(storage).rainSoonNotified())
        val writes = storage.writes
        settings.setRainSoonNotified(true)
        assertEquals(writes, storage.writes)
        settings.setRainSoonNotified(false)
        assertFalse(AppSettings(storage).rainSoonNotified())
    }

    @Test
    fun pollenToggleKindsAndDatesAreStored() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertEquals(PollenNotice.DEFAULT_KINDS, settings.pollenKinds.value)
        settings.setPollenKind(PollenKind.Hazel, true)
        settings.setPollenKind(PollenKind.Birch, false)
        val writes = storage.writes
        settings.setPollenKind(PollenKind.Hazel, true)
        assertEquals(writes, storage.writes)
        assertEquals(setOf(PollenKind.Hazel, PollenKind.Grass, PollenKind.Mugwort, PollenKind.Ragweed), AppSettings(storage).pollenKinds.value)
        PollenKind.entries.forEach { settings.setPollenKind(it, false) }
        assertEquals(emptySet<PollenKind>(), AppSettings(storage).pollenKinds.value)
        assertNull(settings.pollenNotifiedOn())
        assertNull(settings.pollenCheckedAt())
        settings.setPollenNotifiedOn(LocalDate.of(2026, 4, 20))
        settings.setPollenCheckedAt(Instant.parse("2026-04-20T09:00:00Z"))
        assertEquals(LocalDate.of(2026, 4, 20), AppSettings(storage).pollenNotifiedOn())
        assertEquals(Instant.parse("2026-04-20T09:00:00Z"), AppSettings(storage).pollenCheckedAt())
        NotificationToggle.entries.forEach { settings.setNotification(it, false) }
        settings.setNotification(NotificationToggle.Pollen, true)
        val restored = AppSettings(storage).notifications.value
        assertEquals(NotificationPrefs(pollen = true), restored)
        assertTrue(restored.background)
    }

    @Test
    fun summaryTimeIsStoredClampedAndResetsTheSentMarker() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        settings.setSummarySentOn(LocalDate.of(2026, 11, 14))
        assertEquals(LocalDate.of(2026, 11, 14), AppSettings(storage).summarySentOn())
        settings.setSummaryMinutes(19 * 60 + 45)
        assertEquals(19 * 60 + 45, AppSettings(storage).notifications.value.summaryMinutes)
        assertNull(AppSettings(storage).summarySentOn())
        settings.setSummaryMinutes(99999)
        assertEquals(24 * 60 - 1, AppSettings(storage).notifications.value.summaryMinutes)
        storage.write("summary_minutes", "oops")
        assertEquals(NotificationPrefs.SUMMARY_DEFAULT_MINUTES, AppSettings(storage).notifications.value.summaryMinutes)
    }

    @Test
    fun turningTheSummaryOnAgainResetsTheSentMarker() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        settings.setNotification(NotificationToggle.Summary, false)
        settings.setSummarySentOn(LocalDate.of(2026, 11, 14))
        settings.setNotification(NotificationToggle.Summary, true)
        assertNull(AppSettings(storage).summarySentOn())
        assertTrue(AppSettings(storage).notifications.value.summary)
    }

    @Test
    fun summaryAloneNeedsTheBackgroundJob() {
        assertTrue(NotificationPrefs(summary = true).background)
        assertFalse(NotificationPrefs(summary = true).wanted)
    }

    @Test
    fun alertFilterHasDefaultsClampsLevelAndRemembersKinds() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertEquals(AlertFilter(2, AlertFilter.DEFAULT_KINDS), settings.alertFilter.value)
        settings.setAlertMinLevel(1)
        settings.setAlertKind(AlertKind.Frost, true)
        settings.setAlertKind(AlertKind.Thunderstorm, false)
        val expected = AlertFilter(1, AlertFilter.DEFAULT_KINDS + AlertKind.Frost - AlertKind.Thunderstorm)
        assertEquals(expected, settings.alertFilter.value)
        assertEquals(expected, AppSettings(storage).alertFilter.value)
        // Уровни 3 и 4 всегда проходят, поэтому выбрать можно только 1 или 2.
        settings.setAlertMinLevel(4)
        assertEquals(2, settings.alertFilter.value.minLevel)
        settings.setAlertMinLevel(0)
        assertEquals(1, AppSettings(storage).alertFilter.value.minLevel)
        val writes = storage.writes
        settings.setAlertKind(AlertKind.Frost, true)
        settings.setAlertMinLevel(1)
        assertEquals(writes, storage.writes)
    }

    @Test
    fun enabledCountCountsEveryToggle() {
        assertEquals(0, NotificationPrefs().enabledCount)
        assertEquals(2, NotificationPrefs(inShade = true, pollen = true).enabledCount)
        val all = NotificationPrefs(true, true, true, true, true, true, true)
        assertEquals(NotificationPrefs.TOGGLE_COUNT, all.enabledCount)
    }

    @Test
    fun alertsAloneNeedTheBackgroundJobButNotTheWeatherNotification() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        NotificationToggle.entries.forEach { settings.setNotification(it, false) }
        settings.setNotification(NotificationToggle.Alerts, true)
        val restored = AppSettings(storage).notifications.value
        assertEquals(NotificationPrefs(alerts = true), restored)
        assertTrue(restored.background)
        assertFalse(restored.wanted)
        assertFalse(NotificationPrefs().background)
        assertTrue(NotificationPrefs(inShade = true).background)
    }

    @Test
    fun notifiedAlertsAreStoredAndUnchangedValueWritesNothing() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertEquals("", settings.notifiedAlerts())
        settings.setNotifiedAlerts("Wind|1|1|2")
        settings.setNotifiedAlerts("Wind|1|1|2")
        assertEquals("Wind|1|1|2", AppSettings(storage).notifiedAlerts())
        assertEquals(1, storage.writes)
    }

    @Test
    fun permissionPromptIsRememberedOnce() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertFalse(settings.notificationPermissionAsked())
        settings.markNotificationPermissionAsked()
        settings.markNotificationPermissionAsked()
        assertTrue(AppSettings(storage).notificationPermissionAsked())
        assertEquals(1, storage.writes)
    }

    @Test
    fun noticeErrorIsKeptUntilClearedAndTrimmed() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertEquals(null, settings.noticeError.value)
        settings.setNoticeError("x".repeat(1000))
        assertEquals(AppSettings.NOTIFY_ERROR_MAX, AppSettings(storage).noticeError.value?.length)
        settings.setNoticeError(null)
        assertEquals(null, AppSettings(storage).noticeError.value)
        val writes = storage.writes
        settings.setNoticeError(null)
        assertEquals(writes, storage.writes)
    }

    @Test
    fun compactLockScreenIsOffByDefaultRemembersChoiceAndIsNotCounted() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        assertFalse(first.notifications.value.lockScreenCompact)
        first.setNotification(NotificationToggle.LockScreenCompact, true)
        assertTrue(first.notifications.value.lockScreenCompact)
        assertTrue(AppSettings(storage).notifications.value.lockScreenCompact)
        assertEquals(DEFAULT_ENABLED, AppSettings(storage).notifications.value.enabledCount)
        first.setNotification(NotificationToggle.LockScreenCompact, false)
        assertFalse(AppSettings(storage).notifications.value.lockScreenCompact)
    }

    @Test
    fun lockScreenChoiceIsOneOfFourAndRemembered() {
        val storage = MemoryStorage()
        val settings = AppSettings(storage)
        assertEquals(LockScreenForm.WithPlace, settings.notifications.value.lockScreen)
        LockScreenForm.entries.forEach { form ->
            settings.setLockScreen(form)
            assertEquals(form, settings.notifications.value.lockScreen)
            assertEquals(form, AppSettings(storage).notifications.value.lockScreen)
        }
        settings.setLockScreen(LockScreenForm.NoPlace)
        assertFalse(AppSettings(storage).notifications.value.placeOnLockScreen)
        assertFalse(AppSettings(storage).notifications.value.lockScreenCompact)
        assertFalse(AppSettings(storage).notifications.value.lockScreenHidden)
    }

    @Test
    fun oldPlaceSwitchIsReadAsTheLockScreenChoice() {
        val storage = MemoryStorage()
        storage.write(NotificationToggle.LockScreenPlace.key, "0")
        assertEquals(LockScreenForm.NoPlace, AppSettings(storage).notifications.value.lockScreen)
        storage.write(NotificationToggle.LockScreenPlace.key, "1")
        assertEquals(LockScreenForm.WithPlace, AppSettings(storage).notifications.value.lockScreen)
    }

    @Test
    fun hiddenAndCompactAreOffByDefaultAndNotCounted() {
        val prefs = AppSettings(MemoryStorage()).notifications.value
        assertFalse(prefs.lockScreenHidden)
        assertFalse(prefs.lockScreenCompact)
        assertEquals(DEFAULT_ENABLED, prefs.enabledCount)
    }

    private companion object {
        /** Включены по умолчанию: шторка, строка состояния, место на экране блокировки, предупреждения и сводка (дождь и пыльца нет). */
        const val DEFAULT_ENABLED = 5
    }
}
