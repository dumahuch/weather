package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HourlyGeometry
import io.github.dumahuch.pogodaapp.home.hourLabel
import io.github.dumahuch.pogodaapp.home.hourPanelOf
import io.github.dumahuch.pogodaapp.home.isStormWet
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.ZoneId
import java.util.Locale

internal val HourColumnWidth = HourlyGeometry.COLUMN_WIDTH.dp
internal val HourStripHeight = HourlyGeometry.HEIGHT.dp

/**
 * Насколько верх подписи температуры выше точки кривой: 22 в макете минус 2,5, потому что Compose ставит
 * базовую линию строки высотой в 1 em ниже, чем браузер (там строка обрезается поровну сверху и снизу).
 */
internal const val TEMPERATURE_LABEL_RISE = 24.5f

/** Карточка «По часам»: заголовок и своё содержимое (лента или заглушки). */
@Composable
fun HourlyFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(26.dp), glass)
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        // Подпись «Осадки, мм» в углу убрана (22.11.2026): единица стоит у самого количества над водой.
        Text(
            stringResource(R.string.hourly_title),
            style = pogodaText(13f, 700),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
        )
        content()
    }
}

/**
 * Лента часов: время, значок, температура на плавной линии, осадки водой снизу (вариант В1, 22.11.2026).
 * На «Главной» это 48 часов с первой колонкой «Сейчас» и подписью дня в полночь. В подробностях дня ([markNow] и
 * [dayBreaks] выключены) это сутки: подписи только часы, без отметки «Сейчас» и без границы суток, а лента
 * сразу прокручена на [initialColumn]-ю колонку (для сегодняшнего дня это текущий час).
 * Нажатие на час, для которого в [details] есть значения, подсвечивает его и раскрывает под лентой панель
 * (давление, ощущается как, влажность, ветер); нажатие на другой час переносит выбор, на тот же сворачивает панель.
 */
@Composable
fun HourlyStrip(
    points: List<HourPoint>,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    markNow: Boolean = true,
    dayBreaks: Boolean = true,
    initialColumn: Int = 0,
    details: List<HourDetails> = emptyList(),
) {
    if (points.isEmpty()) return
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val nowLabel = stringResource(R.string.now)
    val locale = LocalLocale.current.platformLocale
    val rounded = remember(points) { points.map { roundTemperature(it.temperature) } }
    val curve = remember(rounded) { HourlyGeometry.curveY(rounded) }
    val precipitation = remember(points) { points.map { it.precipitationMm } }
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (HourColumnWidth * initialColumn).roundToPx() })
    val panels = remember(points, details) {
        val byTime = details.associateBy { it.time }
        points.map { hourPanelOf(byTime[it.time]) }
    }
    // Выбор хранится по времени часа: когда лента сдвинется, он останется на том же часе или пропадёт вместе с ним.
    var selectedTime by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedIndex = selectedTime?.let { key -> points.indexOfFirst { it.time.epochSecond == key } }
        ?.takeIf { it >= 0 && panels[it] != null }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .edgeFade(18.dp)
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp),
        ) {
            Box(
                Modifier
                    .width(HourColumnWidth * points.size)
                    .height(HourStripHeight),
            ) {
                WaterLayer(precipitation, locale, Modifier.matchParentSize())
                CurveLayer(curve, Modifier.matchParentSize())
                Row(Modifier.matchParentSize()) {
                    points.forEachIndexed { index, point ->
                        val local = point.time.atZone(zone)
                        HourColumn(
                            label = when {
                                index == 0 && markNow -> nowLabel
                                dayBreaks -> hourLabel(point.time, zone, dayNames)
                                else -> "%02d".format(Locale.ROOT, local.hour)
                            },
                            isNow = index == 0 && markNow,
                            isMidnight = dayBreaks && index > 0 && local.hour == 0,
                            point = point,
                            curveY = curve[index],
                            locale = locale,
                            openable = panels[index] != null,
                            selected = index == selectedIndex,
                            onToggle = {
                                selectedTime = if (index == selectedIndex) null else point.time.epochSecond
                            },
                        )
                    }
                }
            }
        }
        HourPanelHost(
            selection = selectedIndex?.let { HourSelection(points[it], panels[it]!!) },
            zone = zone,
        )
    }
}

/**
 * Осадки водой снизу ленты (вариант В1, решение Димы 22.11.2026): плавная заливка по часам, тёмная кромка того же тона
 * (не белая: белого свечения на графиках нет), пунктир на 1 мм/ч и над самым сильным часом каждого дождя количество
 * «0,6 мм». В сухие часы ничего (раньше черта и «–» в каждом часе). Высоты: [HourlyGeometry.waterHeight].
 */
@Composable
internal fun WaterLayer(values: List<Double>, locale: Locale, modifier: Modifier) {
    if (values.none { it > 0.0 }) return
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val mm = stringResource(R.string.mm_unit)
    val scale = stringResource(R.string.hourly_water_scale)
    val labelStyle = remember(palette) { pogodaText(12f, 600, color = palette.ink, lineHeight = 1f, tabular = true) }
    val scaleStyle = remember(palette) { pogodaText(10.5f, 500, color = palette.ink2, lineHeight = 1f) }
    val heights = remember(values) { values.map { HourlyGeometry.waterHeight(it) } }
    val peaks = remember(values) { HourlyGeometry.waterPeaks(values) }
    val edge = if (palette.darkInk) WaterEdgeOnLight else WaterEdgeOnDark
    Canvas(modifier) {
        val unit = density
        val width = HourColumnWidth.toPx()
        val bottom = size.height
        fun x(index: Int) = index * width + width / 2
        fun y(index: Int) = bottom - heights[index] * unit
        val surface = Path().apply {
            moveTo(0f, y(0))
            lineTo(x(0), y(0))
            for (i in 1 until heights.size) {
                val middle = (x(i - 1) + x(i)) / 2
                cubicTo(middle, y(i - 1), middle, y(i), x(i), y(i))
            }
            lineTo(size.width, y(heights.lastIndex))
        }
        val water = Path().apply {
            addPath(surface)
            lineTo(size.width, bottom)
            lineTo(0f, bottom)
            close()
        }
        val oneMm = bottom - HourlyGeometry.WATER_ONE_MM * unit
        drawLine(
            palette.ink.copy(alpha = 0.35f),
            Offset(0f, oneMm),
            Offset(size.width, oneMm),
            strokeWidth = 1f * unit,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit)),
        )
        // Подписи пиков считаем заранее: подпись шкалы «1 мм/ч» сдвигается вправо, если налезает на одну из них.
        val peakLabels = peaks.map { peak ->
            val text = measurer.measure("${precipitationText(values[peak], locale)} $mm", labelStyle)
            text to Offset(x(peak) - text.size.width / 2f, y(peak) - text.size.height - 4f * unit)
        }
        val scaleText = measurer.measure(scale, scaleStyle)
        val scaleTop = oneMm - scaleText.size.height - 2f * unit
        var scaleLeft = 4f * unit
        var moved = true
        while (moved) {
            moved = false
            for ((text, at) in peakLabels) {
                val overlapsX = scaleLeft < at.x + text.size.width + 4f * unit && at.x < scaleLeft + scaleText.size.width + 4f * unit
                val overlapsY = scaleTop < at.y + text.size.height && at.y < scaleTop + scaleText.size.height
                if (overlapsX && overlapsY) {
                    scaleLeft = at.x + text.size.width + 8f * unit
                    moved = true
                }
            }
        }
        if (scaleLeft + scaleText.size.width <= size.width) drawText(scaleText, topLeft = Offset(scaleLeft, scaleTop))
        drawPath(water, WaterFill)
        // Кромка только над водой: у дна (сухие часы) линия не нужна.
        clipRect(bottom = bottom - 1.5f * unit) {
            drawPath(surface, edge, style = Stroke(width = 2f * unit, cap = StrokeCap.Round))
        }
        for ((text, at) in peakLabels) drawText(text, topLeft = at)
    }
}

private val WaterFill = Color(0xFF2A8FE0).copy(alpha = 0.62f)
private val WaterEdgeOnLight = Color(0xFF0F4C9A)
private val WaterEdgeOnDark = Color(0xFF8FC3F5)

@Composable
internal fun CurveLayer(curve: List<Float>, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier) {
        if (curve.isEmpty()) return@Canvas
        val unit = density
        val width = HourColumnWidth.toPx()
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
    locale: Locale,
    openable: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val showDetails = stringResource(R.string.hour_show_details)
    val mmUnit = stringResource(R.string.mm_unit)
    Box(
        Modifier
            .width(HourColumnWidth)
            .fillMaxHeight()
            .then(if (selected) Modifier.selectedColumn(palette.ink, palette.accent) else Modifier)
            .then(if (isMidnight) Modifier.dayDivider(palette.ink) else Modifier)
            .then(
                if (openable) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = showDetails,
                        onClick = onToggle,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                // Количество осадков нарисовано водой на общем слое; для чтения с экрана оно здесь, у своего часа.
                if (point.precipitationMm > 0) stateDescription = "${precipitationText(point.precipitationMm, locale)} $mmUnit"
            },
    ) {
        val labelStyle = pogodaText(
            size = 12f,
            weight = if (isMidnight) 700 else 500,
            color = if (isNow || isMidnight || selected) palette.ink else palette.ink2,
            lineHeight = 20f / 12f,
            tabular = true,
        )
        val labelModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 2.dp)
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
        if (isNow || selected) {
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

        SmallWeatherGlyph(
            condition = point.condition,
            isDay = point.isDay,
            wet = isStormWet(point.precipitationMm),
            size = 24.dp,
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
    }
}

/** Выбранный час: столбец чуть светлее, внизу стрелка вниз к раскрытой панели. */
private fun Modifier.selectedColumn(ink: Color, accent: Color): Modifier = drawBehind {
    drawRoundRect(
        color = ink.copy(alpha = 0.08f),
        topLeft = Offset(2.dp.toPx(), 0f),
        size = Size(size.width - 4.dp.toPx(), size.height),
        cornerRadius = CornerRadius(14.dp.toPx()),
    )
    val half = 6.dp.toPx()
    val tip = Path().apply {
        moveTo(size.width / 2 - half, size.height - 7.dp.toPx())
        lineTo(size.width / 2 + half, size.height - 7.dp.toPx())
        lineTo(size.width / 2, size.height - 1.dp.toPx())
        close()
    }
    drawPath(tip, accent)
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
internal fun Modifier.edgeFade(width: Dp): Modifier = this
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
