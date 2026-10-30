package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Куда идёт давление ближайшие три часа (плитка «Давление»). */
enum class PressureTrend {
    Rising, Steady, Falling, FallingFast;

    companion object {
        /**
         * [change] изменение давления за три часа вперёд, гПа. Граница «стабильно» ±1 гПа, «быстро падает» от −3 гПа
         * (так в приложениях погоды принято называть падение, предвещающее непогоду).
         */
        fun ofChange(change: Double): PressureTrend = when {
            change <= -3.0 -> FallingFast
            change < -1.0 -> Falling
            change > 1.0 -> Rising
            else -> Steady
        }
    }
}

/**
 * Где давление на уровне моря среди обычных значений (решение Димы 30.10.2026): ниже 1000 гПа пониженное, от 1000 до 1025
 * включительно нормальное, выше 1025 повышенное. Опора: стандартная атмосфера 1013,25 гПа; единых официальных границ
 * «высокое» и «низкое» нет, у разных источников они немного разные, эти выбраны для приложения.
 */
enum class PressureLevel {
    Low, Normal, High;

    companion object {
        const val NORMAL_FROM_HPA = 1000
        const val NORMAL_TO_HPA = 1025

        /** [hpa] целое, как на плитке: число и слово не расходятся. */
        fun ofHpa(hpa: Int): PressureLevel = when {
            hpa < NORMAL_FROM_HPA -> Low
            hpa > NORMAL_TO_HPA -> High
            else -> Normal
        }
    }
}

/** Точка графика давления: час и давление на уровне моря, гПа. */
data class PressurePoint(val time: Instant, val hpa: Double)

/**
 * Ось графика давления: границы [min] и [max] и шаг линий сетки [step] (целые гПа). Линий не больше трёх (два промежутка),
 * чтобы подписи не слипались на плитке в половину ширины экрана. Весь ряд лежит между границами.
 */
data class PressureAxis(val min: Int, val max: Int, val step: Int) {
    val ticks: List<Int> get() = (min..max step step).toList()
}

private val AXIS_STEPS = intArrayOf(1, 2, 5, 10, 20, 50, 100)

fun pressureAxis(values: List<Double>): PressureAxis {
    if (values.isEmpty()) return PressureAxis(0, 1, 1)
    val low = values.min()
    val high = values.max()
    fun axisFor(step: Int): PressureAxis {
        val min = floor(low / step).toInt() * step
        var max = ceil(high / step).toInt() * step
        if (max == min) max = min + step
        return PressureAxis(min, max, step)
    }
    return AXIS_STEPS.map(::axisFor).firstOrNull { (it.max - it.min) / it.step <= 2 } ?: axisFor(AXIS_STEPS.last())
}

/** Давление: ряд для графика и тенденция. [details] идут по часам. */
object PressureOutlook {
    /** Сколько часов до «сейчас» и после него идут по графику. */
    const val HOURS_BEFORE = 6
    const val HOURS_AFTER = 12
    private const val TREND_HOURS = 3

    private fun indexOf(details: List<HourDetails>, now: Instant): Int =
        details.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)

    /**
     * Точки графика: давление за шесть часов до часа «сейчас» (сколько есть в данных: прогноз начинается с полуночи) и за
     * двенадцать после. Часы без значения пропускаются.
     */
    fun points(details: List<HourDetails>, now: Instant): List<PressurePoint> {
        if (details.isEmpty()) return emptyList()
        val index = indexOf(details, now)
        val from = (index - HOURS_BEFORE).coerceAtLeast(0)
        val to = (index + HOURS_AFTER + 1).coerceAtMost(details.size)
        return details.subList(from, to).mapNotNull { hour -> hour.pressureHpa?.let { PressurePoint(hour.time, it) } }
    }

    /** То же, только значения. */
    fun series(details: List<HourDetails>, now: Instant): List<Double> = points(details, now).map { it.hpa }

    /** Номер точки «сейчас» среди [points]: последняя, что не позже [now]; первая, если все позже. */
    fun nowIndex(points: List<PressurePoint>, now: Instant): Int =
        points.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)

    /** Тенденция: давление через три часа минус давление сейчас; пусто, если одного из значений нет. */
    fun trend(details: List<HourDetails>, now: Instant): PressureTrend? {
        if (details.isEmpty()) return null
        val index = indexOf(details, now)
        val current = details[index].pressureHpa ?: return null
        val ahead = details.getOrNull(index + TREND_HOURS)?.pressureHpa ?: return null
        return PressureTrend.ofChange(ahead - current)
    }
}

/** Восемь румбов. Направление ветра по метеорологическому правилу: откуда он дует. */
enum class CompassPoint {
    North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest;

    companion object {
        fun of(degrees: Int): CompassPoint {
            val normalized = Math.floorMod(degrees, 360)
            return entries[((normalized + 22.5) / 45).toInt() % 8]
        }
    }
}

/** Видимость: метры в «24», «3,5» или «0,4» (километры, целые от 10 км). */
fun visibilityText(meters: Double, locale: Locale): String {
    val km = Math.round(meters / 100.0) / 10.0
    return if (km >= 10.0 || km == floor(km)) {
        String.format(locale, "%d", km.toLong())
    } else {
        String.format(locale, "%.1f", km)
    }
}

/**
 * Место солнца или луны на дуге плитки «Солнце и Луна»: днём путь от восхода до заката, ночью от заката до следующего
 * восхода. [fraction] от 0 до 1. Нужны восход и закат сегодня; без них (полярный день или ночь) дуги нет.
 * Закат вчера, если сейчас до восхода, прогноз не хранит: берётся закат сегодня минус сутки (ошибка в минуты).
 * Восход завтра берётся из прогноза, а если завтрашних суток нет, восход сегодня плюс сутки.
 */
data class ArcPosition(val daytime: Boolean, val fraction: Double)

fun arcPosition(now: Instant, today: DayForecast, tomorrow: DayForecast?): ArcPosition? {
    val rise = today.sunrise ?: return null
    val set = today.sunset ?: return null
    if (!set.isAfter(rise)) return null
    val day = Duration.ofDays(1)
    return when {
        now < rise -> ArcPosition(false, share(set.minus(day), rise, now))
        now < set -> ArcPosition(true, share(rise, set, now))
        else -> ArcPosition(false, share(set, tomorrow?.sunrise ?: rise.plus(day), now))
    }
}

private fun share(from: Instant, to: Instant, now: Instant): Double {
    val total = Duration.between(from, to).toMillis().toDouble()
    if (total <= 0.0) return 0.0
    return (Duration.between(from, now).toMillis() / total).coerceIn(0.0, 1.0)
}

/**
 * Ступень шкалы Бофорта (0–12) по средней скорости ветра в км/ч. [kmh] целое, как на плитке: число и слово не расходятся.
 * Нижние границы ступеней 1–12 по таблице MeteoSchweiz (https://www.meteoschweiz.admin.ch): 1, 6, 12, 20, 29, 39, 50, 62,
 * 75, 89, 103, 118 км/ч; ниже 1 км/ч штиль.
 */
fun beaufortOf(kmh: Int): Int = BEAUFORT_LOWER_KMH.count { kmh >= it }

/** С этой ступени Бофорта (умеренный ветер, от 20 км/ч) порыв стоит пояснять словом. */
private const val GUST_WORD_FROM_STEP = 4

/**
 * Ступень Бофорта порыва, если под числом порывов нужна пояснительная строка, иначе null. Строка нужна, когда порыв дошёл
 * до умеренного ветра (от 20 км/ч) и его ступень выше ступени среднего ветра [meanKmh] (решение Димы 30.10.2026);
 * без среднего ветра сравнивать не с чем, тогда достаточно первого условия.
 */
fun gustBeaufort(meanKmh: Int?, gustKmh: Int): Int? {
    val step = beaufortOf(gustKmh)
    if (step < GUST_WORD_FROM_STEP) return null
    if (meanKmh != null && step <= beaufortOf(meanKmh)) return null
    return step
}

private val BEAUFORT_LOWER_KMH = intArrayOf(1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118)

/**
 * Высота дуги солнца, при которой страница «Подробно» как раз помещается на экран. [currentPx] нынешняя высота дуги,
 * [slackPx] запас экрана (высота окна минус высота содержимого, отрицательный при нехватке), [gapPx] зазор, который
 * нужно оставить. Результат не ниже [minPx] (тогда страница прокручивается) и не выше [naturalPx] (дуга не растягивается).
 */
fun fitArcHeight(currentPx: Int, slackPx: Int, gapPx: Int, minPx: Int, naturalPx: Int): Int =
    (currentPx + slackPx - gapPx).coerceIn(minOf(minPx, naturalPx), naturalPx)

/**
 * Лёгкое сглаживание ряда давления для графика: два прохода с весами 1-2-1, края остаются на месте. Часовые значения
 * меняются ступеньками по 0,1 гПа, на графике размахом в пару гПа они выглядели бы зубцами.
 */
fun smoothSeries(values: List<Double>): List<Double> {
    var current = values
    repeat(2) {
        if (current.size < 3) return current
        val previous = current
        current = previous.indices.map { i ->
            if (i == 0 || i == previous.lastIndex) previous[i] else 0.25 * previous[i - 1] + 0.5 * previous[i] + 0.25 * previous[i + 1]
        }
    }
    return current
}

/**
 * Наклоны кривой через точки с равным шагом по x, которые не дают выбросов выше и ниже самих точек (монотонная кубическая
 * интерполяция Фритча и Карлсона). [y] значения в точках; результат наклон в единицах y на один шаг. В экстремуме наклон нулевой.
 */
fun monotoneTangents(y: DoubleArray): DoubleArray {
    val n = y.size
    val m = DoubleArray(n)
    if (n < 2) return m
    val d = DoubleArray(n - 1) { y[it + 1] - y[it] }
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (k in 1 until n - 1) {
        m[k] = if (d[k - 1] * d[k] <= 0.0) 0.0 else 2.0 * d[k - 1] * d[k] / (d[k - 1] + d[k])
    }
    return m
}
