package io.github.dumahuch.pogodaapp.ui.sunmoon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.LightPhase
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.Manrope
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.pogodaText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Рисунки «Солнца и Луны» (0.8.77, макет https://claude.ai/artifact/1nRbyKdjC7NTVMDYS1g1B3) и общий стиль графиков,
 * принятый Димой 21.11.2026: светится главное (пройденный путь, «сейчас»), будущее пунктиром, у метки ореол и тонкая
 * вертикаль к оси. Свечение рисуется несколькими широкими прозрачными мазками под линией: размытие (`setMaskFilter`)
 * аппаратная отрисовка не поддерживает, а слой с размытием на каждый график дороже.
 */

/** Цвета (RGB): солнце, луна и свет суток на полосе. Читаемость меток сверяет `SunMoonShapeTest`. */
internal object SunMoonColors {
    const val SUN = 0xF2B01E
    const val SUN_ICON = 0x3B2A00
    const val MOON_LINE = 0x8DB4F0
    const val MOON_FILL = 0x2F5FB3
    const val MOON_ICON = 0xFFFFFF
    const val MOON_LIT = 0xE9EEF6
    const val GOLD_TEXT = 0xF5C451

    const val NIGHT = 0x141D36
    const val ASTRONOMICAL = 0x26335F
    const val NAUTICAL = 0x34528F
    const val BLUE = 0x4F82D9
    const val GOLDEN = 0xF2B01E
    const val DAY = 0xF4E6BC

    fun of(phase: LightPhase): Int = when (phase) {
        LightPhase.Night -> NIGHT
        LightPhase.Astronomical -> ASTRONOMICAL
        LightPhase.Nautical -> NAUTICAL
        LightPhase.Blue -> BLUE
        LightPhase.Golden -> GOLDEN
        LightPhase.Day -> DAY
    }
}

internal fun rgb(value: Int) = Color(0xFF000000.toInt() or value)

/**
 * Форма кривой «Солнце и Луна»: колокол (функция Гаусса) с плоскими хвостами вместо параболы, по образцу Димы.
 * Концы пути (восход и закат) на [LEFT] и [RIGHT] долях ширины, там кривая пересекает горизонт; за ними хвосты чуть
 * ниже горизонта тают к краям карточки.
 */
internal object BellShape {
    const val LEFT = 0.13f
    const val RIGHT = 0.87f

    /** Высота кривой у концов пути (доля вершины до сдвига вниз): чем меньше, тем круче склоны. */
    private const val EDGE = 0.1f
    private val sigma = (RIGHT - LEFT) / 2f / sqrt(2f * ln(1f / EDGE))

    /** Насколько хвосты ниже горизонта в долях высоты вершины. */
    val TAIL = EDGE / (1f - EDGE)

    /** Подъём над горизонтом в долях вершины для доли ширины [x]: 1 на вершине, 0 у концов, ниже нуля на хвостах. */
    fun lift(x: Float): Float {
        val d = (x - (LEFT + RIGHT) / 2f) / sigma
        return (exp(-d * d / 2f) - EDGE) / (1f - EDGE)
    }

    /** Доля ширины для доли пройденного пути. */
    fun xAt(fraction: Float): Float = LEFT + (RIGHT - LEFT) * fraction.coerceIn(0f, 1f)
}

/** Мазки свечения под линией: во сколько раз шире линии и с какой прозрачностью. */
private val GLOW = listOf(5f to 0.07f, 3.4f to 0.12f, 2.2f to 0.22f)

/** Светящаяся линия: мягкие широкие мазки под ней и сама линия поверх. */
internal fun DrawScope.drawGlowPath(path: Path, color: Color, width: Float) {
    GLOW.forEach { (scale, alpha) ->
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Ореол метки: круг, мягко гаснущий от середины к краю (три ступени, Дима 21.11: вдвое меньше и мягче). */
internal fun DrawScope.drawHalo(at: Offset, color: Color, radius: Float) {
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = 0.4f),
        0.45f to color.copy(alpha = 0.16f),
        1f to color.copy(alpha = 0f),
        center = at,
        radius = radius,
    )
    drawCircle(brush, radius, at)
}

/**
 * Кривая пути солнца (днём) или ночи (метка луны). [fraction] сколько пройдено от 0 до 1; пусто, если метки нет
 * (другой день). Горизонт и пунктир пути тают у краёв, пройденная часть светится, под ней лёгкая заливка.
 */
@Composable
internal fun BellCurve(fraction: Float?, daytime: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val color = rgb(if (daytime) SunMoonColors.SUN else SunMoonColors.MOON_LINE)
    val icon = remember(daytime) {
        (if (daytime) LineIcons.Sun else LineIcons.Moon).paths.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier) {
        val w = size.width
        val markerRadius = MARKER_DP.dp.toPx()
        val top = markerRadius + 4.dp.toPx()
        val bottom = 6.dp.toPx()
        // Горизонт так, чтобы вершина и метка поместились сверху, а хвосты под горизонтом снизу.
        val horizon = (size.height - bottom + BellShape.TAIL * top) / (1f + BellShape.TAIL)
        val amp = horizon - top
        fun y(x: Float) = horizon - amp * BellShape.lift(x / w)
        val steps = (w / 2.dp.toPx()).toInt().coerceAtLeast(16)
        val full = Path().apply {
            moveTo(0f, y(0f))
            for (i in 1..steps) lineTo(w * i / steps, y(w * i / steps))
        }
        val ink = palette.ink
        val fade = Brush.horizontalGradient(
            0f to ink.copy(alpha = 0f),
            EDGE_FADE to ink,
            1f - EDGE_FADE to ink,
            1f to ink.copy(alpha = 0f),
        )
        drawLine(fade, Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.dp.toPx(), alpha = HORIZON_ALPHA)
        drawPath(
            full,
            fade,
            alpha = DOTS_ALPHA,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
            ),
        )
        val x1 = w * BellShape.LEFT
        val x2 = w * BellShape.RIGHT
        if (fraction != null) {
            val xp = w * BellShape.xAt(fraction)
            val passed = Path().apply {
                moveTo(x1, y(x1))
                var x = x1
                while (x < xp) {
                    lineTo(x, y(x))
                    x += 1.5.dp.toPx()
                }
                lineTo(xp, y(xp))
            }
            val area = Path().apply {
                addPath(passed)
                lineTo(xp, horizon)
                lineTo(x1, horizon)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), top, horizon))
            drawGlowPath(passed, color, 2.4.dp.toPx())
        }
        drawCircle(ink.copy(alpha = 0.9f), END_DOT_DP.dp.toPx(), Offset(x1, horizon))
        drawCircle(ink.copy(alpha = 0.9f), END_DOT_DP.dp.toPx(), Offset(x2, horizon))
        if (fraction != null) {
            val at = Offset(w * BellShape.xAt(fraction), y(w * BellShape.xAt(fraction)))
            drawRect(
                Brush.verticalGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f)), at.y, horizon),
                Offset(at.x - 0.6.dp.toPx(), at.y),
                Size(1.2.dp.toPx(), (horizon - at.y).coerceAtLeast(0f)),
            )
            drawMarker(at, daytime, icon, markerRadius)
        }
    }
}

/** Метка: ореол, светлый ободок, залитый круг (жёлтый с солнцем, синий с месяцем) и пиктограмма. */
private fun DrawScope.drawMarker(at: Offset, daytime: Boolean, icon: List<Path>, radius: Float) {
    val fill = rgb(if (daytime) SunMoonColors.SUN else SunMoonColors.MOON_FILL)
    val ink = rgb(if (daytime) SunMoonColors.SUN_ICON else SunMoonColors.MOON_ICON)
    drawHalo(at, if (daytime) fill else rgb(SunMoonColors.MOON_LINE), radius * HALO_SCALE)
    drawCircle(Color.White.copy(alpha = 0.92f), radius + 1.5.dp.toPx(), at)
    drawCircle(fill, radius, at)
    val side = radius * 1.4f
    withTransform({
        translate(at.x - side / 2, at.y - side / 2)
        scale(side / ICON_VIEWPORT, side / ICON_VIEWPORT, pivot = Offset.Zero)
    }) {
        icon.forEach { drawPath(it, ink, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

/**
 * Полоса суток по свету: фазы переходят одна в другую плавно, как на настоящем небе, без свечения (Дима 21.11: пятна
 * свечения выглядели чужеродно). Метка «сейчас» тонкой белой чертой с временем над ней ([nowFraction] доля суток; пусто,
 * если день не сегодняшний), под полосой часы 00, 06, 12, 18, 24.
 */
@Composable
internal fun LightStrip(phases: List<LightPhase>, nowFraction: Float?, nowLabel: String?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = palette.ink2)
    Canvas(modifier) {
        if (phases.isEmpty()) return@Canvas
        val w = size.width
        val stripTop = 18.dp.toPx()
        val stripHeight = 18.dp.toPx()
        val radius = stripHeight / 2
        drawRoundRect(
            Brush.horizontalGradient(*stripStops(phases).toTypedArray(), startX = 0f, endX = w),
            Offset(0f, stripTop),
            Size(w, stripHeight),
            CornerRadius(radius),
        )
        val textStyle = labelStyle.copy(fontSize = 11.dp.toSp())
        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val text = measurer.measure(hour.mod(24).toString().padStart(2, '0'), textStyle)
            val x = (w * hour / 24f - text.size.width / 2f).coerceIn(0f, w - text.size.width)
            drawText(text, topLeft = Offset(x, stripTop + stripHeight + 4.dp.toPx()))
        }
        if (nowFraction != null) {
            val x = w * nowFraction.coerceIn(0f, 1f)
            drawLine(Color.White, Offset(x, stripTop - 5.dp.toPx()), Offset(x, stripTop + stripHeight + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            if (nowLabel != null) {
                val text = measurer.measure(nowLabel, textStyle.copy(color = palette.ink, fontWeight = FontWeight.Bold))
                val left = (x - text.size.width / 2f).coerceIn(0f, w - text.size.width)
                drawText(text, topLeft = Offset(left, 0f))
            }
        }
    }
}

/** Отрезки одинакового света подряд: фаза и номера отрезков. */
internal fun runs(phases: List<LightPhase>): List<Pair<LightPhase, IntRange>> {
    val result = ArrayList<Pair<LightPhase, IntRange>>()
    var start = 0
    while (start < phases.size) {
        var end = start + 1
        while (end < phases.size && phases[end] == phases[start]) end++
        result += phases[start] to (start until end)
        start = end
    }
    return result
}

/**
 * Опорные точки плавной полосы: у каждого отрезка свет держится ровным в середине, а к краям на треть отрезка
 * (не больше [BLEND_STEPS] шагов) переходит в соседний.
 */
internal fun stripStops(phases: List<LightPhase>): List<Pair<Float, Color>> {
    val n = phases.size.toFloat()
    return runs(phases).flatMap { (phase, range) ->
        val length = (range.last + 1 - range.first).toFloat()
        val blend = minOf(length / 3f, BLEND_STEPS)
        val color = rgb(SunMoonColors.of(phase))
        listOf((range.first + blend) / n to color, (range.last + 1 - blend) / n to color)
    }
}

/** Ширина перехода между фазами в шагах полосы (шаг 10 минут). */
private const val BLEND_STEPS = 1.2f

/** Образец цвета фазы (легенда, лента событий): скруглённый прямоугольник без свечения. */
@Composable
internal fun PhaseSwatch(phase: LightPhase, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(rgb(SunMoonColors.of(phase)), Offset.Zero, size, CornerRadius(minOf(size.width, size.height) / 2.5f))
    }
}

/** Цвет текста золотого и синего часа: на тёмном небе светлый, на светлом тёмный (читаемость). */
@Composable
internal fun goldInk(): Color = rgb(if (LocalPogodaPalette.current.darkInk) GOLD_ON_LIGHT else SunMoonColors.GOLD_TEXT)

@Composable
internal fun blueInk(): Color = rgb(if (LocalPogodaPalette.current.darkInk) SunMoonColors.MOON_FILL else SunMoonColors.MOON_LINE)

private const val GOLD_ON_LIGHT = 0x8A5A00

/**
 * Стороны горизонта для съёмки: север сверху, буквы и деления как у компаса ветра. Жёлтая светящаяся дуга по краю:
 * где солнце идёт по небу (от восхода к закату через сторону полудня), пунктир внутри: путь луны. Залитая точка восход,
 * пустая заход. Азимуты в градусах от севера; пустые не рисуются.
 */
@Composable
internal fun SkyCompass(sun: SkyPath?, moon: SkyPath?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val letters = stringArrayResource(R.array.compass_letters).toList()
    val measurer = rememberTextMeasurer()
    val letterStyle = remember(palette) { pogodaText(11f, 800, color = palette.ink.copy(alpha = 0.75f), lineHeight = 1f) }
    val northStyle = remember(palette) { letterStyle.copy(color = palette.warning) }
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2 - 8.dp.toPx()
        drawCircle(palette.ink.copy(alpha = 0.05f), outer, c)
        drawCircle(palette.ink.copy(alpha = 0.18f), outer, c, style = Stroke(1.dp.toPx()))
        for (degrees in 0 until 360 step 15) {
            val cardinal = degrees % 90 == 0
            val length = if (cardinal) 7.dp.toPx() else 3.5.dp.toPx()
            val color = when {
                degrees == 0 -> palette.warning
                cardinal -> palette.ink.copy(alpha = 0.6f)
                else -> palette.ink.copy(alpha = 0.25f)
            }
            drawLine(color, c + point(degrees.toDouble(), outer), c + point(degrees.toDouble(), outer - length), 1.2.dp.toPx(), StrokeCap.Round)
        }
        val letterRadius = outer - 17.dp.toPx()
        listOf(0, 90, 180, 270).forEachIndexed { index, degrees ->
            val letter = letters.getOrNull(index) ?: return@forEachIndexed
            val layout = measurer.measure(letter, if (index == 0) northStyle else letterStyle)
            val at = c + point(degrees.toDouble(), letterRadius)
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
        val sunColor = rgb(SunMoonColors.SUN)
        val moonColor = rgb(SunMoonColors.MOON_LINE)
        if (sun != null) {
            val arc = arcOf(sun, c, outer)
            GLOW.forEach { (scale, alpha) ->
                drawPath(arc, sunColor.copy(alpha = alpha), style = Stroke(2.2.dp.toPx() * scale, cap = StrokeCap.Round))
            }
            drawPath(arc, sunColor, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        }
        val moonRadius = outer - 10.dp.toPx()
        if (moon != null) {
            drawPath(
                arcOf(moon, c, moonRadius),
                moonColor,
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()))),
            )
        }
        fun dot(azimuth: Double?, ring: Float, color: Color, filled: Boolean, dotRadius: Float) {
            if (azimuth == null) return
            val at = c + point(azimuth, ring)
            if (filled) drawCircle(color, dotRadius, at) else drawCircle(color, dotRadius - 1.dp.toPx(), at, style = Stroke(2.dp.toPx()))
        }
        dot(sun?.rise, outer, sunColor, true, 5.dp.toPx())
        dot(sun?.set, outer, sunColor, false, 5.dp.toPx())
        dot(moon?.rise, moonRadius, moonColor, true, 4.dp.toPx())
        dot(moon?.set, moonRadius, moonColor, false, 4.dp.toPx())
        drawCircle(palette.ink.copy(alpha = 0.6f), 2.5.dp.toPx(), c)
    }
}

/** Путь по горизонту: азимут восхода и захода и сторона, через которую тело идёт (азимут самой высокой точки). */
internal data class SkyPath(val rise: Double?, val set: Double?, val via: Double)

/** Смещение от центра для азимута (север вверх, восток вправо). */
private fun point(azimuth: Double, radius: Float): Offset {
    val a = Math.toRadians(azimuth)
    return Offset((sin(a) * radius).toFloat(), (-cos(a) * radius).toFloat())
}

/** Дуга по кругу от восхода к заходу через сторону [SkyPath.via]; без восхода или захода пустая. */
private fun arcOf(path: SkyPath, c: Offset, radius: Float): Path {
    val rise = path.rise ?: return Path()
    val set = path.set ?: return Path()
    val sweep = arcSweep(rise, set, path.via)
    return Path().apply {
        arcTo(Rect(c, radius), (rise - 90).toFloat(), sweep.toFloat(), true)
    }
}

/** Угол дуги от [from] до [to] (градусы, по часовой плюс) так, чтобы она прошла через [via]. */
internal fun arcSweep(from: Double, to: Double, via: Double): Double {
    val clockwise = (((to - from) % 360) + 360) % 360
    val viaOffset = (((via - from) % 360) + 360) % 360
    return if (viaOffset <= clockwise) clockwise else clockwise - 360
}

/**
 * Фаза луны: тусклый диск и освещённая часть (терминатор эллипсом). [litOnRight]: у растущей луны в северном полушарии
 * свет справа, у убывающей слева; в южном наоборот.
 */
@Composable
internal fun MoonGlyph(illumination: Double, litOnRight: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f
        val c = center
        val lit = rgb(SunMoonColors.MOON_LIT)
        drawCircle(palette.ink.copy(alpha = 0.10f), r, c)
        drawCircle(palette.ink.copy(alpha = 0.18f), r, c, style = Stroke(1.dp.toPx()))
        val k = illumination.toFloat().coerceIn(0f, 1f)
        if (k < 0.01f) return@Canvas
        drawCircle(Brush.radialGradient(listOf(lit.copy(alpha = 0.22f * k), lit.copy(alpha = 0f)), c, r * 1.35f), r * 1.35f, c)
        val rx = abs(2f * k - 1f) * r
        val disc = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
        val terminator = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
        val path = Path().apply {
            // Правая половина диска сверху вниз, затем терминатор снизу вверх: влево (больше половины) или вправо (серп).
            arcTo(disc, -90f, 180f, true)
            arcTo(terminator, 90f, if (k > 0.5f) 180f else -180f, false)
            close()
        }
        scale(if (litOnRight) 1f else -1f, 1f, pivot = c) { drawPath(path, lit) }
    }
}

private const val MARKER_DP = 9f
private const val HALO_SCALE = 2f
private const val END_DOT_DP = 2.6f
private const val ICON_VIEWPORT = 24f
private const val EDGE_FADE = 0.08f
private const val HORIZON_ALPHA = 0.3f
private const val DOTS_ALPHA = 0.5f
