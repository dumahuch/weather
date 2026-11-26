package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.ShootingEvent
import io.github.dumahuch.pogodaapp.forecast.SunLight
import io.github.dumahuch.pogodaapp.forecast.sentenceCase
import io.github.dumahuch.pogodaapp.sky.Moon
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.BellCurve
import io.github.dumahuch.pogodaapp.ui.sunmoon.MoonGlyph
import io.github.dumahuch.pogodaapp.ui.sunmoon.SunMoonColors
import io.github.dumahuch.pogodaapp.ui.sunmoon.durationText
import io.github.dumahuch.pogodaapp.ui.sunmoon.rgb
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Плитка «Солнце и Луна» на «Подробно» (0.8.77, макет https://claude.ai/artifact/1nRbyKdjC7NTVMDYS1g1B3): справа вверху
 * ближайшее событие для съёмки (синий час, золотой час, восход, закат), «До заката …» (ночью «До восхода …»), кривая пути
 * в общем стиле графиков, восход, длина дня и закат, строка луны. Нажатие открывает страницу «Солнце и Луна».
 * Солнце и луна считаются на телефоне ([SunLight], [Moon]). В полярный день и полярную ночь кривой нет.
 */
@Composable
internal fun SunMoonTile(place: Place, forecast: Forecast, now: Instant, fit: ArcFit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val zone = forecast.zone
    val date = remember(now, zone) { now.atZone(zone).toLocalDate() }
    val days = remember(place, zone, date) {
        (-1L..1L).map { SunLight.day(date.plusDays(it), zone, place.latitude, place.longitude) }
    }
    val (yesterday, today, tomorrow) = days
    val run = remember(days, now) { SunLight.run(now, yesterday, today, tomorrow) }
    val next = remember(days, now) { SunLight.nextShootingEvent(now, today) ?: SunLight.nextShootingEvent(now, tomorrow) }
    val dash = "–"
    Column(
        modifier
            .glassCard(RoundedCornerShape(22.dp), LocalGlass.current)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sun_moon_open), onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tile_sun_moon), style = pogodaText(12.5f, 700, color = palette.ink2), modifier = Modifier.weight(1f), maxLines = 1)
            if (next != null) {
                val sun = rgb(SunMoonColors.SUN)
                Text(
                    shootingText(next.first, clockText(next.second, zone)),
                    style = pogodaText(11.5f, 700),
                    maxLines = 1,
                    modifier = Modifier
                        .background(sun.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(1.dp, sun.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
        val big = when {
            run == null -> stringResource(if (today.noonElevation > 0) R.string.sun_moon_polar_day else R.string.sun_moon_polar_night)
            run.daytime -> stringResource(R.string.sun_moon_until_sunset, durationText(Duration.between(now, run.to)))
            else -> stringResource(R.string.sun_moon_until_sunrise, durationText(Duration.between(now, run.to)))
        }
        Text(big, style = pogodaText(22f, 300, lineHeight = 1.15f, tabular = true), maxLines = 1)
        if (run != null) {
            val density = LocalDensity.current
            val sizing = if (fit.forcedPx >= 0) {
                Modifier.fillMaxWidth().height(with(density) { fit.forcedPx.toDp() })
            } else {
                Modifier.fillMaxWidth().aspectRatio(ARC_ASPECT)
            }
            BellCurve(
                run.fraction.toFloat(),
                run.daytime,
                sizing.onSizeChanged {
                    fit.current = it.height
                    fit.natural = (it.width / ARC_ASPECT).roundToInt()
                },
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeColumn(stringResource(R.string.sunrise), today.sunrise?.let { clockText(it, zone) } ?: dash, Alignment.Start)
            TimeColumn(stringResource(R.string.tile_daylight), today.daylight?.let { durationText(it) } ?: dash, Alignment.CenterHorizontally)
            TimeColumn(stringResource(R.string.sunset), today.sunset?.let { clockText(it, zone) } ?: dash, Alignment.End)
        }
        Spacer(Modifier.height(4.dp))
        MoonRow(place, forecast, now)
    }
}

@Composable
private fun shootingText(event: ShootingEvent, time: String): String = stringResource(
    when (event) {
        ShootingEvent.BlueHour -> R.string.shoot_blue
        ShootingEvent.GoldenHour -> R.string.shoot_golden
        ShootingEvent.Sunrise -> R.string.shoot_sunrise
        ShootingEvent.Sunset -> R.string.shoot_sunset
    },
    time,
)

@Composable
private fun TimeColumn(label: String, value: String, alignment: Alignment.Horizontal) {
    val palette = LocalPogodaPalette.current
    Column(horizontalAlignment = alignment) {
        Text(label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
        Text(value, style = pogodaText(17f, 500, lineHeight = 1.3f, tabular = true), maxLines = 1)
    }
}

/** Фаза луны рисунком, освещённость и время восхода и захода в хронологическом порядке, стрелка «открыть». */
@Composable
private fun MoonRow(place: Place, forecast: Forecast, now: Instant) {
    val zone = forecast.zone
    val palette = LocalPogodaPalette.current
    val phase = remember(now) { Moon.phaseAt(now) }
    val events = remember(forecast, place, now) {
        Moon.eventsOn(now.atZone(zone).toLocalDate(), zone, place.latitude, place.longitude)
    }
    val phaseName = stringArrayResource(R.array.moon_phases)[phase.name.ordinal]
    val percent = percentText(phase.percent)
    val times = listOfNotNull(
        events.rise?.let { it to stringResource(R.string.moonrise_at, clockText(it, zone)) },
        events.set?.let { it to stringResource(R.string.moonset_at, clockText(it, zone)) },
    ).sortedBy { it.first }.map { it.second }.let { parts ->
        // Строка начинается с заглавной, продолжение после « · » остаётся строчным (единое правило регистра).
        val locale = LocalLocale.current.platformLocale
        parts.mapIndexed { index, part -> if (index == 0) sentenceCase(part, locale) else part }.joinToString(" · ")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoonGlyph(phase.illumination, litOnRight = phase.waxing == (place.latitude >= 0), modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.moon_phase_percent, phaseName, percent), style = pogodaText(13.5f, 600, lineHeight = 1.3f))
            if (times.isNotEmpty()) {
                Text(times, style = pogodaText(12.5f, 500, color = palette.ink2, lineHeight = 1.35f))
            }
        }
    }
}

/** Пропорции кривой на плитке (ширина к высоте) до подгонки под экран. */
private const val ARC_ASPECT = 320f / 84f

/**
 * Высота кривой, которую плитка «Подробно» подгоняет под остаток экрана (с 0.4.4). Кривая сообщает сюда свою нынешнюю и
 * естественную (по ширине) высоту, а страница по запасу высоты окна задаёт [forcedPx]: пока он не задан, кривая естественная.
 */
@Stable
internal class ArcFit {
    var natural by mutableIntStateOf(0)
    var current by mutableIntStateOf(0)
    var forcedPx by mutableIntStateOf(NOT_FORCED)

    companion object {
        const val NOT_FORCED = -1
    }
}
