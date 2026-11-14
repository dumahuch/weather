package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.Forecast
import java.time.Instant

/** Одна часть суток в строке дня: [hour] час, на который взята температура. */
enum class DayStop(val hour: Int) { Morning(9), Day(14), Evening(20) }

/** Температура части суток; [temperature] на начало часа [DayStop.hour]. */
data class DayStopTemp(val stop: DayStop, val temperature: Double)

/**
 * Что ожидается сегодня до конца суток (уведомление о погоде, развёрнутый вид; решение Димы 14.11.2026): температуры утра,
 * дня и вечера из тех, что ещё впереди. Если все они уже прошли, список пуст: тогда уведомление показывает завтрашний день.
 */
object DayOutlook {
    fun remaining(forecast: Forecast, now: Instant): List<DayStopTemp> {
        val local = now.atZone(forecast.zone)
        val today = local.toLocalDate()
        val hours = forecast.hoursOf(today)
        return DayStop.entries.mapNotNull { stop ->
            if (stop.hour <= local.hour) return@mapNotNull null
            val point = hours.firstOrNull { it.time.atZone(forecast.zone).hour == stop.hour } ?: return@mapNotNull null
            DayStopTemp(stop, point.temperature)
        }
    }
}
