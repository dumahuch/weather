package io.github.dumahuch.pogodaapp.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.AirDay
import io.github.dumahuch.pogodaapp.forecast.Pollutant
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LevelPill
import io.github.dumahuch.pogodaapp.ui.details.PollenDots
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.metric.DaysCard
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.Card
import io.github.dumahuch.pogodaapp.ui.sunmoon.CardTitle
import io.github.dumahuch.pogodaapp.weather.AirLevel
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import java.time.LocalDate
import kotlin.math.roundToInt

/** Шкала полукруга воздуха: EAQI 0…100, ступени EEA через каждые 20. */
internal val AirBands = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)

internal fun airFraction(eaqi: Int): Float = (eaqi / 100f).coerceIn(0f, 1f)

internal fun airColor(eaqi: Int): Color = AirColors[AirLevel.of(eaqi).ordinal]

/** Полоса вещества доходит до 140 % нормы: дальше всё равно «выше нормы». */
private const val SHARE_TOP = 1.4f
private const val AIR_DAYS = 5

@Composable
internal fun AirPage(ctx: PageState) {
    val air = ctx.content.airQuality
    val levels = stringArrayResource(R.array.air_levels)
    val words = stringArrayResource(R.array.pg_pollutant_words)
    val names = stringArrayResource(R.array.pg_pollutant_names)
    val locale = LocalLocale.current.platformLocale
    val hours = air?.hours.orEmpty()
    val todayShares = remember(air, ctx.today) {
        Pollutant.entries.mapNotNull { p -> AirDay.daily(hours, ctx.today, ctx.zone, p)?.let { p to it / p.guideline } }.toMap()
    }
    val main = AirDay.main(todayShares)
    NowCard(
        title = stringResource(R.string.now),
        value = air?.eaqi?.toString() ?: "–",
        unit = air?.let { stringResource(R.string.air_unit) },
        word = air?.let { levels[it.level.ordinal] },
        note = when {
            air == null -> stringResource(R.string.air_no_data)
            main != null -> stringResource(R.string.pg_air_main, continuationCase(words[main.ordinal], locale))
            else -> null
        },
        symbol = { air?.let { StepGauge(airFraction(it.eaqi), AirBands, AirColors) } },
    )
    if (air != null && hours.isNotEmpty()) {
        val dates = remember(air, ctx.today) { AirDay.dates(hours, ctx.zone, ctx.today).take(AIR_DAYS) }
        val date = if (ctx.date in dates) ctx.date else ctx.today
        val daily = remember(air, date) { Pollutant.entries.mapNotNull { p -> AirDay.daily(hours, date, ctx.zone, p)?.let { p to it } } }
        if (daily.isNotEmpty()) SubstancesCard(daily, names, words)

        val day = remember(air, date) { hours.filter { it.time.atZone(ctx.zone).toLocalDate() == date } }
        val values = remember(day) { byHour(day, ctx.zone, { it.time }) { h -> h.eaqi?.toDouble() } }
        if (values.any { it != null }) {
            val legend = AirLevel.entries.take(4).map { level ->
                Triple(Mark.Square, AirColors[level.ordinal], continuationCase(levels[level.ordinal], locale))
            } + forecastMark(date == ctx.today)
            val unit = stringResource(R.string.air_unit)
            fun text(v: Double) = "$unit ${v.roundToInt()} · ${continuationCase(levels[AirLevel.of(v.roundToInt()).ordinal], locale)}"
            ChartCard(hourlyTitle(ctx), legend) {
                HourBars(
                    values = values,
                    nowIndex = if (date == ctx.today) nowSlot(ctx) else null,
                    colorOf = { airColor(it.roundToInt()) },
                    describe = { v, _ -> text(v) },
                    brief = ::text,
                    extremeLabel = stringResource(R.string.pg_peak_worst),
                    scaleMax = AIR_SCALE,
                )
            }
        }
        if (dates.size >= 2) {
            DaysCard(
                dates = dates,
                today = ctx.today,
                selected = date,
                onSelect = ctx.onSelect,
                title = stringResource(R.string.pg_days_5),
                legend = {
                    WeekHeader(stringResource(R.string.pg_week_worst), stringResource(R.string.air_unit), null, WORST_COLUMN, 0.dp, beforeRight = TIME_COLUMN)
                },
            ) { index ->
                val hour = AirDay.worstHour(hours, dates[index], ctx.zone)
                val worst = hour?.eaqi
                // Одна строка, как слева: плашка, время худшего часа, число (Дима 26.11: время под плашкой и под числом
                // выглядело неряшливо; таблица из колонок).
                if (worst != null) {
                    LevelPill(levels[AirLevel.of(worst).ordinal], airColor(worst), Modifier.weight(1f))
                } else {
                    Text("–", style = pogodaText(14f, 700), modifier = Modifier.weight(1f), maxLines = 1)
                }
                Text(
                    hour?.let { clockText(it.time, ctx.zone) } ?: "",
                    style = pogodaText(12.5f, 600, color = LocalPogodaPalette.current.ink2, tabular = true),
                    modifier = Modifier.width(TIME_COLUMN),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Text(worst?.toString() ?: "–", style = pogodaText(15f, 700, tabular = true), modifier = Modifier.width(WORST_COLUMN), textAlign = TextAlign.End, maxLines = 1)
            }
        }
    }
    SourceNote(stringResource(R.string.pg_air_note))
}

/** Наименьший верх шкалы штрихов EAQI: до «удовлетворительного» хороший воздух не выглядит плохим. */
private const val AIR_SCALE = 40.0
private val WORST_COLUMN = 36.dp

/** Колонка времени худшего часа левее числа. */
private val TIME_COLUMN = 44.dp

/** Цвет доли нормы: до нормы зелёный, от нормы красный (черта нормы на полосе та же граница). */
private fun shareColor(share: Double): Color = if (share < 1.0) AirColors[0] else AirColors[4]

/** Вещества за сутки против норм ВОЗ: название, полоса доли нормы с чертой нормы, «N % нормы», значение «из нормы». */
@Composable
private fun SubstancesCard(daily: List<Pair<Pollutant, Double>>, names: Array<String>, words: Array<String>) {
    val palette = LocalPogodaPalette.current
    Card {
        CardTitle(stringResource(R.string.pg_substances))
        MapLegend(
            listOf(
                Triple(Mark.Line, AirColors[0], stringResource(R.string.pg_legend_day_mean)),
                Triple(Mark.Tick, palette.ink, stringResource(R.string.pg_legend_who)),
            ),
            Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        daily.forEach { (pollutant, value) ->
            val share = value / pollutant.guideline
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.width(92.dp)) {
                    Text(names[pollutant.ordinal], style = pogodaText(14f, 700), maxLines = 1)
                    Text(words[pollutant.ordinal], style = pogodaText(11f, 500, color = palette.ink2), maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    ShareBar(share)
                    Text(
                        stringResource(R.string.pg_of_norm, (share * 100).roundToInt()),
                        style = pogodaText(11f, 600, color = palette.ink2, tabular = true),
                        modifier = Modifier.padding(top = 3.dp),
                        maxLines = 1,
                    )
                }
                Column(Modifier.width(58.dp), horizontalAlignment = Alignment.End) {
                    Text(value.roundToInt().toString(), style = pogodaText(15f, 700, tabular = true), maxLines = 1)
                    Text(
                        stringResource(R.string.pg_ugm3),
                        style = pogodaText(11f, 500, color = palette.ink2, tabular = true),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBar(share: Double) {
    val palette = LocalPogodaPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
        val track = RoundedCornerShape(3.dp)
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).background(palette.ink.copy(alpha = 0.1f), track))
        val fraction = (share.toFloat() / SHARE_TOP).coerceIn(0.02f, 1f)
        Box(Modifier.align(Alignment.CenterStart).width(maxWidth * fraction).height(6.dp).background(shareColor(share), track))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxWidth / SHARE_TOP - 1.dp)
                .size(width = 2.dp, height = 12.dp)
                .background(palette.ink, RoundedCornerShape(1.dp)),
        )
    }
}

// ---------------------------------------------------------------- пыльца

/** Самый сильный вид на день (при равенстве по [PollenForecast.DISPLAY_PRIORITY]); пусто, если нагрузки нет нигде. */
internal fun strongest(day: Map<PollenKind, PollenLevel>?): Pair<PollenKind, PollenLevel>? =
    PollenForecast.DISPLAY_PRIORITY.mapNotNull { kind -> day?.get(kind)?.let { kind to it } }
        .filter { it.second.halves > 0 }
        .maxByOrNull { it.second }

private const val POLLEN_MORE = 2
private val POLLEN_DAY_COLUMN = 64.dp

@Composable
internal fun PollenPage(ctx: PageState, alertKinds: Set<PollenKind>) {
    val pollen = ctx.content.pollen
    val kinds = stringArrayResource(R.array.pollen_kinds)
    val levels = stringArrayResource(R.array.pollen_levels)
    val locale = LocalLocale.current.platformLocale
    val today = pollen?.on(ctx.today)
    val top = strongest(today)
    val more = today.orEmpty().filter { it.value.halves > 0 && it.key != top?.first }
        .entries.sortedByDescending { it.value }.take(POLLEN_MORE)
        .joinToString(", ") { continuationCase(kinds[it.key.ordinal], locale) }
    NowCard(
        title = stringResource(R.string.now),
        value = when {
            today == null -> "–"
            top == null -> stringResource(R.string.pg_pollen_none)
            else -> kinds[top.first.ordinal]
        },
        unit = null,
        word = when {
            today == null -> null
            top == null -> stringResource(R.string.pg_pollen_none_word)
            else -> levels[top.second.halves]
        },
        note = when {
            today == null -> stringResource(R.string.air_no_data)
            more.isNotEmpty() -> stringResource(R.string.pg_pollen_more, more)
            else -> null
        },
        symbol = { if (top != null) PollenDots(top.second, levels[top.second.halves], Modifier.padding(end = 8.dp)) },
    )
    if (pollen != null && today != null) {
        val dates = (0L until PollenForecast.DAYS_COVERED).map { ctx.today.plusDays(it) }
        // Сверху виды из уведомлений, дальше по нагрузке сегодня, при равенстве по частоте аллергена.
        val order = PollenForecast.DISPLAY_PRIORITY.sortedWith(
            compareByDescending<PollenKind> { it in alertKinds }.thenByDescending { today[it]?.halves ?: 0 },
        )
        PollenGrid(pollen, dates, order, alertKinds)
    }
    SourceNote(stringResource(R.string.pg_pollen_note))
}

@Composable
private fun PollenGrid(pollen: PollenForecast, dates: List<LocalDate>, order: List<PollenKind>, alertKinds: Set<PollenKind>) {
    val palette = LocalPogodaPalette.current
    val kinds = stringArrayResource(R.array.pollen_kinds)
    val levels = stringArrayResource(R.array.pollen_levels)
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val locale = LocalLocale.current.platformLocale
    val heads = dates.mapIndexed { i, date ->
        when (i) {
            0 -> stringResource(R.string.today)
            1 -> stringResource(R.string.pg_tomorrow)
            else -> dayName(date, dayNames)
        }
    }
    val days = dates.map { pollen.on(it) }
    Card {
        CardTitle(stringResource(R.string.pg_days_3))
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(2, 4, 6).forEach { halves ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PollenDots(PollenLevel(halves), levels[halves])
                    Text(continuationCase(levels[halves], locale), style = pogodaText(11f, 600, color = palette.ink2), maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            heads.forEach {
                Text(it, style = pogodaText(11.5f, 600, color = palette.ink2), modifier = Modifier.width(POLLEN_DAY_COLUMN), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        order.forEach { kind ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(kinds[kind.ordinal], style = pogodaText(14f, 600), maxLines = 1)
                    if (kind in alertKinds) {
                        Text(stringResource(R.string.pg_in_alerts), style = pogodaText(11f, 500, color = palette.ink2), maxLines = 1)
                    }
                }
                days.forEach { day ->
                    Box(Modifier.width(POLLEN_DAY_COLUMN), contentAlignment = Alignment.Center) {
                        val level = day?.get(kind)
                        if (level != null) {
                            PollenDots(level, levels[level.halves])
                        } else {
                            Text("–", style = pogodaText(13f, 600, color = palette.ink2))
                        }
                    }
                }
            }
        }
    }
}
