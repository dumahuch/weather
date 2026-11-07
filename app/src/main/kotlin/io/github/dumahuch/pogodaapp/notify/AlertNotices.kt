package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.time.Duration
import java.time.Instant

/**
 * Предупреждение, о котором уже было уведомление: вид явления, уровень и период. Идентификатор DWD не хранится: при
 * каждом уточнении (продлили, сдвинули начало) DWD выпускает сообщение с новым идентификатором, и по нему одно и то же
 * предупреждение приходило бы снова.
 */
data class NotifiedAlert(val kind: AlertKind, val level: Int, val onset: Instant, val expires: Instant?)

/** Нажатие на уведомление: какое предупреждение открыть на «Главной». */
data class AlertRequest(val id: String, val kind: AlertKind)

/**
 * Решения уведомлений о предупреждениях без Android (0.7.0, шаг 2). Уведомление приходит о предупреждении, если о нём ещё
 * не уведомляли: о том же явлении с тем же или более высоким уровнем в пересекающийся период. Повышение уровня даёт новое
 * уведомление, уточнение или понижение нет. На одно явление одно уведомление: о самом серьёзном и раньше начинающемся.
 */
object AlertNotices {
    /** С какого уровня уведомлять (решение Димы 07.11.2026: «от 1»). */
    const val MIN_LEVEL = 1

    /** Сколько записей хранится; старые отбрасываются первыми. */
    internal const val MAX_RECORDS = 40

    /** Запись без известного конца забывается через столько после начала. */
    private val OPEN_END_KEEP: Duration = Duration.ofDays(2)

    /**
     * О чём уведомить: по одному предупреждению на явление из [shown] (уже отсортированных, как на «Главной»), если о нём
     * ещё не уведомляли.
     */
    fun toNotify(shown: List<WeatherAlert>, notified: List<NotifiedAlert>): List<WeatherAlert> = shown
        .filter { it.level.number >= MIN_LEVEL && notified.none { record -> record.covers(it) } }
        .distinctBy { it.kind }

    /**
     * Записи после показа: прежние и все новые предупреждения тех явлений, о которых уведомили (в одном уведомлении о
     * явлении собраны все его предупреждения), без истёкших; не больше [MAX_RECORDS].
     */
    fun remember(notified: List<NotifiedAlert>, shown: List<WeatherAlert>, posted: List<WeatherAlert>, now: Instant): List<NotifiedAlert> {
        val kinds = posted.map { it.kind }.toSet()
        val added = shown.filter { it.kind in kinds }.map { NotifiedAlert(it.kind, it.level.number, it.onset, it.expires) }
        return (notified + added).distinct().filter { it.keptAt(now) }.takeLast(MAX_RECORDS)
    }

    /** Явления, уведомления о которых пора убрать: предупреждений этого вида больше нет. */
    fun gone(shown: List<WeatherAlert>): Set<AlertKind> = AlertKind.entries.toSet() - shown.map { it.kind }.toSet()

    /** Что открыть по нажатию: то же предупреждение, иначе (DWD его уточнил) первое того же явления, иначе ничего. */
    fun pick(request: AlertRequest, shown: List<WeatherAlert>): WeatherAlert? =
        shown.firstOrNull { it.id == request.id } ?: shown.firstOrNull { it.kind == request.kind }

    /** Записи строкой для настроек: по строке на запись, поля через «|», время в секундах. */
    fun encode(records: List<NotifiedAlert>): String = records.joinToString("\n") {
        listOf(it.kind.name, it.level, it.onset.epochSecond, it.expires?.epochSecond ?: "").joinToString("|")
    }

    /** Обратно из строки; повреждённые строки пропускаются. */
    fun decode(text: String): List<NotifiedAlert> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 4) return@mapNotNull null
        val kind = AlertKind.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
        val level = parts[1].toIntOrNull() ?: return@mapNotNull null
        val onset = parts[2].toLongOrNull()?.let(Instant::ofEpochSecond) ?: return@mapNotNull null
        val expires = if (parts[3].isEmpty()) null else parts[3].toLongOrNull()?.let(Instant::ofEpochSecond) ?: return@mapNotNull null
        NotifiedAlert(kind, level, onset, expires)
    }.toList()

    private fun NotifiedAlert.covers(alert: WeatherAlert): Boolean =
        kind == alert.kind && level >= alert.level.number && overlaps(onset, expires, alert.onset, alert.expires)

    /** Периоды пересекаются или касаются; неизвестный конец считается бесконечным. */
    private fun overlaps(startA: Instant, endA: Instant?, startB: Instant, endB: Instant?): Boolean =
        (endB == null || !startA.isAfter(endB)) && (endA == null || !startB.isAfter(endA))

    private fun NotifiedAlert.keptAt(now: Instant): Boolean =
        expires?.isAfter(now) ?: onset.plus(OPEN_END_KEEP).isAfter(now)
}
