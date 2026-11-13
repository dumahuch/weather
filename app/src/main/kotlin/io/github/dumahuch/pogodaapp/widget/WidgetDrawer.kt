package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.home.weatherGlyphBitmap
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlin.math.roundToInt

/** Значки показателей строки внизу виджета. */
internal enum class MetricIcon { Wind, Humidity, Rain }

/**
 * Кисти и примитивы рисования виджета. Координаты в «единицах макета» ([WidgetLayout.designWidth]): холст уже
 * масштабирован под настоящий размер, [scale] нужен только чтобы значки погоды рисовались картинкой нужной резкости.
 * Текст белый (виджет лежит на тёмном стекле или на обоях); [shadow] даёт тень букв, когда фона нет или он прозрачный.
 */
internal class WidgetDrawer(
    context: Context,
    val canvas: Canvas,
    private val scale: Float,
    private val shadow: Boolean,
    private val iconStyle: WeatherIconStyle,
) {
    private val face: Typeface = context.resources.getFont(R.font.manrope)
    private val pens = HashMap<Triple<Float, Int, Int>, TextPaint>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Кисть текста: размер в единицах макета, вес шрифта 200…700, прозрачность 0…1. Кисти запоминаются. */
    fun pen(size: Float, weight: Int = 400, alpha: Float = 1f): TextPaint =
        pens.getOrPut(Triple(size, weight, (alpha * 100).roundToInt())) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                setFontVariationSettings("'wght' $weight")
                textSize = size
                color = Color.argb((alpha * 255).roundToInt(), 255, 255, 255)
                if (shadow) setShadowLayer(SHADOW_RADIUS, 0f, 1f, SHADOW_COLOR)
            }
        }

    fun text(value: String, x: Float, baseline: Float, pen: TextPaint, align: Paint.Align = Paint.Align.LEFT) {
        pen.textAlign = align
        canvas.drawText(value, x, baseline, pen)
    }

    fun width(value: String, pen: TextPaint): Float = pen.measureText(value)

    /** Строка, обрезанная многоточием до ширины [maxWidth]. */
    fun fit(value: String, pen: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(value, pen, maxWidth, TextUtils.TruncateAt.END).toString()

    /** Крупный значок погоды в выбранном наборе (стеклянный или контурный). */
    fun glyph(condition: Condition, isDay: Boolean, windy: Boolean, x: Float, y: Float, size: Float) {
        draw(weatherGlyphBitmap(condition, isDay, windy, iconStyle, false, (size * scale).roundToInt().coerceAtLeast(1)), x, y, size)
    }

    /** Мелкий значок для часов и дней: всегда контурный, как в ленте приложения. */
    fun smallGlyph(condition: Condition, isDay: Boolean, x: Float, y: Float, size: Float) {
        draw(
            weatherGlyphBitmap(condition, isDay, false, WeatherIconStyle.Outline, false, (size * scale).roundToInt().coerceAtLeast(1)),
            x, y, size,
        )
    }

    private fun draw(bitmap: android.graphics.Bitmap, x: Float, y: Float, size: Float) {
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + size, y + size), bitmapPaint)
        bitmap.recycle()
    }

    /** Стрелка вверх или вниз при тексте высотой [size]: ширина стрелки и зазор до текста входят в [arrowTextWidth]. */
    fun arrowText(x: Float, baseline: Float, up: Boolean, value: String, pen: TextPaint): Float {
        val size = pen.textSize
        val box = size * ARROW_BOX
        val cx = x + box / 2
        val cy = baseline - size * DIGIT_HEIGHT / 2
        linePaint.color = Color.argb(ARROW_ALPHA, 255, 255, 255)
        linePaint.strokeWidth = size * ARROW_STROKE
        val tip = if (up) cy - box / 2 else cy + box / 2
        val tail = if (up) cy + box / 2 else cy - box / 2
        val back = if (up) box * HEAD else -box * HEAD
        val path = Path().apply {
            moveTo(cx, tail)
            lineTo(cx, tip)
            moveTo(cx - box * HEAD, tip + back)
            lineTo(cx, tip)
            lineTo(cx + box * HEAD, tip + back)
        }
        canvas.drawPath(path, linePaint)
        text(value, x + box + size * ARROW_GAP, baseline, pen)
        return arrowTextWidth(value, pen)
    }

    fun arrowTextWidth(value: String, pen: TextPaint): Float = pen.textSize * (ARROW_BOX + ARROW_GAP) + pen.measureText(value)

    /** «↑ 9° ↓ 3°» в одну строку; возвращает ширину. */
    fun hiLo(x: Float, baseline: Float, high: String, low: String, pen: TextPaint): Float {
        val first = arrowText(x, baseline, true, high, pen)
        val second = arrowText(x + first + pen.textSize * HI_LO_GAP, baseline, false, low, pen)
        return first + pen.textSize * HI_LO_GAP + second
    }

    fun hiLoWidth(high: String, low: String, pen: TextPaint): Float =
        arrowTextWidth(high, pen) + pen.textSize * HI_LO_GAP + arrowTextWidth(low, pen)

    /** Колечко (значок градуса) с центром в ([cx], [cy]), радиус по оси линии [radius], толщина [strokeWidth]. */
    fun ring(cx: Float, cy: Float, radius: Float, strokeWidth: Float) {
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = strokeWidth
        canvas.drawCircle(cx, cy, radius, linePaint)
    }

    /** Тонкая линия-разделитель. */
    fun rule(x0: Float, y: Float, x1: Float) {
        linePaint.color = Color.argb(RULE_ALPHA, 255, 255, 255)
        linePaint.strokeWidth = RULE_WIDTH
        canvas.drawLine(x0, y, x1, y, linePaint)
    }

    fun verticalRule(x: Float, y0: Float, y1: Float) {
        linePaint.color = Color.argb(RULE_ALPHA, 255, 255, 255)
        linePaint.strokeWidth = RULE_WIDTH
        canvas.drawLine(x, y0, x, y1, linePaint)
    }

    /** Полоска температур дня: серая дорожка от [x0] до [x1] и цветная часть от доли [start] шириной [span]. */
    fun rangeBar(x0: Float, x1: Float, cy: Float, start: Float, span: Float) {
        val half = BAR_HEIGHT / 2
        fillPaint.shader = null
        fillPaint.color = Color.argb(TRACK_ALPHA, 255, 255, 255)
        canvas.drawRoundRect(RectF(x0, cy - half, x1, cy + half), half, half, fillPaint)
        val from = x0 + (x1 - x0) * start
        val to = from + (x1 - x0) * span
        fillPaint.shader = LinearGradient(from, cy, to, cy, COLD, WARM, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(from, cy - half, to, cy + half), half, half, fillPaint)
        fillPaint.shader = null
    }

    /** Значок показателя в квадрате [size] с центром по высоте [cy]. Голубые капли и линии ветра, как в макете. */
    fun metricIcon(kind: MetricIcon, x: Float, cy: Float, size: Float) {
        linePaint.strokeWidth = size * METRIC_STROKE
        linePaint.color = if (kind == MetricIcon.Wind) Color.WHITE else METRIC_BLUE
        val top = cy - size / 2
        val path = Path()
        when (kind) {
            MetricIcon.Wind -> {
                path.moveTo(x + size * 0.1f, top + size * 0.30f)
                path.lineTo(x + size * 0.75f, top + size * 0.30f)
                path.moveTo(x + size * 0.1f, top + size * 0.52f)
                path.lineTo(x + size * 0.95f, top + size * 0.52f)
                path.moveTo(x + size * 0.1f, top + size * 0.74f)
                path.lineTo(x + size * 0.55f, top + size * 0.74f)
            }
            MetricIcon.Humidity -> {
                val cx = x + size / 2
                path.moveTo(cx, top + size * 0.08f)
                path.quadTo(x + size * 0.86f, top + size * 0.52f, cx, top + size * 0.92f)
                path.quadTo(x + size * 0.14f, top + size * 0.52f, cx, top + size * 0.08f)
            }
            MetricIcon.Rain -> {
                for (i in 0..2) {
                    val sx = x + size * (0.30f + 0.25f * i)
                    path.moveTo(sx + size * 0.10f, top + size * 0.25f)
                    path.lineTo(sx - size * 0.10f, top + size * 0.75f)
                }
            }
        }
        canvas.drawPath(path, linePaint)
    }

    companion object {
        /** Высота цифр Manrope в долях размера шрифта (по ней выравниваются блоки текста). */
        const val DIGIT_HEIGHT = 0.72f

        private const val SHADOW_RADIUS = 4f
        private const val SHADOW_COLOR = 0x99000000.toInt()
        private const val ARROW_BOX = 0.62f
        private const val ARROW_GAP = 0.22f
        private const val ARROW_STROKE = 0.09f
        private const val ARROW_ALPHA = 178
        private const val HEAD = 0.32f
        private const val HI_LO_GAP = 0.6f
        private const val RULE_ALPHA = 72
        private const val RULE_WIDTH = 1.5f
        private const val BAR_HEIGHT = 8f
        private const val TRACK_ALPHA = 51
        private const val COLD = 0xFF8FC6FF.toInt()
        private const val WARM = 0xFFFFD27A.toInt()
        private const val METRIC_BLUE = 0xFF9FD0FF.toInt()
        private const val METRIC_STROKE = 0.07f
    }
}
