package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Страница подробностей, которая открывается нажатием на показатель под температурой на «Главной» (0.8.70). */
enum class Metric { Humidity, Wind, Precipitation }

/**
 * Видимость словом по шкале Met Office: меньше 1 км очень плохая, 1–4 плохая, 4–10 умеренная, 10–20 хорошая, 20–40 очень
 * хорошая, от 40 отличная (https://www.metoffice.gov.uk/services/data/datapoint/code-definitions).
 */
enum class VisibilityLevel {
    VeryPoor, Poor, Moderate, Good, VeryGood, Excellent;

    companion object {
        fun ofMeters(meters: Double): VisibilityLevel = when {
            meters < 1_000 -> VeryPoor
            meters < 4_000 -> Poor
            meters < 10_000 -> Moderate
            meters < 20_000 -> Good
            meters < 40_000 -> VeryGood
            else -> Excellent
        }
    }
}

/**
 * Насколько влажно ощущается воздух, по точке росы (NWS, отделение Ла-Кросс: до 55 °F сухо и комфортно, 55–65 °F липко,
 * от 65 °F гнетуще; https://www.weather.gov/arx/why_dewpoint_vs_humidity). Ниже 13 °C слова нет: зимой высокая
 * относительная влажность при низкой точке росы не душная.
 */
enum class Mugginess {
    Sticky, Oppressive;

    companion object {
        private const val STICKY_FROM_C = 13.0
        private const val OPPRESSIVE_FROM_C = 18.0

        fun ofDewPoint(celsius: Double): Mugginess? = when {
            celsius >= OPPRESSIVE_FROM_C -> Oppressive
            celsius >= STICKY_FROM_C -> Sticky
            else -> null
        }
    }
}

/** Давление на экране: в русском интерфейсе миллиметры ртутного столба (решение Димы 20.11), иначе гПа. */
object PressureUnits {
    /** 1 гПа = 100 Па, 1 мм рт. ст. = 133,322 Па. */
    const val MMHG_PER_HPA = 100.0 / 133.322

    fun shown(hpa: Double, mmHg: Boolean): Double = if (mmHg) hpa * MMHG_PER_HPA else hpa
}

/** Непрерывный отрезок часов с осадками: от начала первого часа до конца последнего. */
data class PrecipitationSpan(val start: Instant, val end: Instant)

/** Расчёты страниц подробностей: дни списка, часы дня, отрезки осадков, преобладающее направление ветра. */
object MetricDays {
    /** Сколько дней в списке внизу страницы. */
    const val SHOWN = 7

    /** Наименьшая ширина полоски «от и до», чтобы день с одинаковыми значениями не стал точкой (как у температур). */
    private const val MIN_WIDTH = 0.06f

    /** Дни списка от сегодняшнего; у прогноза без суточных данных (до 0.3.0) дни берутся из часовых. */
    fun dates(forecast: Forecast, today: LocalDate): List<LocalDate> {
        val fromDays = forecast.days.map { it.date }
        val all = fromDays.ifEmpty { forecast.hourDetails.map { it.time.atZone(forecast.zone).toLocalDate() }.distinct() }
        return all.filter { !it.isBefore(today) }.take(SHOWN)
    }

    /** Часовые значения одних суток по местному времени. */
    fun detailsOf(all: List<HourDetails>, zone: ZoneId, date: LocalDate): List<HourDetails> =
        all.filter { it.time.atZone(zone).toLocalDate() == date }

    /**
     * Направление, откуда дул ветер за сутки (градусы): средний вектор, где каждый час весит по своей скорости.
     * Пусто, если направлений нет или ветер дул с разных сторон так, что вектор почти нулевой.
     */
    fun dominantDirection(hours: List<HourDetails>): Int? {
        var x = 0.0
        var y = 0.0
        var total = 0.0
        for (hour in hours) {
            val direction = hour.windDirection ?: continue
            val weight = hour.windKmh ?: continue
            val radians = Math.toRadians(direction.toDouble())
            x += sin(radians) * weight
            y += cos(radians) * weight
            total += weight
        }
        if (total <= 0.0 || hypot(x, y) < total * CALM_SHARE) return null
        return Math.floorMod(Math.toDegrees(atan2(x, y)).roundToInt(), 360)
    }

    /** Если вектор короче этой доли суммы скоростей, преобладающего направления нет. */
    private const val CALM_SHARE = 0.2

    /** Отрезки часов с осадками (больше нуля) по порядку; часы в [points] идут подряд. */
    fun precipitationSpans(points: List<HourPoint>): List<PrecipitationSpan> {
        val spans = ArrayList<PrecipitationSpan>()
        var start: Instant? = null
        var last: Instant? = null
        for (point in points) {
            if (point.precipitationMm > 0.0) {
                if (start == null) start = point.time
                last = point.time
            } else if (start != null) {
                spans += PrecipitationSpan(start, checkNotNull(last).plus(HOUR))
                start = null
            }
        }
        if (start != null) spans += PrecipitationSpan(start, checkNotNull(last).plus(HOUR))
        return spans
    }

    private val HOUR: Duration = Duration.ofHours(1)

    /** Номер наибольшего значения; пусто, если значений нет. При равенстве первый. */
    fun peakIndex(values: List<Double?>): Int? {
        var best: Int? = null
        values.forEachIndexed { index, value ->
            if (value != null && (best == null || value > checkNotNull(values[checkNotNull(best)]))) best = index
        }
        return best
    }

    /**
     * Полоски «от и до» на общей шкале от [floor] до [ceiling] (шкала раздвигается, если значения за её пределами):
     * доли ширины строки, полоска не уже [MIN_WIDTH].
     */
    fun spans(lows: List<Double>, highs: List<Double>, floor: Double, ceiling: Double): List<RangeSpan> {
        if (lows.isEmpty()) return emptyList()
        val low = minOf(floor, lows.min())
        val high = maxOf(ceiling, highs.max())
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        return lows.indices.map { i ->
            val width = maxOf(MIN_WIDTH, ((highs[i] - lows[i]) / span).toFloat())
            val start = ((lows[i] - low) / span).toFloat().coerceIn(0f, 1f - width)
            RangeSpan(start, width)
        }
    }
}
