package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.DayForecast
import java.time.LocalDate

/** Вид осадков недели для фразы: дождь, снег или всё вместе и неизвестное («осадки»). */
enum class WeekPrecipKind { Rain, Snow, Mixed }

/** Небо сухой недели: большинство дней ясные, большинство пасмурные, иначе без слова о небе. */
enum class DrySky { Clear, Cloudy, Plain }

/** Как менялась температура за неделю: к концу теплее, холоднее или без заметной разницы. */
enum class TemperatureTrend { Warmer, Colder, Steady }

/** Осадки недели: сухо или дни с осадками (их даты, вид и «возможны» ли они только). */
sealed interface WeekPrecipitation {
    data class Dry(val sky: DrySky) : WeekPrecipitation

    /** [dates] даты дней с осадками по порядку; [possible] у всех таких дней вероятность осадков ниже половины. */
    data class Wet(val kind: WeekPrecipKind, val dates: List<LocalDate>, val possible: Boolean) : WeekPrecipitation
}

/** Фраза недели на «Прогнозе»: осадки, размах температур и куда они идут. Текст собирает интерфейс (три языка). */
data class WeekSummary(
    val precipitation: WeekPrecipitation,
    val lowC: Int,
    val highC: Int,
    val trend: TemperatureTrend,
)

/**
 * Сводка семи дней от сегодняшнего (решение Димы 26.11.2026: «в течение недели солнечно», «до конца недели дожди»).
 * Правила выбрал Claude: день с осадками, если погода дня дождь, гроза или снег либо сумма осадков не меньше
 * [WET_DAY_MM]; сухая неделя «солнечная» или «пасмурная», когда таких дней не меньше [SKY_DAYS] из семи; тренд по
 * средним максимумам первых и последних трёх дней, порог [TREND_DEGREES].
 */
object WeekSummaries {
    const val DAYS = 7
    const val MIN_DAYS = 3
    const val WET_DAY_MM = 1.0
    const val SKY_DAYS = 5
    const val TREND_DEGREES = 3
    private const val POSSIBLE_BELOW_PERCENT = 50
    private const val EDGE_DAYS = 3

    fun of(days: List<DayForecast>): WeekSummary? {
        val week = days.take(DAYS)
        if (week.size < MIN_DAYS) return null
        val wet = week.filter(::isWet)
        val precipitation = if (wet.isEmpty()) {
            WeekPrecipitation.Dry(drySky(week))
        } else {
            WeekPrecipitation.Wet(
                kind = kindOf(wet),
                dates = wet.map { it.date },
                possible = wet.all { (it.precipitationProbability ?: 100) < POSSIBLE_BELOW_PERCENT },
            )
        }
        val first = week.take(EDGE_DAYS).map { it.max }.average()
        val last = week.takeLast(EDGE_DAYS).map { it.max }.average()
        val diff = last - first
        val trend = when {
            diff >= TREND_DEGREES -> TemperatureTrend.Warmer
            diff <= -TREND_DEGREES -> TemperatureTrend.Colder
            else -> TemperatureTrend.Steady
        }
        return WeekSummary(
            precipitation = precipitation,
            lowC = roundTemperature(week.minOf { it.min }),
            highC = roundTemperature(week.maxOf { it.max }),
            trend = trend,
        )
    }

    private fun isWet(day: DayForecast): Boolean =
        day.condition == Condition.Rain || day.condition == Condition.Storm || day.condition == Condition.Snow ||
            day.precipitationMm >= WET_DAY_MM

    private fun kindOf(wet: List<DayForecast>): WeekPrecipKind {
        val kinds = wet.map {
            when (it.condition) {
                Condition.Rain, Condition.Storm -> WeekPrecipKind.Rain
                Condition.Snow -> WeekPrecipKind.Snow
                else -> WeekPrecipKind.Mixed
            }
        }.toSet()
        return kinds.singleOrNull() ?: WeekPrecipKind.Mixed
    }

    private fun drySky(week: List<DayForecast>): DrySky = when {
        week.count { it.condition == Condition.Clear } >= SKY_DAYS -> DrySky.Clear
        week.count { it.condition == Condition.Cloudy } >= SKY_DAYS -> DrySky.Cloudy
        else -> DrySky.Plain
    }
}
