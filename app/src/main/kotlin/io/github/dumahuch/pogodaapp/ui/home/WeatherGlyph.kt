package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.dumahuch.pogodaapp.home.HeroIcon
import io.github.dumahuch.pogodaapp.home.heroIconFor
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Выбранный набор значков погоды (настройка «Значки погоды»), задаётся в `MainActivity`. */
val LocalWeatherIconStyle = staticCompositionLocalOf { WeatherIconStyle.Glass }

/** Вид погоды, подставленный экраном проверки; `null`: значок у температуры берёт настоящую погоду. Задаётся в `MainActivity`. */
val LocalHeroConditionOverride = staticCompositionLocalOf<Condition?> { null }

/** Солнечный час (0…24), подставленный экраном проверки; `null`: день или ночь берутся из настоящих данных. */
val LocalHeroSolarHourOverride = staticCompositionLocalOf<Double?> { null }

private const val VIEWPORT = 24f

/** Толщина контура в единицах значка: тоньше на крупном значке, толще на мелком (подбирается по размеру, не в настройках). */
internal fun outlineStroke(size: Dp): Float = if (size.value >= 40f) 1.2f else 1.6f

/** Значок погоды «Главной» в выбранном наборе ([WeatherIconStyle]); рисуется кодом в квадрате [size]. */
@Composable
fun WeatherGlyph(icon: HeroIcon, style: WeatherIconStyle, size: Dp, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val accents = if (palette.darkInk) Accents.OnLight else Accents.OnDark
    when (style) {
        WeatherIconStyle.Glass -> {
            val shapes = remember(icon) { GlassShapes(icon) }
            Canvas(modifier.size(size)) {
                inUnits { drawGlass(icon, shapes, accents, palette.darkInk) }
            }
        }
        WeatherIconStyle.Outline -> {
            val parts = remember(icon) { OutlineParts(icon) }
            val stroke = outlineStroke(size)
            Canvas(modifier.size(size)) {
                inUnits { drawOutline(parts, palette.ink, accents, stroke) }
            }
        }
    }
}

/**
 * Значок погоды картинкой [sizePx] × [sizePx] для уведомления (0.7.0): тот же набор, что на «Главной». [onLight]: фон
 * шторки светлый (дневная тема системы), линии и стекло темнее. При ясной погоде неба за значком нет, поэтому вместо
 * пустого места солнце днём и месяц ночью.
 */
fun weatherGlyphBitmap(condition: Condition, isDay: Boolean, style: WeatherIconStyle, onLight: Boolean, sizePx: Int): ImageBitmap {
    val bitmap = ImageBitmap(sizePx, sizePx)
    val accents = if (onLight) Accents.OnLight else Accents.OnDark
    val icon = heroIconFor(condition, isDay)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(sizePx.toFloat(), sizePx.toFloat())) {
        inUnits {
            when {
                icon == null -> drawClearSky(isDay, accents)
                style == WeatherIconStyle.Glass -> drawGlass(icon, GlassShapes(icon), accents, onLight)
                else -> drawOutline(OutlineParts(icon), if (onLight) NOTIFY_INK_LIGHT else NOTIFY_INK_DARK, accents, NOTIFY_STROKE)
            }
        }
    }
    return bitmap
}

/** Цвет линий контурного значка в шторке: как текст уведомлений Android на светлом и тёмном фоне. */
private val NOTIFY_INK_LIGHT = Color(0xFF1F1F1F)
private val NOTIFY_INK_DARK = Color(0xFFE3E3E3)

/** Значок уведомления мелкий (около 40 dp): линия толще, чем у крупного значка «Главной». */
private const val NOTIFY_STROKE = 1.6f

/** Ясно: солнце с лучами днём, месяц ночью, по центру значка. */
private fun DrawScope.drawClearSky(isDay: Boolean, accents: Accents) {
    if (isDay) {
        for (k in 0 until 8) {
            val a = k * PI / 4
            val from = Offset(12f + (cos(a) * 7.2).toFloat(), 12f + (sin(a) * 7.2).toFloat())
            val to = Offset(12f + (cos(a) * 9.6).toFloat(), 12f + (sin(a) * 9.6).toFloat())
            drawLine(accents.boltBottom, from, to, 1.5f, StrokeCap.Round)
        }
        val sun = circlePath(12f, 12f, 5.4f)
        drawPath(sun, Brush.verticalGradient(listOf(accents.boltTop, accents.boltBottom), 6.6f, 17.4f))
        drawPath(sun, accents.boltEdge, style = Stroke(0.35f))
    } else {
        val moon = Path.combine(PathOperation.Difference, circlePath(11.5f, 12.5f, 7f), circlePath(15.2f, 10.3f, 5.8f))
        drawPath(moon, Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFE6C96A)), 5.5f, 19.5f))
        drawPath(moon, accents.boltEdge, style = Stroke(0.35f, join = StrokeJoin.Round))
    }
}

private inline fun DrawScope.inUnits(crossinline block: DrawScope.() -> Unit) {
    val factor = size.minDimension / VIEWPORT
    withTransform({ scale(factor, factor, pivot = Offset.Zero) }) { block() }
}

/** Цвета капель, молнии и снега: на светлом небе (тёмный текст) насыщеннее, чтобы не терялись. */
private enum class Accents(
    val dropTop: Color,
    val dropBottom: Color,
    val boltTop: Color,
    val boltBottom: Color,
    val boltEdge: Color,
    val snow: Color,
    val snowEdge: Color,
) {
    OnDark(
        dropTop = Color(0xFF8FD0FF),
        dropBottom = Color(0xFF3A86F2),
        boltTop = Color(0xFFFFE68A),
        boltBottom = Color(0xFFFFA126),
        boltEdge = Color(0x99D87000),
        snow = Color(0xFFF2F8FF),
        snowEdge = Color(0x995B95DA),
    ),
    OnLight(
        dropTop = Color(0xFF5AB2FF),
        dropBottom = Color(0xFF1F63D6),
        boltTop = Color(0xFFFFD54A),
        boltBottom = Color(0xFFF08A00),
        boltEdge = Color(0xCCB85A00),
        snow = Color(0xFFE6F1FF),
        snowEdge = Color(0xCC3F7FCC),
    ),
}

// ---------- Стеклянный набор ----------

/**
 * Многодольное облако, симметричное относительно x = 12 (step-41): крупная доля по центру, две боковые одного размера,
 * плоское основание между ними. Боковые доли закруглены снизу и выступают за основание с обеих сторон, поэтому ни
 * ступеньки слева, ни среза справа нет. [k] масштаб, [dx] и [dy] сдвиг (после масштаба).
 */
private fun cloudPath(dy: Float, dx: Float = 0f, k: Float = 1f): Path {
    fun x(v: Float) = v * k + dx
    fun y(v: Float) = v * k + dy
    fun circle(cx: Float, cy: Float, r: Float) = Path().apply {
        addOval(Rect(x(cx) - r * k, y(cy) - r * k, x(cx) + r * k, y(cy) + r * k))
    }
    var shape = Path().apply { addRect(Rect(x(7.5f), y(11.5f), x(16.5f), y(18.5f))) }
    for (lobe in listOf(circle(7.5f, 14.5f, 4f), circle(12f, 9.8f, 5.6f), circle(16.5f, 14.5f, 4f))) {
        shape = Path.combine(PathOperation.Union, shape, lobe)
    }
    return shape
}

private fun circlePath(cx: Float, cy: Float, r: Float): Path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }

/** Центр солнца и луны на значке «облако с солнцем / с луной» (единицы значка 0…24). */
private const val SUN_X = 8.2f
private const val SUN_Y = 8.4f

/** Капля остриём вверх: верх [top], высота [h], ширина [w]. */
private fun dropPath(x: Float, top: Float, w: Float, h: Float): Path = Path().apply {
    val r = w / 2
    moveTo(x, top)
    cubicTo(x + 0.35f, top + 1.4f, x + r, top + h - w * 0.95f, x + r, top + h - r)
    arcTo(Rect(x - r, top + h - w, x + r, top + h), 0f, 180f, false)
    cubicTo(x - r, top + h - w * 0.95f, x - 0.35f, top + 1.4f, x, top)
    close()
}

private class GlassShapes(icon: HeroIcon) {
    /** Облаку с каплями, молнией или снегом нужно место снизу: оно поднято. */
    private val lift = when (icon) {
        HeroIcon.Cloud, HeroIcon.CloudSun, HeroIcon.CloudMoon, HeroIcon.Fog -> 0f
        else -> -3f
    }
    private val partly = icon == HeroIcon.CloudSun || icon == HeroIcon.CloudMoon

    /**
     * Облако переменной облачности меньше и сдвинуто вправо вниз: над ним выглядывает солнце или луна. У тумана облака
     * нет (step-41): только горизонтальные полосы.
     */
    val cloud: Path? = when {
        icon == HeroIcon.Fog -> null
        partly -> cloudPath(dy = 4.5f, dx = 5f, k = 0.8f)
        else -> cloudPath(lift)
    }
    val top = if (partly) 7.8f else 4.2f + lift
    val bottom = if (partly) 19.3f else 18.5f + lift

    /** Солнце: диск и восемь коротких лучей; нижние правые лучи скрыты облаком. */
    val sun: Path? = if (icon == HeroIcon.CloudSun) circlePath(SUN_X, SUN_Y, 3.9f) else null
    val rays: List<Pair<Offset, Offset>> = if (icon == HeroIcon.CloudSun) {
        (0 until 8).map { k ->
            val a = k * PI / 4
            Offset(SUN_X + (cos(a) * 5.5).toFloat(), SUN_Y + (sin(a) * 5.5).toFloat()) to
                Offset(SUN_X + (cos(a) * 7.0).toFloat(), SUN_Y + (sin(a) * 7.0).toFloat())
        }
    } else {
        emptyList()
    }

    /** Луна: полумесяц, рога вверх и влево. */
    val moon: Path? = if (icon == HeroIcon.CloudMoon) {
        Path.combine(PathOperation.Difference, circlePath(SUN_X, SUN_Y + 0.2f, 4.7f), circlePath(SUN_X + 2.5f, SUN_Y - 1.3f, 3.9f))
    } else {
        null
    }
    val drops = if (icon == HeroIcon.Rain) {
        listOf(dropPath(7.6f, 14.6f, 3f, 6f), dropPath(12.1f, 16f, 3f, 6f), dropPath(16.6f, 14.6f, 3f, 6f))
    } else {
        emptyList()
    }
    val bolt = if (icon == HeroIcon.Storm) {
        Path().apply {
            moveTo(13.8f, 11f)
            lineTo(9.2f, 17.4f)
            lineTo(12.1f, 17.4f)
            lineTo(10.3f, 23.2f)
            lineTo(16.2f, 15.4f)
            lineTo(13.2f, 15.4f)
            lineTo(15.2f, 11f)
            close()
        }
    } else {
        null
    }
    val flakes = if (icon == HeroIcon.Snow) {
        listOf(Triple(7.5f, 18.6f, 2f), Triple(12f, 20.8f, 2.2f), Triple(16.5f, 18.6f, 2f))
    } else {
        emptyList()
    }

    /** Три полосы тумана со сдвигом влево, вправо, влево; вместе симметричны относительно x = 12, низ на 18,5 как у облака. */
    val fogBars = if (icon == HeroIcon.Fog) {
        listOf(Rect(4f, 7.7f, 17f, 9.9f), Rect(7f, 12f, 20f, 14.2f), Rect(4f, 16.3f, 17f, 18.5f))
    } else {
        emptyList()
    }
}

/**
 * Матовое стекло: полупрозрачная светлая заливка сверху вниз, тень, светлая кромка и блик. Цветные капли, молния и
 * снежинки видны только под облаком: за стеклом они не просвечивают (облако стирает их над собой, step-41).
 */
private fun DrawScope.drawGlass(icon: HeroIcon, s: GlassShapes, accents: Accents, darkInk: Boolean) {
    s.sun?.let {
        for ((from, to) in s.rays) drawLine(accents.boltBottom, from, to, 1.1f, StrokeCap.Round)
        drawPath(it, Brush.verticalGradient(listOf(accents.boltTop, accents.boltBottom), 4.5f, 12.3f))
        drawPath(it, accents.boltEdge, style = Stroke(0.3f))
    }
    s.moon?.let {
        drawPath(it, Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFE6C96A)), 3.7f, 13.3f))
        drawPath(it, accents.boltEdge, style = Stroke(0.3f, join = StrokeJoin.Round))
    }
    val cloud = s.cloud
    if (cloud != null && (s.drops.isNotEmpty() || s.bolt != null || s.flakes.isNotEmpty())) {
        beneathCloud(cloud) {
            for (drop in s.drops) {
                drawPath(drop, Brush.verticalGradient(listOf(accents.dropTop, accents.dropBottom), 14.6f, 22f))
            }
            s.bolt?.let {
                drawPath(it, Brush.verticalGradient(listOf(accents.boltTop, accents.boltBottom), 11f, 23.2f))
                drawPath(it, accents.boltEdge, style = Stroke(0.35f, join = StrokeJoin.Round))
            }
            for ((x, y, r) in s.flakes) snowflake(x, y, r, accents)
        }
    }
    cloud?.let { glassBody(it, s.top, s.bottom, darkInk, dim = false) }
    for (bar in s.fogBars) {
        val path = Path().apply { addRoundRect(RoundRect(bar, CornerRadius(1.1f))) }
        glassBody(path, bar.top, bar.bottom, darkInk, dim = false, sheen = false)
    }
    if (icon == HeroIcon.Rain) {
        // Блики на каплях поверх стекла: капли читаются как стеклянные бусины.
        for ((x, y) in listOf(7.0f to 17.9f, 11.5f to 19.3f, 16.0f to 17.9f)) {
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 0.5f, center = Offset(x, y))
        }
    }
}

/**
 * Рисует [draw] в отдельном слое и стирает из него всё, что лежит внутри контура [cloud]: останется только то, что
 * выходит за облако (капли, молния и снег под ним). Слой нужен, чтобы стирание не задело то, что нарисовано раньше.
 */
private fun DrawScope.beneathCloud(cloud: Path, draw: DrawScope.() -> Unit) {
    drawIntoCanvas { it.saveLayer(Rect(-2f, -2f, VIEWPORT + 2f, VIEWPORT + 2f), Paint()) }
    draw()
    drawPath(cloud, Color.Black, blendMode = BlendMode.DstOut)
    drawIntoCanvas { it.restore() }
}

private fun DrawScope.glassBody(
    path: Path,
    top: Float,
    bottom: Float,
    darkInk: Boolean,
    dim: Boolean,
    sheen: Boolean = true,
) {
    val shadowAlpha = if (darkInk) 0.10f else 0.18f
    translate(0.3f, 0.8f) { drawPath(path, Color.Black.copy(alpha = shadowAlpha)) }
    val low = if (darkInk) Color(0xFF9FB3CC) else Color.White
    val fill = if (dim) {
        listOf(Color(0xFFDCE5F0).copy(alpha = 0.46f), Color(0xFF8FA3BC).copy(alpha = 0.34f))
    } else {
        listOf(Color.White.copy(alpha = 0.80f), low.copy(alpha = if (darkInk) 0.62f else 0.42f))
    }
    drawPath(path, Brush.verticalGradient(fill, top, bottom))
    if (darkInk) drawPath(path, Color(0xFF2A3B52).copy(alpha = 0.20f), style = Stroke(0.4f))
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.12f)),
            top,
            top + (bottom - top) * 0.6f,
        ),
        style = Stroke(0.5f),
    )
    if (sheen) {
        // Мягкий свет по верхней кромке: плавный градиент от верха вниз, без резких краёв.
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                top,
                top + (bottom - top) * 0.5f,
            ),
        )
    }
}

private fun DrawScope.snowflake(x: Float, y: Float, r: Float, accents: Accents) {
    for (pass in 0..1) {
        val color = if (pass == 0) accents.snowEdge else accents.snow
        val width = if (pass == 0) 1.15f else 0.6f
        for (k in 0 until 3) {
            val a = k * PI / 3 + PI / 2
            val dx = (cos(a) * r).toFloat()
            val dy = (sin(a) * r).toFloat()
            drawLine(color, Offset(x - dx, y - dy), Offset(x + dx, y + dy), width, StrokeCap.Round)
        }
    }
}

// ---------- Контурный набор: контуры Lucide (лицензия ISC, third_party/LICENSE-Lucide.txt) ----------

private const val CLOUD_OPEN = "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"

private class OutlineParts(icon: HeroIcon) {
    private fun parse(d: String) = PathParser().parsePathString(d).toPath()

    /** Линии цветом текста. */
    val ink: List<Path> = when (icon) {
        HeroIcon.Cloud -> listOf("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")
        HeroIcon.CloudSun -> listOf("M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z")
        HeroIcon.CloudMoon -> listOf(
            "M13 16a3 3 0 0 1 0 6H7a5 5 0 1 1 4.9-6z",
            "M18.376 14.512a6 6 0 0 0 3.461-4.127c.148-.625-.659-.97-1.248-.714a4 4 0 0 1-5.259-5.26c.255-.589-.09-1.395-.716-1.248a6 6 0 0 0-4.594 5.36",
        )
        HeroIcon.Rain, HeroIcon.Snow -> listOf(CLOUD_OPEN)
        HeroIcon.Storm -> listOf("M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973")
        // Туман без облака (step-41): три горизонтальные линии со сдвигом, вместе симметричны относительно x = 12.
        HeroIcon.Fog -> listOf("M4 10h13M7 14.5h13M4 19h13")
    }.map(::parse)

    /** Цветные части: синие капли и снег, оранжевая молния. */
    val accent: Path? = when (icon) {
        HeroIcon.Rain -> "M16 14v6M8 14v6M12 16v6"
        HeroIcon.Snow -> "M8 15h.01M8 19h.01M12 17h.01M12 21h.01M16 15h.01M16 19h.01"
        HeroIcon.Storm -> "M13 12l-3 5h4l-3 5"
        HeroIcon.CloudSun -> "M12 2v2M4.93 4.93l1.41 1.41M20 12h2M19.07 4.93l-1.41 1.41M15.947 12.65a4 4 0 0 0-5.925-4.128"
        else -> null
    }?.let(::parse)

    val warm = icon == HeroIcon.Storm || icon == HeroIcon.CloudSun

    /** Снег рисуется точками: точка толще линии, иначе на крупном значке теряется. */
    val dots = icon == HeroIcon.Snow
}

private fun DrawScope.drawOutline(parts: OutlineParts, ink: Color, accents: Accents, stroke: Float) {
    for (path in parts.ink) drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val accent = parts.accent ?: return
    val color = when {
        parts.warm -> accents.boltBottom
        accents == Accents.OnDark -> accents.dropTop
        else -> accents.dropBottom
    }
    val width = if (parts.dots) stroke * 1.8f else stroke
    drawPath(accent, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
