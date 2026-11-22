package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.goldInk
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Рисунки страниц ветра, осадков и влажности (0.8.83, выбор Димы 22.11.2026): компас в духе Apple с выделенным
 * севером, круг суток из столбиков осадков с дугой дня и ночи внутри, полукруг ступеней точки росы.
 */

/** Сила осадков (AMS): слабые, умеренные, сильные. */
internal val RainColors = listOf(Color(0xFFA9D3F7), Color(0xFF6FA6EE), Color(0xFF4C78E0))

/** Ощущение по точке росы (NWS): комфортно, ощутимо влажно, душно; шкала полукруга 0…25 °C. */
internal val DewColors = listOf(Color(0xFF8CCFE6), Color(0xFFF2D04B), Color(0xFFF29A3E))
private const val DEW_TOP_C = 25f
internal val DewBands = listOf(13f / DEW_TOP_C, 18f / DEW_TOP_C, 1f)

internal fun dewFraction(celsius: Double): Float = (celsius.toFloat() / DEW_TOP_C).coerceIn(0f, 1f)

/** Красный треугольник севера на кольце компаса. */
private val NorthMark = Color(0xFFE8584F)

/**
 * Компас: частые тонкие деления (каждые 3°, длиннее каждые 30°), буквы сторон света в кольце, север тёплой буквой и
 * красным треугольником на кольце. Белая стрелка через круг от стороны, откуда дует, наконечником туда, куда дует;
 * при [speed] стрелка разорвана вокруг числа в центре.
 */
@Composable
internal fun WindCompass(fromDegrees: Int, size: Dp, modifier: Modifier = Modifier, speed: String? = null, unit: String? = null) {
    val palette = LocalPogodaPalette.current
    val letters = stringArrayResource(R.array.compass_letters).toList()
    val measurer = rememberTextMeasurer()
    val north = goldInk()
    // Большой компас (страница «Направление») крупнее и по буквам, и по числу; мелкий остаётся прежним.
    val scale = (size.value / COMPASS_BASE_DP).coerceIn(1f, MAX_FONT_SCALE)
    val letterStyle = remember(palette, scale) { pogodaText(12f * scale, 700, color = palette.ink, lineHeight = 1f) }
    val numberStyle = remember(palette, scale) { pogodaText(24f * scale, 500, color = palette.ink, lineHeight = 1f, tabular = true) }
    val unitStyle = remember(palette, scale) { pogodaText(10f * scale, 600, color = palette.ink2, lineHeight = 1f) }
    Canvas(modifier.size(size).semantics { contentDescription = listOfNotNull(speed, unit).joinToString(" ") }) {
        val c = center
        val k = this.size.minDimension / 120f
        val r = this.size.minDimension / 2 - 2.dp.toPx()
        for (g in 0 until 360 step TICK_DEGREES) {
            val nearCardinal = (0 until 360 step 90).any { val d = Math.floorMod(g - it + 180, 360) - 180; kotlin.math.abs(d) < CARDINAL_GAP }
            if (nearCardinal) continue
            val major = g % 30 == 0
            val length = (if (major) 7f else 5f) * k
            rotate(g.toFloat(), c) {
                drawLine(
                    palette.ink.copy(alpha = if (major) 0.75f else 0.32f),
                    Offset(c.x, c.y - r),
                    Offset(c.x, c.y - r + length),
                    (if (major) 1.3f else 0.8f) * k,
                )
            }
        }
        // Север: красный треугольник на кольце.
        val tri = Path().apply {
            moveTo(c.x - 4f * k, c.y - r - 1f * k)
            lineTo(c.x + 4f * k, c.y - r - 1f * k)
            lineTo(c.x, c.y - r + 7f * k)
            close()
        }
        drawPath(tri, NorthMark)
        letters.take(4).forEachIndexed { i, letter ->
            val style = if (i == 0) letterStyle.copy(color = north) else letterStyle
            val layout = measurer.measure(letter, style)
            val radius = if (i == 0) r - 15f * k else r - 4f * k
            val a = Math.toRadians(i * 90.0)
            val at = Offset(c.x + (radius * sin(a)).toFloat(), c.y - (radius * cos(a)).toFloat())
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
        val reach = r - 15f * k
        val gap = if (speed != null) 20f * k else 0f
        rotate(fromDegrees + 180f, c) {
            val width = 2.4f * k
            // Хвост со стороны, откуда дует (внизу после поворота), наконечник вверху.
            drawLine(palette.ink, Offset(c.x, c.y + reach), Offset(c.x, c.y + gap), width, StrokeCap.Round)
            drawLine(palette.ink, Offset(c.x, c.y - gap), Offset(c.x, c.y - reach + 6f * k), width, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x, c.y - reach)
                lineTo(c.x - 5f * k, c.y - reach + 10f * k)
                lineTo(c.x + 5f * k, c.y - reach + 10f * k)
                close()
            }
            drawPath(head, palette.ink)
        }
        if (speed != null) {
            val number = measurer.measure(speed, numberStyle)
            drawText(number, topLeft = Offset(c.x - number.size.width / 2f, c.y - number.size.height * 0.62f))
            unit?.let {
                val u = measurer.measure(it, unitStyle)
                drawText(u, topLeft = Offset(c.x - u.size.width / 2f, c.y + number.size.height * 0.42f))
            }
        }
    }
}

private const val COMPASS_BASE_DP = 168f
private const val MAX_FONT_SCALE = 1.4f
private const val TICK_DEGREES = 3
private const val CARDINAL_GAP = 7

/**
 * Круг суток из столбиков осадков (сверху полночь, по часовой), размер задаёт [modifier] (макет 22.11: большой, во всю
 * карточку): столбик часа наружу, длина по количеству, цвет по силе; прошедшие часы сплошные, прогноз контуром, выбранный
 * час шире. Нажми на столбик или нажми, задержись и проведи: в центре время выбранного часа и его осадки; по умолчанию
 * выбран текущий час (обычное проведение вдоль страницы прокручивает её, а не выбирает час). Внутри дуга дня (золотая)
 * и ночи (тёмно-синяя) с плавными рассветом и закатом через [sunriseHour] и [sunsetHour] (доли часа; пусто в полярный
 * день и ночь, тогда дуги нет); светлая кайма вдоль дуги держит её читаемой на тёмном ночном небе.
 */
@Composable
internal fun RainRing(
    values: List<Double?>,
    nowIndex: Int?,
    sunriseHour: Double?,
    sunsetHour: Double?,
    valueText: (Double) -> String,
    dryText: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val peak = values.indices.filter { (values[it] ?: 0.0) > 0 }.maxByOrNull { values[it]!! }
    var selected by remember(values, nowIndex) { mutableIntStateOf(nowIndex ?: peak ?: HOURS / 2) }
    val timeStyle = remember(palette) { pogodaText(26f, 500, color = palette.ink, lineHeight = 1f, tabular = true) }
    val valueStyle = remember(palette) { pogodaText(15f, 600, color = palette.ink2, lineHeight = 1f, tabular = true) }
    fun pick(at: Offset, side: Float) {
        val dx = at.x - side / 2
        val dy = at.y - side / 2
        var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
        if (angle < 0) angle += 360.0
        selected = floor(angle / DEGREES_PER_HOUR).toInt().coerceIn(0, HOURS - 1)
    }
    val selectedValue = values.getOrNull(selected) ?: 0.0
    val description = "%02d:00, %s".format(java.util.Locale.ROOT, selected, if (selectedValue > 0) "${valueText(selectedValue)} $unit" else dryText)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(values) { detectTapGestures { pick(it, this.size.width.toFloat()) } }
            .pointerInput(values) { detectDragGesturesAfterLongPress(onDragStart = { pick(it, this.size.width.toFloat()) }) { change, _ -> pick(change.position, this.size.width.toFloat()) } },
    ) {
        val c = center
        val k = this.size.minDimension / 120f
        val inner = RING_INNER * k
        val longest = RING_LONGEST * k
        if (sunriseHour != null && sunsetHour != null) dayArc(c, RING_ARC_RADIUS * k, RING_ARC_WIDTH * k, sunriseHour, sunsetHour)
        val max = maxOf(MIN_RING_MM, values.filterNotNull().maxOrNull() ?: 0.0)
        val line = 1.5f * k
        values.forEachIndexed { h, v ->
            val value = v ?: 0.0
            val angle = (h + 0.5f) * DEGREES_PER_HOUR
            val w = (if (h == selected) 3.6f else 2.0f) * k
            rotate(angle, c) {
                if (value <= 0) {
                    drawCircle(palette.ink.copy(alpha = 0.28f), (if (h == selected) 1.5f else 0.9f) * k, Offset(c.x, c.y - inner))
                } else {
                    val len = maxOf(3f * k, (value / max).toFloat() * longest)
                    val color = RainColors[rainLevel(value)]
                    if (nowIndex != null && h > nowIndex) {
                        drawRoundRect(
                            color,
                            Offset(c.x - w / 2 + line / 2, c.y - inner - len + line / 2),
                            Size(w - line, (len - line).coerceAtLeast(line)),
                            CornerRadius(w / 2 - line / 2),
                            style = Stroke(line),
                        )
                    } else {
                        drawRoundRect(color, Offset(c.x - w / 2, c.y - inner - len), Size(w, len), CornerRadius(w / 2))
                    }
                }
            }
        }
        val time = measurer.measure("%02d:00".format(java.util.Locale.ROOT, selected), timeStyle)
        val main = measurer.measure(if (selectedValue > 0) "${valueText(selectedValue)} $unit" else dryText, valueStyle)
        val total = time.size.height + main.size.height + 6.dp.toPx()
        var y = c.y - total / 2
        drawText(time, topLeft = Offset(c.x - time.size.width / 2f, y))
        y += time.size.height + 6.dp.toPx()
        drawText(main, topLeft = Offset(c.x - main.size.width / 2f, y))
    }
}

private const val HOURS = 24
private const val DEGREES_PER_HOUR = 15f

/** Размеры круга в долях k (одна k = 1/120 стороны): дуга суток, внутренний край и наибольшая длина столбиков. */
private const val RING_ARC_RADIUS = 39f
private const val RING_ARC_WIDTH = 3.4f
private const val RING_INNER = 45f
private const val RING_LONGEST = 13f

/** Наименьший верх шкалы круга, мм за час: слабый дождь не должен выглядеть ливнем. */
private const val MIN_RING_MM = 1.5

internal fun rainLevel(mmPerHour: Double): Int = io.github.dumahuch.pogodaapp.forecast.RainIntensity.ofMmPerHour(mmPerHour).ordinal

/**
 * Ночь, рассвет, день: тёмный индиго, персиковый, золотой (Дима 22.11: ночь должна выглядеть ночью, а не ясным небом);
 * переход занимает по два часа в каждую сторону от восхода и заката.
 */
private val NightTone = Color(0xFF3A4A96)
private val DawnTone = Color(0xFFEC9670)
private val DayTone = Color(0xFFF4B61E)
private const val TWILIGHT_HOURS = 2.0

/** Светлая кайма дуги: на тёмном небе отделяет индиго от фона, на светлом почти не видна. */
private val ArcEdge = Color.White.copy(alpha = 0.55f)

/** Дуга суток внутри круга: тёмно-синяя ночью, золотая днём, с плавными рассветом и закатом и светлой каймой. */
private fun DrawScope.dayArc(c: Offset, radius: Float, width: Float, sunrise: Double, sunset: Double) {
    fun f(hour: Double) = (hour / HOURS).coerceIn(0.0, 1.0).toFloat()
    val stops = listOf(
        0f to NightTone,
        f(sunrise - TWILIGHT_HOURS) to NightTone,
        f(sunrise) to DawnTone,
        f(sunrise + TWILIGHT_HOURS) to DayTone,
        f(sunset - TWILIGHT_HOURS) to DayTone,
        f(sunset) to DawnTone,
        f(sunset + TWILIGHT_HOURS) to NightTone,
        1f to NightTone,
    ).sortedBy { it.first }.toTypedArray()
    drawCircle(ArcEdge, radius, c, style = Stroke(width + 2.6f * (width / RING_ARC_WIDTH)))
    // Градиент по кругу начинается справа (3 часа): поворот на −90°, чтобы полночь была сверху.
    rotate(-90f, c) {
        drawCircle(Brush.sweepGradient(*stops, center = c), radius, c, style = Stroke(width))
    }
}
