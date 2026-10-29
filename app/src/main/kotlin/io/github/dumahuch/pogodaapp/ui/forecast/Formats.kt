package io.github.dumahuch.pogodaapp.ui.forecast

import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Дата по шаблону системы для языка: «29 октября», «29. Oktober», «October 29» (шаблон «MMMMd») или «29 окт» (шаблон «MMMd»). */
private fun formatBySkeleton(date: LocalDate, locale: Locale, skeleton: String): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** «29 октября»: подпись даты в заголовке и в подробностях дня. */
internal fun fullDate(date: LocalDate, locale: Locale): String = formatBySkeleton(date, locale, "MMMMd")

/** «29 окт»: короткая дата под названием дня; точка в конце сокращения («окт.») убирается, как в макете. */
internal fun shortDate(date: LocalDate, locale: Locale): String = formatBySkeleton(date, locale, "MMMd").trimEnd('.')

/** Название дня недели из массива «Пн…Вс». */
internal fun dayName(date: LocalDate, names: List<String>): String = names[date.dayOfWeek.value - 1]

/** Время суток «07:20» по часовому поясу места. */
internal fun clockText(instant: Instant, zone: ZoneId): String {
    val local = instant.atZone(zone)
    return "%02d:%02d".format(Locale.ROOT, local.hour, local.minute)
}

/** Часы и минуты длительности: (8, 12) для 8 ч 12 мин. */
internal fun hoursAndMinutes(duration: Duration): Pair<Int, Int> =
    duration.toHours().toInt() to (duration.toMinutes() % 60).toInt()

/** Вероятность осадков с неразрывным пробелом перед знаком процента: «85 %». */
internal fun percentText(value: Int): String = "$value %"
