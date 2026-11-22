package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.MIN_NOTICEABLE_MM
import io.github.dumahuch.pogodaapp.forecast.MetricDays
import io.github.dumahuch.pogodaapp.forecast.Mugginess
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.RainChance
import io.github.dumahuch.pogodaapp.forecast.VisibilityLevel
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.forecast.visibilityText
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.pages.ChartCard
import io.github.dumahuch.pogodaapp.ui.pages.HOURS
import io.github.dumahuch.pogodaapp.ui.pages.HourBars
import io.github.dumahuch.pogodaapp.ui.pages.HourLine
import io.github.dumahuch.pogodaapp.ui.pages.Mark
import io.github.dumahuch.pogodaapp.ui.pages.NowCard
import io.github.dumahuch.pogodaapp.ui.pages.SourceNote
import io.github.dumahuch.pogodaapp.ui.pages.StepGauge
import io.github.dumahuch.pogodaapp.ui.pages.WeekHeader
import io.github.dumahuch.pogodaapp.ui.pages.byHour
import io.github.dumahuch.pogodaapp.ui.pages.forecastMark
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.Card
import io.github.dumahuch.pogodaapp.ui.sunmoon.CardTitle
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * Страницы ветра, осадков и влажности в едином стиле (claude/rules-style.md, 0.8.83; макет
 * https://claude.ai/artifact/KcpRCjaazEbEVi9YBNSPKP): «Сейчас» с символом справа, график по часам, «7 дней» плашками,
 * неброское пояснение шкал и источников в конце.
 */

/** Всё, что нужно странице: прогноз, выбранный день, сегодня, сейчас, дни списка и выбор дня. */
internal class PageContext(
    val forecast: Forecast,
    val date: LocalDate,
    val today: LocalDate,
    val now: Instant,
    val dates: List<LocalDate>,
    val onSelect: (LocalDate) -> Unit,
) {
    val zone: ZoneId get() = forecast.zone
    val isToday: Boolean get() = date == today
    val nowIndex: Int? get() = if (isToday) now.atZone(zone).hour else null
}

/** Шкалы: ветер не уже 0…20 км/ч, влажность не уже 20 пунктов, столбики осадков не ниже 1,5 мм (как на «Главной»). */
private const val WIND_SCALE_KMH = 20.0
private const val MIN_HUMIDITY_SPAN = 20.0
private const val HUMIDITY_PAD = 8.0
private const val MIN_RAIN_SCALE_MM = 1.5

/** Часы, которые показывает список направлений: сейчас, через 6 и через 12 часов (у другого дня 06, 12 и 18). */
private const val DIRECTION_STEP_HOURS = 6L
private val OTHER_DAY_HOURS = listOf(6, 12, 18)

private const val BEAUFORT_TOP = 12f

/** Компас карточки «Направление», ветряк карточки «Сейчас» и круг осадков, dp. */
private const val DIRECTION_COMPASS_DP = 168
private const val WINDMILL_DP = 112
private const val RAIN_RING_DP = 156

@Composable
private fun hourlyTitle(ctx: PageContext): String {
    if (ctx.isToday) return stringResource(R.string.hourly_title)
    val names = stringArrayResource(R.array.days_short).toList()
    return stringResource(R.string.pg_hourly_day, dayName(ctx.date, names))
}

/** Легенда линии: цвет линии и «прогноз» точками (только сегодня). */
@Composable
private fun lineLegend(ctx: PageContext, color: Color, name: String): List<Triple<Mark, Color, String>> =
    listOf(Triple(Mark.Line, color, name)) +
        if (ctx.isToday) listOf(Triple(Mark.Dots, LocalPogodaPalette.current.ink.copy(alpha = 0.7f), stringResource(R.string.pg_legend_forecast))) else emptyList()

private fun hourOf(time: Instant, zone: ZoneId): Double = time.atZone(zone).let { it.hour + it.minute / 60.0 }

// ---------------------------------------------------------------- ветер

/** Цвет ступени Бофорта: от спокойного цвета ветра к тёплому, как на шкале. */
@Composable
private fun beaufortColor(step: Int): Color {
    val palette = LocalPogodaPalette.current
    return lerp(palette.accent, palette.warning, step / BEAUFORT_TOP)
}

@Composable
internal fun WindPage(ctx: PageContext) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val palette = LocalPogodaPalette.current
    val kmh = stringResource(R.string.kmh_unit)
    val strength = stringArrayResource(R.array.beaufort_names)
    val directions = stringArrayResource(R.array.wind_directions)
    val points = stringArrayResource(R.array.wind_points_short)
    val hours = remember(forecast, ctx.date) { MetricDays.detailsOf(forecast.hourDetails, zone, ctx.date) }
    val current = if (ctx.isToday) forecast.detailsAt(ctx.now) else null
    val peakIndex = MetricDays.peakIndex(hours.map { it.windKmh })
    val gustIndex = MetricDays.peakIndex(hours.map { it.gustKmh })
    val maxSpeed = peakIndex?.let { hours[it].windKmh?.roundToInt() }
    val gustMax = gustIndex?.let { hours[it].gustKmh?.roundToInt() }
    val shown = if (ctx.isToday) current?.windKmh?.roundToInt() else maxSpeed
    val gustShown = if (ctx.isToday) current?.gustKmh?.roundToInt() else gustMax
    val direction = if (ctx.isToday) current?.windDirection else MetricDays.dominantDirection(hours)
    val step = shown?.let { beaufortOf(it) }
    NowCard(
        title = stringResource(if (ctx.isToday) R.string.now else R.string.metric_day_max),
        value = shown?.toString() ?: "–",
        unit = kmh,
        word = step?.let { strength[it] },
        wordDot = step?.let { beaufortColor(it) },
        note = listOfNotNull(
            direction?.let { directions[CompassPoint.of(it).ordinal] },
            gustShown?.let { stringResource(R.string.metric_gusts_short, "$it $kmh") },
        ).joinToString(", ").ifEmpty { null },
        symbol = { Windmill(shown, WINDMILL_DP.dp) },
    ) {
        if (step != null) {
            Box(Modifier.padding(top = 12.dp)) {
                BeaufortScale(step, gustShown?.let { beaufortOf(it) }, stringResource(R.string.metric_marker_now))
            }
        }
        if (maxSpeed != null && gustMax != null) {
            TwoTiles(
                TileText(
                    stringResource(if (ctx.isToday) R.string.metric_max_today else R.string.metric_day_max),
                    "$maxSpeed $kmh",
                    stringResource(R.string.metric_at, clockText(hours[peakIndex].time, zone)),
                ),
                TileText(
                    stringResource(R.string.metric_gusts_upto),
                    "$gustMax $kmh",
                    stringResource(R.string.metric_at, clockText(hours[gustIndex].time, zone)),
                ),
            )
        }
    }

    // Направление: большой компас со скоростью и список «сейчас / через 6 ч / через 12 ч».
    val moments: List<Pair<String, HourDetails?>> = if (ctx.isToday) {
        listOf(stringResource(R.string.now) to current) + (1L..2L).map { n ->
            val at = ctx.now.plus(Duration.ofHours(DIRECTION_STEP_HOURS * n))
            val details = forecast.detailsAt(at)
            (details?.let { clockText(it.time, zone) } ?: "") to details
        }
    } else {
        OTHER_DAY_HOURS.map { h -> "%02d:00".format(java.util.Locale.ROOT, h) to hours.firstOrNull { it.time.atZone(zone).hour == h } }
    }
    val compassFrom = moments.first().second?.windDirection ?: direction
    if (compassFrom != null) {
        val speed = moments.first().second?.windKmh?.roundToInt() ?: shown
        Card {
            CardTitle(stringResource(R.string.pg_wind_direction))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WindCompass(compassFrom, DIRECTION_COMPASS_DP.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    moments.forEach { (label, details) ->
                        val from = details?.windDirection ?: return@forEach
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Text(label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
                            Text(directions[CompassPoint.of(from).ordinal], style = pogodaText(14f, 700), maxLines = 1)
                            Text("${Math.floorMod(from, 360)}°", style = pogodaText(11.5f, 500, color = palette.ink2, tabular = true), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (hours.isNotEmpty()) {
        val speeds = byHour(hours, zone, { it.time }) { it.windKmh }
        val gusts = byHour(hours, zone, { it.time }) { it.gustKmh }
        val arrows = byHour(hours, zone, { it.time }) { it.windDirection?.toDouble() }.map { it?.roundToInt() }
        val top = maxOf(WIND_SCALE_KMH, (gusts + speeds).filterNotNull().maxOrNull() ?: 0.0) * 1.05
        val grid = listOf(20.0, 40.0, 60.0, 80.0, 100.0).filter { it < top }.takeLast(3).map { it to "${it.roundToInt()} $kmh" }
        ChartCard(
            hourlyTitle(ctx),
            lineLegend(ctx, palette.accent, stringResource(R.string.metric_legend_wind)) + listOf(
                Triple(Mark.Band, palette.accent, stringResource(R.string.metric_legend_gusts)),
                Triple(Mark.Arrow, palette.ink, stringResource(R.string.metric_legend_blows)),
            ),
        ) {
            HourLine(speeds, ctx.nowIndex, palette.accent, 0.0, top, grid, band = gusts, arrows = arrows)
        }
    }

    val perDay = remember(forecast, ctx.dates) { ctx.dates.map { MetricDays.detailsOf(forecast.hourDetails, zone, it) } }
    DaysCard(
        ctx.dates, ctx.today, ctx.date, ctx.onSelect,
        legend = { WeekHeader(stringResource(R.string.metric_strongest), "", "", DayValueWidth, ARROW_COLUMN) },
    ) { i ->
        val day = perDay[i]
        val high = day.mapNotNull { it.windKmh }.maxOrNull()?.roundToInt()
        val gust = day.mapNotNull { it.gustKmh }.maxOrNull()?.roundToInt()
        val s = high?.let { beaufortOf(it) }
        if (s != null) {
            StepWord(strength[s], beaufortColor(s), Modifier.weight(1f))
        } else {
            Text("–", style = pogodaText(14f, 700), modifier = Modifier.weight(1f))
        }
        DayValue(high?.let { "$it $kmh" } ?: "–", gust?.let { stringResource(R.string.metric_gusts_short, it.toString()) })
        Column(Modifier.width(ARROW_COLUMN), horizontalAlignment = Alignment.CenterHorizontally) {
            MetricDays.dominantDirection(day)?.let {
                WindArrow(it, 20.dp, palette.ink)
                Text(points[CompassPoint.of(it).ordinal], style = pogodaText(10.5f, 700, color = palette.ink2, lineHeight = 1.1f), maxLines = 1)
            }
        }
    }
    SourceNote(stringResource(R.string.pg_wind_note))
}

private val ARROW_COLUMN = 40.dp

// ---------------------------------------------------------------- осадки

@Composable
private fun kindName(kind: PrecipitationKind): String = stringResource(
    when (kind) {
        PrecipitationKind.Generic -> R.string.kind_generic
        PrecipitationKind.Rain -> R.string.kind_rain
        PrecipitationKind.Storm -> R.string.kind_storm
        PrecipitationKind.Snow -> R.string.kind_snow
        PrecipitationKind.Drizzle -> R.string.kind_drizzle
    },
)

@Composable
internal fun RainPage(ctx: PageContext) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val locale = LocalLocale.current.platformLocale
    val mm = stringResource(R.string.mm_unit)
    val mmH = stringResource(R.string.pg_mm_h)
    val points = remember(forecast, ctx.date) { forecast.hoursOf(ctx.date) }
    val day = forecast.days.firstOrNull { it.date == ctx.date }
    val total = day?.precipitationMm ?: points.sumOf { it.precipitationMm }
    val parts = MetricDays.partPeaks(points, zone)
    val whenWords = stringArrayResource(R.array.metric_parts_when)
    val wetParts = parts.indices.filter { parts[it] >= MIN_NOTICEABLE_MM }.map { whenWords[it] }
    val willRain = total >= MIN_NOTICEABLE_MM && wetParts.isNotEmpty()
    val kind = points.firstOrNull { it.precipitationMm >= MIN_NOTICEABLE_MM }?.let { PrecipitationKind.of(it.condition) }
    val headline = if (willRain && kind != null) {
        val and = stringResource(R.string.metric_and)
        val joined = if (wetParts.size > 1) wetParts.dropLast(1).joinToString(", ") + " $and " + wetParts.last() else wetParts.first()
        stringResource(R.string.metric_kind_when, kindName(kind), joined)
    } else {
        stringResource(R.string.next24_none)
    }
    val intensityWords = stringArrayResource(R.array.rain_intensity)
    val chanceWords = stringArrayResource(R.array.rain_chance)
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val spell = if (ctx.isToday) {
        remember(forecast, ctx.now) { MetricDays.nextSpell(forecast.hours, ctx.now) }
    } else {
        remember(points) { MetricDays.spells(points).maxByOrNull { it.totalMm } }
    }
    val spellText = spell?.let {
        val start = it.start.atZone(zone).toLocalDate()
        val prefix = if (start == ctx.date) "" else dayName(start, dayNames) + ", "
        "$prefix${clockText(it.start, zone)}–${clockText(it.end, zone)}, ${intensityWords[rainLevel(it.peakMm)]}"
    }
    val amounts = remember(points) { byHour(points, zone, { it.time }) { p -> p.precipitationMm.takeIf { it >= MIN_NOTICEABLE_MM } ?: 0.0 } }
    val chances = remember(points) { byHour(points, zone, { it.time }) { p -> p.precipitationProbability?.toDouble() } }
    val sunrise = day?.sunrise?.let { hourOf(it, zone) }
    val sunset = day?.sunset?.let { hourOf(it, zone) }
    RainRingPicker(amounts, ctx.nowIndex) { selected, ring ->
        NowCard(
            title = stringResource(if (ctx.isToday) R.string.today else R.string.metric_day),
            value = dayPrecipitationText(total, locale).ifEmpty { "0" },
            unit = mm,
            word = headline,
            note = spellText ?: stringResource(R.string.metric_no_rain_ahead),
            symbol = {
                if (points.isNotEmpty()) {
                    RainRing(
                        amounts, selected, ctx.nowIndex, sunrise, sunset, { precipitationText(it, locale) }, stringResource(R.string.pg_rain_dry), mmH,
                        Modifier.size(RAIN_RING_DP.dp).then(ring),
                    )
                }
            },
        ) {
            val strongest = amounts.indices.maxByOrNull { amounts[it] ?: 0.0 }?.takeIf { (amounts[it] ?: 0.0) > 0 }
            val chance = day?.precipitationProbability
            TwoTiles(
                TileText(
                    stringResource(R.string.metric_strongest),
                    strongest?.let { "${precipitationText(amounts[it]!!, locale)} $mmH" } ?: "–",
                    strongest?.let { stringResource(R.string.pg_level_at, intensityWords[rainLevel(amounts[it]!!)], "%02d:00".format(java.util.Locale.ROOT, it)) },
                ),
                TileText(
                    stringResource(R.string.metric_chance),
                    chance?.let(::percentText) ?: "–",
                    chance?.let { chanceWords[RainChance.of(it).ordinal] },
                ),
            )
        }
    }

    if (points.isNotEmpty()) {
        val legend = intensityWords.mapIndexed { i, w -> Triple(Mark.Square, RainColors[i], w) } + forecastMark(ctx.isToday)
        fun chanceOf(hour: Int) = chances.getOrNull(hour)?.let { percentText(it.roundToInt()) } ?: "–"
        // Строки с подстановками берутся заранее: подпись часа собирается вне компоновки.
        val wetHour = stringResource(R.string.pg_rain_hour)
        val dryHour = stringResource(R.string.pg_rain_hour_dry)
        ChartCard(hourlyTitle(ctx), legend) {
            HourBars(
                values = amounts,
                nowIndex = ctx.nowIndex,
                colorOf = { RainColors[rainLevel(it)] },
                describe = { v, h ->
                    if (v > 0) wetHour.format(precipitationText(v, locale), intensityWords[rainLevel(v)], chanceOf(h)) else dryHour.format(chanceOf(h))
                },
                brief = { "${precipitationText(it, locale)} $mmH" },
                extremeLabel = stringResource(R.string.metric_strongest),
                scaleMax = MIN_RAIN_SCALE_MM,
                noneText = stringResource(R.string.next24_none),
            )
        }
    }

    val perDay = remember(forecast, ctx.dates) {
        ctx.dates.map { d -> byHour(forecast.hoursOf(d), zone, { it.time }) { p -> p.precipitationMm.takeIf { it >= MIN_NOTICEABLE_MM } ?: 0.0 } }
    }
    val totals = remember(forecast, ctx.dates) {
        ctx.dates.map { d -> forecast.days.firstOrNull { it.date == d }?.precipitationMm ?: forecast.hoursOf(d).sumOf { it.precipitationMm } }
    }
    DaysCard(
        ctx.dates, ctx.today, ctx.date, ctx.onSelect,
        legend = {
            WeekHeader(stringResource(R.string.hourly_title), stringResource(R.string.pg_week_day_total), stringResource(R.string.metric_chance), TOTAL_COLUMN, CHANCE_COLUMN)
        },
    ) { i ->
        DayBars(perDay[i], Modifier.weight(1f))
        Text(
            if (totals[i] >= MIN_NOTICEABLE_MM) "${dayPrecipitationText(totals[i], locale)} $mm" else "–",
            style = pogodaText(15f, 700, tabular = true),
            modifier = Modifier.width(TOTAL_COLUMN),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            forecast.days.firstOrNull { it.date == ctx.dates[i] }?.precipitationProbability?.let(::percentText) ?: "–",
            style = pogodaText(14f, 600, tabular = true),
            modifier = Modifier.width(CHANCE_COLUMN),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    SourceNote(stringResource(R.string.pg_rain_note))
}

private val TOTAL_COLUMN = 64.dp
private val CHANCE_COLUMN = 78.dp

/**
 * Маленькие столбики суток в строке дня (Дима 22.11: ряд точек читался плохо): тонкая линия суток, а часы с осадками
 * поднимаются над ней столбиками цвета силы, как на большом графике и круге; сухой день это просто ровная линия.
 */
@Composable
private fun DayBars(values: List<Double?>, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.height(28.dp)) {
        val max = maxOf(MIN_RAIN_SCALE_MM, values.filterNotNull().maxOrNull() ?: 0.0)
        val step = size.width / HOURS
        val w = (step * 0.62f).coerceAtMost(3.dp.toPx())
        val base = 1.dp.toPx()
        drawRoundRect(palette.ink.copy(alpha = 0.22f), Offset(0f, size.height - base), Size(size.width, base), CornerRadius(base / 2))
        values.forEachIndexed { h, v ->
            val value = v ?: 0.0
            if (value <= 0) return@forEachIndexed
            val height = maxOf(5.dp.toPx(), (value / max).toFloat() * size.height)
            drawRoundRect(RainColors[rainLevel(value)], Offset(h * step + (step - w) / 2, size.height - height), Size(w, height), CornerRadius(w / 2))
        }
    }
}

// ---------------------------------------------------------------- влажность

@Composable
internal fun HumidityPage(ctx: PageContext) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val locale = LocalLocale.current.platformLocale
    val palette = LocalPogodaPalette.current
    val km = stringResource(R.string.km_unit)
    val dewLabel = stringResource(R.string.hour_dew_point)
    val visibilityLevels = stringArrayResource(R.array.visibility_levels)
    val mugginess = stringArrayResource(R.array.mugginess_levels)
    val comfortable = stringResource(R.string.metric_comfortable)
    val hours = remember(forecast, ctx.date) { MetricDays.detailsOf(forecast.hourDetails, zone, ctx.date) }
    val current: HourDetails? = if (ctx.isToday) forecast.detailsAt(ctx.now) else null
    val humidity = if (ctx.isToday) current?.humidity else hours.mapNotNull { it.humidity }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val dew = if (ctx.isToday) current?.dewPointC else hours.mapNotNull { it.dewPointC }.takeIf { it.isNotEmpty() }?.average()
    val sight = if (ctx.isToday) current?.visibilityM else hours.mapNotNull { it.visibilityM }.minOrNull()
    fun feelOf(d: Double) = Mugginess.ofDewPoint(d)?.let { mugginess[it.ordinal] } ?: comfortable
    fun feelColor(d: Double) = DewColors[Mugginess.ofDewPoint(d)?.let { it.ordinal + 1 } ?: 0]
    fun mean(details: List<HourDetails>, range: IntRange): Int? = MetricDays.meanOver(details, zone, range) { it.humidity?.toDouble() }?.roundToInt()
    NowCard(
        title = stringResource(if (ctx.isToday) R.string.now else R.string.metric_day),
        value = humidity?.toString() ?: "–",
        unit = "%",
        word = dew?.let { feelOf(it) },
        wordDot = dew?.let { feelColor(it) },
        note = dew?.let { "$dewLabel ${temperatureText(it)}" },
        symbol = { dew?.let { StepGauge(dewFraction(it), DewBands, DewColors) } },
    ) {
        val dayMean = mean(hours, MetricDays.DAYTIME_HOURS)
        val nightMean = mean(hours, MetricDays.NIGHT_HOURS)
        if (dayMean != null && nightMean != null) {
            TwoTiles(
                TileText(stringResource(R.string.metric_daytime_tile), percentText(dayMean), stringResource(R.string.metric_daytime_hours)),
                TileText(stringResource(R.string.metric_night_tile), percentText(nightMean), stringResource(R.string.metric_night_hours)),
            )
        }
    }

    if (sight != null) {
        val level = VisibilityLevel.ofMeters(sight)
        Card {
            CardTitle(stringResource(R.string.metric_visibility))
            Row(Modifier.padding(top = 4.dp, bottom = 10.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${visibilityText(sight, locale)} $km", style = pogodaText(20f, 600, tabular = true), maxLines = 1)
                Text(continuationCase(visibilityLevels[level.ordinal], locale), style = pogodaText(14f, 600, color = palette.ink2), modifier = Modifier.padding(bottom = 2.dp), maxLines = 1)
            }
            VisibilityScale(level.ordinal, VisibilityLevel.entries.size)
        }
    }

    val values = remember(hours) { byHour(hours, zone, { it.time }) { it.humidity?.toDouble() } }
    val known = values.filterNotNull()
    if (known.size >= 2) {
        val low = known.min()
        val high = known.max()
        val span = maxOf(MIN_HUMIDITY_SPAN, high - low + 2 * HUMIDITY_PAD)
        val bottom = ((low + high) / 2 - span / 2).coerceAtLeast(0.0)
        val top = (bottom + span).coerceAtMost(100.0 + HUMIDITY_PAD)
        val grid = listOf(low, (low + high) / 2, high).map { it.roundToInt() }.distinct().map { it.toDouble() to "$it %" }
        ChartCard(hourlyTitle(ctx), lineLegend(ctx, palette.accent, stringResource(R.string.metric_legend_humidity))) {
            HourLine(values, ctx.nowIndex, palette.accent, bottom, top, grid)
        }
    }

    val perDay = remember(forecast, ctx.dates) { ctx.dates.map { MetricDays.detailsOf(forecast.hourDetails, zone, it) } }
    DaysCard(
        ctx.dates, ctx.today, ctx.date, ctx.onSelect,
        legend = {
            WeekHeader(
                stringResource(R.string.pg_week_feels),
                stringResource(R.string.metric_daytime_tile),
                stringResource(R.string.metric_night_tile),
                PERCENT_COLUMN,
                PERCENT_COLUMN,
            )
        },
    ) { i ->
        val day = perDay[i]
        val fog = day.mapNotNull { it.visibilityM }.minOrNull()?.let { it < FOG_BELOW_M } == true
        val dewHigh = day.mapNotNull { it.dewPointC }.maxOrNull()
        val feelText = if (fog) stringResource(R.string.metric_fog) else dewHigh?.let { feelOf(it) }
        if (feelText != null) {
            StepWord(feelText, if (fog || dewHigh == null) palette.ink2 else feelColor(dewHigh), Modifier.weight(1f))
        } else {
            Text("–", style = pogodaText(14f, 700), modifier = Modifier.weight(1f))
        }
        Text(mean(day, MetricDays.DAYTIME_HOURS)?.let(::percentText) ?: "–", style = pogodaText(15f, 700, tabular = true), modifier = Modifier.width(PERCENT_COLUMN), textAlign = TextAlign.End, maxLines = 1)
        Text(mean(day, MetricDays.NIGHT_HOURS)?.let(::percentText) ?: "–", style = pogodaText(15f, 700, tabular = true), modifier = Modifier.width(PERCENT_COLUMN), textAlign = TextAlign.Center, maxLines = 1)
    }
    SourceNote(stringResource(R.string.pg_humidity_note))
}

private val PERCENT_COLUMN = 56.dp

/** Туман: видимость меньше километра (ступень Met Office «очень плохая»). */
private const val FOG_BELOW_M = 1_000.0
