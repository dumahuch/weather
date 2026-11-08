package io.github.dumahuch.pogodaapp.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Тень карточек (решение Димы 31.10.2026): «Мягкая» это стекло как до версии 0.5.3, «Выраженная» добавляет вторую тень,
 * светлую кромку и лёгкую вуаль (карточки лучше видны на сером небе). Действует на все стеклянные карточки.
 */
enum class CardShadow(val key: String) {
    Soft("soft"),
    Strong("strong"),
    ;

    companion object {
        /** По умолчанию «Мягкая»; незнакомое или пустое сохранённое значение тоже даёт её. */
        fun fromKey(key: String?): CardShadow = entries.firstOrNull { it.key == key } ?: Soft
    }
}

/**
 * Значки погоды на «Главной» (решения Димы 31.10 и 06.11.2026): «Стеклянные» (по умолчанию) это матовое стекло с цветными
 * каплями, молнией и снежинками, «Контурные» это линия цветом текста с цветными каплями, молнией и снегом. Меняет только
 * значок рядом с температурой на «Главной».
 */
enum class WeatherIconStyle(val key: String) {
    Glass("glass"),
    Outline("outline"),
    ;

    companion object {
        /** По умолчанию «Стеклянные»; незнакомое или пустое сохранённое значение тоже даёт их. */
        fun fromKey(key: String?): WeatherIconStyle = entries.firstOrNull { it.key == key } ?: Glass
    }
}

/**
 * Выключатели уведомлений (0.7.0, решения Димы 07.11.2026), все по умолчанию выключены. Число в строке состояния
 * Android показывает только как значок уведомления, поэтому при нём запись в шторке тоже есть. [alerts] это
 * предупреждения DWD в фоне (шаг 2), [rainSoon] «скоро дождь» по радару DWD (шаг 3).
 */
data class NotificationPrefs(
    val inShade: Boolean = false,
    val inStatusBar: Boolean = false,
    val placeOnLockScreen: Boolean = false,
    val alerts: Boolean = false,
    val rainSoon: Boolean = false,
) {
    /** Уведомление о погоде нужно, если включена шторка или строка состояния. */
    val wanted: Boolean get() = inShade || inStatusBar

    /** Фоновое обновление нужно, если нужно уведомление о погоде, предупреждения или «скоро дождь». */
    val background: Boolean get() = wanted || alerts || rainSoon
}

/** Один выключатель уведомления и ключ, под которым он хранится. */
enum class NotificationToggle(val key: String) {
    Shade("notify_shade"),
    StatusBar("notify_status_bar"),
    LockScreenPlace("notify_lock_place"),
    Alerts("notify_alerts"),
    RainSoon("notify_rain_soon"),
}

/** Где лежат настройки: ключи и значения строками, чтобы логику можно было проверить без Android. */
interface SettingsStorage {
    fun read(name: String): String?
    fun write(name: String, value: String)
}

/** Настройки приложения: «Тень карточек», «Значки погоды» и уведомления. Выбор запоминается и переживает перезапуск. */
class AppSettings(private val storage: SettingsStorage) {
    /** Где хранится, о каких предупреждениях уже было уведомление (см. AlertNotices). */
    fun notifiedAlerts(): String = storage.read(NOTIFIED_ALERTS).orEmpty()

    fun setNotifiedAlerts(value: String) {
        if (notifiedAlerts() != value) storage.write(NOTIFIED_ALERTS, value)
    }

    /** Сообщали ли уже о дожде, который идёт или скоро начнётся (см. RainSoon.decide); без места и времени. */
    fun rainSoonNotified(): Boolean = storage.read(RAIN_NOTIFIED) == ON

    fun setRainSoonNotified(value: Boolean) {
        if (rainSoonNotified() != value) storage.write(RAIN_NOTIFIED, if (value) ON else OFF)
    }

    private val mutableCardShadow = MutableStateFlow(CardShadow.fromKey(storage.read(CARD_SHADOW)))

    val cardShadow: StateFlow<CardShadow> = mutableCardShadow.asStateFlow()

    fun setCardShadow(value: CardShadow) {
        if (mutableCardShadow.value == value) return
        mutableCardShadow.value = value
        storage.write(CARD_SHADOW, value.key)
    }

    private val mutableIconStyle = MutableStateFlow(WeatherIconStyle.fromKey(storage.read(ICON_STYLE)))

    val iconStyle: StateFlow<WeatherIconStyle> = mutableIconStyle.asStateFlow()

    fun setIconStyle(value: WeatherIconStyle) {
        if (mutableIconStyle.value == value) return
        mutableIconStyle.value = value
        storage.write(ICON_STYLE, value.key)
    }

    private val mutableNotifications = MutableStateFlow(
        NotificationPrefs(
            inShade = flag(NotificationToggle.Shade),
            inStatusBar = flag(NotificationToggle.StatusBar),
            placeOnLockScreen = flag(NotificationToggle.LockScreenPlace),
            alerts = flag(NotificationToggle.Alerts),
            rainSoon = flag(NotificationToggle.RainSoon),
        ),
    )

    val notifications: StateFlow<NotificationPrefs> = mutableNotifications.asStateFlow()

    fun setNotification(toggle: NotificationToggle, on: Boolean) {
        val current = mutableNotifications.value
        val next = when (toggle) {
            NotificationToggle.Shade -> current.copy(inShade = on)
            NotificationToggle.StatusBar -> current.copy(inStatusBar = on)
            NotificationToggle.LockScreenPlace -> current.copy(placeOnLockScreen = on)
            NotificationToggle.Alerts -> current.copy(alerts = on)
            NotificationToggle.RainSoon -> current.copy(rainSoon = on)
        }
        if (next == current) return
        mutableNotifications.value = next
        storage.write(toggle.key, if (on) ON else OFF)
    }

    /** Системное окно «Разрешить уведомления» уже показывали: при следующем отказе ведём в системные настройки. */
    fun notificationPermissionAsked(): Boolean = storage.read(NOTIFY_ASKED) == ON

    fun markNotificationPermissionAsked() {
        if (!notificationPermissionAsked()) storage.write(NOTIFY_ASKED, ON)
    }

    private val mutableNoticeError = MutableStateFlow(storage.read(NOTIFY_ERROR)?.takeIf { it.isNotEmpty() })

    /** Текст последней ошибки уведомления о погоде (показывается в настройках, чтобы Дима мог прислать снимок); `null`: ошибок нет. */
    val noticeError: StateFlow<String?> = mutableNoticeError.asStateFlow()

    fun setNoticeError(text: String?) {
        val value = text?.take(NOTIFY_ERROR_MAX)?.takeIf { it.isNotEmpty() }
        if (mutableNoticeError.value == value) return
        mutableNoticeError.value = value
        storage.write(NOTIFY_ERROR, value.orEmpty())
    }

    private fun flag(toggle: NotificationToggle): Boolean = storage.read(toggle.key) == ON

    internal companion object {
        const val CARD_SHADOW = "card_shadow"
        const val ICON_STYLE = "weather_icon_style"
        const val NOTIFY_ASKED = "notify_permission_asked"
        const val NOTIFY_ERROR = "notify_last_error"
        const val NOTIFY_ERROR_MAX = 400
        const val NOTIFIED_ALERTS = "notified_alerts"
        const val RAIN_NOTIFIED = "rain_soon_notified"
        private const val ON = "1"
        private const val OFF = "0"
    }
}

/** Настройки в файле SharedPreferences папки приложения. Резервное копирование у приложения выключено, в облако они не уходят. */
class PreferencesSettingsStorage(context: Context) : SettingsStorage {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(name: String): String? = preferences.getString(name, null)

    override fun write(name: String, value: String) {
        preferences.edit().putString(name, value).apply()
    }

    private companion object {
        const val FILE_NAME = "settings"
    }
}
