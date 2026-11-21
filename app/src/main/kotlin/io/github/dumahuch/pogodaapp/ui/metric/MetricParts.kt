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

/** Карточка сверху: заголовок, крупное число, строки под ним, рисунок справа и сведения ниже черты. */
@Composable
internal fun TopCard(
    title: String,
    value: String,
    unit: String?,
    lines: List<String>,
    modifier: Modifier = Modifier,
    picture: (@Composable () -> Unit)? = null,
    below: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(CardShape, LocalGlass.current)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = pogodaText(12.5f, 700, color = palette.ink2), maxLines = 1)
                BigValue(value, unit)
                lines.firstOrNull()?.let { Text(it, style = pogodaText(14f, 600, lineHeight = 1.3f)) }
                lines.drop(1).forEach { SubText(it) }
            }
            if (picture != null) Box(Modifier.padding(start = 8.dp)) { picture() }
        }
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(palette.glassLine))
        below()
    }
}

/** Плитка сведений: подпись, значение, пояснение. */
internal class TileText(val label: String, val value: String, val note: String?)

/** Две плитки рядом вместо длинных строк «подпись … значение». */
@Composable
internal fun TwoTiles(left: TileText, right: TileText, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(left, Modifier.weight(1f))
        Tile(right, Modifier.weight(1f))
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

/** Строка «подпись — значение» над шкалой. */
@Composable
internal fun LabelRow(label: String, value: String) {
    val palette = LocalPogodaPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = pogodaText(12.5f, 600, color = palette.ink2), modifier = Modifier.weight(1f))
        Text(value, style = pogodaText(13f, 700, tabular = true))
    }
}

/** Образцы в легенде графиков: линия, полоса, столбик, пунктир, точка, стрелка. */
internal enum class Swatch { Line, Band, Bar, Dashed, Dot, Arrow }

/** Легенда: образец цвета и подпись, как у классических графиков. */
@Composable
internal fun Legend(items: List<Pair<Swatch, String>>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (swatch, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SwatchView(swatch, palette)
                Text(text, style = pogodaText(11.5f, 600, color = palette.ink2), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SwatchView(swatch: Swatch, palette: PogodaPalette) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val mid = size.height / 2
        when (swatch) {
            Swatch.Line -> drawLine(palette.accent, Offset(0f, mid), Offset(size.width, mid), 2.5.dp.toPx(), StrokeCap.Round)
            Swatch.Band -> {
                drawRect(palette.accent.copy(alpha = BAND_ALPHA), Offset(0f, 2.dp.toPx()), Size(size.width, size.height - 4.dp.toPx()))
                drawLine(palette.accent.copy(alpha = BAND_EDGE_ALPHA), Offset(0f, 2.dp.toPx()), Offset(size.width, 2.dp.toPx()), 1.5.dp.toPx())
            }
            Swatch.Bar -> drawRoundRect(
                palette.accent,
                Offset(size.width / 2 - 4.dp.toPx(), 0f),
                Size(8.dp.toPx(), size.height),
                CornerRadius(2.dp.toPx()),
            )
            Swatch.Dashed -> drawLine(
                palette.warning,
                Offset(0f, mid),
                Offset(size.width, mid),
                2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
            Swatch.Dot -> drawCircle(palette.warning, 4.dp.toPx(), center)
            Swatch.Arrow -> drawWindArrow(225, palette.ink, size.minDimension)
        }
    }
}

/** Доли прозрачности полосы порывов и её верхней кромки. */
internal const val BAND_ALPHA = 0.28f
internal const val BAND_EDGE_ALPHA = 0.6f

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
 * Компас как у классического: деления через 5°, длиннее через 45°, самые длинные на сторонах света; север отмечен
 * тёплым цветом, буквы сторон жирные. Стрелка показывает, куда дует ветер. Числа внутри нет: оно крупно слева (Дима, 20.11).
 */
@Composable
internal fun BigCompass(fromDegrees: Int, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val letters = stringArrayResource(R.array.compass_letters).toList()
    val measurer = rememberTextMeasurer()
    val letterStyle = remember(palette) { pogodaText(12f, 800, color = palette.ink, lineHeight = 1f) }
    val northStyle = remember(palette) { letterStyle.copy(color = palette.warning) }
    Canvas(modifier.size(COMPASS_SIZE)) {
        val c = center
        val outer = size.minDimension / 2 - 1.dp.toPx()
        for (i in 0 until TICKS) {
            val degrees = i * TICK_STEP
            val cardinal = degrees % 90 == 0
            val major = degrees % 45 == 0
            val length = when {
                cardinal -> 9.dp.toPx()
                major -> 6.dp.toPx()
                else -> 3.5.dp.toPx()
            }
            val color = when {
                degrees == 0 -> palette.warning
                cardinal -> palette.ink
                major -> palette.ink.copy(alpha = 0.7f)
                else -> palette.ink.copy(alpha = 0.3f)
            }
            rotate(degrees.toFloat(), c) {
                drawLine(
                    color,
                    Offset(c.x, c.y - outer),
                    Offset(c.x, c.y - outer + length),
                    if (cardinal) 2.2.dp.toPx() else 1.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
        val letterRadius = outer - 18.dp.toPx()
        val spots = listOf(Offset(0f, -1f), Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f))
        spots.forEachIndexed { index, d ->
            val letter = letters.getOrNull(index) ?: return@forEachIndexed
            val layout = measurer.measure(letter, if (index == 0) northStyle else letterStyle)
            drawText(
                layout,
                topLeft = Offset(c.x + d.x * letterRadius - layout.size.width / 2f, c.y + d.y * letterRadius - layout.size.height / 2f),
            )
        }
        // Стрелка через весь круг: хвост с той стороны, откуда дует, наконечник туда, куда дует.
        val reach = outer - 12.dp.toPx()
        rotate(fromDegrees + 180f, c) {
            val stroke = 3.dp.toPx()
            drawLine(palette.accent, Offset(c.x, c.y + reach), Offset(c.x, c.y - reach + 6.dp.toPx()), stroke, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x, c.y - reach)
                lineTo(c.x - 7.dp.toPx(), c.y - reach + 12.dp.toPx())
                lineTo(c.x + 7.dp.toPx(), c.y - reach + 12.dp.toPx())
                close()
            }
            drawPath(head, palette.accent)
            drawCircle(palette.accent, 4.dp.toPx(), c)
        }
    }
}

private val COMPASS_SIZE = 112.dp
private const val TICKS = 72
private const val TICK_STEP = 5

/**
 * Шкала Бофорта из 13 ступеней от штиля до урагана, от холодного цвета к тёплому: закрашено до ступени порывов,
 * сплошной треугольник над ступенью «сейчас», контурный над ступенью порывов. Подписи концов и легенда ниже.
 */
@Composable
internal fun BeaufortScale(step: Int, gustStep: Int?, nowLabel: String) {
    val palette = LocalPogodaPalette.current
    val top = gustStep ?: step
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
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
            Box(Modifier.weight(1f))
            Text(stringResource(R.string.metric_beaufort), style = pogodaText(11f, 600, color = palette.ink2))
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

/** Зонт: раскрытый с каплями, если осадки будут; сложенный рядом с солнцем, если нет. */
@Composable
internal fun Umbrella(open: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.size(PICTURE_SIZE)) {
        val u = size.minDimension / 110f
        val handle = Path().apply {
            moveTo(55f * u, (if (open) 52f else 72f) * u)
            lineTo(55f * u, 92f * u)
            quadraticTo(55f * u, 100f * u, 47f * u, 100f * u)
            quadraticTo(41f * u, 100f * u, 40f * u, 94f * u)
        }
        drawPath(handle, palette.ink, style = Stroke(4f * u, cap = StrokeCap.Round))
        if (open) {
            val canopy = Path().apply {
                moveTo(10f * u, 52f * u)
                quadraticTo(55f * u, -6f * u, 100f * u, 52f * u)
                quadraticTo(88f * u, 44f * u, 77f * u, 52f * u)
                quadraticTo(66f * u, 44f * u, 55f * u, 52f * u)
                quadraticTo(44f * u, 44f * u, 33f * u, 52f * u)
                quadraticTo(22f * u, 44f * u, 10f * u, 52f * u)
                close()
            }
            drawPath(canopy, palette.accent)
            listOf(20f to 66f, 40f to 74f, 72f to 66f, 92f to 74f).forEach { (x, y) ->
                drawLine(palette.accent, Offset(x * u, y * u), Offset((x - 3f) * u, (y + 8f) * u), 3f * u, StrokeCap.Round)
            }
        } else {
            val folded = Path().apply {
                moveTo(55f * u, 10f * u)
                lineTo(68f * u, 72f * u)
                quadraticTo(55f * u, 78f * u, 42f * u, 72f * u)
                close()
            }
            drawPath(folded, palette.accent.copy(alpha = 0.6f))
            val sun = Offset(82f * u, 28f * u)
            drawCircle(palette.warning, 11f * u, sun, style = Stroke(3f * u))
            for (k in 0 until 8) {
                rotate(k * 45f, sun) {
                    drawLine(palette.warning, Offset(sun.x, sun.y - 16f * u), Offset(sun.x, sun.y - 21f * u), 3f * u, StrokeCap.Round)
                }
            }
        }
    }
}

/** Капля, наполненная снизу на [percent] процентов. */
@Composable
internal fun DropGauge(percent: Int, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.size(PICTURE_SIZE)) {
        val u = size.minDimension / 110f
        val left = (size.width - 82f * u) / 2
        val drop = Path().apply {
            moveTo(left + 41f * u, 6f * u)
            cubicTo(left + 41f * u, 6f * u, left, 54f * u, left, 70f * u)
            cubicTo(left, 92f * u, left + 18f * u, 108f * u, left + 41f * u, 108f * u)
            cubicTo(left + 64f * u, 108f * u, left + 82f * u, 92f * u, left + 82f * u, 70f * u)
            cubicTo(left + 82f * u, 54f * u, left + 41f * u, 6f * u, left + 41f * u, 6f * u)
            close()
        }
        drawPath(drop, palette.ink.copy(alpha = 0.06f))
        val level = 108f * u - (102f * u) * percent.coerceIn(0, 100) / 100f
        clipPath(drop) { drawRect(palette.accent.copy(alpha = 0.85f), Offset(0f, level), Size(size.width, size.height - level)) }
        drawPath(drop, palette.ink.copy(alpha = 0.5f), style = Stroke(2f * u))
    }
}

private val PICTURE_SIZE = 104.dp

/**
 * Список «7 дней»: заголовок, легенда, строки с днём и датой слева; содержимое строки своё у каждой страницы.
 * Нажатие на строку показывает этот день выше; выбранная строка подсвечена.
 */
@Composable
internal fun DaysCard(
    dates: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    note: String,
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
            .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title ?: stringResource(R.string.forecast_7_days), style = pogodaText(13f, 700), modifier = Modifier.alignByBaseline())
            if (note.isNotEmpty()) Text(note, style = pogodaText(12f, 400, color = palette.ink2), modifier = Modifier.alignByBaseline())
        }
        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) { legend() }
        dates.forEachIndexed { index, date ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(1.dp).background(palette.glassLine))
            val isSelected = date == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(vertical = 2.dp)
                    .then(if (isSelected) Modifier.background(palette.accentSoft, RoundedCornerShape(14.dp)) else Modifier)
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
                row(index)
            }
        }
    }
}

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

/**
 * Шапка над строками дней: подписи над своими прямоугольниками и единица над правой колонкой.
 * Отступы совпадают со строкой дня (колонка названия, зазор 8 dp, зазор 3 dp между частями), поэтому всё стоит ровно.
 */
@Composable
internal fun PartsHeader(parts: List<String>, unit: String) {
    val palette = LocalPogodaPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(DayColumnWidth))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEach { name ->
                Text(
                    name,
                    style = pogodaText(11.5f, 600, color = palette.ink2),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Text(unit, style = pogodaText(11.5f, 600, color = palette.ink2), modifier = Modifier.width(DayValueWidth), textAlign = TextAlign.End, maxLines = 1)
    }
}
