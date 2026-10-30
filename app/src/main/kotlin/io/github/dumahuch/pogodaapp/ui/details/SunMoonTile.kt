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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.ArcPosition
import io.github.dumahuch.pogodaapp.forecast.arcPosition
import io.github.dumahuch.pogodaapp.forecast.daylight
import io.github.dumahuch.pogodaapp.forecast.sentenceCase
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
import kotlin.math.roundToInt

/**
 * Плитка «Солнце и Луна»: дуга дня с меткой («сейчас»), время восхода, длина дня и время заката, ниже фаза и время
 * восхода и захода луны. Время солнца берётся из прогноза, луна считается на телефоне ([Moon]).
 * Если солнце в эти сутки не встаёт или не садится (полярная ночь и день), дуги нет, а время показывается прочерком.
 */
@Composable
internal fun SunMoonTile(place: Place, forecast: Forecast, now: Instant, fit: ArcFit, modifier: Modifier = Modifier) {
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
            SunArc(position, fit)
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
    ).sortedBy { it.first }.map { it.second }.let { parts ->
        // Строка начинается с заглавной, продолжение после « · » остаётся строчным (единое правило регистра).
        val locale = LocalLocale.current.platformLocale
        parts.mapIndexed { index, part -> if (index == 0) sentenceCase(part, locale) else part }.joinToString(" · ")
    }
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

/** Высота дуги в единицах макета после среза верха: так же высока естественная дуга, и основание лежит на нижнем краю. */
private const val ARC_UNITS = ARC_HEIGHT - ARC_TOP

/**
 * Высота дуги, которую плитка «Подробно» подгоняет под остаток экрана (с 0.4.4). Дуга сообщает сюда свою нынешнюю и
 * естественную (по ширине) высоту, а страница по запасу высоты окна задаёт [forcedPx]: пока он не задан, дуга естественная.
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

/**
 * Форма дуги в единицах макета (320 по ширине, [ARC_UNITS] по высоте): где лежит линия горизонта ([baseY]), куда тянется
 * управляющая точка кривой ([controlY]) и с какого конца идёт путь. Днём дуга над горизонтом, солнце идёт слева направо
 * (от восхода к закату). Ночью горизонт поднят, дуга зеркально под ним, луна идёт справа налево (от заката к восходу):
 * подписи «Восход» слева и «Закат» справа остаются верными в любое время суток.
 */
internal data class SunArcShape(val baseY: Float, val controlY: Float, val startsAtRight: Boolean) {
    /** Самая дальняя от горизонта точка дуги (вершина днём, низ ночью). */
    val apexY: Float get() = (baseY + controlY) / 2f

    companion object {
        const val LEFT_X = 24f
        const val RIGHT_X = 296f

        /** Дневная дуга: основание на нижнем краю, вершина на 48 единиц выше (как до 0.5.1). */
        val Day = SunArcShape(baseY = ARC_UNITS, controlY = -26f - ARC_TOP, startsAtRight = false)

        /** Ночная: горизонт на 14 единиц от верха (место для метки), низ дуги на 38 единиц ниже горизонта. */
        val Night = SunArcShape(baseY = 14f, controlY = 14f + 2f * 38f, startsAtRight = true)

        fun of(daytime: Boolean): SunArcShape = if (daytime) Day else Night
    }
}

/** Цвета меток дуги (RGB): солнце жёлтое с тёмной пиктограммой, луна синяя с белой. Читаемость сверяет `SunMoonShapeTest`. */
internal object SunMoonColors {
    const val SUN_FILL = 0xF2B01E
    const val SUN_ICON = 0x3B2A00
    const val SUN_LINE = 0xE09A0A
    const val MOON_FILL = 0x2F5FB3
    const val MOON_ICON = 0xFFFFFF
    const val MOON_LINE = 0x7FA6EE
}

private fun rgb(value: Int) = Color(0xFF000000.toInt() or value)

/**
 * Дуга пути солнца или луны: пунктир целиком, пройденная часть сплошной линией, метка на текущем месте. По ширине рисунок
 * как в макете, по высоте сжимается, когда страница не помещается на экран ([ArcFit]); метка и толщина линий остаются прежними.
 */
@Composable
private fun SunArc(position: ArcPosition, fit: ArcFit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val density = LocalDensity.current
    val shape = SunArcShape.of(position.daytime)
    val lineColor = rgb(if (position.daytime) SunMoonColors.SUN_LINE else SunMoonColors.MOON_LINE)
    val icon = remember(position.daytime) {
        val source = if (position.daytime) LineIcons.Sun else LineIcons.Moon
        source.paths.map { PathParser().parsePathString(it).toPath() }
    }
    val sizing = if (fit.forcedPx >= 0) {
        Modifier.fillMaxWidth().height(with(density) { fit.forcedPx.toDp() })
    } else {
        Modifier.fillMaxWidth().aspectRatio(ARC_WIDTH / ARC_UNITS)
    }
    Canvas(
        modifier.then(sizing).onSizeChanged {
            fit.current = it.height
            fit.natural = (it.width * ARC_UNITS / ARC_WIDTH).roundToInt()
        },
    ) {
        val sx = size.width / ARC_WIDTH
        val sy = size.height / ARC_UNITS
        val baseY = shape.baseY * sy
        drawLine(palette.ink2.copy(alpha = 0.4f), Offset(8f * sx, baseY), Offset(312f * sx, baseY), strokeWidth = 1.dp.toPx())
        val fromX = (if (shape.startsAtRight) SunArcShape.RIGHT_X else SunArcShape.LEFT_X) * sx
        val toX = (if (shape.startsAtRight) SunArcShape.LEFT_X else SunArcShape.RIGHT_X) * sx
        val arc = Path().apply {
            moveTo(fromX, baseY)
            quadraticTo(160f * sx, shape.controlY * sy, toX, baseY)
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
        drawPath(done, lineColor, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawMarker(measure.getPosition(passed), position.daytime, icon)
    }
}

/** Метка: залитый круг (жёлтый с солнцем днём, синий с месяцем ночью) в светлом ободке, чтобы читалась на любом небе. */
private fun DrawScope.drawMarker(at: Offset, daytime: Boolean, icon: List<Path>) {
    val radius = 10.dp.toPx()
    val fill = rgb(if (daytime) SunMoonColors.SUN_FILL else SunMoonColors.MOON_FILL)
    val ink = rgb(if (daytime) SunMoonColors.SUN_ICON else SunMoonColors.MOON_ICON)
    drawCircle(Color.White.copy(alpha = 0.92f), radius + 1.5.dp.toPx(), at)
    drawCircle(fill, radius, at)
    val side = 14.dp.toPx()
    withTransform({
        translate(at.x - side / 2, at.y - side / 2)
        scale(side / MOON_VIEWPORT, side / MOON_VIEWPORT, pivot = Offset.Zero)
    }) {
        icon.forEach { drawPath(it, ink, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

private const val MOON_VIEWPORT = 24f
