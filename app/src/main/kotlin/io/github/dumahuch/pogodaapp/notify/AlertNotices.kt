package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.AlertFilter
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
 * Что сделать с уведомлениями о предупреждениях сейчас. [loud] новые предупреждения уровней 3 и 4, по одному на явление, со
 * звуком. [quiet] все нынешние выбранные предупреждения уровней 1 и 2, по одному на явление, самое серьёзное первым: они
 * собраны в одно тихое уведомление, которое обновляется, только если среди них есть новое ([quietFresh]). [cancelLoud] явления,
 * у которых громких уведомлений больше быть не должно, [cancelQuiet] общее тихое уведомление пора убрать.
 */
data class AlertPlan(
    val loud: List<WeatherAlert>,
    val quiet: List<WeatherAlert>,
    val quietFresh: Boolean,
    val cancelLoud: Set<AlertKind>,
    val cancelQuiet: Boolean,
)

/**
 * Решения уведомлений о предупреждениях без Android (0.7.0, шаг 2; переделано 25.11.2026). Уведомление приходит о
 * предупреждении, если о нём ещё не уведомляли: о том же явлении с тем же или более высоким уровнем в пересекающийся период.
 * Повышение уровня даёт новое уведомление, уточнение или понижение нет. Уровни 3 и 4 громкие, по одному уведомлению на
 * явление; уровни 1 и 2 (если их выбрали, см. [AlertFilter]) тихие и собраны в одно общее уведомление.
 */
object AlertNotices {
    /** Сколько записей хранится; старые отбрасываются первыми. */
    internal const val MAX_RECORDS = 40

    /** Запись без известного конца забывается через столько после начала. */
    private val OPEN_END_KEEP: Duration = Duration.ofDays(2)

    /**
     * Что показать и что убрать: из [shown] (уже отсортированных, как на «Главной») берутся предупреждения, прошедшие [filter].
     * О громких уведомляют, если о них ещё не было; тихое общее обновляется, если среди его предупреждений есть новое.
     */
    fun plan(shown: List<WeatherAlert>, notified: List<NotifiedAlert>, filter: AlertFilter): AlertPlan {
        val accepted = shown.filter(filter::accepts)
        val (loudAll, quietAll) = accepted.partition { it.level.number >= AlertFilter.LOUD_LEVEL }
        fun fresh(alert: WeatherAlert) = notified.none { record -> record.covers(alert) }
        return AlertPlan(
            loud = loudAll.filter(::fresh).distinctBy { it.kind },
            quiet = quietAll.distinctBy { it.kind },
            quietFresh = quietAll.any(::fresh),
            cancelLoud = AlertKind.entries.toSet() - loudAll.map { it.kind }.toSet(),
            cancelQuiet = quietAll.isEmpty(),
        )
    }

    /**
     * Записи после показа: прежние и все новые предупреждения тех явлений, о которых уведомили (в одном уведомлении о
     * явлении собраны все его предупреждения), без истёкших; не больше [MAX_RECORDS].
     */
    fun remember(notified: List<NotifiedAlert>, shown: List<WeatherAlert>, posted: List<WeatherAlert>, now: Instant): List<NotifiedAlert> {
        val kinds = posted.map { it.kind }.toSet()
        val added = shown.filter { it.kind in kinds }.map { NotifiedAlert(it.kind, it.level.number, it.onset, it.expires) }
        return (notified + added).distinct().filter { it.keptAt(now) }.takeLast(MAX_RECORDS)
    }

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
