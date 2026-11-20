package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.home.HourlyGeometry
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.home.CurveLayer
import io.github.dumahuch.pogodaapp.ui.home.HourColumnWidth
import io.github.dumahuch.pogodaapp.ui.home.HourStripHeight
import io.github.dumahuch.pogodaapp.ui.home.TEMPERATURE_LABEL_RISE
import io.github.dumahuch.pogodaapp.ui.home.edgeFade
import io.github.dumahuch.pogodaapp.ui.pogodaText

/** Что рисуется под временем: стрелка ветра, капля осадков или ничего. */
internal enum class StripGlyph { None, Arrow, Drop }

/**
 * Один час ленты страницы подробностей. [value] число на линии (пусто: точки нет, подпись «–»), [valueText] его подпись,
 * [bottomText] подпись внизу, [bar] значение столбика (пусто: столбиков в ленте нет), [direction] откуда дует ветер,
 * [wet] капля закрашена (в этот час есть осадки).
 */
internal class MetricColumn(
    val label: String,
    val value: Int?,
    val valueText: String,
    val bottomText: String,
    val bar: Double? = null,
    val direction: Int? = null,
    val wet: Boolean = false,
)

/**
 * Лента по часам на странице подробностей: устроена как лента «По часам» на «Главной» (столбцы 58 dp, время с плашкой у
 * «Сейчас», значок, число на плавной линии, внизу столбик и подпись), чтобы страница не выглядела чужой.
 * [nowIndex] час с плашкой «Сейчас», [initialColumn] с какого столбца лента открыта, [barMinScale] шкала столбиков
 * не меньше этого значения.
 */
@Composable
internal fun MetricStrip(
    columns: List<MetricColumn>,
    glyph: StripGlyph,
    nowIndex: Int?,
    initialColumn: Int,
    barMinScale: Double,
    barAlpha: Float,
    modifier: Modifier = Modifier,
) {
    if (columns.isEmpty()) return
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (HourColumnWidth * initialColumn).roundToPx() })
    val known = columns.mapNotNull { it.value }
    val curve = remember(columns) {
        val fill = known.firstOrNull() ?: 0
        HourlyGeometry.curveY(columns.map { it.value ?: fill })
    }
    val bars = remember(columns) {
        if (columns.all { it.bar == null }) null else HourlyGeometry.barHeights(columns.map { it.bar ?: 0.0 }, barMinScale)
    }
    Box(
        modifier
            .fillMaxWidth()
            .edgeFade(18.dp)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .width(HourColumnWidth * columns.size)
                .height(HourStripHeight),
        ) {
            if (known.isNotEmpty()) CurveLayer(curve, Modifier.matchParentSize())
            Row(Modifier.matchParentSize()) {
                columns.forEachIndexed { index, column ->
                    StripColumn(column, glyph, index == nowIndex, curve[index], bars?.get(index), barAlpha)
                }
            }
        }
    }
}

@Composable
private fun StripColumn(column: MetricColumn, glyph: StripGlyph, isNow: Boolean, curveY: Float, barHeight: Float?, barAlpha: Float) {
    val palette = LocalPogodaPalette.current
    Box(
        Modifier
            .width(HourColumnWidth)
            .fillMaxHeight()
            .clearAndSetSemantics { contentDescription = "${column.label}: ${column.valueText}, ${column.bottomText}" },
    ) {
        val labelStyle = pogodaText(12f, 500, color = if (isNow) palette.ink else palette.ink2, lineHeight = 20f / 12f, tabular = true)
        val labelModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 2.dp)
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
        Text(
            column.label,
            style = labelStyle,
            maxLines = 1,
            softWrap = false,
            modifier = if (isNow) {
                labelModifier
                    .background(palette.accentSoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp)
            } else {
                labelModifier
            },
        )
        val glyphModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 25.dp)
        when (glyph) {
            StripGlyph.Arrow -> column.direction?.let { WindArrow(it, 22.dp, palette.ink, glyphModifier) }
            StripGlyph.Drop -> LineIconView(
                LineIcons.Drop,
                20.dp,
                if (column.wet) palette.bar else palette.ink.copy(alpha = DRY_DROP_ALPHA),
                glyphModifier,
                strokeWidth = 1.8f,
            )
            StripGlyph.None -> Unit
        }
        Text(
            column.valueText,
            style = pogodaText(14f, 500, lineHeight = 1f, tabular = true),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (curveY - TEMPERATURE_LABEL_RISE).dp)
                .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        )
        if (barHeight != null) {
            val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .width(12.dp)
                    .height(if (barHeight > 0f) barHeight.dp else 2.dp)
                    .background(palette.bar.copy(alpha = if (barHeight > 0f) barAlpha else EMPTY_BAR_ALPHA), shape),
            )
        }
        Text(
            column.bottomText,
            style = pogodaText(11.5f, 400, color = palette.ink2, tabular = true),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

/** Капля часа без осадков бледная: значок остаётся на месте, но не спорит с закрашенными. */
private const val DRY_DROP_ALPHA = 0.35f

/** Пустой час: тонкая бледная полоска, как у осадков на «Главной». */
private const val EMPTY_BAR_ALPHA = 0.35f

/**
 * Стрелка ветра: показывает, куда дует (направление «откуда» плюс 180°), как стрелка компаса на «Подробно».
 * Тонкая линия с открытым наконечником.
 */
@Composable
internal fun WindArrow(fromDegrees: Int, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        rotate(fromDegrees + 180f) {
            val c = center
            drawLine(color, Offset(c.x, c.y + 8f * unit), Offset(c.x, c.y - 8f * unit), strokeWidth = stroke.width, cap = StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x - 4.5f * unit, c.y - 3.5f * unit)
                lineTo(c.x, c.y - 8f * unit)
                lineTo(c.x + 4.5f * unit, c.y - 3.5f * unit)
            }
            drawPath(head, color, style = stroke)
        }
    }
}
