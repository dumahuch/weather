package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.MIN_NOTICEABLE_MM
import io.github.dumahuch.pogodaapp.forecast.MetricDays
import io.github.dumahuch.pogodaapp.forecast.Mugginess
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.RainChance
import io.github.dumahuch.pogodaapp.forecast.RainIntensity
import io.github.dumahuch.pogodaapp.forecast.VisibilityLevel
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.forecast.visibilityText
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.home.HourlyFrame
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

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
}

/** С какого часа открывается лента не сегодняшнего дня, если нечего показать раньше. */
private const val MORNING_COLUMN = 6

/** Шкалы: ветер и порывы не уже 0…20 км/ч, столбики осадков не ниже 1,5 мм (как на «Главной»), влажность не уже 20 пунктов. */
private const val WIND_SCALE_KMH = 20.0
private const val MIN_RAIN_SCALE_MM = 1.5
private const val MIN_HUMIDITY_SPAN = 20.0
private const val HUMIDITY_PAD = 8.0

private fun hourText(time: Instant, zone: ZoneId): String = "%02d".format(Locale.ROOT, time.atZone(zone).hour)

private fun nowIndexOf(times: List<Instant>, isToday: Boolean, now: Instant): Int? =
    if (!isToday) null else times.indexOfLast { !it.isAfter(now) }.takeIf { it >= 0 }

private fun firstColumn(nowIndex: Int?, fallback: Int): Int = nowIndex?.let { (it - 1).coerceAtLeast(0) } ?: fallback

@Composable
private fun labelsOf(times: List<Instant>, zone: ZoneId, nowIndex: Int?): List<String> {
    val now = stringResource(R.string.now)
    return times.mapIndexed { i, t -> if (i == nowIndex) now else hourText(t, zone) }
}

/** Подпись под заголовком карточки «По часам»: легенда с образцами цвета. */
@Composable
private fun StripCard(side: Modifier, legend: List<Pair<Swatch, String>>, content: @Composable () -> Unit) {
    HourlyFrame(caption = "", modifier = side) {
        Legend(legend, Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        content()
    }
}

// ---------------------------------------------------------------- ветер

@Composable
internal fun WindPage(ctx: PageContext, side: Modifier) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val kmh = stringResource(R.string.kmh_unit)
    val strength = stringArrayResource(R.array.beaufort_names)
    val directions = stringArrayResource(R.array.wind_directions)
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
    TopCard(
        title = stringResource(if (ctx.isToday) R.string.now else R.string.metric_day_max),
        value = shown?.toString() ?: "–",
        unit = kmh,
        lines = listOfNotNull(step?.let { strength[it] }, direction?.let { directions[CompassPoint.of(it).ordinal] }),
        modifier = side,
        picture = direction?.let { { BigCompass(it) } },
    ) {
        if (step != null) BeaufortScale(step, gustShown?.let { beaufortOf(it) }, stringResource(R.string.metric_marker_now))
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
    if (hours.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val nowIndex = nowIndexOf(hours.map { it.time }, ctx.isToday, ctx.now)
        val labels = labelsOf(hours.map { it.time }, zone, nowIndex)
        val gustTop = (hours.maxOf { maxOf(it.windKmh ?: 0.0, it.gustKmh ?: 0.0) }).coerceAtLeast(WIND_SCALE_KMH)
        val spec = remember(hours, labels, nowIndex) {
            StripSpec(
                labels = labels,
                nowIndex = nowIndex,
                directions = hours.map { it.windDirection },
                line = Series(hours.map { it.windKmh }, hours.map { it.windKmh?.roundToInt()?.toString() ?: "–" }),
                band = Series(
                    hours.map { h -> h.gustKmh?.let { g -> maxOf(g, h.windKmh ?: g) } },
                    hours.map { it.gustKmh?.roundToInt()?.toString() ?: "–" },
                ),
                lineMin = 0.0,
                lineMax = gustTop * 1.05,
                description = hours.mapIndexed { i, h -> "${labels[i]}: ${h.windKmh?.roundToInt() ?: "–"}, ${h.gustKmh?.roundToInt() ?: "–"}" },
            )
        }
        StripCard(
            side,
            listOf(
                Swatch.Line to stringResource(R.string.metric_legend_wind),
                Swatch.Band to stringResource(R.string.metric_legend_gusts),
                Swatch.Arrow to stringResource(R.string.metric_legend_blows),
            ),
        ) {
            androidx.compose.runtime.key(ctx.date) { MetricStrip(spec, firstColumn(nowIndex, MORNING_COLUMN)) }
        }
        Spacer(Modifier.height(12.dp))
        val perDay = remember(forecast, ctx.dates) { ctx.dates.map { MetricDays.detailsOf(forecast.hourDetails, zone, it) } }
        val windHigh = perDay.map { day -> day.mapNotNull { it.windKmh }.maxOrNull() ?: 0.0 }
        val gustHigh = perDay.mapIndexed { i, day -> maxOf(windHigh[i], day.mapNotNull { it.gustKmh }.maxOrNull() ?: 0.0) }
        val scale = maxOf(WIND_SCALE_KMH, gustHigh.max())
        val palette = LocalPogodaPalette.current
        DaysCard(
            ctx.dates, ctx.today, ctx.date, ctx.onSelect, stringResource(R.string.metric_days_wind_note), side,
            legend = {
                Legend(listOf(Swatch.Bar to stringResource(R.string.metric_days_wind), Swatch.Band to stringResource(R.string.metric_days_gusts)))
            },
        ) { i ->
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                MetricDays.dominantDirection(perDay[i])?.let { WindArrow(it, 22.dp, palette.ink) }
            }
            Canvas(Modifier.weight(1f).height(10.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(palette.ink.copy(alpha = 0.08f), size = size, cornerRadius = radius)
                drawRoundRect(palette.accent.copy(alpha = BAND_ALPHA), size = Size((gustHigh[i] / scale).toFloat() * size.width, size.height), cornerRadius = radius)
                drawRoundRect(palette.accent, size = Size((windHigh[i] / scale).toFloat() * size.width, size.height), cornerRadius = radius)
            }
            DayValue(windHigh[i].roundToInt().toString(), stringResource(R.string.metric_gusts_short, gustHigh[i].roundToInt().toString()))
        }
    }
}

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
internal fun RainPage(ctx: PageContext, side: Modifier) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val locale = LocalLocale.current.platformLocale
    val mm = stringResource(R.string.mm_unit)
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
        val prefix = if (start == ctx.today && ctx.isToday || !ctx.isToday) "" else dayName(start, dayNames) + ", "
        val clock = "${clockText(it.start, zone)}–${clockText(it.end, zone)}"
        "$prefix$clock · ${intensityWords[RainIntensity.ofMmPerHour(it.peakMm).ordinal]} · ${precipitationText(it.totalMm, locale)} $mm"
    } ?: stringResource(R.string.metric_no_rain_ahead)
    val perDayTotals = remember(forecast, ctx.dates) {
        ctx.dates.map { d -> forecast.days.firstOrNull { it.date == d }?.precipitationMm ?: forecast.hoursOf(d).sumOf { it.precipitationMm } }
    }
    val weekTotal = perDayTotals.sum()
    val wetDays = perDayTotals.count { it >= MIN_NOTICEABLE_MM }
    val chance = day?.precipitationProbability
    val palette = LocalPogodaPalette.current
    TopCard(
        title = stringResource(if (ctx.isToday) R.string.today else R.string.metric_day),
        value = dayPrecipitationText(total, locale).ifEmpty { "0" },
        unit = mm,
        lines = listOf(headline, stringResource(if (willRain) R.string.metric_umbrella_yes else R.string.metric_umbrella_no)),
        modifier = side,
        picture = { Umbrella(willRain) },
    ) {
        Text(
            stringResource(if (ctx.isToday) R.string.metric_next_rain else R.string.metric_strongest),
            style = pogodaText(12.5f, 600, color = palette.ink2),
        )
        Text(spellText, style = pogodaText(14.5f, 600, lineHeight = 1.3f), modifier = Modifier.padding(top = 2.dp))
        TwoTiles(
            TileText(
                stringResource(R.string.metric_chance),
                chance?.let(::percentText) ?: "–",
                chance?.let { chanceWords[RainChance.of(it).ordinal] },
            ),
            TileText(
                stringResource(R.string.metric_week_total),
                "${dayPrecipitationText(weekTotal, locale).ifEmpty { "0" }} $mm",
                if (wetDays == 0) stringResource(R.string.metric_week_dry) else pluralStringResource(R.plurals.metric_wet_days, wetDays, wetDays),
            ),
        )
    }
    if (points.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val nowIndex = nowIndexOf(points.map { it.time }, ctx.isToday, ctx.now)
        val left = if (nowIndex != null) points.drop(nowIndex) else points
        val anyWet = left.any { it.precipitationMm >= MIN_NOTICEABLE_MM }
        if (!anyWet) {
            HourlyFrame(caption = "", modifier = side) { DryNote(left, ctx.isToday) }
        } else {
            val labels = labelsOf(points.map { it.time }, zone, nowIndex)
            val spec = remember(points, labels, nowIndex) {
                StripSpec(
                    labels = labels,
                    nowIndex = nowIndex,
                    bars = Series(
                        points.map { it.precipitationMm },
                        points.map { if (it.precipitationMm >= MIN_NOTICEABLE_MM) precipitationText(it.precipitationMm, locale) else "" },
                    ),
                    dashed = Series(
                        points.map { it.precipitationProbability?.toDouble() },
                        points.map { it.precipitationProbability?.let(::percentText) ?: "–" },
                    ),
                    barMax = maxOf(MIN_RAIN_SCALE_MM, points.maxOf { it.precipitationMm }),
                    description = points.mapIndexed { i, p -> "${labels[i]}: ${precipitationText(p.precipitationMm, locale)}" },
                )
            }
            val firstWet = points.indexOfFirst { it.precipitationMm >= MIN_NOTICEABLE_MM }
            StripCard(
                side,
                listOf(Swatch.Bar to stringResource(R.string.metric_legend_rain), Swatch.Dashed to stringResource(R.string.metric_legend_chance)),
            ) {
                androidx.compose.runtime.key(ctx.date) {
                    MetricStrip(spec, firstColumn(nowIndex, if (firstWet > 0) firstWet - 1 else MORNING_COLUMN))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val partNames = stringArrayResource(R.array.metric_parts)
    val allParts = remember(forecast, ctx.dates) { ctx.dates.map { MetricDays.partPeaks(forecast.hoursOf(it), zone) } }
    DaysCard(
        ctx.dates, ctx.today, ctx.date, ctx.onSelect, "", side,
        legend = { PartsHeader(partNames.toList(), stringResource(R.string.mm_unit)) },
    ) { i ->
        Canvas(Modifier.weight(1f).height(16.dp)) {
            val gap = 3.dp.toPx()
            val segment = (size.width - gap * 3) / 4
            allParts[i].forEachIndexed { k, peak ->
                val color = if (peak >= MIN_NOTICEABLE_MM) {
                    palette.accent.copy(alpha = 0.45f + minOf(peak, 3.0).toFloat() / 3f * 0.45f)
                } else {
                    palette.ink.copy(alpha = 0.08f)
                }
                drawRoundRect(color, Offset(k * (segment + gap), 0f), Size(segment, size.height), CornerRadius(4.dp.toPx()))
            }
        }
        val amount = perDayTotals[i]
        DayValue(
            if (amount >= MIN_NOTICEABLE_MM) dayPrecipitationText(amount, locale) else "–",
            forecast.days.firstOrNull { it.date == ctx.dates[i] }?.precipitationProbability?.let(::percentText),
        )
    }
}

/** Вместо пустой ленты с нулями: одна строка, что осадков не будет, и самая высокая вероятность. */
@Composable
private fun DryNote(left: List<HourPoint>, isToday: Boolean) {
    val palette = LocalPogodaPalette.current
    val top = left.mapNotNull { it.precipitationProbability }.maxOrNull()
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(
            stringResource(if (isToday) R.string.metric_dry_rest else R.string.next24_none),
            style = pogodaText(14.5f, 600),
        )
        top?.let {
            Text(stringResource(R.string.metric_dry_chance, percentText(it)), style = pogodaText(12.5f, 500, color = palette.ink2))
        }
    }
}

// ---------------------------------------------------------------- влажность

@Composable
internal fun HumidityPage(ctx: PageContext, side: Modifier) {
    val zone = ctx.zone
    val forecast = ctx.forecast
    val locale = LocalLocale.current.platformLocale
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
    val feel = dew?.let { d -> Mugginess.ofDewPoint(d)?.let { mugginess[it.ordinal] } ?: comfortable }
    fun mean(range: IntRange): Int? = MetricDays.meanOver(hours, zone, range) { it.humidity?.toDouble() }?.roundToInt()
    val dayMean = mean(MetricDays.DAYTIME_HOURS)
    val nightMean = mean(MetricDays.NIGHT_HOURS)
    TopCard(
        title = stringResource(if (ctx.isToday) R.string.now else R.string.metric_day),
        value = humidity?.toString() ?: "–",
        unit = "%",
        lines = listOfNotNull(feel, dew?.let { "$dewLabel ${temperatureText(it)}" }),
        modifier = side,
        picture = humidity?.let { { DropGauge(it) } },
    ) {
        if (sight != null) {
            val level = VisibilityLevel.ofMeters(sight)
            LabelRow(
                stringResource(R.string.metric_visibility),
                "${visibilityText(sight, locale)} $km · " + continuationCase(visibilityLevels[level.ordinal], locale),
            )
            VisibilityScale(level.ordinal, VisibilityLevel.entries.size)
        }
        if (dayMean != null && nightMean != null) {
            TwoTiles(
                TileText(stringResource(R.string.metric_daytime_tile), percentText(dayMean), stringResource(R.string.metric_daytime_hours)),
                TileText(stringResource(R.string.metric_night_tile), percentText(nightMean), stringResource(R.string.metric_night_hours)),
            )
        }
    }
    if (hours.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val nowIndex = nowIndexOf(hours.map { it.time }, ctx.isToday, ctx.now)
        val labels = labelsOf(hours.map { it.time }, zone, nowIndex)
        val known = hours.mapNotNull { it.humidity }
        val spec = remember(hours, labels, nowIndex) {
            val low = (known.minOrNull() ?: 0).toDouble()
            val high = (known.maxOrNull() ?: 100).toDouble()
            val padded = maxOf(MIN_HUMIDITY_SPAN, high - low + 2 * HUMIDITY_PAD)
            val min = (((low + high) / 2) - padded / 2).coerceAtLeast(0.0)
            StripSpec(
                labels = labels,
                nowIndex = nowIndex,
                line = Series(hours.map { it.humidity?.toDouble() }, hours.map { it.humidity?.toString() ?: "–" }),
                dots = Series(hours.map { it.dewPointC }, hours.map { it.dewPointC?.let(::temperatureText) ?: "–" }),
                lineMin = min,
                lineMax = min + padded,
                description = hours.mapIndexed { i, h -> "${labels[i]}: ${h.humidity ?: "–"} %, $dewLabel ${h.dewPointC?.let(::temperatureText) ?: "–"}" },
            )
        }
        StripCard(
            side,
            listOf(Swatch.Line to stringResource(R.string.metric_legend_humidity), Swatch.Dot to stringResource(R.string.metric_legend_dew)),
        ) {
            androidx.compose.runtime.key(ctx.date) { MetricStrip(spec, firstColumn(nowIndex, MORNING_COLUMN)) }
        }
    }
    Spacer(Modifier.height(12.dp))
    val palette = LocalPogodaPalette.current
    val perDay = remember(forecast, ctx.dates) { ctx.dates.map { MetricDays.detailsOf(forecast.hourDetails, zone, it) } }
    DaysCard(
        ctx.dates, ctx.today, ctx.date, ctx.onSelect, stringResource(R.string.metric_days_humidity_note), side,
        legend = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconLabel(LineIcons.Sun, stringResource(R.string.metric_daytime))
                IconLabel(LineIcons.Moon, stringResource(R.string.metric_night))
            }
        },
    ) { i ->
        val day = perDay[i]
        val fog = day.mapNotNull { it.visibilityM }.minOrNull()?.let { it < FOG_BELOW_M } == true
        val word = day.mapNotNull { it.dewPointC }.maxOrNull()?.let { d -> Mugginess.ofDewPoint(d)?.let { mugginess[it.ordinal] } ?: comfortable }
        Text(
            if (fog) stringResource(R.string.metric_fog) else word.orEmpty(),
            style = pogodaText(14f, if (fog) 700 else 600, color = if (fog) palette.ink else palette.ink2),
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        val d = MetricDays.meanOver(day, zone, MetricDays.DAYTIME_HOURS) { it.humidity?.toDouble() }?.roundToInt()
        val n = MetricDays.meanOver(day, zone, MetricDays.NIGHT_HOURS) { it.humidity?.toDouble() }?.roundToInt()
        IconValue(LineIcons.Sun, d?.toString() ?: "–", palette.warning)
        IconValue(LineIcons.Moon, n?.toString() ?: "–", palette.accent)
    }
}

/** Туман: видимость меньше километра (ступень Met Office «очень плохая»). */
private const val FOG_BELOW_M = 1_000.0

@Composable
private fun IconLabel(icon: io.github.dumahuch.pogodaapp.ui.components.LineIcon, text: String) {
    val palette = LocalPogodaPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        LineIconView(icon, 14.dp, palette.ink2, strokeWidth = 1.8f)
        Text(text, style = pogodaText(11.5f, 600, color = palette.ink2))
    }
}

@Composable
private fun IconValue(icon: io.github.dumahuch.pogodaapp.ui.components.LineIcon, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(IconValueWidth)) {
        LineIconView(icon, 15.dp, tint, strokeWidth = 1.8f)
        Text(text, style = pogodaText(15f, 700, tabular = true), maxLines = 1)
    }
}

private val IconValueWidth = 44.dp
