package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.ArcPosition
import io.github.dumahuch.pogodaapp.forecast.arcPosition
import io.github.dumahuch.pogodaapp.forecast.daylight
import io.github.dumahuch.pogodaapp.sky.Moon
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.hoursAndMinutes
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Instant

/**
 * Плитка «Солнце и Луна»: дуга дня с меткой («сейчас»), время восхода, длина дня и время заката, ниже фаза и время
 * восхода и захода луны. Время солнца берётся из прогноза, луна считается на телефоне ([Moon]).
 * Если солнце в эти сутки не встаёт или не садится (полярная ночь и день), дуги нет, а время показывается прочерком.
 */
@Composable
internal fun SunMoonTile(place: Place, forecast: Forecast, now: Instant, modifier: Modifier = Modifier) {
    val zone = forecast.zone
    val today = remember(forecast, now) {
        val date = now.atZone(zone).toLocalDate()
        forecast.days.firstOrNull { it.date == date }
    }
    val tomorrow = remember(forecast, today) { today?.let { day -> forecast.days.firstOrNull { it.date == day.date.plusDays(1) } } }
    val position = remember(today, tomorrow, now) { today?.let { arcPosition(now, it, tomorrow) } }
    val dash = "–"
    DetailTile(stringResource(R.string.tile_sun_moon), modifier) {
        if (position != null) {
            SunArc(position, Modifier.fillMaxWidth().aspectRatio(ARC_WIDTH / ARC_HEIGHT))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeColumn(
                stringResource(R.string.sunrise),
                today?.sunrise?.let { clockText(it, zone) } ?: dash,
                Alignment.Start,
            )
            TimeColumn(
                stringResource(R.string.tile_daylight),
                today?.let(::daylight)?.let {
                    val (hours, minutes) = hoursAndMinutes(it)
                    stringResource(R.string.hours_minutes, hours, minutes)
                } ?: dash,
                Alignment.CenterHorizontally,
            )
            TimeColumn(
                stringResource(R.string.sunset),
                today?.sunset?.let { clockText(it, zone) } ?: dash,
                Alignment.End,
            )
        }
        Spacer(Modifier.height(6.dp))
        MoonRow(place, forecast, now)
    }
}

@Composable
private fun TimeColumn(label: String, value: String, alignment: Alignment.Horizontal) {
    val palette = LocalPogodaPalette.current
    Column(horizontalAlignment = alignment) {
        Text(label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
        Text(value, style = pogodaText(17f, 500, lineHeight = 1.3f, tabular = true), maxLines = 1)
    }
}

/** Фаза луны и время её восхода и захода в хронологическом порядке. */
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
    ).sortedBy { it.first }.joinToString(" · ") { it.second }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LineIconView(LineIcons.Moon, 20.dp, palette.ink)
        Column {
            Text(stringResource(R.string.moon_phase_percent, phaseName, percent), style = pogodaText(13.5f, 600, lineHeight = 1.3f))
            if (times.isNotEmpty()) {
                Text(times, style = pogodaText(12.5f, 500, color = palette.ink2, lineHeight = 1.35f))
            }
        }
    }
}

private const val ARC_WIDTH = 320f
private const val ARC_HEIGHT = 70f

/** Сколько единиц макета сверху срезано: до верха метки на вершине дуги остаётся запас. */
private const val ARC_TOP = 8f

/** Дуга пути солнца: пунктир целиком, пройденная часть сплошной линией, метка на текущем месте. */
@Composable
private fun SunArc(position: ArcPosition, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val color = if (position.daytime) palette.warning else palette.accent
    val moon = remember { PathParser().parsePathString(LineIcons.Moon.paths[0]).toPath() }
    Canvas(modifier) {
        val scale = size.width / ARC_WIDTH
        // Верхние ARC_TOP единиц макета пусты (дуга ниже), их срезаем: плитка ниже, все плитки помещаются на экран.
        translate(top = -ARC_TOP * scale) {
            val baseY = 70f * scale
            drawLine(palette.ink2.copy(alpha = 0.4f), Offset(8f * scale, baseY), Offset(312f * scale, baseY), strokeWidth = 1.dp.toPx())
            val arc = Path().apply {
                moveTo(24f * scale, baseY)
                quadraticTo(160f * scale, -26f * scale, 296f * scale, baseY)
            }
            drawPath(
                arc,
                palette.ink2.copy(alpha = 0.7f),
                style = Stroke(width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
            )
            val measure = PathMeasure().apply { setPath(arc, false) }
            val passed = measure.length * position.fraction.toFloat()
            val done = Path()
            measure.getSegment(0f, passed, done, true)
            drawPath(done, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val at = measure.getPosition(passed)
            drawMarker(at, color, position.daytime, moon)
        }
    }
}

private fun DrawScope.drawMarker(at: Offset, color: Color, daytime: Boolean, moon: Path) {
    val radius = 9.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.92f), radius, at)
    drawCircle(color, radius, at, style = Stroke(width = 1.5.dp.toPx()))
    if (daytime) {
        drawCircle(color, 3.2.dp.toPx(), at)
    } else {
        val side = 12.dp.toPx()
        withTransform({
            translate(at.x - side / 2, at.y - side / 2)
            scale(side / MOON_VIEWPORT, side / MOON_VIEWPORT, pivot = Offset.Zero)
        }) {
            drawPath(moon, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private const val MOON_VIEWPORT = 24f
