package io.github.dumahuch.pogodaapp.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun notificationsAreOffByDefault() {
        val prefs = AppSettings(MemoryStorage()).notifications.value
        assertEquals(NotificationPrefs(), prefs)
        assertFalse(prefs.wanted)
    }

    @Test
    fun notificationTogglesAreStoredSeparatelyAndComeBackAfterRestart() {
        val storage = MemoryStorage()
        val first = AppSettings(storage)
        first.setNotification(NotificationToggle.StatusBar, true)
        first.setNotification(NotificationToggle.LockScreenPlace, true)
        first.setNotification(NotificationToggle.StatusBar, true)
        assertEquals(2, storage.writes)
        val restored = AppSettings(storage).notifications.value
        assertEquals(NotificationPrefs(inShade = false, inStatusBar = true, placeOnLockScreen = true), restored)
        assertTrue(restored.wanted)
        first.setNotification(NotificationToggle.StatusBar, false)
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
}
