package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.ForecastException
import java.io.IOException

/**
 * Короткий текст ошибки уведомления для строки в настройках (0.7.2): вид ошибки, её сообщение и первое место в коде. Журнала
 * падений у приложения нет, поэтому снимок этой строки и есть способ узнать, что сломалось на телефоне.
 */
object NoticeFailure {
    /**
     * Сбой сети или ответа службы (нет связи, ответ не 2xx, превышен предел): для телефона дело обычное, предупреждать о нём
     * в настройках не нужно: уведомление остаётся прежним до следующего запуска. Остальное (ошибка в коде, система) записывается.
     */
    fun isNetwork(error: Throwable): Boolean = error is ForecastException || error is IOException

    fun describe(error: Throwable): String {
        val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val head = if (message.isEmpty()) error.javaClass.simpleName else "${error.javaClass.simpleName}: $message"
        val frame = error.stackTrace.firstOrNull()?.let { " (${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber})" }
        return head + frame.orEmpty()
    }
}
