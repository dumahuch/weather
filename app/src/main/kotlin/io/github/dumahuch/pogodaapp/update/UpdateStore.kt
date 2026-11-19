package io.github.dumahuch.pogodaapp.update

import io.github.dumahuch.pogodaapp.settings.SettingsStorage
import java.time.Instant

/**
 * Что запомнено об обновлениях (в том же файле настроек): выключатель автопроверки (по умолчанию включён), время последней
 * проверки, найденный выпуск, о какой версии уже было уведомление и какую версию человек начал ставить.
 */
class UpdateStore(private val storage: SettingsStorage) {

    var auto: Boolean
        get() = storage.read(AUTO)?.let { it == ON } ?: true
        set(value) = storage.write(AUTO, if (value) ON else OFF)

    var checkedAt: Instant?
        get() = storage.read(CHECKED)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        set(value) = storage.write(CHECKED, value?.toEpochMilli()?.toString().orEmpty())

    var offer: ReleaseOffer?
        get() = decode(storage.read(OFFER))
        set(value) = storage.write(OFFER, value?.let(::encode).orEmpty())

    var notified: String?
        get() = storage.read(NOTIFIED)?.takeIf { it.isNotEmpty() }
        set(value) = storage.write(NOTIFIED, value.orEmpty())

    /** Версия, установку которой начал человек: после замены приложения по ней видно, что обновление пришло отсюда. */
    var started: String?
        get() = storage.read(STARTED)?.takeIf { it.isNotEmpty() }
        set(value) = storage.write(STARTED, value.orEmpty())

    internal companion object {
        const val AUTO = "update_auto"
        const val CHECKED = "update_checked_at"
        const val OFFER = "update_offer"
        const val NOTIFIED = "update_notified"
        const val STARTED = "update_started"
        private const val ON = "1"
        private const val OFF = "0"
        private const val SEPARATOR = "\n"

        fun encode(offer: ReleaseOffer): String = listOf(offer.version, offer.apkUrl, offer.sha256, offer.size.toString()).joinToString(SEPARATOR)

        fun decode(text: String?): ReleaseOffer? {
            val parts = text?.split(SEPARATOR) ?: return null
            if (parts.size != 4) return null
            val size = parts[3].toLongOrNull() ?: return null
            return ReleaseOffer(parts[0], parts[1], parts[2], size)
        }
    }
}
