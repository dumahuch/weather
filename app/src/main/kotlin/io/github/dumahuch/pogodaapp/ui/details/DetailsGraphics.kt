package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.forecast.PressurePoint
import io.github.dumahuch.pogodaapp.forecast.monotoneTangents
import io.github.dumahuch.pogodaapp.forecast.pressureAxis
import io.github.dumahuch.pogodaapp.forecast.smoothSeries
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Цвета шкалы качества воздуха: пять ступеней, от хорошего к плохому (как в макете). */
internal val AirScale = listOf(
    Color(0xFF4F9A8C),
    Color(0xFF8EA35A),
    Color(0xFFB9A44E),
    Color(0xFFC27A4A),
    Color(0xFFA8483F),
)

/** Цвета шкалы УФ-индекса: плавный переход от зелёного к красному (как в макете). */
internal val UvScale = listOf(
    Color(0xFF4F9A8C),
    Color(0xFFB9A44E),
    Color(0xFFC27A4A),
    Color(0xFFA8483F),
)

/**
 * Полоска со шкалой и меткой. [fraction] от 0 до 1. Со [stepped] полоска из отдельных ступеней (воздух),
 * без него сплошной переход цвета (УФ). Значение всегда дублируется текстом, полоска только иллюстрирует.
 */
@Composable
internal fun ScaleBar(fraction: Float, colors: List<Color>, stepped: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        if (stepped) {
            val gap = 2.dp.toPx()
            val segment = (size.width - gap * (colors.size - 1)) / colors.size
            colors.forEachIndexed { index, color ->
                drawRoundRect(color, Offset(index * (segment + gap), top), Size(segment, barHeight), radius)
            }
        } else {
            drawRoundRect(Brush.horizontalGradient(colors), Offset(0f, top), Size(size.width, barHeight), radius)
        }
        val markerRadius = 4.5.dp.toPx()
        val x = markerRadius + fraction * (size.width - 2 * markerRadius)
        val center = Offset(x, size.height / 2)
        drawCircle(Color.White, markerRadius, center)
        drawCircle(palette.ink, markerRadius, center, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * Компас ветра как в макете (с 0.4.4): тонкое кольцо с разрывами, на четырёх сторонах света буквы [letters] (север, восток,
 * юг, запад; буквы зависят от языка интерфейса) и длинная стрелка цветом акцента с открытым наконечником. Стрелка смотрит туда,
 * куда ветер дует ([blowsTowardDegrees], 0 это на север, по часовой стрелке). Рисунок в долях стороны (в макете 74 единицы,
 * кольцо радиусом 29 в середине), поэтому не зависит от размера; размер букв не следует за размером шрифта системы.
 */
@Composable
internal fun Compass(blowsTowardDegrees: Float, letters: List<String>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val letterStyle = remember(palette.ink2) { pogodaText(10f, 500, color = palette.ink2, lineHeight = 1f) }
    Canvas(modifier.size(COMPASS_SIZE)) {
        val unit = size.minDimension / 74f
        val center = Offset(size.width / 2, size.height / 2)
        val radius = 29f * unit
        val ring = palette.ink.copy(alpha = 0.5f)
        // Кольцо четырьмя дугами: перед каждой буквой разрыв.
        for (quarter in 0..3) {
            drawArc(
                ring,
                startAngle = -90f + quarter * 90f + GAP_DEGREES,
                sweepAngle = 90f - 2 * GAP_DEGREES,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(width = 1.3f * unit),
            )
        }
        val style = letterStyle.copy(fontSize = (10f * unit).toSp(), lineHeight = (10f * unit).toSp())
        val spots = listOf(
            Offset(center.x, center.y - radius),
            Offset(center.x + radius, center.y),
            Offset(center.x, center.y + radius),
            Offset(center.x - radius, center.y),
        )
        spots.forEachIndexed { index, spot ->
            val letter = letters.getOrNull(index) ?: return@forEachIndexed
            val layout = measurer.measure(letter, style)
            drawText(layout, topLeft = Offset(spot.x - layout.size.width / 2f, spot.y - layout.size.height / 2f))
        }
        rotate(blowsTowardDegrees, center) {
            val tip = Offset(center.x, center.y - 20f * unit)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(center.x, center.y + 14f * unit)
                moveTo(tip.x, tip.y)
                lineTo(center.x - 6f * unit, center.y - 12f * unit)
                moveTo(tip.x, tip.y)
                lineTo(center.x + 6f * unit, center.y - 12f * unit)
            }
            drawPath(
                arrow,
                palette.accent,
                style = Stroke(width = 2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Сторона компаса: кольцо радиусом 29 единиц макета помещается с запасом для букв (рисунок 74 единицы). */
private val COMPASS_SIZE = 60.dp

/**
 * Половина разрыва кольца вокруг буквы, градусы: в макете разрыв это круг радиусом 7 единиц на кольце радиусом 29,
 * то есть угол 2·asin(7 / 58) в обе стороны от середины буквы.
 */
private const val GAP_DEGREES = 13.9f

/**
 * График давления на плитке (вариант Б, решение Димы 30.10.2026): линия цветом акцента с заливкой под ней, горизонтальные
 * линии сетки с подписями давления слева (гПа, целые), внизу время начала, «сейчас» и конца ряда. Точка и тонкая линия
 * отмечают «сейчас» ([nowIndex] считается от начала [points]). Ряд слегка сглаживается ([smoothSeries]) и проводится
 * плавной кривой без выбросов ([monotoneTangents]); ось и линии сетки считает [pressureAxis].
 */
@Composable
internal fun PressureChart(points: List<PressurePoint>, nowIndex: Int, zone: ZoneId, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(palette.ink2) { pogodaText(9.5f, 500, color = palette.ink2, lineHeight = 1f) }
    val smooth = remember(points) { smoothSeries(points.map { it.hpa }) }
    val axis = remember(smooth) { pressureAxis(smooth) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val count = smooth.size
        if (count < 2) return@Canvas
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val yLabels = axis.ticks.map { measurer.measure(it.toString(), labelStyle) }
        val gutter = (yLabels.maxOf { it.size.width }) + 5.dp.toPx()
        val rightPad = 3.dp.toPx()
        val top = labelHeight / 2 + 1.dp.toPx()
        val bottom = size.height - labelHeight - 4.dp.toPx()
        val left = gutter
        val width = size.width - left - rightPad
        val step = width / (count - 1)
        val xs = FloatArray(count) { left + it * step }
        val span = (axis.max - axis.min).toDouble()
        fun yOf(value: Double): Double = bottom - ((value - axis.min) / span) * (bottom - top)

        // Сетка и подписи по оси давления.
        axis.ticks.forEachIndexed { index, tick ->
            val y = yOf(tick.toDouble()).toFloat()
            drawLine(palette.glassLine, Offset(left, y), Offset(size.width - rightPad, y), strokeWidth = 0.8.dp.toPx())
            val label = yLabels[index]
            drawText(label, topLeft = Offset(gutter - 5.dp.toPx() - label.size.width, y - label.size.height / 2f))
        }

        val ys = DoubleArray(count) { yOf(smooth[it]) }
        val slopes = monotoneTangents(ys)
        val line = Path().apply {
            moveTo(xs[0], ys[0].toFloat())
            for (k in 0 until count - 1) {
                cubicTo(
                    xs[k] + step / 3, (ys[k] + slopes[k] / 3).toFloat(),
                    xs[k + 1] - step / 3, (ys[k + 1] - slopes[k + 1] / 3).toFloat(),
                    xs[k + 1], ys[k + 1].toFloat(),
                )
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xs[count - 1], bottom)
            lineTo(xs[0], bottom)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(palette.accent.copy(alpha = 0.30f), palette.accent.copy(alpha = 0.02f)),
                startY = top,
                endY = bottom,
            ),
        )
        drawPath(
            line,
            palette.accent,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Время под графиком: начало ряда, «сейчас» (если не вплотную к краям) и конец.
        fun timeLabel(index: Int) = measurer.measure(points[index].time.atZone(zone).format(timeFormat), labelStyle)
        val labelY = size.height - labelHeight
        val first = timeLabel(0)
        drawText(first, topLeft = Offset(xs[0], labelY))
        val last = timeLabel(count - 1)
        drawText(last, topLeft = Offset(xs[count - 1] - last.size.width, labelY))
        if (nowIndex in 0 until count) {
            val x = xs[nowIndex]
            drawLine(palette.accent.copy(alpha = 0.35f), Offset(x, ys[nowIndex].toFloat()), Offset(x, bottom), strokeWidth = 0.8.dp.toPx())
            drawCircle(palette.accent, 3.dp.toPx(), Offset(x, ys[nowIndex].toFloat()))
            if (nowIndex >= NOW_LABEL_MARGIN && nowIndex <= count - 1 - NOW_LABEL_MARGIN) {
                val mid = timeLabel(nowIndex)
                drawText(mid, topLeft = Offset(x - mid.size.width / 2f, labelY))
            }
        }
    }
}

/** Высота графика: прежняя полоса была 28 dp, с осями и подписями нужно на 24 dp больше (дуга солнца ниже уступает). */
private val CHART_HEIGHT = 52.dp

/** Подпись «сейчас» не ставится, если до края ряда меньше стольких точек: иначе налезает на первую или последнюю подпись. */
private const val NOW_LABEL_MARGIN = 4
