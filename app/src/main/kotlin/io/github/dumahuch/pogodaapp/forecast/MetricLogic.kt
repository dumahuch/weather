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

/** Осадки подряд: от начала первого часа до конца последнего, сумма и самый сильный час (мм). */
data class RainSpell(val start: Instant, val end: Instant, val totalMm: Double, val peakMm: Double)

/**
 * Сила осадков по самому сильному часу, по глоссарию Американского метеорологического общества: до 2,5 мм/ч слабые,
 * до 7,6 мм/ч умеренные, сильнее сильные (https://glossary.ametsoc.org/wiki/Rain).
 */
enum class RainIntensity {
    Light, Moderate, Heavy;

    companion object {
        private const val MODERATE_FROM_MM = 2.5
        private const val HEAVY_FROM_MM = 7.6

        fun ofMmPerHour(mm: Double): RainIntensity = when {
            mm >= HEAVY_FROM_MM -> Heavy
            mm >= MODERATE_FROM_MM -> Moderate
            else -> Light
        }
    }
}

/**
 * Вероятность осадков словом по ступеням Национальной службы погоды США: 20 % «небольшая вероятность», 30–50 %
 * «возможно», 60–70 % «вероятно», 80–100 % без оговорок (https://www.weather.gov/ffc/pop). Между ступенями граница
 * посередине: до 25 % маловероятно, до 55 % возможно, до 75 % вероятно, дальше почти наверняка.
 */
enum class RainChance {
    Unlikely, Possible, Likely, AlmostCertain;

    companion object {
        fun of(percent: Int): RainChance = when {
            percent < 25 -> Unlikely
            percent < 55 -> Possible
            percent < 75 -> Likely
            else -> AlmostCertain
        }
    }
}

/** Расчёты страниц подробностей: дни списка, часы дня, отрезки осадков, преобладающее направление ветра. */
object MetricDays {
    /** Сколько дней в списке внизу страницы. */
    const val SHOWN = 7

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

    /** Отрезки часов с заметными осадками (от [MIN_NOTICEABLE_MM]) по порядку; часы в [points] идут подряд. */
    fun spells(points: List<HourPoint>): List<RainSpell> {
        val spells = ArrayList<RainSpell>()
        var run = ArrayList<HourPoint>()
        fun close() {
            if (run.isEmpty()) return
            spells += RainSpell(run.first().time, run.last().time.plus(HOUR), run.sumOf { it.precipitationMm }, run.maxOf { it.precipitationMm })
            run = ArrayList()
        }
        for (point in points) {
            if (point.precipitationMm >= MIN_NOTICEABLE_MM) run += point else close()
        }
        close()
        return spells
    }

    /** Первые осадки, которые ещё не закончились к [now]; пусто, если их нет в [points]. */
    fun nextSpell(points: List<HourPoint>, now: Instant): RainSpell? = spells(points).firstOrNull { it.end.isAfter(now) }

    /** Четыре части суток по шесть часов (ночь, утро, день, вечер): самый сильный час каждой, мм. */
    fun partPeaks(points: List<HourPoint>, zone: ZoneId): List<Double> {
        val peaks = DoubleArray(PARTS)
        for (point in points) {
            val part = point.time.atZone(zone).hour / PART_HOURS
            peaks[part] = maxOf(peaks[part], point.precipitationMm)
        }
        return peaks.toList()
    }

    private const val PARTS = 4
    private const val PART_HOURS = 6

    /** Днём: средняя за 12–18 ч местного времени, ночью: за 0–6 ч (часы с началом в этих пределах). */
    val DAYTIME_HOURS = 12 until 18
    val NIGHT_HOURS = 0 until 6

    /** Среднее значение [value] за часы [hours] местного времени; пусто, если значений нет. */
    fun meanOver(details: List<HourDetails>, zone: ZoneId, hours: IntRange, value: (HourDetails) -> Double?): Double? =
        details.filter { it.time.atZone(zone).hour in hours }.mapNotNull(value).takeIf { it.isNotEmpty() }?.average()

    private val HOUR: Duration = Duration.ofHours(1)

    /** Номер наибольшего значения; пусто, если значений нет. При равенстве первый. */
    fun peakIndex(values: List<Double?>): Int? {
        var best: Int? = null
        values.forEachIndexed { index, value ->
            if (value != null && (best == null || value > checkNotNull(values[checkNotNull(best)]))) best = index
        }
        return best
    }
}
