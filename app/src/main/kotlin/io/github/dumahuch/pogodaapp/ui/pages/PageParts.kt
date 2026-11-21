package io.github.dumahuch.pogodaapp.ui.pages

import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.Manrope
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.details.BigValue
import io.github.dumahuch.pogodaapp.ui.metric.drawWindArrow
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.Card
import io.github.dumahuch.pogodaapp.ui.sunmoon.CardTitle
import io.github.dumahuch.pogodaapp.ui.sunmoon.drawGlowPath
import io.github.dumahuch.pogodaapp.ui.sunmoon.drawHalo

/*
 * Общие части страниц плиток «Подробно» по единому стилю (claude/rules-style.md): карточка «Сейчас» с символом справа,
 * полукруг ступеней (тот же символ стоит на плитке), линия и штрихи по часам, легенда как у карт, неброское пояснение
 * шкал и источников в конце страницы.
 */

/** Цвета ступеней УФ (ВОЗ: низкий, умеренный, высокий, очень высокий, экстремальный) в приглушённой гамме приложения. */
internal val UvColors = listOf(Color(0xFF6FCF7A), Color(0xFFF2D04B), Color(0xFFF29A3E), Color(0xFFE8584F), Color(0xFFA77BE8))

/** Цвета ступеней EAQI (EEA: от хорошего до крайне плохого). */
internal val AirColors = listOf(
    Color(0xFF5BC8A8), Color(0xFF9ED25F), Color(0xFFF2D04B), Color(0xFFF29A3E), Color(0xFFE8584F), Color(0xFF9B5BC8),
)

/** Давление: пониженное, нормальное, повышенное. */
internal val PressureColors = listOf(Color(0xFF8DB4F0), Color(0xFFB9A6F2), Color(0xFFF2B98A))
internal val PressureLine = Color(0xFFB9A6F2)

/**
 * Карточка «Сейчас»: заголовок, крупное значение с единицей, главное слово, пояснение, символ справа и сведения ниже.
 */
@Composable
internal fun NowCard(
    title: String,
    value: String,
    unit: String?,
    word: String?,
    note: String?,
    symbol: @Composable () -> Unit,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = LocalPogodaPalette.current
    Card {
        CardTitle(title)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = pogodaText(30f, 300, lineHeight = 1.1f, letterSpacing = (-0.5f).sp, tabular = true), maxLines = 1)
                    if (unit != null) {
                        Text(unit, style = pogodaText(13f, 600, color = palette.ink2), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp), maxLines = 1)
                    }
                }
                word?.let { Text(it, style = pogodaText(18f, 600, lineHeight = 1.2f)) }
                note?.let { Text(it, style = pogodaText(12.5f, 500, color = palette.ink2, lineHeight = 1.3f), modifier = Modifier.padding(top = 2.dp)) }
            }
            Box(Modifier.padding(start = 8.dp)) { symbol() }
        }
        Column(content = below)
    }
}

/**
 * Полукруг ступеней: дуга цветами ступеней (границы [bands] в долях шкалы, последняя 1), метка белым кругом с цветом
 * ступени внутри на доле [fraction]. Тот же символ на плитке «Подробно» и в карточке «Сейчас».
 */
@Composable
internal fun StepGauge(fraction: Float, bands: List<Float>, colors: List<Color>, modifier: Modifier = Modifier, width: Dp = 112.dp) {
    Canvas(modifier.size(width = width, height = width * GAUGE_RATIO)) {
        val stroke = 7.dp.toPx()
        val radius = size.width / 2 - stroke
        val c = Offset(size.width / 2, radius + stroke)
        var from = 0f
        bands.forEachIndexed { i, to ->
            drawArc(
                colors[i.coerceAtMost(colors.lastIndex)],
                startAngle = 180f + from * 180f,
                sweepAngle = (to - from) * 180f,
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(stroke),
            )
            from = to
        }
        // Круглые концы дуги.
        drawCircle(colors.first(), stroke / 2, Offset(c.x - radius, c.y))
        drawCircle(colors[(bands.size - 1).coerceAtMost(colors.lastIndex)], stroke / 2, Offset(c.x + radius, c.y))
        val f = fraction.coerceIn(0f, 1f)
        val angle = Math.toRadians(180.0 + f * 180.0)
        val at = Offset(c.x + radius * kotlin.math.cos(angle).toFloat(), c.y + radius * kotlin.math.sin(angle).toFloat())
        val band = bands.indexOfFirst { f <= it }.let { if (it < 0) bands.lastIndex else it }
        drawCircle(Color.White, 7.dp.toPx(), at)
        drawCircle(colors[band.coerceAtMost(colors.lastIndex)], 4.dp.toPx(), at)
    }
}

/** Часов в сутках: значения графиков стоят по часу суток (0…23), ось от 00 до 24. */
internal const val HOURS = 24

/** Высота полукруга от ширины: дуга и половина толщины метки. */
private const val GAUGE_RATIO = 0.62f

/** Полоса порывов на линии ветра: заливка и верхняя кромка. */
private const val BAND_FILL = 0.18f
private const val BAND_EDGE = 0.55f

/** Стрелки «куда дует» под линией ветра: каждый третий час. */
private const val ARROW_STEP = 3

/** Время на оси и рядом с пиком второстепенное: мельче и бледнее значений. */
private const val TIME_ALPHA = 0.62f

private fun hourText(hour: Int) = hour.toString().padStart(2, '0') + ":00"

/** Подписи часов под графиком: 00:00, 06:00, 12:00, 18:00, 24:00 (с минутами, чтобы не принять за дату). */
private fun DrawScope.hourAxis(measurer: TextMeasurer, style: TextStyle, x: (Float) -> Float, y: Float) {
    val small = style.copy(fontSize = style.fontSize * 0.94f, color = style.color.copy(alpha = style.color.alpha * TIME_ALPHA))
    listOf(0, 6, 12, 18, 24).forEach { hour ->
        val text = measurer.measure(hourText(hour), small)
        val left = (x(hour / 24f) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
        drawText(text, topLeft = Offset(left, y))
    }
}

/**
 * Линия по часам суток ([values] по номеру часа 0…23, пусто где данных нет): пройденное до [nowIndex] светится и с лёгкой заливкой, дальше пунктир точками; метка «сейчас»
 * с ореолом и вертикалью. Без [nowIndex] (другой день) линия ровная, без свечения и метки. [grid] линии сетки
 * (значение и подпись справа), не больше трёх.
 */
@Composable
internal fun HourLine(
    values: List<Double?>,
    nowIndex: Int?,
    color: Color,
    low: Double,
    high: Double,
    grid: List<Pair<Double, String>>,
    modifier: Modifier = Modifier,
    band: List<Double?>? = null,
    arrows: List<Int?>? = null,
) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = palette.ink2)
    Canvas(modifier.fillMaxWidth().height(if (arrows != null) 178.dp else 150.dp)) {
        val style = label.copy(fontSize = 11.dp.toSp())
        val top = 22.dp.toPx()
        val bottom = size.height - (if (arrows != null) 52.dp else 24.dp).toPx()
        fun x(fraction: Float) = 8.dp.toPx() + (size.width - 16.dp.toPx()) * fraction
        fun xi(i: Int) = x(i / HOURS.toFloat())
        fun y(v: Double) = (bottom - (v - low) / (high - low) * (bottom - top)).toFloat()
        grid.forEach { (v, text) ->
            drawLine(palette.ink.copy(alpha = 0.12f), Offset(0f, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
            val layout = measurer.measure(text, style)
            drawText(layout, topLeft = Offset(size.width - layout.size.width, y(v) - layout.size.height - 1.dp.toPx()))
        }
        val points = values.mapIndexedNotNull { i, v -> v?.let { Offset(xi(i), y(it)) to i } }
        fun path(list: List<Offset>) = Path().apply { list.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        // Полоса порывов: от линии до верхней кромки, без свечения.
        if (band != null) {
            val pairs = values.indices.mapNotNull { i -> val v = values[i]; val b = band.getOrNull(i); if (v != null && b != null) Triple(xi(i), y(v), y(maxOf(b, v))) else null }
            if (pairs.size >= 2) {
                val edge = pairs.map { Offset(it.first, it.third) }
                val area = path(edge).apply { pairs.asReversed().forEach { lineTo(it.first, it.second) }; close() }
                drawPath(area, color.copy(alpha = BAND_FILL))
                drawPath(path(edge), color.copy(alpha = BAND_EDGE), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        // Стрелки «куда дует» через каждые 3 часа под графиком.
        arrows?.forEachIndexed { i, from ->
            if (from != null && i % ARROW_STEP == 1) {
                drawWindArrow(from, palette.ink.copy(alpha = 0.8f), 18.dp.toPx(), Offset(xi(i), size.height - 36.dp.toPx()))
            }
        }
        if (points.size >= 2) {
            if (nowIndex == null) {
                drawPath(path(points.map { it.first }), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            } else {
                val past = points.filter { it.second <= nowIndex }.map { it.first }
                val future = points.filter { it.second >= nowIndex }.map { it.first }
                if (future.size >= 2) {
                    drawPath(
                        path(future),
                        palette.ink.copy(alpha = 0.55f),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))),
                    )
                }
                if (past.size >= 2) {
                    val area = path(past).apply {
                        lineTo(past.last().x, bottom)
                        lineTo(past.first().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), top, bottom))
                    drawGlowPath(path(past), color, 2.4.dp.toPx())
                }
                past.lastOrNull()?.let { at ->
                    drawRect(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f)), at.y, bottom),
                        Offset(at.x - 0.6.dp.toPx(), at.y),
                        Size(1.2.dp.toPx(), (bottom - at.y).coerceAtLeast(0f)),
                    )
                    drawHalo(at, color, 6.dp.toPx() * 2)
                    drawCircle(Color.White.copy(alpha = 0.9f), 7.6.dp.toPx(), at)
                    drawCircle(color, 6.dp.toPx(), at)
                }
            }
        }
        hourAxis(measurer, style, ::x, size.height - 16.dp.toPx())
    }
}

/**
 * Штрихи по часам суток (4 dp, круглые концы) цветом ступени, у всех полная яркость, как в легенде (вариант 2, Дима
 * 21.11): прошедшие часы сплошные, прогноз контуром того же цвета, выбранный час вдвое шире. Над графиком строка
 * выбранного часа («17:00 · 21 · хорошо»): нажми на столбик или проведи пальцем, по умолчанию выбран текущий час (у
 * другого дня самый сильный). Ниже строка самого сильного часа с квадратиком его цвета или [noneText], если его нет.
 * [scaleMax] наименьший верх шкалы, чтобы слабые значения не выглядели большими; [describe] значение часа со словом
 * ступени и единицей; [brief] то же коротко для строки самого сильного часа; [extremeLabel] «Худший час», «Максимум».
 */
@Composable
internal fun HourBars(
    values: List<Double?>,
    nowIndex: Int?,
    colorOf: (Double) -> Color,
    describe: (value: Double, hour: Int) -> String,
    brief: (Double) -> String,
    extremeLabel: String,
    scaleMax: Double,
    modifier: Modifier = Modifier,
    noneText: String? = null,
) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = palette.ink2)
    val peak = values.indices.filter { values[it] != null && values[it]!! > 0 }.maxByOrNull { values[it]!! }
    var selected by remember(values, nowIndex) { mutableIntStateOf(nowIndex ?: peak ?: HOURS / 2) }
    val chosen = values.getOrNull(selected)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hourText(selected), style = pogodaText(12f, 600, color = palette.ink2, tabular = true), maxLines = 1)
            Text(
                chosen?.let { describe(it, selected) } ?: stringResource(R.string.air_no_data),
                style = pogodaText(14f, 700, tabular = true),
                maxLines = 1,
            )
        }
        val best = peak?.let { values[it] }
        if (peak != null && best != null) {
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(9.dp).background(colorOf(best), RoundedCornerShape(3.dp)))
                Text(
                    stringResource(R.string.pg_peak_line, extremeLabel, brief(best), stringResource(R.string.metric_at, hourText(peak))),
                    style = pogodaText(11.5f, 500, color = palette.ink2, tabular = true),
                    maxLines = 1,
                )
            }
        } else if (noneText != null) {
            Text(noneText, style = pogodaText(11.5f, 500, color = palette.ink2), modifier = Modifier.padding(top = 3.dp), maxLines = 1)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(top = 6.dp)
                .pointerInput(Unit) { detectTapGestures { selected = hourAt(it.x, size.width.toFloat()) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> selected = hourAt(change.position.x, size.width.toFloat()) } },
        ) {
            val style = label.copy(fontSize = 11.dp.toSp())
            val top = 4.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            fun x(fraction: Float) = 8.dp.toPx() + (size.width - 16.dp.toPx()) * fraction
            val max = maxOf(scaleMax, values.filterNotNull().maxOrNull() ?: 0.0)
            val line = 1.5.dp.toPx()
            values.forEachIndexed { i, v ->
                val cx = x(i / HOURS.toFloat())
                val value = v ?: 0.0
                val half = (if (i == selected) 4.dp else 2.dp).toPx()
                if (value <= 0) {
                    drawRoundRect(palette.ink.copy(alpha = 0.22f), Offset(cx - half, bottom - 2.dp.toPx()), Size(2 * half, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                    return@forEachIndexed
                }
                val h = maxOf(4.dp.toPx(), (value / max * (bottom - top)).toFloat())
                val color = colorOf(value)
                if (nowIndex != null && i > nowIndex) {
                    // Прогноз: контур того же цвета внутри размеров столбика.
                    drawRoundRect(
                        color,
                        Offset(cx - half + line / 2, bottom - h + line / 2),
                        Size(2 * half - line, (h - line).coerceAtLeast(line)),
                        CornerRadius(half - line / 2),
                        style = Stroke(line),
                    )
                } else {
                    drawRoundRect(color, Offset(cx - half, bottom - h), Size(2 * half, h), CornerRadius(half))
                }
            }
            hourAxis(measurer, style, ::x, size.height - 16.dp.toPx())
        }
    }
}

/** Час суток под точкой касания [x] на холсте шириной [width] (поля по 8 dp). */
private fun androidx.compose.ui.unit.Density.hourAt(x: Float, width: Float): Int {
    val inner = width - 16.dp.toPx()
    return ((x - 8.dp.toPx()) / inner * HOURS).roundToInt().coerceIn(0, HOURS - 1)
}

/** Образцы легенды, как у карт. */
internal enum class Mark { Line, Dots, Square, Tick, Outline, Band, Arrow }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapLegend(items: List<Triple<Mark, Color, String>>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (mark, color, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(width = if (mark == Mark.Square || mark == Mark.Tick || mark == Mark.Outline) 9.dp else if (mark == Mark.Arrow) 12.dp else 16.dp, height = 10.dp)) {
                    val y = size.height / 2
                    when (mark) {
                        Mark.Line -> drawLine(color, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                        Mark.Dots -> drawLine(color, Offset(1.dp.toPx(), y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())))
                        Mark.Square -> drawRoundRect(color, Offset(0f, (size.height - size.width) / 2), Size(size.width, size.width), CornerRadius(3.dp.toPx()))
                        Mark.Tick -> drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx(), StrokeCap.Round)
                        Mark.Outline -> drawRoundRect(
                            color,
                            Offset(0.75.dp.toPx(), (size.height - size.width) / 2 + 0.75.dp.toPx()),
                            Size(size.width - 1.5.dp.toPx(), size.width - 1.5.dp.toPx()),
                            CornerRadius(2.5.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                        Mark.Band -> drawRoundRect(color.copy(alpha = BAND_FILL * 2), Offset(0f, 1.dp.toPx()), Size(size.width, size.height - 2.dp.toPx()), CornerRadius(2.dp.toPx()))
                        Mark.Arrow -> drawWindArrow(225, color, size.height + 2.dp.toPx())
                    }
                }
                Text(text, style = pogodaText(11f, 600, color = palette.ink2), maxLines = 1)
            }
        }
    }
}

/**
 * Карточка графика: заголовок, легенда, сам график. Единицы в углу нет (Дима 22.11: «опять где-то в углу»): она стоит
 * рядом со значением в строке выбранного часа и у подписей сетки.
 */
@Composable
internal fun ChartCard(title: String, legend: List<Triple<Mark, Color, String>>, chart: @Composable () -> Unit) {
    Card {
        CardTitle(title)
        if (legend.isNotEmpty()) MapLegend(legend, Modifier.padding(top = 8.dp, bottom = 2.dp))
        chart()
    }
}

/** Неброское пояснение шкал и источников в конце страницы (вне карточек). */
@Composable
internal fun SourceNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = pogodaText(10.5f, 500, color = LocalPogodaPalette.current.ink.copy(alpha = 0.5f), lineHeight = 1.45f),
        modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Шапка «7 дней»: подписи над своими столбцами (колонка дня 62 dp и зазоры 8 dp, как в строках [io.github.dumahuch.pogodaapp.ui.metric.DaysCard]). */
@Composable
internal fun WeekHeader(middle: String, right: String, extra: String?, rightWidth: Dp, extraWidth: Dp) {
    val style = pogodaText(11.5f, 600, color = LocalPogodaPalette.current.ink2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.width(62.dp))
        Text(middle, style = style, modifier = Modifier.weight(1f), maxLines = 1)
        Text(right, style = style, modifier = Modifier.width(rightWidth), textAlign = TextAlign.End, maxLines = 1)
        if (extra != null) Text(extra, style = style, modifier = Modifier.width(extraWidth), textAlign = TextAlign.Center, maxLines = 1)
    }
}

/**
 * Плитка «Подробно» в общем виде: заголовок и стрелка ›, ниже содержимое (крупное значение, слово, символ страницы);
 * нажимается целиком и открывает страницу.
 */
@Composable
internal fun PageTile(title: String, onOpen: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .glassCard(RoundedCornerShape(22.dp), LocalGlass.current)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = pogodaText(12.5f, 700, color = palette.ink2), modifier = Modifier.weight(1f), maxLines = 1)
            LineIconView(LineIcons.ChevronRight, 16.dp, palette.ink2)
        }
        content()
    }
}

/** Крупное значение плитки и слово под ним. */
@Composable
internal fun TileValue(value: String, unit: String?, word: String?) {
    BigValue(value, unit)
    word?.let { Text(it, style = pogodaText(14f, 600, lineHeight = 1.25f), maxLines = 2) }
}

/** Плитка «Подробно» с крупным значением, словом и символом-полукругом внизу по центру. */
@Composable
internal fun ColumnScope.GaugeTileBody(value: String, unit: String?, word: String?, gauge: @Composable () -> Unit) {
    TileValue(value, unit, word)
    // Полукруг прижат к низу: соседняя плитка в ряду может быть выше.
    Spacer(Modifier.weight(1f).height(6.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { gauge() }
}

/** «Нет данных» на плитке. */
@Composable
internal fun NoData() {
    BigValue("–")
    Text(stringResource(R.string.air_no_data), style = pogodaText(12.5f, 500, color = LocalPogodaPalette.current.ink2))
}
