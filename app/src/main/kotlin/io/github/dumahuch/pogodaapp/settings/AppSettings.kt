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
 * Выключатели уведомления о погоде (0.7.0, решения Димы 07.11.2026), все по умолчанию выключены. Число в строке состояния
 * Android показывает только как значок уведомления, поэтому при нём запись в шторке тоже есть.
 */
data class NotificationPrefs(
    val inShade: Boolean = false,
    val inStatusBar: Boolean = false,
    val placeOnLockScreen: Boolean = false,
) {
    /** Уведомление нужно, если включена шторка или строка состояния. */
    val wanted: Boolean get() = inShade || inStatusBar
}

/** Один выключатель уведомления и ключ, под которым он хранится. */
enum class NotificationToggle(val key: String) {
    Shade("notify_shade"),
    StatusBar("notify_status_bar"),
    LockScreenPlace("notify_lock_place"),
}

/** Где лежат настройки: ключи и значения строками, чтобы логику можно было проверить без Android. */
interface SettingsStorage {
    fun read(name: String): String?
    fun write(name: String, value: String)
}

/** Настройки приложения: «Тень карточек», «Значки погоды» и уведомление о погоде. Выбор запоминается и переживает перезапуск. */
class AppSettings(private val storage: SettingsStorage) {
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
        ),
    )

    val notifications: StateFlow<NotificationPrefs> = mutableNotifications.asStateFlow()

    fun setNotification(toggle: NotificationToggle, on: Boolean) {
        val current = mutableNotifications.value
        val next = when (toggle) {
            NotificationToggle.Shade -> current.copy(inShade = on)
            NotificationToggle.StatusBar -> current.copy(inStatusBar = on)
            NotificationToggle.LockScreenPlace -> current.copy(placeOnLockScreen = on)
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

    private fun flag(toggle: NotificationToggle): Boolean = storage.read(toggle.key) == ON

    internal companion object {
        const val CARD_SHADOW = "card_shadow"
        const val ICON_STYLE = "weather_icon_style"
        const val NOTIFY_ASKED = "notify_permission_asked"
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
