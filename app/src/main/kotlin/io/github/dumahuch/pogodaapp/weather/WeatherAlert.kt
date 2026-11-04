package io.github.dumahuch.pogodaapp.weather

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Уровень официального предупреждения DWD: 1 предупреждение о погоде, 2 выраженная погода, 3 непогода,
 * 4 экстремальная непогода. В CAP-сообщении это поле severity (minor, moderate, severe, extreme).
 */
enum class AlertLevel(val number: Int) {
    Minor(1),
    Moderate(2),
    Severe(3),
    Extreme(4),
    ;

    companion object {
        fun ofSeverity(text: String?): AlertLevel? = when (text?.lowercase()) {
            "minor" -> Minor
            "moderate" -> Moderate
            "severe" -> Severe
            "extreme" -> Extreme
            else -> null
        }
    }
}

/** Вид явления: по нему выбираются короткое название на трёх языках и значок. */
enum class AlertKind { Thunderstorm, Wind, Rain, Snow, Frost, Ice, Fog, Heat, Uv, Thaw, Other }

/**
 * Одно официальное предупреждение. Тексты DWD даёт на немецком и английском; [onset] и [expires] это ожидаемые начало
 * и конец явления ([expires] у DWD бывает пустым, тогда конец не известен), [issued] время выпуска.
 */
data class WeatherAlert(
    val id: String,
    val level: AlertLevel,
    val kind: AlertKind,
    val onset: Instant,
    val expires: Instant?,
    val issued: Instant?,
    val headlineDe: String,
    val headlineEn: String,
    val descriptionDe: String,
    val descriptionEn: String,
    val instructionDe: String?,
    val instructionEn: String?,
) {
    /** Предупреждение ещё действует (или ожидается) в момент [now]. */
    fun activeAt(now: Instant): Boolean = expires == null || expires.isAfter(now)
}

/** Предупреждения для места: [area] название района DWD («Stadt Hannover»), если сервис его назвал. */
data class AlertSet(val area: String?, val alerts: List<WeatherAlert>) {

    /** Что показывать в момент [now]: без истёкших, сначала самые серьёзные, при равном уровне раньше начинающиеся. */
    fun shownAt(now: Instant): List<WeatherAlert> = alerts
        .filter { it.activeAt(now) }
        .sortedWith(compareByDescending<WeatherAlert> { it.level.number }.thenBy { it.onset })
}

/**
 * Вид явления по названию события DWD. Названия берутся из поля event (на немецком «STARKES GEWITTER», «WINDBÖEN»,
 * на английском «strong thunderstorms», «wind gusts»); список событий DWD велик, поэтому вид определяется по ключевым
 * словам, а не по номерам. Порядок важен: «GEWITTER mit STARKREGEN» это гроза, а не дождь.
 */
object AlertKinds {
    private val German = listOf(
        AlertKind.Thunderstorm to listOf("GEWITTER", "HAGEL"),
        AlertKind.Heat to listOf("HITZE"),
        AlertKind.Uv to listOf("UV"),
        AlertKind.Frost to listOf("FROST"),
        AlertKind.Ice to listOf("GLÄTTE", "GLAETTE", "GLATTEIS"),
        AlertKind.Thaw to listOf("TAUWETTER"),
        AlertKind.Snow to listOf("SCHNEE"),
        AlertKind.Rain to listOf("REGEN"),
        AlertKind.Wind to listOf("BÖEN", "BOEN", "WIND", "STURM", "ORKAN"),
        AlertKind.Fog to listOf("NEBEL"),
    )

    private val English = listOf(
        AlertKind.Thunderstorm to listOf("thunder", "hail"),
        AlertKind.Heat to listOf("heat"),
        AlertKind.Uv to listOf("uv"),
        AlertKind.Frost to listOf("frost"),
        AlertKind.Ice to listOf("icy", "ice", "glaze"),
        AlertKind.Thaw to listOf("thaw"),
        AlertKind.Snow to listOf("snow"),
        AlertKind.Rain to listOf("rain"),
        AlertKind.Wind to listOf("gust", "wind", "storm", "hurricane"),
        AlertKind.Fog to listOf("fog"),
    )

    fun classify(eventDe: String?, eventEn: String?): AlertKind {
        val de = eventDe?.uppercase()
        if (!de.isNullOrBlank()) German.firstOrNull { (_, words) -> words.any { it in de } }?.let { return it.first }
        val en = eventEn?.lowercase()
        if (!en.isNullOrBlank()) English.firstOrNull { (_, words) -> words.any { it in en } }?.let { return it.first }
        return AlertKind.Other
    }
}

/**
 * Когда действует предупреждение, если смотреть из момента «сейчас»: подпись справа на плашке.
 * [days] на сколько суток (по часовому поясу места) время дальше сегодняшних: 0 сегодня, 1 завтра и так далее.
 */
sealed interface AlertWhen {
    /** Начнётся меньше чем через час: «через 20 мин». */
    data class StartsIn(val minutes: Int) : AlertWhen

    /** Начнётся позже, в [at]: «с 15:00», «завтра с 06:00». */
    data class From(val at: Instant, val days: Int) : AlertWhen

    /** Уже идёт, кончится в [at]: «до 06:00», «до завтра 06:00». */
    data class Until(val at: Instant, val days: Int) : AlertWhen

    /** Уже идёт, конец не известен. */
    data object Ongoing : AlertWhen

    companion object {
        /** Предупреждения, которые начнутся меньше чем через столько минут, подписываются «через N мин». */
        const val SOON_MINUTES = 60L

        /** Минуты для «через N мин» округляются вверх до пяти, чтобы подпись не менялась каждую минуту. */
        private const val MINUTE_STEP = 5

        fun of(alert: WeatherAlert, now: Instant, zone: ZoneId): AlertWhen {
            if (alert.onset.isAfter(now)) {
                val minutes = Duration.between(now, alert.onset).toMinutes()
                if (minutes < SOON_MINUTES) {
                    val rounded = ((minutes + MINUTE_STEP - 1) / MINUTE_STEP * MINUTE_STEP).toInt().coerceAtLeast(MINUTE_STEP)
                    return StartsIn(rounded)
                }
                return From(alert.onset, daysBetween(now, alert.onset, zone))
            }
            val end = alert.expires ?: return Ongoing
            return Until(end, daysBetween(now, end, zone))
        }

        /** Разница календарных дат места между [from] и [to]. */
        fun daysBetween(from: Instant, to: Instant, zone: ZoneId): Int =
            ChronoUnit.DAYS.between(from.atZone(zone).toLocalDate(), to.atZone(zone).toLocalDate()).toInt()
    }
}
