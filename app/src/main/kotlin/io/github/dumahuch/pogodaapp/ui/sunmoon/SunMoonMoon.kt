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
import androidx.compose.ui.graphics.Color
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

/* Карточка «Луна» страницы «Солнце и Луна» (версия 3) и данные луны выбранного дня. */

/** Луна выбранного дня: восход, следующий за ним заход (бывает уже завтра), самая высокая точка. */
internal class MoonDay(val rise: Instant?, val set: Instant?, val setTomorrow: Boolean, val top: Moon.Transit)

internal fun moonDay(ctx: SunMoonContext): MoonDay {
    val lat = ctx.place.latitude
    val lon = ctx.place.longitude
    val today = Moon.eventsOn(ctx.date, ctx.zone, lat, lon)
    val rise = today.rise
    val sameDaySet = today.set?.takeIf { rise == null || it > rise }
    val nextSet = if (sameDaySet == null && rise != null) Moon.eventsOn(ctx.date.plusDays(1), ctx.zone, lat, lon).set else null
    return MoonDay(
        rise,
        sameDaySet ?: nextSet ?: today.set,
        setTomorrow = sameDaySet == null && nextSet != null,
        top = Moon.transitOn(ctx.date, ctx.zone, lat, lon),
    )
}

/**
 * Луна: название фазы крупно (Дима: «растущая Луна вообще не замечается»), под ним освещённость и день цикла; плитки:
 * восход и заход со стороной словами, выше всего (словами, без градусов), ближайшее полнолуние; четыре главные фазы.
 */
@Composable
internal fun MoonCard(ctx: SunMoonContext, moon: MoonDay) {
    val palette = LocalPogodaPalette.current
    val zone = ctx.zone
    val lat = ctx.place.latitude
    val lon = ctx.place.longitude
    val at = if (ctx.isToday) ctx.now else ctx.date.atTime(12, 0).atZone(zone).toInstant()
    val phase = remember(at) { Moon.phaseAt(at) }
    val quarters = remember(ctx.date, zone) { Moon.nextQuarters(ctx.date.atStartOfDay(zone).toInstant()) }
    val fullDate = quarters.firstOrNull { it.first == Moon.Quarter.Full }?.second?.atZone(zone)?.toLocalDate()
    val fullRise = remember(fullDate, ctx.place) { fullDate?.let { Moon.eventsOn(it, zone, lat, lon).rise } }
    val north = lat >= 0
    val names = stringArrayResource(R.array.moon_phases)
    val quarterNames = stringArrayResource(R.array.moon_quarters)
    val sides = stringArrayResource(R.array.compass_in)
    val locale = LocalLocale.current.platformLocale
    Card {
        CardTitle(stringResource(R.string.moon_title))
        Row(Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            MoonGlyph(phase.illumination, litOnRight = phase.waxing == north, modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(names[phase.name.ordinal], style = pogodaText(22f, 600, lineHeight = 1.15f))
                val rest = stringResource(R.string.moon_lit_line, Moon.ageDays(phase) + 1)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.ink, fontWeight = FontWeight.Bold)) { append(percentText(phase.percent)) }
                        append(" ")
                        append(rest)
                    },
                    style = pogodaText(13.5f, 500, color = palette.ink2, lineHeight = 1.35f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        val riseSide = moon.rise?.let { sideOf(Moon.positionAt(it, lat, lon).azimuth, sides) }
        val setSide = moon.set?.let { sideOf(Moon.positionAt(it, lat, lon).azimuth, sides) }
        TwoTiles(
            TileText(stringResource(R.string.moonrise), moon.rise?.let { clockText(it, zone) } ?: "–", riseSide),
            TileText(
                stringResource(R.string.moonset),
                moon.set?.let { clockText(it, zone) } ?: "–",
                if (moon.setTomorrow && setSide != null) stringResource(R.string.moon_tomorrow_side, continuationCase(setSide, locale)) else setSide,
            ),
        )
        TwoTiles(
            TileText(
                stringResource(R.string.moon_highest),
                if (moon.top.elevation > 0) clockText(moon.top.time, zone) else "–",
                stringResource(heightWord(moon.top.elevation)),
            ),
            TileText(
                quarterNames[Moon.Quarter.Full.ordinal],
                fullDate?.let { shortDate(it, locale) } ?: "–",
                fullNote(ctx.date, fullDate, fullRise, zone),
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
                        style = pogodaText(10.5f, 500, color = palette.ink2, lineHeight = 1.2f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** Высота луны словами: от 45° высоко, от 20° невысоко, выше горизонта низко, иначе под горизонтом весь день. */
private fun heightWord(elevation: Double): Int = when {
    elevation >= 45 -> R.string.moon_high
    elevation >= 20 -> R.string.moon_mid
    elevation > 0 -> R.string.moon_low
    else -> R.string.moon_below
}

/** «Через 3 дня, восход в 15:30» или, если полнолуние в выбранный день, «Восход Луны 15:30». */
@Composable
private fun fullNote(date: LocalDate, fullDate: LocalDate?, rise: Instant?, zone: ZoneId): String? {
    if (fullDate == null || rise == null) return null
    val days = ChronoUnit.DAYS.between(date, fullDate).toInt()
    val time = clockText(rise, zone)
    return if (days <= 0) stringResource(R.string.moon_rise_on, time) else pluralStringResource(R.plurals.full_moon_in, days, days, time)
}
