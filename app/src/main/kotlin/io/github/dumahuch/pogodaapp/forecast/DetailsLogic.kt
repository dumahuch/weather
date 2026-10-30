package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Duration
import java.time.Instant
import java.util.Locale
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

/** Давление: ряд для графика и тенденция. [details] идут по часам. */
object PressureOutlook {
    /** Сколько часов до «сейчас» и после него идут по графику. */
    const val HOURS_BEFORE = 6
    const val HOURS_AFTER = 12
    private const val TREND_HOURS = 3

    private fun indexOf(details: List<HourDetails>, now: Instant): Int =
        details.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)

    /** Давление за шесть часов до часа «сейчас» (сколько есть в данных: прогноз начинается с полуночи) и за двенадцать после. */
    fun series(details: List<HourDetails>, now: Instant): List<Double> {
        if (details.isEmpty()) return emptyList()
        val index = indexOf(details, now)
        val from = (index - HOURS_BEFORE).coerceAtLeast(0)
        val to = (index + HOURS_AFTER + 1).coerceAtMost(details.size)
        return details.subList(from, to).mapNotNull { it.pressureHpa }
    }

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
