package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import io.github.dumahuch.pogodaapp.ui.home.HourColumnWidth
import io.github.dumahuch.pogodaapp.ui.home.edgeFade
import io.github.dumahuch.pogodaapp.ui.pogodaText

/** Ряд значений ленты: число для высоты (пусто: часа нет) и подпись над или под точкой. */
internal class Series(val values: List<Double?>, val texts: List<String>)

/**
 * Что нарисовать в ленте страницы подробностей. Слои необязательные: линия (основной показатель), полоса над ней
 * (порывы), столбики (осадки) с пунктиром (вероятность), ряд точек внизу (точка росы). Подписи часов, плашка «Сейчас» и
 * ряд стрелок ([directions]) общие. Шкалы задают страницы: [lineMin]…[lineMax] для линии и полосы, [barMax] для столбиков.
 */
internal class StripSpec(
    val labels: List<String>,
    val nowIndex: Int?,
    val directions: List<Int?>? = null,
    val line: Series? = null,
    val band: Series? = null,
    val bars: Series? = null,
    val dashed: Series? = null,
    val dots: Series? = null,
    val lineMin: Double = 0.0,
    val lineMax: Double = 1.0,
    val barMax: Double = 1.0,
    val description: List<String> = emptyList(),
)

private const val LABEL_ROW = 24f
private const val ARROW_ROW = 26f
private const val BOTTOM_ROW = 24f
private val StripHeight = 188.dp

/**
 * Лента по часам: один холст на всю ширину (столбцы как на «Главной», 58 dp), чтобы прокрутка двигала готовую
 * картинку, а не пересобирала десятки элементов (0.8.72: прежняя лента из элементов тормозила при открытии страницы).
 */
@Composable
internal fun MetricStrip(spec: StripSpec, initialColumn: Int, modifier: Modifier = Modifier) {
    val count = spec.labels.size
    if (count == 0) return
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (HourColumnWidth * initialColumn).roundToPx() })
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val styles = remember(palette) { StripStyles(palette) }
    val summary = remember(spec) { spec.description.joinToString("; ") }
    Box(
        modifier
            .fillMaxWidth()
            .edgeFade(18.dp)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
    ) {
        Canvas(
            Modifier
                .width(HourColumnWidth * count)
                .height(StripHeight)
                .clearAndSetSemantics { contentDescription = summary },
        ) { drawStrip(spec, palette, styles, measurer) }
    }
}

private class StripStyles(palette: PogodaPalette) {
    val time: TextStyle = pogodaText(12f, 500, color = palette.ink2, lineHeight = 1f, tabular = true)
    val now: TextStyle = pogodaText(12f, 600, color = palette.ink, lineHeight = 1f, tabular = true)
    val main: TextStyle = pogodaText(14f, 700, color = palette.ink, lineHeight = 1f, tabular = true)
    val minor: TextStyle = pogodaText(11.5f, 600, color = palette.ink2, lineHeight = 1f, tabular = true)
    val warm: TextStyle = pogodaText(11.5f, 600, color = palette.warning, lineHeight = 1f, tabular = true)
}

private fun DrawScope.centred(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, centerY: Float) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, centerY - layout.size.height / 2f))
}

private fun DrawScope.drawStrip(spec: StripSpec, palette: PogodaPalette, styles: StripStyles, measurer: TextMeasurer) {
    val dp = density
    val column = HourColumnWidth.toPx()
    fun x(i: Int) = i * column + column / 2f
    // Подписи часов и плашка «Сейчас».
    spec.labels.forEachIndexed { i, label ->
        if (i == spec.nowIndex) {
            val layout = measurer.measure(label, styles.now)
            val w = layout.size.width + 18 * dp
            drawRoundRect(palette.accentSoft, Offset(x(i) - w / 2, 2 * dp), Size(w, 20 * dp), CornerRadius(10 * dp))
            drawText(layout, topLeft = Offset(x(i) - layout.size.width / 2f, 12 * dp - layout.size.height / 2f))
        } else {
            centred(measurer, label, styles.time, x(i), 12 * dp)
        }
    }
    spec.directions?.forEachIndexed { i, d ->
        if (d != null) drawWindArrow(d, palette.ink, 22 * dp, Offset(x(i), (LABEL_ROW + ARROW_ROW / 2 + 6) * dp))
    }
    val top = (LABEL_ROW + (if (spec.directions != null) ARROW_ROW + 12f else 22f)) * dp
    val bottom = size.height - (if (spec.dots != null || spec.dashed != null) BOTTOM_ROW else 8f) * dp
    val lineSpan = (spec.lineMax - spec.lineMin).takeIf { it > 0.0 } ?: 1.0
    fun yLine(v: Double) = bottom - ((v - spec.lineMin) / lineSpan).toFloat().coerceIn(0f, 1f) * (bottom - top)

    spec.bars?.let { bars ->
        drawLine(palette.ink.copy(alpha = 0.25f), Offset(0f, bottom), Offset(size.width, bottom), 1 * dp)
        bars.values.forEachIndexed { i, v ->
            val value = v ?: return@forEachIndexed
            if (value <= 0.0) return@forEachIndexed
            val h = ((value / spec.barMax).toFloat().coerceIn(0f, 1f) * (bottom - top - 16 * dp)).coerceAtLeast(3 * dp)
            drawRoundRect(palette.accent, Offset(x(i) - 12 * dp, bottom - h), Size(24 * dp, h), CornerRadius(4 * dp))
            centred(measurer, bars.texts[i], styles.main, x(i), bottom - h - 9 * dp)
        }
    }
    spec.dashed?.let { dashed ->
        // Пунктир вероятности лежит над столбиками на своей шкале 0…100 %; подписи в нижнем ряду.
        val points = dashed.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), bottom - (it / 100.0).toFloat() * (bottom - top)) } }
        if (points.size > 1) {
            drawPath(smooth(points), palette.warning, style = Stroke(2 * dp, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * dp, 4 * dp))))
        }
        dashed.texts.forEachIndexed { i, t -> centred(measurer, t, styles.warm, x(i), size.height - 11 * dp) }
    }
    spec.band?.let { band ->
        val upper = band.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yLine(it)) } }
        val lower = spec.line?.values?.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yLine(it)) } }.orEmpty()
        if (upper.size > 1 && upper.size == lower.size) {
            val path = smooth(upper)
            val area = Path().apply {
                addPath(path)
                lineTo(lower.last().x, lower.last().y)
                addPath(smooth(lower.asReversed()))
                close()
            }
            drawPath(area, palette.accent.copy(alpha = BAND_ALPHA))
            drawPath(path, palette.accent.copy(alpha = BAND_EDGE_ALPHA), style = Stroke(1.5f * dp))
        }
        band.texts.forEachIndexed { i, t -> band.values[i]?.let { centred(measurer, t, styles.minor, x(i), yLine(it) - 10 * dp) } }
    }
    spec.line?.let { line ->
        val points = line.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yLine(it)) } }
        if (points.size > 1) drawPath(smooth(points), palette.accent, style = Stroke(3 * dp, cap = StrokeCap.Round))
        line.values.forEachIndexed { i, v ->
            val value = v ?: return@forEachIndexed
            val p = Offset(x(i), yLine(value))
            drawCircle(palette.ink, 4.5f * dp, p)
            drawCircle(palette.accent, 3f * dp, p)
            // Подпись под линией, если над ней полоса порывов; иначе над линией.
            val below = spec.band != null
            centred(measurer, line.texts[i], styles.main, p.x, p.y + if (below) 15 * dp else -13 * dp)
        }
    }
    spec.dots?.let { dots ->
        dots.values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            drawCircle(palette.warning, 4 * dp, Offset(x(i), size.height - 24 * dp))
            centred(measurer, dots.texts[i], styles.minor, x(i), size.height - 9 * dp)
        }
    }
}

/** Плавная кривая через точки (кубическая, середины по горизонтали), как линия температуры на «Главной». */
private fun smooth(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val mid = (a.x + b.x) / 2f
        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
    }
}
