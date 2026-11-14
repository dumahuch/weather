package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.forecast.DAY_PART_MIN_MM
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.Forecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs

/** Осадки завтрашнего дня одним окном: от начала первого часа с осадками до конца последнего. */
data class RainWindow(val kind: PrecipitationKind, val from: Instant, val to: Instant, val totalMm: Double)

/** Чем завтра отличается от сегодня по дневному максимуму. */
enum class Versus { Same, Warmer, Colder }

/**
 * Завтрашний день для вечерней сводки (0.8.35, решение Димы 14.11.2026): температуры, сравнение с сегодня, какая погода держится
 * большую часть дня, будет ли дождь и когда. [versusDegrees] разница дневных максимумов в градусах без знака.
 * [dominant] вид погоды, который держится большую часть светлого времени (не меньше [SummaryNotice.DOMINANT_SHARE] часов), иначе `null`.
 */
data class TomorrowOutlook(
    val high: Double,
    val low: Double,
    val versus: Versus,
    val versusDegrees: Int,
    val dominant: Condition?,
    val rain: RainWindow?,
    val condition: Condition,
)

/** Решения вечерней сводки без Android: что написать про завтра, когда слать и когда ставить задачу. */
object SummaryNotice {
    /** Дневные максимумы, отличающиеся не больше чем на столько градусов, считаются «как сегодня». */
    const val SAME_DEGREES = 1

    /** Доля светлых часов (6–22), при которой одна и та же погода «держится большую часть дня». */
    const val DOMINANT_SHARE = 0.6

    /** Час с осадками это час не меньше этого количества мм (меньше это капли, которых не замечаешь). */
    const val RAINY_HOUR_MM = 0.1

    private const val DAY_FROM = 6
    private const val DAY_TO = 22

    /** Сколько после выбранного времени сводка ещё имеет смысл (телефон был выключен или без сети). */
    val LATE_LIMIT: Duration = Duration.ofHours(3)

    /** Окно, в которое система может сдвинуть задачу после выбранного времени. */
    val JOB_WINDOW: Duration = Duration.ofMinutes(10)

    /** Прогноз на завтра по местным суткам места; `null`, если в прогнозе нет завтрашнего дня (старый формат). */
    fun outlook(forecast: Forecast, now: Instant): TomorrowOutlook? {
        val today = now.atZone(forecast.zone).toLocalDate()
        val tomorrow = today.plusDays(1)
        val day = forecast.days.firstOrNull { it.date == tomorrow } ?: return null
        val todayMax = forecast.days.firstOrNull { it.date == today }?.max
        val diff = todayMax?.let { roundTemperature(day.max) - roundTemperature(it) } ?: 0
        val versus = when {
            abs(diff) <= SAME_DEGREES -> Versus.Same
            diff > 0 -> Versus.Warmer
            else -> Versus.Colder
        }
        val hours = forecast.hoursOf(tomorrow)
        val light = hours.filter { it.time.atZone(forecast.zone).hour in DAY_FROM until DAY_TO }
        val dominant = light.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }
            ?.takeIf { light.isNotEmpty() && it.value.toDouble() / light.size >= DOMINANT_SHARE }?.key
        val wet = hours.filter { it.precipitationMm >= RAINY_HOUR_MM }
        val total = hours.sumOf { it.precipitationMm }
        val rain = if (wet.isEmpty() || total < DAY_PART_MIN_MM) {
            null
        } else {
            RainWindow(PrecipitationKind.of(wet.first().condition), wet.first().time, wet.last().time.plus(Duration.ofHours(1)), total)
        }
        return TomorrowOutlook(day.max, day.min, versus, abs(diff), dominant, rain, day.condition)
    }

    /** Пора ли слать сводку: выбранное время наступило, не прошло больше [LATE_LIMIT], и за сегодня её ещё не было. */
    fun isDue(now: ZonedDateTime, minutes: Int, sentOn: LocalDate?): Boolean {
        if (sentOn == now.toLocalDate()) return false
        val target = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minutes.toLong())
        return !now.isBefore(target) && !now.isAfter(target.plus(LATE_LIMIT))
    }

    /**
     * Через сколько поставить задачу: сегодня в выбранное время, если оно ещё впереди и сводки не было; сразу, если время
     * прошло недавно, а сводки не было; иначе завтра в то же время.
     */
    fun delayUntilNext(now: ZonedDateTime, minutes: Int, sentOn: LocalDate?): Duration {
        val today = now.toLocalDate()
        val target = today.atStartOfDay(now.zone).plusMinutes(minutes.toLong())
        if (sentOn != today) {
            if (now.isBefore(target)) return Duration.between(now, target)
            if (!now.isAfter(target.plus(LATE_LIMIT))) return Duration.ZERO
        }
        return Duration.between(now, today.plusDays(1).atStartOfDay(now.zone).plusMinutes(minutes.toLong()))
    }
}
