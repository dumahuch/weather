package io.github.dumahuch.pogodaapp.ui.sunmoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.sky.Moon
import io.github.dumahuch.pogodaapp.sky.SunPosition
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.forecast.shortDate
import io.github.dumahuch.pogodaapp.ui.metric.CardShape
import io.github.dumahuch.pogodaapp.ui.metric.TileText
import io.github.dumahuch.pogodaapp.ui.metric.TwoTiles
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Forecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.tan

/*
 * Карточки страницы «Солнце и Луна», версия 3 (разбор Димы 21.11.2026: тексты не сливаются в одну кучу, понятные данные
 * вместо градусов, золотой и синий одним обозначением везде). Три уровня текста: крупные значения цветом текста,
 * подписи жирнее, пояснения бледные и мельче; золотой и синий час подписаны своим цветом.
 */

/** Карточка из стекла с отступами страниц подробностей. */
@Composable
internal fun Card(modifier: Modifier = Modifier, padded: Boolean = true, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(CardShape, LocalGlass.current)
            .then(if (padded) Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp) else Modifier),
    ) { content() }
}

@Composable
internal fun CardTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = pogodaText(12.5f, 700, color = LocalPogodaPalette.current.ink2), modifier = modifier, maxLines = 1)
}

@Composable
private fun FinePrint(text: String) {
    Text(text, style = pogodaText(11f, 500, color = LocalPogodaPalette.current.ink2, lineHeight = 1.35f), modifier = Modifier.padding(top = 10.dp))
}

/**
 * Солнце: сегодня «До заката …» (ночью «До восхода …») и кривая с меткой; в другой день длина дня и кривая без метки.
 * Подписи концов над временем. Плитки: длина дня (ночи) с разницей со вчера; днём полдень и длина теней, ночью когда
 * совсем темно (астрономические сумерки кончились).
 */
@Composable
internal fun SunCard(ctx: SunMoonContext, day: SunDay) {
    val zone = ctx.zone
    val yesterday = ctx.days[ctx.date.minusDays(1)]
    val tomorrow = ctx.days[ctx.date.plusDays(1)]
    val run = if (ctx.isToday && yesterday != null && tomorrow != null) SunLight.run(ctx.now, yesterday, day, tomorrow) else null
    val night = run?.takeIf { !it.daytime }
    val polar = day.sunrise == null || day.sunset == null
    val title = when {
        night != null -> stringResource(R.string.sun_moon_night)
        run == null && !polar -> stringResource(R.string.tile_daylight)
        else -> stringResource(R.string.sun_moon_sun)
    }
    val big = when {
        polar -> stringResource(if (day.noonElevation > 0) R.string.sun_moon_polar_day else R.string.sun_moon_polar_night)
        run != null -> stringResource(
            if (run.daytime) R.string.sun_moon_until_sunset else R.string.sun_moon_until_sunrise,
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
            BellCurve(run?.fraction?.toFloat(), night == null, Modifier.fillMaxWidth().height(CURVE_HEIGHT))
            // Концы кривой: днём восход слева и закат справа, ночью наоборот (ночь идёт от заката к восходу).
            val left = night?.from ?: day.sunrise
            val right = night?.to ?: day.sunset
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                EndTime(stringResource(if (night == null) R.string.sunrise else R.string.sunset), left?.let { clockText(it, zone) } ?: "–", Alignment.Start, Modifier.weight(1f))
                EndTime(stringResource(if (night == null) R.string.sunset else R.string.sunrise), right?.let { clockText(it, zone) } ?: "–", Alignment.End, Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            val change = changeNote(day, yesterday, night = night != null)
            if (night != null) {
                val evening = ctx.days[night.from.atZone(zone).toLocalDate()]
                val morning = ctx.days[night.to.atZone(zone).toLocalDate()]
                val dusk = evening?.setting?.get(SunEdge.Astronomical)
                val dawn = morning?.rising?.get(SunEdge.Astronomical)
                TwoTiles(
                    TileText(stringResource(R.string.sun_moon_night), durationText(Duration.between(night.from, night.to)), change),
                    TileText(
                        stringResource(R.string.sun_moon_dark),
                        if (dusk != null && dawn != null) "${clockText(dusk, zone)}–${clockText(dawn, zone)}" else "–",
                        if (dusk != null && dawn != null) stringResource(R.string.sun_moon_dark_note) else null,
                    ),
                )
            } else {
                TwoTiles(
                    TileText(stringResource(R.string.tile_daylight), day.daylight?.let { durationText(it) } ?: "–", change),
                    TileText(stringResource(R.string.sun_moon_noon), clockText(day.noon, zone), shadowNote(day.noonElevation)),
                )
            }
        }
    }
}

@Composable
private fun EndTime(label: String, time: String, alignment: Alignment.Horizontal, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = alignment) {
        Text(label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
        Text(time, style = pogodaText(20f, 600, tabular = true), maxLines = 1)
    }
}

/** «Тени в 3,1 раза длиннее предметов» по высоте полуденного солнца; когда солнце не выше 1°, пусто. */
@Composable
private fun shadowNote(elevation: Double): String? {
    if (elevation < 1.0) return null
    val ratio = 1.0 / tan(Math.toRadians(elevation))
    val locale = LocalLocale.current.platformLocale
    val text = if (ratio >= 10) ratio.roundToInt().toString() else String.format(locale, "%.1f", ratio)
    return stringResource(R.string.shadow_ratio, text)
}

/** «+3 мин за сутки» (короткая строка, помещается в плитку); ночью наоборот: короче день, значит длиннее ночь. */
@Composable
private fun changeNote(day: SunDay, yesterday: SunDay?, night: Boolean): String? {
    val now = day.daylight ?: return null
    val before = yesterday?.daylight ?: return null
    var minutes = (now.seconds - before.seconds) / 60.0
    if (night) minutes = -minutes
    val rounded = minutes.roundToInt()
    return when {
        rounded > 0 -> stringResource(R.string.sun_moon_change, "+$rounded")
        rounded < 0 -> stringResource(R.string.sun_moon_change, "−${-rounded}")
        else -> stringResource(R.string.sun_moon_same)
    }
}

/** Облака в промежутке: среднее по часам, которые его задевают ([SpanCloudsLogic]). */
internal fun spanClouds(forecast: Forecast, span: Span): SpanClouds? =
    SpanCloudsLogic.during(span, forecast.hours, forecast.hourDetails)

/** Событие ленты света: цвет фазы, название, пояснение и промежуток. */
private class LightStep(val phase: LightPhase, val name: String, val note: String?, val span: Span)

/**
 * Свет для съёмки: полоса суток (золотой и синий светятся), переключатель «Утро / Вечер», облака в золотой час
 * плашкой, ниже события по порядку, как идёт свет (метка цвета, название, пояснение, время и длительность).
 */
@Composable
internal fun LightCard(ctx: SunMoonContext, day: SunDay) {
    val palette = LocalPogodaPalette.current
    val zone = ctx.zone
    val phases = remember(ctx.date, ctx.place, zone) { SunLight.strip(ctx.date, zone, ctx.place.latitude, ctx.place.longitude) }
    val start = ctx.date.atStartOfDay(zone).toInstant()
    val length = Duration.between(start, ctx.date.plusDays(1).atStartOfDay(zone).toInstant()).seconds.toFloat()
    val nowFraction = if (ctx.isToday) Duration.between(start, ctx.now).seconds / length else null
    // Сначала то время суток, которое впереди: утро, пока утренний золотой час не кончился.
    val morningAhead = ctx.isToday && day.rising[SunEdge.Golden]?.let { ctx.now < it } == true
    var chosenMorning by rememberSaveable(ctx.date) { mutableStateOf<Boolean?>(null) }
    val morning = chosenMorning ?: morningAhead
    val steps = buildList {
        if (morning) {
            day.morningTwilight(SunEdge.Astronomical)?.let { add(LightStep(LightPhase.Astronomical, stringResource(R.string.tl_astro), stringResource(R.string.tl_astro_morning), it)) }
            day.morningTwilight(SunEdge.Nautical)?.let { add(LightStep(LightPhase.Nautical, stringResource(R.string.tl_nautical), stringResource(R.string.tl_nautical_morning), it)) }
            day.morningBlue?.let { add(LightStep(LightPhase.Blue, stringResource(R.string.blue_hour), stringResource(R.string.tl_blue_note), it)) }
            day.morningGolden?.let { span ->
                add(LightStep(LightPhase.Golden, stringResource(R.string.golden_hour), day.sunrise?.let { stringResource(R.string.shoot_sunrise, clockText(it, zone)) }, span))
            }
        } else {
            day.eveningGolden?.let { span ->
                add(LightStep(LightPhase.Golden, stringResource(R.string.golden_hour), day.sunset?.let { stringResource(R.string.shoot_sunset, clockText(it, zone)) }, span))
            }
            day.eveningBlue?.let { add(LightStep(LightPhase.Blue, stringResource(R.string.blue_hour), stringResource(R.string.tl_blue_note), it)) }
            day.eveningTwilight(SunEdge.Nautical)?.let { add(LightStep(LightPhase.Nautical, stringResource(R.string.tl_nautical), stringResource(R.string.tl_nautical_evening), it)) }
            day.eveningTwilight(SunEdge.Astronomical)?.let { add(LightStep(LightPhase.Astronomical, stringResource(R.string.tl_astro), stringResource(R.string.tl_night_after), it)) }
        }
    }
    val golden = if (morning) day.morningGolden else day.eveningGolden
    val clouds = golden?.let { spanClouds(ctx.forecast, it) }
    Card {
        CardTitle(stringResource(R.string.sun_moon_light))
        LightLegend(Modifier.padding(top = 8.dp))
        LightStrip(
            phases,
            nowFraction,
            if (ctx.isToday) clockText(ctx.now, zone) else null,
            Modifier.fillMaxWidth().padding(top = 6.dp).height(58.dp),
        )
        Segmented(
            stringResource(R.string.sun_moon_morning),
            stringResource(R.string.sun_moon_evening),
            firstSelected = morning,
            onSelect = { chosenMorning = it },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (clouds != null) {
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(palette.ink.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(stringResource(R.string.light_cloud_plaque, percentText(clouds.percent)), style = pogodaText(14f, 700))
                cloudNote(clouds)?.let { Text(it, style = pogodaText(12f, 500, color = palette.ink2)) }
            }
        }
        Column(Modifier.padding(top = 6.dp)) {
            steps.forEachIndexed { index, step ->
                if (index > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
                StepRow(step, zone)
            }
        }
        FinePrint(stringResource(R.string.sun_moon_light_note))
    }
}

@Composable
private fun StepRow(step: LightStep, zone: ZoneId) {
    val palette = LocalPogodaPalette.current
    val nameColor = when (step.phase) {
        LightPhase.Golden -> goldInk()
        LightPhase.Blue -> blueInk()
        else -> Color.Unspecified
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 9.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhaseSwatch(step.phase, Modifier.width(5.dp).fillMaxHeight())
        Column(Modifier.weight(1f)) {
            Text(step.name, style = pogodaText(15f, 600, color = nameColor), maxLines = 1)
            step.note?.let { Text(it, style = pogodaText(12f, 500, color = palette.ink2), maxLines = 1) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${clockText(step.span.from, zone)}–${clockText(step.span.to, zone)}", style = pogodaText(15f, 600, tabular = true), maxLines = 1)
            Text(durationText(Duration.between(step.span.from, step.span.to)), style = pogodaText(12f, 500, color = palette.ink2), maxLines = 1)
        }
    }
}

/** Переключатель из двух частей: выбранная светлее. */
@Composable
private fun Segmented(first: String, second: String, firstSelected: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(first to true, second to false).forEach { (label, isFirst) ->
            val on = isFirst == firstSelected
            Text(
                label,
                style = pogodaText(14f, 700, color = if (on) palette.ink else palette.ink2),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) palette.ink.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(11.dp))
                    .clickable(role = Role.Tab) { onSelect(isFirst) }
                    .semantics { selected = on }
                    .padding(vertical = 7.dp),
            )
        }
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

/** Легенда полосы: образцы как в ленте событий (золотой и синий светятся). */
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PhaseSwatch(phase, Modifier.size(9.dp))
                Text(stringResource(text), style = pogodaText(11f, 600, color = palette.ink2), maxLines = 1)
            }
        }
    }
}

/** Сторона горизонта словами: «На юго-востоке». */
internal fun sideOf(azimuth: Double?, sides: Array<String>): String? = azimuth?.let { sides[compassPoint(it)] }

/**
 * Куда смотреть: круг сторон горизонта (путь солнца и луны, точки восхода и захода) и рядом список: восход, закат,
 * восход и заход луны со временем и стороной словами. Вместо градусов (Дима 21.11: «не соображу, как пользоваться»).
 */
@Composable
internal fun DirectionCard(ctx: SunMoonContext, day: SunDay, moon: MoonDay) {
    val zone = ctx.zone
    val lat = ctx.place.latitude
    val lon = ctx.place.longitude
    val sides = stringArrayResource(R.array.compass_in)
    val sunPath = remember(day, ctx.place) {
        day.riseAzimuth?.let { SkyPath(it, day.setAzimuth, SunPosition.horizontal(lat, lon, day.noon).azimuth) }
    }
    val moonPath = remember(moon, ctx.place) {
        if (moon.rise != null && moon.set != null) {
            SkyPath(
                Moon.positionAt(moon.rise, lat, lon).azimuth,
                Moon.positionAt(moon.set, lat, lon).azimuth,
                Moon.positionAt(moon.top.time, lat, lon).azimuth,
            )
        } else {
            null
        }
    }
    val sunColor = rgb(SunMoonColors.SUN)
    val moonColor = rgb(SunMoonColors.MOON_LINE)
    val moonRiseSide = moon.rise?.let { sideOf(Moon.positionAt(it, lat, lon).azimuth, sides) }
    val moonSetSide = moon.set?.let { sideOf(Moon.positionAt(it, lat, lon).azimuth, sides) }
    Card {
        CardTitle(stringResource(R.string.dir_title))
        PathLegend(sunColor, moonColor, Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SkyCompass(sunPath, moonPath, Modifier.size(136.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DirectionRow(sunColor, true, day.sunrise?.let { stringResource(R.string.shoot_sunrise, clockText(it, zone)) }, sideOf(day.riseAzimuth, sides))
                DirectionRow(sunColor, false, day.sunset?.let { stringResource(R.string.shoot_sunset, clockText(it, zone)) }, sideOf(day.setAzimuth, sides))
                DirectionRow(moonColor, true, moon.rise?.let { stringResource(R.string.moon_rise_on, clockText(it, zone)) }, moonRiseSide)
                DirectionRow(moonColor, false, moon.set?.let { stringResource(R.string.moon_set_on, clockText(it, zone)) }, moonSetSide)
            }
        }
    }
}

/** Легенда круга, как у карт: образец линии и подпись (жёлтая линия путь Солнца, голубой пунктир путь Луны). */
@Composable
private fun PathLegend(sun: Color, moon: Color, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(true to R.string.dir_legend_sun, false to R.string.dir_legend_moon).forEach { (isSun, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
                    val y = size.height / 2
                    if (isSun) {
                        drawLine(sun, Offset(0f, y), Offset(size.width, y), 2.5.dp.toPx(), StrokeCap.Round)
                    } else {
                        drawLine(
                            moon,
                            Offset(1.dp.toPx(), y),
                            Offset(size.width, y),
                            1.8.dp.toPx(),
                            StrokeCap.Round,
                            PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
                        )
                    }
                }
                Text(stringResource(text), style = pogodaText(11f, 600, color = palette.ink2), maxLines = 1)
            }
        }
    }
}

@Composable
private fun DirectionRow(color: Color, filled: Boolean, title: String?, side: String?) {
    if (title == null) return
    val palette = LocalPogodaPalette.current
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Canvas(Modifier.padding(top = 4.dp).size(10.dp)) {
            if (filled) {
                drawCircle(color)
            } else {
                drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        Column {
            Text(title, style = pogodaText(14f, 700, tabular = true), maxLines = 1)
            side?.let { Text(it, style = pogodaText(12f, 500, color = palette.ink2), maxLines = 1) }
        }
    }
}

private val CURVE_HEIGHT = 138.dp
