package io.github.dumahuch.pogodaapp.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.PressureDay
import io.github.dumahuch.pogodaapp.forecast.PressureLevel
import io.github.dumahuch.pogodaapp.forecast.PressureTrend
import io.github.dumahuch.pogodaapp.forecast.PressureUnits
import io.github.dumahuch.pogodaapp.forecast.UvDay
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.uvLevelLabel
import io.github.dumahuch.pogodaapp.ui.metric.DaysCard
import io.github.dumahuch.pogodaapp.ui.metric.TileText
import io.github.dumahuch.pogodaapp.ui.metric.TwoTiles
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.drawGlowPath
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Часы суток [date] по порядку. */
internal fun hoursOn(details: List<HourDetails>, date: LocalDate, zone: ZoneId): List<HourDetails> =
    details.filter { it.time.atZone(zone).toLocalDate() == date }.sortedBy { it.time }

/** Значения по часу суток 0…23 (пусто, где часа нет; при переводе часов второй одинаковый час заменяет первый). */
internal fun <T> byHour(hours: List<T>, zone: ZoneId, time: (T) -> Instant, value: (T) -> Double?): List<Double?> {
    val slots = MutableList<Double?>(HOURS) { null }
    hours.forEach { slots[time(it).atZone(zone).hour] = value(it) }
    return slots
}

/** Номер часа «сейчас» на графике сегодняшнего дня, у другого дня пусто. */
internal fun nowSlot(ctx: PageState): Int? = if (ctx.isToday) ctx.now.atZone(ctx.zone).hour else null

/** Дни из прогноза, начиная с сегодня, у которых есть значение. */
private fun daysWith(ctx: PageState, max: Int, has: (HourDetails) -> Boolean): List<LocalDate> =
    ctx.forecast.hourDetails.filter(has).map { it.time.atZone(ctx.zone).toLocalDate() }
        .filter { !it.isBefore(ctx.today) }.distinct().sorted().take(max)

private const val WEEK_DAYS = 7

/** Шкала полукруга давления: 985…1045 гПа, границы ступеней 1000 и 1025 (как у слова на плитке). */
private const val GAUGE_LOW_HPA = 985.0
private const val GAUGE_SPAN_HPA = 60.0
internal val PressureBands = listOf(
    ((PressureLevel.NORMAL_FROM_HPA - GAUGE_LOW_HPA) / GAUGE_SPAN_HPA).toFloat(),
    ((PressureLevel.NORMAL_TO_HPA - GAUGE_LOW_HPA) / GAUGE_SPAN_HPA).toFloat(),
    1f,
)

internal fun pressureFraction(hpa: Double): Float = ((hpa - GAUGE_LOW_HPA) / GAUGE_SPAN_HPA).toFloat()

internal fun pressureLevelLabel(level: PressureLevel): Int = when (level) {
    PressureLevel.Low -> R.string.pressure_level_low
    PressureLevel.Normal -> R.string.pressure_level_normal
    PressureLevel.High -> R.string.pressure_level_high
}

internal fun trendLabel(trend: PressureTrend): Int = when (trend) {
    PressureTrend.Rising -> R.string.trend_rising
    PressureTrend.Steady -> R.string.trend_steady
    PressureTrend.Falling -> R.string.trend_falling
    PressureTrend.FallingFast -> R.string.trend_falling_fast
}

/** Шкала полукруга УФ: 0…11, границы ступеней ВОЗ посередине между целыми (индекс показывается целым). */
private const val UV_TOP = 11f
internal val UvBands = listOf(2.5f / UV_TOP, 5.5f / UV_TOP, 7.5f / UV_TOP, 10.5f / UV_TOP, 1f)

internal fun uvFraction(index: Int): Float = (index / UV_TOP).coerceIn(0f, 1f)

internal fun uvColor(index: Int): Color = UvColors[UvLevel.of(index).ordinal]

private const val MINUS = '−'

// ---------------------------------------------------------------- давление

@Composable
internal fun PressurePage(ctx: PageState) {
    val mmHg = booleanResource(R.bool.pressure_mmhg)
    fun shown(hpa: Double) = PressureUnits.shown(hpa, mmHg)
    val nowHpa = ctx.forecast.detailsAt(ctx.now)?.pressureHpa
    val beforeHpa = ctx.forecast.detailsAt(ctx.now.minus(Duration.ofHours(3)))?.pressureHpa
    val todayHours = remember(ctx.forecast, ctx.today) { hoursOn(ctx.forecast.hourDetails, ctx.today, ctx.zone) }
    val short = stringResource(R.string.pg_pressure_short)

    val change = if (nowHpa != null && beforeHpa != null) shown(nowHpa).roundToInt() - shown(beforeHpa).roundToInt() else null
    val changeText = when {
        change == null -> null
        change == 0 -> stringResource(R.string.pg_pressure_3h_steady)
        else -> stringResource(
            R.string.pg_pressure_3h,
            stringResource(if (change > 0) R.string.trend_rising else R.string.trend_falling),
            (if (change > 0) "+$change" else "$MINUS${abs(change)}") + " " + short,
        )
    }
    NowCard(
        title = stringResource(R.string.now),
        value = nowHpa?.let { shown(it).roundToInt().toString() } ?: "–",
        unit = stringResource(R.string.pressure_unit),
        word = nowHpa?.let { stringResource(pressureLevelLabel(PressureLevel.ofHpa(it.roundToInt()))) },
        note = changeText,
        symbol = { nowHpa?.let { StepGauge(pressureFraction(it), PressureBands, PressureColors) } },
    ) {
        val high = PressureDay.highest(todayHours)
        val low = PressureDay.lowest(todayHours)
        if (high != null && low != null) {
            TwoTiles(
                TileText(stringResource(R.string.pg_highest_today), shown(high.value).roundToInt().toString(), stringResource(R.string.metric_at, clockText(high.time, ctx.zone))),
                TileText(stringResource(R.string.pg_lowest_today), shown(low.value).roundToInt().toString(), stringResource(R.string.metric_at, clockText(low.time, ctx.zone))),
            )
        }
    }

    val dates = remember(ctx.forecast, ctx.today) { daysWith(ctx, WEEK_DAYS) { it.pressureHpa != null } }
    val day = remember(ctx.forecast, ctx.date) { hoursOn(ctx.forecast.hourDetails, ctx.date, ctx.zone) }
    val values = remember(day, mmHg) { byHour(day, ctx.zone, { it.time }) { h -> h.pressureHpa?.let { shown(it) } } }
    val known = values.filterNotNull()
    if (known.size >= 2) {
        val nowIndex = nowSlot(ctx)
        val low = known.min()
        val high = known.max()
        // Не уже 6 единиц, чтобы ровный день не выглядел скачущим; три линии сетки: низ, середина, верх дня.
        val pad = maxOf(1.0, (6.0 - (high - low)) / 2)
        val grid = listOf(low, (low + high) / 2, high).map { it to "${it.roundToInt()} $short" }.distinctBy { it.second }
        ChartCard(title = hourlyTitle(ctx), legend = pastFutureLegend(ctx, PressureLine)) {
            HourLine(values, nowIndex, PressureLine, low - pad, high + pad, grid)
        }
    }

    if (dates.size >= 2) {
        val perDay = remember(ctx.forecast, dates) { dates.map { hoursOn(ctx.forecast.hourDetails, it, ctx.zone) } }
        DaysCard(
            dates = dates,
            today = ctx.today,
            selected = ctx.date,
            onSelect = ctx.onSelect,
            legend = {
                WeekHeader(stringResource(R.string.pg_week_course), stringResource(R.string.pg_week_noon), "", NOON_COLUMN, SPARK_COLUMN)
            },
        ) { index ->
            val hours = perDay[index]
            val trend = PressureDay.trend(hours)
            Text(
                trend?.let { stringResource(trendLabel(it)) } ?: "–",
                style = pogodaText(14f, 600),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                PressureDay.noon(hours, ctx.zone)?.let { shown(it).roundToInt().toString() } ?: "–",
                style = pogodaText(15f, 700, tabular = true),
                modifier = Modifier.width(NOON_COLUMN),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            DayLine(byHour(hours, ctx.zone, { it.time }) { h -> h.pressureHpa?.let { shown(it) } }, isToday = dates[index] == ctx.today)
        }
    }
    SourceNote(stringResource(R.string.pg_pressure_note))
}

private val NOON_COLUMN = 56.dp
private val SPARK_COLUMN = 74.dp

/** Наименьший размах шкалы линии дня (мм или гПа), как у большого графика: ровный день остаётся ровным. */
private const val MIN_DAY_SPAN = 6.0

/**
 * Маленькая линия дня в строке списка: тот же цвет, закруглённые концы и свечение, что у большого графика «По часам»;
 * у каждого дня своя шкала с наименьшим размахом, чтобы по форме можно было сравнить с сегодняшним днём.
 */
@Composable
private fun DayLine(values: List<Double?>, isToday: Boolean) {
    Canvas(Modifier.size(width = SPARK_COLUMN, height = 28.dp)) {
        val known = values.filterNotNull()
        if (known.size < 2) return@Canvas
        val mid = (known.min() + known.max()) / 2
        val span = maxOf(known.max() - known.min(), MIN_DAY_SPAN)
        val pad = 4.dp.toPx()
        val points = values.mapIndexedNotNull { i, v ->
            v?.let {
                val y = size.height / 2 - ((it - mid) / span).toFloat() * (size.height - 2 * pad)
                Offset(pad + (size.width - 2 * pad) * i / (HOURS - 1), y)
            }
        }
        val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        if (isToday) drawGlowPath(path, PressureLine, 2.dp.toPx()) else drawPath(path, PressureLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun hourlyTitle(ctx: PageState): String {
    if (ctx.isToday) return stringResource(R.string.hourly_title)
    val names = stringArrayResource(R.array.days_short).toList()
    return stringResource(R.string.pg_hourly_day, dayName(ctx.date, names))
}

/** Образец «прогноз» контуром для легенды столбиков; у другого дня его нет (все столбики сплошные). */
@Composable
internal fun forecastMark(isToday: Boolean): List<Triple<Mark, Color, String>> =
    if (isToday) listOf(Triple(Mark.Outline, LocalPogodaPalette.current.ink.copy(alpha = 0.8f), stringResource(R.string.pg_legend_forecast))) else emptyList()

/** Легенда линии: «прошло» цветом линии и «прогноз» точками; у другого дня легенды нет (линия одна). */
@Composable
private fun pastFutureLegend(ctx: PageState, color: Color): List<Triple<Mark, Color, String>> =
    if (!ctx.isToday) {
        emptyList()
    } else {
        listOf(
            Triple(Mark.Line, color, stringResource(R.string.pg_legend_past)),
            Triple(Mark.Dots, LocalPogodaPalette.current.ink.copy(alpha = 0.7f), stringResource(R.string.pg_legend_forecast)),
        )
    }

// ---------------------------------------------------------------- УФ

/** Часы защиты «11–16» для строки дня; пусто, если защита не нужна. */
private fun protectionHours(hours: List<HourDetails>, zone: ZoneId): String? =
    UvDay.protection(hours)?.let { (from, to) -> "${from.atZone(zone).hour}–${to.atZone(zone).hour}" }

@Composable
internal fun UvPage(ctx: PageState) {
    val locale = LocalLocale.current.platformLocale
    val today = remember(ctx.forecast, ctx.today) { hoursOn(ctx.forecast.hourDetails, ctx.today, ctx.zone) }
    val current = ctx.forecast.detailsAt(ctx.now)?.uvIndex?.roundToInt()
    val protection = UvDay.protection(today)
    val protectionText = protection?.let { (from, to) -> "${clockText(from, ctx.zone)}–${clockText(to, ctx.zone)}" }
    NowCard(
        title = stringResource(R.string.now),
        value = current?.toString() ?: "–",
        unit = null,
        word = current?.let { stringResource(uvLevelLabel(UvLevel.of(it))) },
        note = if (protectionText != null) stringResource(R.string.pg_uv_protect, protectionText.substringBefore('–'), protectionText.substringAfter('–')) else stringResource(R.string.pg_uv_no_protect),
        symbol = { current?.let { StepGauge(uvFraction(it), UvBands, UvColors) } },
    ) {
        val peak = UvDay.peak(today)
        if (peak != null) {
            val peakIndex = peak.value.roundToInt()
            val hours = protection?.let { Duration.between(it.first, it.second).toHours().toInt() }
            TwoTiles(
                TileText(
                    stringResource(R.string.metric_max_today),
                    peakIndex.toString(),
                    stringResource(R.string.pg_level_at, stringResource(uvLevelLabel(UvLevel.of(peakIndex))), clockText(peak.time, ctx.zone)),
                ),
                TileText(
                    stringResource(R.string.pg_uv_protect_title),
                    protectionText ?: stringResource(R.string.pg_uv_not_needed),
                    if (hours != null) stringResource(R.string.pg_hours_short, hours) else stringResource(R.string.pg_uv_below),
                ),
            )
        }
    }

    val day = remember(ctx.forecast, ctx.date) { hoursOn(ctx.forecast.hourDetails, ctx.date, ctx.zone) }
    val values = remember(day) { byHour(day, ctx.zone, { it.time }) { it.uvIndex } }
    if (values.any { it != null }) {
        val legend = UvLevel.entries.take(4).map { level ->
            Triple(Mark.Square, UvColors[level.ordinal], continuationCase(stringResource(uvLevelLabel(level)), locale))
        } + forecastMark(ctx.isToday)
        val words = UvLevel.entries.map { continuationCase(stringResource(uvLevelLabel(it)), locale) }
        val uv = stringResource(R.string.tile_uv)
        fun text(v: Double) = "$uv ${v.roundToInt()} · ${words[UvLevel.of(v.roundToInt()).ordinal]}"
        ChartCard(hourlyTitle(ctx), legend) {
            HourBars(
                values = values,
                nowIndex = nowSlot(ctx),
                colorOf = { uvColor(it.roundToInt()) },
                describe = { v, _ -> text(v) },
                brief = ::text,
                extremeLabel = stringResource(R.string.pg_peak_max),
                scaleMax = UvDay.PROTECT_FROM.toDouble(),
            )
        }
    }

    val dates = remember(ctx.forecast, ctx.today) { daysWith(ctx, WEEK_DAYS) { it.uvIndex != null } }
    if (dates.size >= 2) {
        val perDay = remember(ctx.forecast, dates) { dates.map { hoursOn(ctx.forecast.hourDetails, it, ctx.zone) } }
        DaysCard(
            dates = dates,
            today = ctx.today,
            selected = ctx.date,
            onSelect = ctx.onSelect,
            legend = {
                WeekHeader(stringResource(R.string.pg_week_peak), stringResource(R.string.pg_week_uv), stringResource(R.string.pg_week_protect), UV_COLUMN, PROTECT_COLUMN)
            },
        ) { index ->
            val peak = UvDay.peak(perDay[index])?.value?.roundToInt()
            Text(
                peak?.let { stringResource(uvLevelLabel(UvLevel.of(it))) } ?: "–",
                style = pogodaText(14f, 700, color = peak?.let { uvColor(it) } ?: Color.Unspecified),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(peak?.toString() ?: "–", style = pogodaText(15f, 700, tabular = true), modifier = Modifier.width(UV_COLUMN), textAlign = TextAlign.End, maxLines = 1)
            Text(
                protectionHours(perDay[index], ctx.zone) ?: "–",
                style = pogodaText(12.5f, 600, color = LocalPogodaPalette.current.ink2, tabular = true),
                modifier = Modifier.width(PROTECT_COLUMN),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
    SourceNote(stringResource(R.string.pg_uv_note))
}

private val UV_COLUMN = 40.dp
private val PROTECT_COLUMN = 64.dp
