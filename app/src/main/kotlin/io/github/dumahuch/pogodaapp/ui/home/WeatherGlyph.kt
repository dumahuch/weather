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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import io.github.dumahuch.pogodaapp.home.HeroIcon
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

private const val VIEWPORT = 24f

/** Толщина контура в единицах значка: тоньше на крупном значке, толще на мелком (подбирается по размеру, не в настройках). */
internal fun outlineStroke(size: Dp): Float = if (size.value >= 40f) 1.2f else 1.6f

/**
 * Нижний край рисунка в единицах значка (0…24): по нему значок ставится на нижний край цифр температуры.
 * Для контура учтена половина толщины линии.
 */
internal fun glyphBottom(icon: HeroIcon, style: WeatherIconStyle, size: Dp): Float = when (style) {
    WeatherIconStyle.Glass -> when (icon) {
        HeroIcon.Cloud -> 18.5f
        HeroIcon.Clouds -> 19.5f
        HeroIcon.Rain -> 22f
        HeroIcon.Storm -> 23.2f
        HeroIcon.Snow -> 23f
        HeroIcon.Fog -> 22f
    }
    WeatherIconStyle.Outline -> outlineStroke(size) / 2 + when (icon) {
        HeroIcon.Cloud -> 19f
        HeroIcon.Clouds -> 21f
        HeroIcon.Rain, HeroIcon.Storm -> 22f
        HeroIcon.Snow, HeroIcon.Fog -> 21f
    }
}

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

/** Многодольное облако: три круга и скруглённое основание; [k] масштаб, [dx] и [dy] сдвиг (после масштаба). */
private fun cloudPath(dy: Float, dx: Float = 0f, k: Float = 1f): Path {
    fun x(v: Float) = v * k + dx
    fun y(v: Float) = v * k + dy
    fun circle(cx: Float, cy: Float, r: Float) = Path().apply {
        addOval(Rect(x(cx) - r * k, y(cy) - r * k, x(cx) + r * k, y(cy) + r * k))
    }
    var shape = Path().apply { addRoundRect(RoundRect(Rect(x(2.5f), y(11.5f), x(21.5f), y(18.5f)), CornerRadius(3.5f * k))) }
    for (lobe in listOf(circle(8f, 12.5f, 4.2f), circle(13.2f, 9.6f, 5.4f), circle(18f, 12.6f, 3.5f))) {
        shape = Path.combine(PathOperation.Union, shape, lobe)
    }
    return shape
}

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
    /** Облаку с каплями, молнией, снегом или туманом нужно место снизу: оно поднято. */
    private val lift = when (icon) {
        HeroIcon.Cloud -> 0f
        HeroIcon.Clouds -> 1f
        HeroIcon.Fog -> -2.5f
        else -> -3f
    }
    val cloud = cloudPath(lift)
    val back: Path? = if (icon == HeroIcon.Clouds) cloudPath(dy = -1.2f, dx = 8.6f, k = 0.66f) else null
    val top = 4.2f + lift
    val bottom = 18.5f + lift
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
    val fogBars = if (icon == HeroIcon.Fog) {
        listOf(Rect(3f, 16.8f, 17f, 18.8f), Rect(7f, 20f, 21f, 22f))
    } else {
        emptyList()
    }
}

/**
 * Матовое стекло: полупрозрачная светлая заливка сверху вниз, тень, светлая кромка и блик. Цветные капли, молния и
 * снежинки нарисованы под стеклом и просвечивают сквозь нижний край облака.
 */
private fun DrawScope.drawGlass(icon: HeroIcon, s: GlassShapes, accents: Accents, darkInk: Boolean) {
    s.back?.let { glassBody(it, top = 1.5f, bottom = 11f, darkInk = darkInk, dim = true) }
    for (drop in s.drops) {
        drawPath(drop, Brush.verticalGradient(listOf(accents.dropTop, accents.dropBottom), 14.6f, 22f))
    }
    s.bolt?.let {
        drawPath(it, Brush.verticalGradient(listOf(accents.boltTop, accents.boltBottom), 11f, 23.2f))
        drawPath(it, accents.boltEdge, style = Stroke(0.35f, join = StrokeJoin.Round))
    }
    for ((x, y, r) in s.flakes) snowflake(x, y, r, accents)
    glassBody(s.cloud, s.top, s.bottom, darkInk, dim = false)
    for (bar in s.fogBars) {
        val path = Path().apply { addRoundRect(RoundRect(bar, CornerRadius(1f))) }
        glassBody(path, bar.top, bar.bottom, darkInk, dim = false, sheen = false)
    }
    if (icon == HeroIcon.Rain) {
        // Блики на каплях поверх стекла: капли читаются как стеклянные бусины.
        for ((x, y) in listOf(7.0f to 17.9f, 11.5f to 19.3f, 16.0f to 17.9f)) {
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 0.5f, center = Offset(x, y))
        }
    }
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
        HeroIcon.Clouds -> listOf(
            "M17.5 21H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
            "M22 10a3 3 0 0 0-3-3h-2.207a5.502 5.502 0 0 0-10.702.5",
        )
        HeroIcon.Rain, HeroIcon.Snow -> listOf(CLOUD_OPEN)
        HeroIcon.Storm -> listOf("M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973")
        HeroIcon.Fog -> listOf(CLOUD_OPEN, "M16 17H7M17 21H9")
    }.map(::parse)

    /** Цветные части: синие капли и снег, оранжевая молния. */
    val accent: Path? = when (icon) {
        HeroIcon.Rain -> "M16 14v6M8 14v6M12 16v6"
        HeroIcon.Snow -> "M8 15h.01M8 19h.01M12 17h.01M12 21h.01M16 15h.01M16 19h.01"
        HeroIcon.Storm -> "M13 12l-3 5h4l-3 5"
        else -> null
    }?.let(::parse)

    val warm = icon == HeroIcon.Storm

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
