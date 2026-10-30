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
import io.github.dumahuch.pogodaapp.forecast.monotoneTangents
import io.github.dumahuch.pogodaapp.forecast.smoothSeries
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText

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
 * Линия давления за ближайшие часы цветом акцента, как в макете; точка отмечает «сейчас» ([nowIndex] считается от начала ряда).
 * Ряд слегка сглаживается ([smoothSeries]) и проводится плавной кривой без выбросов ([monotoneTangents]).
 */
@Composable
internal fun Sparkline(values: List<Double>, nowIndex: Int, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val smooth = remember(values) { smoothSeries(values) }
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        if (smooth.size < 2) return@Canvas
        val count = smooth.size
        val pad = 4.dp.toPx()
        // Размах не меньше 2 гПа: ровное давление остаётся ровной линией, а не растягивается на всю высоту.
        val low = smooth.min()
        val high = smooth.max()
        val middle = (low + high) / 2
        val range = maxOf(high - low, MIN_SPAN_HPA)
        val step = (size.width - 2 * pad) / (count - 1)
        val xs = FloatArray(count) { pad + it * step }
        val ys = DoubleArray(count) { size.height / 2 - ((smooth[it] - middle) / range) * (size.height - 2 * pad) }
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
        drawPath(
            line,
            palette.accent,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (nowIndex in 0 until count) drawCircle(palette.accent, 3.dp.toPx(), Offset(xs[nowIndex], ys[nowIndex].toFloat()))
    }
}

private const val MIN_SPAN_HPA = 2.0
