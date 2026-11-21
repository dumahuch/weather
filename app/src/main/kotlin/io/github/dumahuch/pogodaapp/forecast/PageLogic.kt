package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.AirHour
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Логика страниц шага Б (давление, УФ, воздух; 21.11.2026, макет https://claude.ai/artifact/LcjikZKRqfX7zLgcFrTUnJ).
 * Без Android: проверяется тестами `PageLogicTest`.
 */

/** Значение и час, когда оно было (самое высокое или низкое за день). */
data class TimedValue(val value: Double, val time: Instant)

object PressureDay {

    /** Изменение давления за сутки (гПа) словами: «стабильно» в пределах ±2, «быстро падает» от −8 (выбрано Claude). */
    fun trend(hours: List<HourDetails>): PressureTrend? {
        val values = hours.mapNotNull { it.pressureHpa }
        if (values.size < 2) return null
        val change = values.last() - values.first()
        return when {
            change <= FAST_FALL_HPA -> PressureTrend.FallingFast
            change <= -STEADY_HPA -> PressureTrend.Falling
            change >= STEADY_HPA -> PressureTrend.Rising
            else -> PressureTrend.Steady
        }
    }

    fun highest(hours: List<HourDetails>): TimedValue? =
        hours.filter { it.pressureHpa != null }.maxByOrNull { it.pressureHpa!! }?.let { TimedValue(it.pressureHpa!!, it.time) }

    fun lowest(hours: List<HourDetails>): TimedValue? =
        hours.filter { it.pressureHpa != null }.minByOrNull { it.pressureHpa!! }?.let { TimedValue(it.pressureHpa!!, it.time) }

    /** Давление в полдень (или в ближайший к нему час с данными). */
    fun noon(hours: List<HourDetails>, zone: ZoneId): Double? =
        hours.filter { it.pressureHpa != null }.minByOrNull { Math.abs(it.time.atZone(zone).hour - 12) }?.pressureHpa

    private const val STEADY_HPA = 2.0
    private const val FAST_FALL_HPA = -8.0
}

/** Часы, когда УФ-индекс от 3 и нужна защита (ВОЗ): от начала первого такого часа до конца последнего. */
object UvDay {
    const val PROTECT_FROM = 3

    fun protection(hours: List<HourDetails>): Pair<Instant, Instant>? {
        // Индекс показывается целым, поэтому и защита по округлённому: число на экране и часы защиты не расходятся.
        val strong = hours.filter { Math.round(it.uvIndex ?: 0.0) >= PROTECT_FROM }
        if (strong.isEmpty()) return null
        return strong.first().time to strong.last().time.plusSeconds(3600)
    }

    fun peak(hours: List<HourDetails>): TimedValue? =
        hours.filter { it.uvIndex != null }.maxByOrNull { it.uvIndex!! }?.let { TimedValue(it.uvIndex!!, it.time) }
}

/** Вещества воздуха и нормы ВОЗ 2021 (средние за сутки, у озона за 8 часов), мкг/м³. */
enum class Pollutant(val guideline: Double) {
    Pm25(15.0), Pm10(45.0), No2(25.0), O3(100.0), So2(40.0);

    fun of(hour: AirHour): Double? = when (this) {
        Pm25 -> hour.pm25
        Pm10 -> hour.pm10
        No2 -> hour.no2
        O3 -> hour.o3
        So2 -> hour.so2
    }
}

object AirDay {

    /**
     * Значение вещества за сутки [date] для сравнения с нормой ВОЗ: среднее по часам суток, у озона наибольшее
     * среднее за 8 часов подряд. Пусто, если данных за сутки меньше [MIN_HOURS] часов.
     */
    fun daily(hours: List<AirHour>, date: LocalDate, zone: ZoneId, pollutant: Pollutant): Double? {
        val day = hours.filter { it.time.atZone(zone).toLocalDate() == date }
        val values = day.map { pollutant.of(it) }
        if (values.count { it != null } < MIN_HOURS) return null
        if (pollutant != Pollutant.O3) return values.filterNotNull().average()
        return values.windowed(OZONE_WINDOW).mapNotNull { window ->
            window.filterNotNull().takeIf { it.size >= OZONE_WINDOW - 2 }?.average()
        }.maxOrNull() ?: values.filterNotNull().average()
    }

    /** Наихудший индекс EAQI за сутки (для «Дней»). */
    fun worst(hours: List<AirHour>, date: LocalDate, zone: ZoneId): Int? =
        hours.filter { it.time.atZone(zone).toLocalDate() == date }.mapNotNull { it.eaqi }.maxOrNull()

    /** Сутки, на которые в ответе есть индекс, по порядку. */
    fun dates(hours: List<AirHour>, zone: ZoneId, from: LocalDate): List<LocalDate> =
        hours.filter { it.eaqi != null }.map { it.time.atZone(zone).toLocalDate() }.distinct().filter { it >= from }.sorted()

    /** Вещество, которое ближе всех к своей норме (наибольшая доля нормы). */
    fun main(shares: Map<Pollutant, Double>): Pollutant? = shares.maxByOrNull { it.value }?.key

    const val MIN_HOURS = 6
    private const val OZONE_WINDOW = 8
}
