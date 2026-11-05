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
 * Фон приложения (решение Димы 05.11.2026): «Нейтральный» это нарисованные картины неба с облаками (как до версии с «Ди»),
 * «Ди» это слоистые бумажные горы, слои сдвигаются по времени суток и при прокрутке.
 */
enum class SkyBackground(val key: String) {
    Neutral("neutral"),
    Di("di"),
    ;

    companion object {
        /** По умолчанию «Нейтральный»; незнакомое или пустое сохранённое значение тоже даёт его. */
        fun fromKey(key: String?): SkyBackground = entries.firstOrNull { it.key == key } ?: Neutral
    }
}

/** Где лежат настройки: ключи и значения строками, чтобы логику можно было проверить без Android. */
interface SettingsStorage {
    fun read(name: String): String?
    fun write(name: String, value: String)
}

/** Настройки приложения: «Тень карточек» и «Фон». Выбор запоминается и переживает перезапуск. */
class AppSettings(private val storage: SettingsStorage) {
    private val mutableCardShadow = MutableStateFlow(CardShadow.fromKey(storage.read(CARD_SHADOW)))

    val cardShadow: StateFlow<CardShadow> = mutableCardShadow.asStateFlow()

    fun setCardShadow(value: CardShadow) {
        if (mutableCardShadow.value == value) return
        mutableCardShadow.value = value
        storage.write(CARD_SHADOW, value.key)
    }

    private val mutableBackground = MutableStateFlow(SkyBackground.fromKey(storage.read(BACKGROUND)))

    val background: StateFlow<SkyBackground> = mutableBackground.asStateFlow()

    fun setBackground(value: SkyBackground) {
        if (mutableBackground.value == value) return
        mutableBackground.value = value
        storage.write(BACKGROUND, value.key)
    }

    internal companion object {
        const val CARD_SHADOW = "card_shadow"
        const val BACKGROUND = "sky_background"
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
