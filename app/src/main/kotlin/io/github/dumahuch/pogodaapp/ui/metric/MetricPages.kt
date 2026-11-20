package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.MIN_NOTICEABLE_MM
import io.github.dumahuch.pogodaapp.forecast.Metric
import io.github.dumahuch.pogodaapp.forecast.MetricDays
import io.github.dumahuch.pogodaapp.forecast.Mugginess
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.PrecipitationOutlook
import io.github.dumahuch.pogodaapp.forecast.PrecipitationOutlooks
import io.github.dumahuch.pogodaapp.forecast.VisibilityLevel
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.forecast.visibilityText
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.details.BigValue
import io.github.dumahuch.pogodaapp.ui.details.Compass
import io.github.dumahuch.pogodaapp.ui.details.SubText
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Карточка сверху: заголовок, крупное число, строки под ним, строки «название — значение»; [compassFrom] компас справа. */
internal class NowContent(
    val title: String,
    val value: String,
    val unit: String?,
    val lines: List<String>,
    val rows: List<Pair<String, String>>,
    val compassFrom: Int? = null,
)

internal class StripContent(
    val caption: String,
    val columns: List<MetricColumn>,
    val glyph: StripGlyph,
    val nowIndex: Int?,
    val initialColumn: Int,
    val barMinScale: Double = 0.0,
    val barAlpha: Float = 1f,
)

internal class MetricPage(val now: NowContent, val strip: StripContent, val days: List<MetricDayRow>, val daysNote: String)

/** С какого часа открывается лента не сегодняшнего дня, если нечего показать раньше. */
private const val MORNING_COLUMN = 6

/** Шкала столбиков порывов не меньше этого (км/ч): в тихий день столбики остаются низкими. */
private const val GUST_SCALE_KMH = 40.0

/** Порывы второстепенны рядом с линией ветра: столбики бледнее. */
private const val GUST_BAR_ALPHA = 0.45f

/** Шкала строк осадков в списке дней не уже 0…3 мм; влажности 60…100 %; ветра 0…20 км/ч. */
private const val RAIN_SCALE_MM = 3.0
private const val HUMIDITY_FLOOR = 60.0
private const val HUMIDITY_CEILING = 100.0
private const val WIND_SCALE_KMH = 20.0

/** Отрезков осадков в одной строке не больше этого, остальные заменяет многоточие. */
private const val MAX_SPANS = 3

@Composable
internal fun metricPage(metric: Metric, forecast: Forecast, date: LocalDate, today: LocalDate, now: Instant, dates: List<LocalDate>): MetricPage =
    when (metric) {
        Metric.Wind -> windPage(forecast, date, today, now, dates)
        Metric.Precipitation -> rainPage(forecast, date, today, now, dates)
        Metric.Humidity -> humidityPage(forecast, date, today, now, dates)
    }

private fun hourText(time: Instant, zone: ZoneId): String = "%02d".format(Locale.ROOT, time.atZone(zone).hour)

/** Час «сейчас» в часах сегодняшнего дня; для другого дня пусто. */
private fun nowIndexOf(times: List<Instant>, isToday: Boolean, now: Instant): Int? =
    if (!isToday) null else times.indexOfLast { !it.isAfter(now) }.takeIf { it >= 0 }

/** Сегодня лента открыта так, чтобы прошлый час был виден слева; в другой день с утра. */
private fun firstColumn(nowIndex: Int?, fallback: Int): Int = nowIndex?.let { (it - 1).coerceAtLeast(0) } ?: fallback

@Composable
private fun windPage(forecast: Forecast, date: LocalDate, today: LocalDate, now: Instant, dates: List<LocalDate>): MetricPage {
    val zone = forecast.zone
    val kmh = stringResource(R.string.kmh_unit)
    val strength = stringArrayResource(R.array.beaufort_names)
    val directions = stringArrayResource(R.array.wind_directions)
    val nowLabel = stringResource(R.string.now)
    val isToday = date == today
    val hours = remember(forecast, date) { MetricDays.detailsOf(forecast.hourDetails, zone, date) }
    val peak = MetricDays.peakIndex(hours.map { it.windKmh })
    val maxSpeed = peak?.let { hours[it].windKmh }?.roundToInt()
    val gustMax = hours.mapNotNull { it.gustKmh }.maxOrNull()?.roundToInt()
    val current = if (isToday) forecast.detailsAt(now) else null
    val shown = if (isToday) current?.windKmh?.roundToInt() else maxSpeed
    val direction = if (isToday) current?.windDirection else MetricDays.dominantDirection(hours)
    val rows = ArrayList<Pair<String, String>>()
    if (maxSpeed != null && peak != null) {
        val at = clockText(hours[peak].time, zone)
        rows += if (isToday) {
            stringResource(R.string.metric_max_today) to stringResource(R.string.metric_at_time, "$maxSpeed $kmh", at)
        } else {
            stringResource(R.string.metric_strongest) to at
        }
    }
    if (gustMax != null) rows += stringResource(R.string.metric_gusts_max) to "$gustMax $kmh"
    val nowContent = NowContent(
        title = stringResource(if (isToday) R.string.now else R.string.metric_day_max),
        value = shown?.toString() ?: "–",
        unit = kmh,
        lines = listOfNotNull(shown?.let { strength[beaufortOf(it)] }, direction?.let { directions[CompassPoint.of(it).ordinal] }),
        rows = rows,
        compassFrom = direction,
    )
    val nowIndex = nowIndexOf(hours.map { it.time }, isToday, now)
    val columns = hours.mapIndexed { index, hour ->
        val speed = hour.windKmh?.roundToInt()
        MetricColumn(
            label = if (index == nowIndex) nowLabel else hourText(hour.time, zone),
            value = speed,
            valueText = speed?.toString() ?: "–",
            bottomText = hour.gustKmh?.roundToInt()?.toString() ?: "–",
            bar = hour.gustKmh ?: 0.0,
            direction = hour.windDirection,
        )
    }
    val days = remember(forecast, dates) {
        val perDay = dates.map { MetricDays.detailsOf(forecast.hourDetails, zone, it) }
        val lows = perDay.map { day -> day.mapNotNull { it.windKmh }.minOrNull() ?: 0.0 }
        val highs = perDay.map { day -> day.mapNotNull { it.windKmh }.maxOrNull() ?: 0.0 }
        val spans = MetricDays.spans(lows, highs, 0.0, WIND_SCALE_KMH)
        dates.indices.map { i ->
            MetricDayRow(
                date = dates[i],
                left = lows[i].roundToInt().toString(),
                right = highs[i].roundToInt().toString(),
                span = spans[i],
                direction = MetricDays.dominantDirection(perDay[i]),
            )
        }
    }
    return MetricPage(
        now = nowContent,
        strip = StripContent(
            caption = stringResource(R.string.metric_strip_wind),
            columns = columns,
            glyph = StripGlyph.Arrow,
            nowIndex = nowIndex,
            initialColumn = firstColumn(nowIndex, MORNING_COLUMN),
            barMinScale = GUST_SCALE_KMH,
            barAlpha = GUST_BAR_ALPHA,
        ),
        days = days,
        daysNote = stringResource(R.string.metric_days_kmh),
    )
}

@Composable
private fun rainPage(forecast: Forecast, date: LocalDate, today: LocalDate, now: Instant, dates: List<LocalDate>): MetricPage {
    val zone = forecast.zone
    val locale = LocalLocale.current.platformLocale
    val mm = stringResource(R.string.mm_unit)
    val nowLabel = stringResource(R.string.now)
    val isToday = date == today
    val points = remember(forecast, date) { forecast.hoursOf(date) }
    val day = forecast.days.firstOrNull { it.date == date }
    val total = day?.precipitationMm ?: points.sumOf { it.precipitationMm }
    val line = if (isToday) {
        outlookText(remember(forecast, now) { PrecipitationOutlooks.of(forecast.strip(now).take(OUTLOOK_HOURS)) }, zone)
    } else {
        spansText(points, zone)
    }
    val rows = ArrayList<Pair<String, String>>()
    day?.precipitationProbability?.let { rows += stringResource(R.string.metric_chance) to percentText(it) }
    val peak = MetricDays.peakIndex(points.map { it.precipitationMm.takeIf { mmValue -> mmValue > 0.0 } })
    if (peak != null) {
        val amount = "${precipitationText(points[peak].precipitationMm, locale)} $mm"
        rows += stringResource(R.string.metric_strongest) to stringResource(R.string.metric_at_time, amount, clockText(points[peak].time, zone))
    }
    val nowContent = NowContent(
        title = stringResource(if (isToday) R.string.today else R.string.metric_day),
        value = dayPrecipitationText(total, locale).ifEmpty { "0" },
        unit = mm,
        lines = listOf(line),
        rows = rows,
    )
    val nowIndex = nowIndexOf(points.map { it.time }, isToday, now)
    val columns = points.mapIndexed { index, point ->
        MetricColumn(
            label = if (index == nowIndex) nowLabel else hourText(point.time, zone),
            value = point.precipitationProbability,
            valueText = point.precipitationProbability?.let(::percentText) ?: "–",
            bottomText = precipitationText(point.precipitationMm, locale),
            bar = point.precipitationMm,
            wet = point.precipitationMm > 0.0,
        )
    }
    val firstWet = points.indexOfFirst { it.precipitationMm > 0.0 }
    val days = remember(forecast, dates) {
        val amounts = dates.map { d ->
            forecast.days.firstOrNull { it.date == d }?.precipitationMm ?: forecast.hoursOf(d).sumOf { it.precipitationMm }
        }
        val spans = MetricDays.spans(amounts.map { 0.0 }, amounts, 0.0, RAIN_SCALE_MM)
        dates.indices.map { i ->
            val wet = amounts[i] >= MIN_NOTICEABLE_MM
            MetricDayRow(
                date = dates[i],
                left = forecast.days.firstOrNull { it.date == dates[i] }?.precipitationProbability?.let(::percentText) ?: "–",
                right = dayPrecipitationText(amounts[i], locale).ifEmpty { "0" },
                span = if (wet) spans[i] else null,
                wet = wet,
            )
        }
    }
    return MetricPage(
        now = nowContent,
        strip = StripContent(
            caption = stringResource(R.string.metric_strip_rain),
            columns = columns,
            glyph = StripGlyph.Drop,
            nowIndex = nowIndex,
            initialColumn = firstColumn(nowIndex, if (firstWet > 0) firstWet - 1 else MORNING_COLUMN),
            barMinScale = MIN_RAIN_SCALE_MM,
        ),
        days = days,
        daysNote = stringResource(R.string.metric_days_rain),
    )
}

/** Шкала столбиков осадков как на «Главной»: не меньше 1,5 мм. */
private const val MIN_RAIN_SCALE_MM = 1.5

/** Подпись «сейчас» у осадков смотрит на сутки вперёд, как бывшая карточка «Ближайшие 24 часа». */
private const val OUTLOOK_HOURS = 24

@Composable
private fun humidityPage(forecast: Forecast, date: LocalDate, today: LocalDate, now: Instant, dates: List<LocalDate>): MetricPage {
    val zone = forecast.zone
    val locale = LocalLocale.current.platformLocale
    val km = stringResource(R.string.km_unit)
    val dewLabel = stringResource(R.string.hour_dew_point)
    val visibilityLevels = stringArrayResource(R.array.visibility_levels)
    val mugginess = stringArrayResource(R.array.mugginess_levels)
    val nowLabel = stringResource(R.string.now)
    val isToday = date == today
    val hours = remember(forecast, date) { MetricDays.detailsOf(forecast.hourDetails, zone, date) }
    val nowContent = if (isToday) {
        val current: HourDetails? = forecast.detailsAt(now)
        val dew = current?.dewPointC
        NowContent(
            title = stringResource(R.string.now),
            value = current?.humidity?.toString() ?: "–",
            unit = "%",
            lines = listOfNotNull(
                dew?.let { "$dewLabel ${temperatureText(it)}" },
                dew?.let { Mugginess.ofDewPoint(it) }?.let { mugginess[it.ordinal] },
            ),
            rows = listOfNotNull(
                current?.visibilityM?.let { meters ->
                    stringResource(R.string.metric_visibility) to "${visibilityText(meters, locale)} $km · " +
                        continuationCase(visibilityLevels[VisibilityLevel.ofMeters(meters).ordinal], locale)
                },
            ),
        )
    } else {
        val values = hours.mapNotNull { it.humidity }
        val dews = hours.mapNotNull { it.dewPointC }
        val seen = hours.mapNotNull { it.visibilityM }
        NowContent(
            title = stringResource(R.string.metric_day),
            value = if (values.isEmpty()) "–" else rangeText(values.min().toString(), values.max().toString()),
            unit = "%",
            lines = listOfNotNull(
                dews.takeIf { it.isNotEmpty() }?.let {
                    "$dewLabel ${rangeText(temperatureText(it.min()), temperatureText(it.max()))}"
                },
                dews.maxOrNull()?.let { Mugginess.ofDewPoint(it) }?.let { mugginess[it.ordinal] },
            ),
            rows = listOfNotNull(
                seen.takeIf { it.isNotEmpty() }?.let {
                    stringResource(R.string.metric_visibility) to
                        "${rangeText(visibilityText(it.min(), locale), visibilityText(it.max(), locale))} $km"
                },
            ),
        )
    }
    val nowIndex = nowIndexOf(hours.map { it.time }, isToday, now)
    val columns = hours.mapIndexed { index, hour ->
        MetricColumn(
            label = if (index == nowIndex) nowLabel else hourText(hour.time, zone),
            value = hour.humidity,
            valueText = hour.humidity?.let(::percentText) ?: "–",
            bottomText = hour.dewPointC?.let(::temperatureText) ?: "–",
        )
    }
    val days = remember(forecast, dates) {
        val perDay = dates.map { d -> MetricDays.detailsOf(forecast.hourDetails, zone, d).mapNotNull { it.humidity } }
        val lows = perDay.map { (it.minOrNull() ?: 0).toDouble() }
        val highs = perDay.map { (it.maxOrNull() ?: 0).toDouble() }
        val spans = MetricDays.spans(lows, highs, HUMIDITY_FLOOR, HUMIDITY_CEILING)
        dates.indices.map { i ->
            MetricDayRow(
                date = dates[i],
                left = if (perDay[i].isEmpty()) "–" else lows[i].roundToInt().toString(),
                right = if (perDay[i].isEmpty()) "–" else highs[i].roundToInt().toString(),
                span = if (perDay[i].isEmpty()) null else spans[i],
            )
        }
    }
    return MetricPage(
        now = nowContent,
        strip = StripContent(
            caption = stringResource(R.string.metric_strip_humidity),
            columns = columns,
            glyph = StripGlyph.None,
            nowIndex = nowIndex,
            initialColumn = firstColumn(nowIndex, MORNING_COLUMN),
        ),
        days = days,
        daysNote = stringResource(R.string.metric_days_percent),
    )
}

/** «72–90»; одинаковые края дают одно значение. */
private fun rangeText(low: String, high: String): String = if (low == high) low else "$low–$high"

/** Подпись осадков на сутки вперёд (бывшая карточка «Ближайшие 24 часа» на «Прогнозе»). */
@Composable
private fun outlookText(outlook: PrecipitationOutlook, zone: ZoneId): String = when (outlook) {
    PrecipitationOutlook.None -> stringResource(R.string.next24_none)
    is PrecipitationOutlook.Ongoing -> {
        val kind = kindName(outlook.kind)
        if (outlook.until == null) {
            stringResource(R.string.outlook_all_day, kind)
        } else {
            stringResource(R.string.outlook_until, kind, clockText(outlook.until, zone))
        }
    }
    is PrecipitationOutlook.Starts -> stringResource(R.string.outlook_from, kindName(outlook.kind), clockText(outlook.at, zone))
}

/** Когда идут осадки в выбранный день: «Дождь 02:00–08:00, 18:00–21:00»; без осадков «Осадков не ожидается». */
@Composable
private fun spansText(points: List<HourPoint>, zone: ZoneId): String {
    val spans = MetricDays.precipitationSpans(points)
    val kind = points.firstOrNull { it.precipitationMm > 0.0 }?.let { PrecipitationKind.of(it.condition) }
    if (spans.isEmpty() || kind == null) return stringResource(R.string.next24_none)
    val shown = spans.take(MAX_SPANS).joinToString(", ") { "${clockText(it.start, zone)}–${clockText(it.end, zone)}" }
    return stringResource(R.string.metric_spans, kindName(kind), if (spans.size > MAX_SPANS) "$shown…" else shown)
}

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

/** Карточка «Сейчас» (или выбранного дня) в виде плиток «Подробно»: крупное тонкое число, строки, компас справа. */
@Composable
internal fun NowCard(content: NowContent, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val letters = stringArrayResource(R.array.compass_letters).toList()
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(content.title, style = pogodaText(12.5f, 700, color = palette.ink2), maxLines = 1)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BigValue(content.value, content.unit)
                content.lines.forEach { SubText(it) }
            }
            content.compassFrom?.let { Compass(blowsTowardDegrees = it + 180f, letters = letters) }
        }
        if (content.rows.isNotEmpty()) {
            Column(Modifier.padding(top = 6.dp)) {
                content.rows.forEach { (label, value) ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = pogodaText(13.5f, 500, color = palette.ink2), modifier = Modifier.weight(1f))
                        Text(value, style = pogodaText(13.5f, 600, tabular = true), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
