package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HourlyGeometry
import io.github.dumahuch.pogodaapp.home.hourLabel
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.components.iconFor
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.ZoneId
import java.util.Locale

private val ColumnWidth = HourlyGeometry.COLUMN_WIDTH.dp
private val StripHeight = HourlyGeometry.HEIGHT.dp

/**
 * Насколько верх подписи температуры выше точки кривой: 22 в макете минус 2,5, потому что Compose ставит
 * базовую линию строки высотой в 1 em ниже, чем браузер (там строка обрезается поровну сверху и снизу).
 */
private const val TEMPERATURE_LABEL_RISE = 24.5f

/** Карточка «По часам»: заголовок и своё содержимое (лента или заглушки). */
@Composable
fun HourlyFrame(caption: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(26.dp), palette)
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.hourly_title),
                style = pogodaText(13f, 700),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                caption,
                style = pogodaText(12f, 400, color = palette.ink2),
                modifier = Modifier.alignByBaseline(),
            )
        }
        content()
    }
}

/** Лента на 48 часов: время, значок, температура на плавной линии, столбики осадков (мм). */
@Composable
fun HourlyStrip(points: List<HourPoint>, zone: ZoneId, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val nowLabel = stringResource(R.string.now)
    val locale = LocalLocale.current.platformLocale
    val rounded = remember(points) { points.map { roundTemperature(it.temperature) } }
    val curve = remember(rounded) { HourlyGeometry.curveY(rounded) }
    val bars = remember(points) { HourlyGeometry.barHeights(points.map { it.precipitationMm }) }

    Box(
        modifier
            .fillMaxWidth()
            .edgeFade(18.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .width(ColumnWidth * points.size)
                .height(StripHeight),
        ) {
            CurveLayer(curve, Modifier.matchParentSize())
            Row(Modifier.matchParentSize()) {
                points.forEachIndexed { index, point ->
                    val local = point.time.atZone(zone)
                    HourColumn(
                        label = if (index == 0) nowLabel else hourLabel(point.time, zone, dayNames),
                        isNow = index == 0,
                        isMidnight = index > 0 && local.hour == 0,
                        point = point,
                        curveY = curve[index],
                        barHeight = bars[index],
                        locale = locale,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurveLayer(curve: List<Float>, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier) {
        if (curve.isEmpty()) return@Canvas
        val unit = density
        val width = ColumnWidth.toPx()
        fun x(index: Int) = index * width + width / 2
        val path = Path()
        path.moveTo(x(0), curve[0] * unit)
        for (i in 1 until curve.size) {
            val middle = (x(i - 1) + x(i)) / 2
            path.cubicTo(middle, curve[i - 1] * unit, middle, curve[i] * unit, x(i), curve[i] * unit)
        }
        drawPath(path, palette.accent, style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round))
        for (i in curve.indices) {
            val center = Offset(x(i), curve[i] * unit)
            drawCircle(palette.ink, radius = 3.2f * unit, center = center)
            drawCircle(palette.accent, radius = 3.2f * unit, center = center, style = Stroke(width = 1.6f * unit))
        }
    }
}

@Composable
private fun HourColumn(
    label: String,
    isNow: Boolean,
    isMidnight: Boolean,
    point: HourPoint,
    curveY: Float,
    barHeight: Float,
    locale: Locale,
) {
    val palette = LocalPogodaPalette.current
    Box(
        Modifier
            .width(ColumnWidth)
            .fillMaxHeight()
            .then(if (isMidnight) Modifier.dayDivider(palette.ink) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        val labelStyle = pogodaText(
            size = 12f,
            weight = if (isMidnight) 700 else 500,
            color = if (isNow || isMidnight) palette.ink else palette.ink2,
            lineHeight = 20f / 12f,
            tabular = true,
        )
        val labelModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 2.dp)
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
        if (isNow) {
            Text(
                label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
                modifier = labelModifier
                    .background(palette.accentSoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp),
            )
        } else {
            Text(label, style = labelStyle, maxLines = 1, softWrap = false, modifier = labelModifier)
        }

        LineIconView(
            icon = iconFor(point.condition, point.isDay),
            size = 24.dp,
            color = palette.ink,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        )

        Text(
            temperatureText(point.temperature),
            style = pogodaText(14f, 500, lineHeight = 1f, tabular = true),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (curveY - TEMPERATURE_LABEL_RISE).dp)
                .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        )

        val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .width(12.dp)
                .height(if (barHeight > 0f) barHeight.dp else 2.dp)
                .background(if (barHeight > 0f) palette.bar else palette.bar.copy(alpha = 0.35f), barShape),
        )

        Text(
            precipitationText(point.precipitationMm, locale),
            style = pogodaText(11.5f, 400, color = palette.ink2, tabular = true),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

/** Пунктир слева от первого часа нового дня. */
private fun Modifier.dayDivider(ink: Color): Modifier = drawBehind {
    drawLine(
        color = ink.copy(alpha = 0.30f),
        start = Offset(0f, 2.dp.toPx()),
        end = Offset(0f, size.height - 6.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

/** Края ленты плавно гаснут, показывая, что её можно листать. */
private fun Modifier.edgeFade(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = minOf(width.toPx(), size.width / 2)
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startX = size.width - fade,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
