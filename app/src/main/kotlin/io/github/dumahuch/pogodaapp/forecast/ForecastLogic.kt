package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Положение полоски температур дня внутри строки: доли ширины от 0 до 1. */
data class RangeSpan(val start: Float, val width: Float)

/**
 * Полоски температур строк «Прогноза». Шкала общая для всех показанных дней: от самого холодного утра до самого
 * тёплого дня, но не уже, чем от −2 до 13 °C (как в макете), чтобы небольшой разброс не растягивался на всю строку.
 * Полоска не уже 6 % ширины, иначе день с одной и той же температурой превратился бы в точку.
 */
object TemperatureRange {
    private const val FLOOR_MIN = -2
    private const val CEILING_MAX = 13
    private const val MIN_WIDTH = 0.06f

    fun layout(days: List<DayForecast>): List<RangeSpan> {
        if (days.isEmpty()) return emptyList()
        val lows = days.map { roundTemperature(it.min) }
        val highs = days.map { roundTemperature(it.max) }
        val low = minOf(FLOOR_MIN, lows.min())
        val high = maxOf(CEILING_MAX, highs.max())
        val span = (high - low).toFloat()
        return days.indices.map { i ->
            val width = maxOf(MIN_WIDTH, (highs[i] - lows[i]) / span)
            val start = ((lows[i] - low) / span).coerceAtMost(1f - width)
            RangeSpan(start, width)
        }
    }
}

/** Что будет с осадками в ближайшие часы (карточка «Ближайшие 24 часа»). */
sealed interface PrecipitationOutlook {

    /** Осадков не ожидается. */
    data object None : PrecipitationOutlook

    /** Осадки идут сейчас. [until] первый сухой час, пусто, если осадки идут до конца показанного отрезка. */
    data class Ongoing(val kind: PrecipitationKind, val until: Instant?) : PrecipitationOutlook

    /** Осадки начнутся в [at]. */
    data class Starts(val kind: PrecipitationKind, val at: Instant) : PrecipitationOutlook
}

/** Вид осадков для подписи: по виду погоды часа; ясно и облачность с осадками (слабая морось) называются просто «осадки». */
enum class PrecipitationKind {
    Generic, Rain, Storm, Snow, Drizzle;

    companion object {
        fun of(condition: Condition): PrecipitationKind = when (condition) {
            Condition.Rain -> Rain
            Condition.Storm -> Storm
            Condition.Snow -> Snow
            Condition.Fog -> Drizzle
            Condition.Clear, Condition.Partly, Condition.Cloudy -> Generic
        }
    }
}

object PrecipitationOutlooks {

    /** [points] идут по часам, первая точка текущий час. Час с осадками это любое значение больше нуля. */
    fun of(points: List<HourPoint>): PrecipitationOutlook {
        val first = points.indexOfFirst { it.precipitationMm > 0.0 }
        if (first < 0) return PrecipitationOutlook.None
        val kind = PrecipitationKind.of(points[first].condition)
        if (first > 0) return PrecipitationOutlook.Starts(kind, points[first].time)
        val dry = points.indexOfFirst { it.precipitationMm <= 0.0 }
        return PrecipitationOutlook.Ongoing(kind, if (dry < 0) null else points[dry].time)
    }

    fun totalMm(points: List<HourPoint>): Double = points.sumOf { it.precipitationMm }
}

/** Сколько миллиметров показывать в строке дня: пусто при нуле и при значениях, которые округлятся до «0,0». */
fun dayPrecipitationText(mm: Double, locale: Locale): String =
    if (mm < 0.05) "" else String.format(locale, "%.1f", mm)

/** Ступени УФ-индекса по шкале ВОЗ: 0–2, 3–5, 6–7, 8–10, 11 и выше. */
enum class UvLevel {
    Low, Moderate, High, VeryHigh, Extreme;

    companion object {
        /** Индекс показывается целым числом, ступень берётся от него, чтобы цифра и подпись не расходились. */
        fun of(roundedIndex: Int): UvLevel = when {
            roundedIndex < 3 -> Low
            roundedIndex < 6 -> Moderate
            roundedIndex < 8 -> High
            roundedIndex < 11 -> VeryHigh
            else -> Extreme
        }
    }
}

/** Длина светового дня по восходу и закату; пусто, если одного из них нет (полярный день или ночь) или закат раньше восхода. */
fun daylight(day: DayForecast): Duration? {
    val rise = day.sunrise ?: return null
    val set = day.sunset ?: return null
    return if (set.isAfter(rise)) Duration.between(rise, set) else null
}
