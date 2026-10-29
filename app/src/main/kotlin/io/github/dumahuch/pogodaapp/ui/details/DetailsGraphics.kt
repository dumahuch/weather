package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette

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
 * Компас ветра как в макете: тонкое кольцо, четыре короткие засечки и длинная стрелка цветом акцента с открытым
 * наконечником. Стрелка смотрит туда, куда ветер дует ([blowsTowardDegrees], 0 это на север, по часовой стрелке).
 * Размеры заданы в долях стороны (в макете 64 единицы), поэтому рисунок не зависит от размера.
 */
@Composable
internal fun Compass(blowsTowardDegrees: Float, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.size(52.dp)) {
        val unit = size.minDimension / 64f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(palette.glassLine, 29f * unit, center, style = Stroke(width = 2f * unit))
        val tick = palette.ink.copy(alpha = 0.5f)
        for (quarter in 0..3) {
            rotate(quarter * 90f, center) {
                drawLine(
                    tick,
                    Offset(center.x, center.y - 29f * unit),
                    Offset(center.x, center.y - 24f * unit),
                    strokeWidth = 2f * unit,
                    cap = StrokeCap.Round,
                )
            }
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

/** Линия давления за ближайшие часы; точка отмечает «сейчас» ([nowIndex] считается от начала ряда). */
@Composable
internal fun Sparkline(values: List<Double>, nowIndex: Int, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        if (values.size < 2) return@Canvas
        val pad = 4.dp.toPx()
        // Размах не меньше 2 гПа: ровное давление остаётся ровной линией, а не растягивается на всю высоту.
        val low = values.min()
        val high = values.max()
        val middle = (low + high) / 2
        val range = maxOf(high - low, MIN_SPAN_HPA)
        fun point(index: Int): Offset {
            val x = pad + index * (size.width - 2 * pad) / (values.size - 1)
            val y = size.height / 2 - ((values[index] - middle) / range).toFloat() * (size.height - 2 * pad)
            return Offset(x, y)
        }
        val line = Path().apply {
            values.indices.forEach { index ->
                val p = point(index)
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(
            line,
            palette.ink2,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (nowIndex in values.indices) drawCircle(palette.ink, 3.dp.toPx(), point(nowIndex))
    }
}

private const val MIN_SPAN_HPA = 2.0
