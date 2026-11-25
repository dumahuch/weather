package io.github.dumahuch.pogodaapp.notify

import android.app.Notification
import android.app.NotificationManager

/**
 * Одно место в шторке для необязательных сообщений: «скоро дождь», пыльца и вечерняя сводка (решение Димы 25.11.2026,
 * чтобы не висело по три записи). Новое сообщение заменяет прежнее, а убрать «своё» сообщение можно, не задев чужое:
 * у каждого свой тег. Все три приходят без звука.
 */
object InfoSlot {
    enum class Kind(val tag: String) { Rain("info-rain"), Pollen("info-pollen"), Summary("info-summary") }

    private const val ID = 7300

    /** Номера, под которыми эти сообщения показывала версия до 25.11.2026 (дождь и пыльца с сводкой); их убираем при первом показе. */
    private val LEGACY_IDS = intArrayOf(7301, 7401)

    /** Показывает [notification] и убирает остальные сообщения этого места. */
    fun post(manager: NotificationManager, kind: Kind, notification: Notification) {
        Kind.entries.filter { it != kind }.forEach { manager.cancel(it.tag, ID) }
        LEGACY_IDS.forEach { manager.cancel(it) }
        manager.notify(kind.tag, ID, notification)
    }

    /** Убирает сообщение [kind], если оно сейчас показано. */
    fun cancel(manager: NotificationManager, kind: Kind) {
        manager.cancel(kind.tag, ID)
    }
}
