package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.details.BigValue
import io.github.dumahuch.pogodaapp.ui.details.SubText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.shortDate
import io.github.dumahuch.pogodaapp.ui.pogodaText
import java.time.LocalDate

/*
 * Общие части страниц подробностей (0.8.72, макет https://claude.ai/artifact/JctK99dLV2y258xD3xHbSb). Принцип Димы:
 * у каждой карточки своё оформление под вопрос, на который она отвечает; пустые места заняты рисунком или сведениями;
 * у каждого графика легенда с образцом цвета, как в обычных таблицах.
 */

internal val CardShape = RoundedCornerShape(24.dp)

/** Плитка сведений: подпись, значение, пояснение. */
internal class TileText(val label: String, val value: String, val note: String?)

/** Две плитки рядом вместо длинных строк «подпись … значение». */
@Composable
internal fun TwoTiles(left: TileText, right: TileText, modifier: Modifier = Modifier) = TileRow(listOf(left, right), modifier)

/** Ряд плиток одной ширины. */
@Composable
internal fun TileRow(tiles: List<TileText>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { Tile(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun Tile(tile: TileText, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .background(palette.ink.copy(alpha = TILE_ALPHA), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(tile.label, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
        Text(tile.value, style = pogodaText(18f, 600, tabular = true), maxLines = 1)
        tile.note?.let { Text(it, style = pogodaText(11.5f, 500, color = palette.ink2), maxLines = 1) }
    }
}

/** Подложка плиток: тот же цвет, что у текста, едва заметно (на светлом небе темнее, на тёмном светлее). */
private const val TILE_ALPHA = 0.07f

/**
 * Стрелка ветра со стороной [side] в центре области рисования: показывает, куда дует (направление «откуда» плюс 180°),
 * тонкая линия с открытым наконечником.
 */
internal fun DrawScope.drawWindArrow(fromDegrees: Int, color: Color, side: Float, at: Offset = center) {
    val unit = side / 24f
    val width = 1.9f * unit
    rotate(fromDegrees + 180f, at) {
        drawLine(color, Offset(at.x, at.y + 8f * unit), Offset(at.x, at.y - 8f * unit), width, StrokeCap.Round)
        val head = Path().apply {
            moveTo(at.x - 4.5f * unit, at.y - 3.5f * unit)
            lineTo(at.x, at.y - 8f * unit)
            lineTo(at.x + 4.5f * unit, at.y - 3.5f * unit)
        }
        drawPath(head, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun WindArrow(fromDegrees: Int, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawWindArrow(fromDegrees, color, this.size.minDimension) }
}

/**
 * Шкала Бофорта из 13 ступеней от штиля до урагана, название слева над полоской, от холодного цвета к тёплому: закрашено до ступени порывов,
 * сплошной треугольник над ступенью «сейчас», контурный над ступенью порывов. Подписи концов и легенда ниже.
 */
@Composable
internal fun BeaufortScale(step: Int, gustStep: Int?, nowLabel: String) {
    val palette = LocalPogodaPalette.current
    val top = gustStep ?: step
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(stringResource(R.string.metric_beaufort), style = pogodaText(11.5f, 600, color = palette.ink2))
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val gap = 2.dp.toPx()
            val segment = size.width / BEAUFORT_STEPS
            val barTop = 12.dp.toPx()
            for (i in 0 until BEAUFORT_STEPS) {
                val tone = lerp(palette.accent, palette.warning, i / (BEAUFORT_STEPS - 1f))
                drawRoundRect(
                    tone.copy(alpha = if (i <= top) 0.9f else 0.22f),
                    Offset(i * segment + gap / 2, barTop),
                    Size(segment - gap, 9.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                )
            }
            drawMarker((step + 0.5f) * segment, palette.ink, filled = true)
            if (gustStep != null && gustStep != step) drawMarker((gustStep + 0.5f) * segment, palette.ink, filled = false)
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Text(stringResource(R.string.metric_calm), style = pogodaText(10.5f, 600, color = palette.ink2), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.metric_hurricane), style = pogodaText(10.5f, 600, color = palette.ink2))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkerLegend(filled = true, text = nowLabel)
            if (gustStep != null && gustStep != step) {
                Box(Modifier.width(12.dp))
                MarkerLegend(filled = false, text = stringResource(R.string.metric_marker_gusts))
            }
        }
    }
}

private const val BEAUFORT_STEPS = 13

private fun DrawScope.drawMarker(x: Float, color: Color, filled: Boolean) {
    val half = 5.dp.toPx()
    val path = Path().apply {
        moveTo(x - half, 1.dp.toPx())
        lineTo(x + half, 1.dp.toPx())
        lineTo(x, 9.dp.toPx())
        close()
    }
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
}

@Composable
private fun MarkerLegend(filled: Boolean, text: String) {
    val palette = LocalPogodaPalette.current
    Canvas(Modifier.size(width = 10.dp, height = 10.dp)) { drawMarker(size.width / 2, palette.ink, filled) }
    Text(
        text,
        style = pogodaText(11f, if (filled) 700 else 600, color = if (filled) palette.ink else palette.ink2),
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Шкала видимости из шести ступеней Met Office, точка на нынешней; подписи концов ниже. */
@Composable
internal fun VisibilityScale(level: Int, levels: Int) {
    val palette = LocalPogodaPalette.current
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val gap = 2.dp.toPx()
            val segment = size.width / levels
            val y = size.height / 2
            for (i in 0 until levels) {
                val alpha = if (i <= level) 0.3f + 0.6f * i / (levels - 1) else 0.14f
                drawRoundRect(
                    palette.accent.copy(alpha = alpha),
                    Offset(i * segment + gap / 2, y - 4.dp.toPx()),
                    Size(segment - gap, 8.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                )
            }
            drawCircle(palette.ink, 6.dp.toPx(), Offset((level + 0.5f) * segment, y))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Text(stringResource(R.string.metric_vis_fog), style = pogodaText(10.5f, 600, color = palette.ink2), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.metric_vis_far), style = pogodaText(10.5f, 600, color = palette.ink2))
        }
    }
}

/**
 * Список «7 дней»: заголовок, шапка столбцов, строки с днём и датой слева; содержимое строки своё у каждой страницы.
 * Каждая строка отдельная плашка, как плитки (Дима 22.11: нажимаемое должно выглядеть нажимаемым, без надписи «нажми»):
 * выбранная светлее, нажатие показывает этот день выше.
 */
@Composable
internal fun DaysCard(
    dates: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    legend: @Composable () -> Unit,
    row: @Composable RowScope.(index: Int) -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val todayName = stringResource(R.string.today)
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(CardShape, LocalGlass.current)
            .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp),
    ) {
        Text(title ?: stringResource(R.string.forecast_7_days), style = pogodaText(13f, 700), modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) { legend() }
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            dates.forEach { date ->
                val isSelected = date == selected
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(palette.ink.copy(alpha = if (isSelected) ROW_SELECTED_ALPHA else ROW_ALPHA))
                        .clickable(role = Role.Button) { onSelect(date) }
                        .semantics(mergeDescendants = true) { this.selected = isSelected }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.width(DayColumnWidth)) {
                        Text(
                            if (date == today) todayName else dayName(date, dayNames),
                            style = pogodaText(15f, 700, lineHeight = 1.15f),
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(shortDate(date, locale), style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.15f), maxLines = 1)
                    }
                    row(dates.indexOf(date))
                }
            }
        }
    }
}

/** Плашки строк дней: зазор, заливка как у плиток, у выбранной заметно светлее. */
private val ROW_GAP = 6.dp
private const val ROW_ALPHA = 0.06f
private const val ROW_SELECTED_ALPHA = 0.16f

private val DayColumnWidth = 62.dp

/** Правая колонка строки дня: крупное значение и мелкая подпись под ним. */
@Composable
internal fun DayValue(value: String, note: String?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Column(modifier.width(DayValueWidth), horizontalAlignment = Alignment.End) {
        Text(value, style = pogodaText(15f, 700, tabular = true), maxLines = 1)
        note?.let { Text(it, style = pogodaText(11f, 500, color = palette.ink2, tabular = true), maxLines = 1) }
    }
}

internal val DayValueWidth = 78.dp
