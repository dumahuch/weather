package io.github.dumahuch.pogodaapp.ui.sunmoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.dumahuch.pogodaapp.forecast.CloudLayer
import io.github.dumahuch.pogodaapp.forecast.LightPhase
import io.github.dumahuch.pogodaapp.forecast.Span
import io.github.dumahuch.pogodaapp.forecast.SpanClouds
import io.github.dumahuch.pogodaapp.forecast.SpanCloudsLogic
import io.github.dumahuch.pogodaapp.forecast.SunDay
import io.github.dumahuch.pogodaapp.forecast.SunEdge
import io.github.dumahuch.pogodaapp.forecast.SunLight
import io.github.dumahuch.pogodaapp.forecast.compassPoint
import io.github.dumahuch.pogodaapp.sky.Moon
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.details.SubText
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.forecast.shortDate
import io.github.dumahuch.pogodaapp.ui.metric.CardShape
import io.github.dumahuch.pogodaapp.ui.metric.TileText
import io.github.dumahuch.pogodaapp.ui.metric.TwoTiles
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Forecast
import java.time.Duration
import java.time.ZoneId
import kotlin.math.roundToInt

/** Карточка из стекла с отступами страниц подробностей. */
@Composable
private fun Card(modifier: Modifier = Modifier, padded: Boolean = true, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(CardShape, LocalGlass.current)
            .then(if (padded) Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp) else Modifier),
    ) { content() }
}

@Composable
private fun CardTitle(title: String, note: String? = null, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = pogodaText(12.5f, 700, color = palette.ink2), modifier = Modifier.weight(1f), maxLines = 1)
        if (note != null) Text(note, style = pogodaText(12.5f, 600, color = palette.ink2), maxLines = 1)
    }
}

/**
 * Солнце: сегодня «До заката …» (ночью «До восхода …») и кривая с меткой; в другой день длина дня и кривая без метки.
 * Ниже время концов пути и плитки: длина дня (ночи) с разницей со вчера, полдень и высота солнца, где оно восходит и садится.
 */
@Composable
internal fun SunCard(ctx: SunMoonContext, day: SunDay) {
    val zone = ctx.zone
    val yesterday = ctx.days[ctx.date.minusDays(1)]
    val tomorrow = ctx.days[ctx.date.plusDays(1)]
    val run = if (ctx.isToday && yesterday != null && tomorrow != null) SunLight.run(ctx.now, yesterday, day, tomorrow) else null
    val daytime = run?.daytime ?: true
    val polar = day.sunrise == null || day.sunset == null
    val title = when {
        run != null && !daytime -> stringResource(R.string.sun_moon_night)
        run == null && !polar -> stringResource(R.string.tile_daylight)
        else -> stringResource(R.string.sun_moon_sun)
    }
    val big = when {
        polar -> stringResource(if (day.noonElevation > 0) R.string.sun_moon_polar_day else R.string.sun_moon_polar_night)
        run != null -> stringResource(
            if (daytime) R.string.sun_moon_until_sunset else R.string.sun_moon_until_sunrise,
            durationText(Duration.between(ctx.now, run.to)),
        )
        else -> durationText(day.daylight ?: Duration.ZERO)
    }
    Card(padded = false) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp).semantics(mergeDescendants = true) {}) {
            CardTitle(title)
            Text(big, style = pogodaText(28f, 300, lineHeight = 1.15f, tabular = true), modifier = Modifier.padding(top = 2.dp))
        }
        if (!polar) {
            BellCurve(run?.fraction?.toFloat(), daytime, Modifier.fillMaxWidth().height(CURVE_HEIGHT))
            // Концы кривой: днём восход слева и закат справа, ночью наоборот (ночь идёт от заката к восходу).
            val night = run?.takeIf { !it.daytime }
            val left = night?.from ?: day.sunrise
            val right = night?.to ?: day.sunset
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                EndTime(left?.let { clockText(it, zone) } ?: "–", stringResource(if (daytime) R.string.sunrise else R.string.sunset), Alignment.Start, Modifier.weight(1f))
                EndTime(right?.let { clockText(it, zone) } ?: "–", stringResource(if (daytime) R.string.sunset else R.string.sunrise), Alignment.End, Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            val change = changeNote(day, yesterday, night = !daytime)
            val lengthTile = if (run != null && !daytime) {
                TileText(stringResource(R.string.sun_moon_night), durationText(Duration.between(run.from, run.to)), change)
            } else {
                TileText(stringResource(R.string.tile_daylight), day.daylight?.let { durationText(it) } ?: "–", change)
            }
            TwoTiles(
                lengthTile,
                TileText(
                    stringResource(R.string.sun_moon_noon),
                    clockText(day.noon, zone),
                    stringResource(R.string.sun_moon_sun_height, day.noonElevation.roundToInt()),
                ),
            )
            val sides = stringArrayResource(R.array.compass_in)
            TwoTiles(
                TileText(
                    stringResource(R.string.sun_moon_rises),
                    day.riseAzimuth?.let { "${it.roundToInt()}°" } ?: "–",
                    day.riseAzimuth?.let { sides[compassPoint(it)] },
                ),
                TileText(
                    stringResource(R.string.sun_moon_sets),
                    day.setAzimuth?.let { "${it.roundToInt()}°" } ?: "–",
                    day.setAzimuth?.let { sides[compassPoint(it)] },
                ),
            )
        }
    }
}

@Composable
private fun EndTime(time: String, label: String, alignment: Alignment.Horizontal, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = alignment) {
        Text(time, style = pogodaText(20f, 600, tabular = true), maxLines = 1)
        Text(label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
    }
}

/** «На 2 мин короче, чем вчера» и подобное; ночью наоборот: короче день, значит длиннее ночь. */
@Composable
private fun changeNote(day: SunDay, yesterday: SunDay?, night: Boolean): String? {
    val now = day.daylight ?: return null
    val before = yesterday?.daylight ?: return null
    var minutes = (now.seconds - before.seconds) / 60.0
    if (night) minutes = -minutes
    val rounded = minutes.roundToInt()
    return when {
        rounded > 0 -> stringResource(R.string.sun_moon_longer, rounded)
        rounded < 0 -> stringResource(R.string.sun_moon_shorter, -rounded)
        else -> stringResource(R.string.sun_moon_same)
    }
}

/** Облака в золотой час: среднее по часам, которые его задевают ([SpanCloudsLogic]). */
internal fun goldenClouds(forecast: Forecast, span: Span): SpanClouds? =
    SpanCloudsLogic.during(span, forecast.hours, forecast.hourDetails)

private fun spanText(span: Span?, zone: ZoneId): String =
    span?.let { "${clockText(it.from, zone)}–${clockText(it.to, zone)}" } ?: "–"

/**
 * Свет для съёмки: полоса суток по свету с меткой «сейчас» и легендой, синий и золотой час утром и вечером, облака
 * в золотой час (сколько и какого слоя), определения мелким шрифтом.
 */
@Composable
internal fun LightCard(ctx: SunMoonContext, day: SunDay) {
    val zone = ctx.zone
    val phases = remember(ctx.date, ctx.place, zone) { SunLight.strip(ctx.date, zone, ctx.place.latitude, ctx.place.longitude) }
    val start = ctx.date.atStartOfDay(zone).toInstant()
    val length = Duration.between(start, ctx.date.plusDays(1).atStartOfDay(zone).toInstant()).seconds.toFloat()
    val nowFraction = if (ctx.isToday) Duration.between(start, ctx.now).seconds / length else null
    Card {
        CardTitle(stringResource(R.string.sun_moon_light))
        LightLegend(Modifier.padding(top = 8.dp))
        LightStrip(
            phases,
            nowFraction,
            if (ctx.isToday) clockText(ctx.now, zone) else null,
            Modifier.fillMaxWidth().padding(top = 6.dp).height(56.dp),
        )
        val morningClouds = day.morningGolden?.let { goldenClouds(ctx.forecast, it) }
        val eveningClouds = day.eveningGolden?.let { goldenClouds(ctx.forecast, it) }
        val rows = buildList {
            add(TimeRow(stringResource(R.string.blue_hour), spanText(day.morningBlue, zone), spanText(day.eveningBlue, zone), SunMoonColors.BLUE))
            add(TimeRow(stringResource(R.string.golden_hour), spanText(day.morningGolden, zone), spanText(day.eveningGolden, zone), SunMoonColors.GOLDEN))
            if (morningClouds != null || eveningClouds != null) {
                add(
                    TimeRow(
                        stringResource(R.string.sun_moon_clouds),
                        morningClouds?.let { percentText(it.percent) } ?: "–",
                        eveningClouds?.let { percentText(it.percent) } ?: "–",
                        morningNote = morningClouds?.let { cloudNote(it) },
                        eveningNote = eveningClouds?.let { cloudNote(it) },
                    ),
                )
            }
        }
        TimeTable(rows, Modifier.padding(top = 10.dp))
        FinePrint(stringResource(R.string.sun_moon_light_note))
    }
}

@Composable
private fun cloudNote(clouds: SpanClouds): String? = when {
    clouds.percent < SpanCloudsLogic.CLEAR_BELOW -> stringResource(R.string.cloud_clear)
    else -> when (clouds.layer) {
        CloudLayer.Low -> stringResource(R.string.cloud_layer_low)
        CloudLayer.Mid -> stringResource(R.string.cloud_layer_mid)
        CloudLayer.High -> stringResource(R.string.cloud_layer_high)
        null -> null
    }
}

/** Легенда полосы: образцы цвета дня, золотого и синего часа, сумерек и ночи. */
@Composable
private fun LightLegend(modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val items = listOf(
        LightPhase.Day to R.string.light_day,
        LightPhase.Golden to R.string.light_golden,
        LightPhase.Blue to R.string.light_blue,
        LightPhase.Nautical to R.string.light_twilight,
        LightPhase.Night to R.string.light_night,
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (phase, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.size(9.dp).background(rgb(SunMoonColors.of(phase)), RoundedCornerShape(3.dp)))
                Text(stringResource(text), style = pogodaText(11f, 600, color = palette.ink2), maxLines = 1)
            }
        }
    }
}

@Composable
private fun FinePrint(text: String) {
    val palette = LocalPogodaPalette.current
    Text(text, style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.35f), modifier = Modifier.padding(top = 6.dp))
}

/** Сумерки трёх видов утром и вечером, у каждого пояснение словами; белыми ночами астрономических нет (прочерк). */
@Composable
internal fun TwilightCard(day: SunDay, zone: ZoneId) {
    val rows = listOf(
        Triple(SunEdge.Civil, R.string.twilight_civil, R.string.twilight_civil_note) to SunMoonColors.BLUE,
        Triple(SunEdge.Nautical, R.string.twilight_nautical, R.string.twilight_nautical_note) to SunMoonColors.NAUTICAL,
        Triple(SunEdge.Astronomical, R.string.twilight_astro, R.string.twilight_astro_note) to SunMoonColors.ASTRONOMICAL,
    ).map { (names, color) ->
        TimeRow(
            stringResource(names.second),
            spanText(day.morningTwilight(names.first), zone),
            spanText(day.eveningTwilight(names.first), zone),
            color,
            stringResource(names.third),
        )
    }
    Card {
        CardTitle(stringResource(R.string.twilight_title))
        TimeTable(rows, Modifier.padding(top = 8.dp))
        FinePrint(stringResource(R.string.twilight_note))
    }
}

/**
 * Луна выбранного дня: фаза и освещённость, день цикла, восход и заход со стороной горизонта, самое высокое положение,
 * ближайшее полнолуние и восход луны в тот день, четыре ближайшие главные фазы.
 */
@Composable
internal fun MoonCard(ctx: SunMoonContext) {
    val zone = ctx.zone
    val lat = ctx.place.latitude
    val lon = ctx.place.longitude
    val at = if (ctx.isToday) ctx.now else ctx.date.atTime(12, 0).atZone(zone).toInstant()
    val phase = remember(at) { Moon.phaseAt(at) }
    val events = remember(ctx.date, ctx.place, zone) { Moon.eventsOn(ctx.date, zone, lat, lon) }
    val top = remember(ctx.date, ctx.place, zone) { Moon.transitOn(ctx.date, zone, lat, lon) }
    val quarters = remember(ctx.date, zone) { Moon.nextQuarters(ctx.date.atStartOfDay(zone).toInstant()) }
    val full = quarters.firstOrNull { it.first == Moon.Quarter.Full }?.second
    val fullRise = remember(full, ctx.place) {
        full?.let { Moon.eventsOn(it.atZone(zone).toLocalDate(), zone, lat, lon).rise }
    }
    val north = lat >= 0
    val names = stringArrayResource(R.array.moon_phases)
    val quarterNames = stringArrayResource(R.array.moon_quarters)
    val sides = stringArrayResource(R.array.compass_in)
    val locale = LocalLocale.current.platformLocale
    fun side(time: java.time.Instant?) = time?.let { sides[compassPoint(Moon.positionAt(it, lat, lon).azimuth)] }
    Card {
        CardTitle(stringResource(R.string.moon_title), names[phase.name.ordinal])
        Row(Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            MoonGlyph(phase.illumination, litOnRight = phase.waxing == north, modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(percentText(phase.percent), style = pogodaText(30f, 300, tabular = true))
                SubText(stringResource(R.string.moon_lit, Moon.ageDays(phase) + 1))
            }
        }
        TwoTiles(
            TileText(stringResource(R.string.moonrise), events.rise?.let { clockText(it, zone) } ?: "–", side(events.rise)),
            TileText(stringResource(R.string.moonset), events.set?.let { clockText(it, zone) } ?: "–", side(events.set)),
        )
        TwoTiles(
            TileText(
                stringResource(R.string.moon_highest),
                if (top.elevation > 0) clockText(top.time, zone) else "–",
                if (top.elevation > 0) stringResource(R.string.moon_height, top.elevation.roundToInt()) else stringResource(R.string.moon_below),
            ),
            TileText(
                quarterNames[Moon.Quarter.Full.ordinal],
                full?.let { shortDate(it.atZone(zone).toLocalDate(), locale) } ?: "–",
                fullRise?.let { stringResource(R.string.moon_rise_on, clockText(it, zone)) },
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            quarters.forEach { (quarter, time) ->
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    val lit = when (quarter) {
                        Moon.Quarter.New -> 0.0
                        Moon.Quarter.Full -> 1.0
                        else -> 0.5
                    }
                    MoonGlyph(lit, litOnRight = (quarter != Moon.Quarter.LastQuarter) == north, modifier = Modifier.size(24.dp))
                    Text(shortDate(time.atZone(zone).toLocalDate(), locale), style = pogodaText(13f, 700), modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                    Text(
                        quarterNames[quarter.ordinal],
                        style = pogodaText(10.5f, 500, color = LocalPogodaPalette.current.ink2, lineHeight = 1.2f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private val CURVE_HEIGHT = 138.dp
