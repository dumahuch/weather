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
    private val pens = HashMap<PenKey, TextPaint>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class PenKey(val size: Float, val weight: Int, val alpha: Int, val rgb: Int)

    /** Кисть текста: размер в единицах макета, вес шрифта 200…700, прозрачность 0…1, цвет (по умолчанию белый). Кисти запоминаются. */
    fun pen(size: Float, weight: Int = 400, alpha: Float = 1f, rgb: Int = Color.WHITE): TextPaint =
        pens.getOrPut(PenKey(size, weight, (alpha * 100).roundToInt(), rgb and 0xFFFFFF)) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                setFontVariationSettings("'wght' $weight")
                textSize = size
                color = Color.argb((alpha * 255).roundToInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
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

    /** Мелкий значок дней: всегда контурный, как в ленте приложения. */
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
        linePaint.color = pen.color
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

    /** Кисть максимума (тёплая) и минимума (холодная) размера [size]: правила надписей, стрелка вверх и вниз. */
    fun highPen(size: Float): TextPaint = pen(size, 400, 1f, HIGH)

    fun lowPen(size: Float): TextPaint = pen(size, 400, 1f, LOW)

    /** «↑ 9° ↓ 3°» в одну строку (максимум тёплым, минимум холодным); возвращает ширину. */
    fun hiLo(x: Float, baseline: Float, high: String, low: String, size: Float): Float {
        val first = arrowText(x, baseline, true, high, highPen(size))
        val second = arrowText(x + first + size * HI_LO_GAP, baseline, false, low, lowPen(size))
        return first + size * HI_LO_GAP + second
    }

    /**
     * Максимум и минимум по центру [cx]: со стрелками, если они помещаются в [maxWidth], иначе просто цветными числами (цвет и
     * так различает их); 1×1 очень узкий.
     */
    fun hiLoCentered(cx: Float, baseline: Float, high: String, low: String, size: Float, maxWidth: Float) {
        val hiPen = highPen(size)
        val loPen = lowPen(size)
        val gap = size * HI_LO_GAP
        val arrows = arrowTextWidth(high, hiPen) + gap + arrowTextWidth(low, loPen)
        if (arrows <= maxWidth) {
            hiLo(cx - arrows / 2, baseline, high, low, size)
            return
        }
        val first = width(high, hiPen)
        val x = cx - (first + gap + width(low, loPen)) / 2
        text(high, x, baseline, hiPen)
        text(low, x + first + gap, baseline, loPen)
    }

    /** Показатель: бледная мелкая подпись над значением (правила надписей); [valueBase] нижняя линия значения размера [size]. */
    fun fact(x: Float, valueBase: Float, label: String, value: String, size: Float, maxWidth: Float) {
        val valuePen = pen(size, 400)
        val labelPen = pen(FACT_LABEL, 400, 0.65f)
        text(fit(label, labelPen, maxWidth), x, valueBase - size * DIGIT_HEIGHT - FACT_GAP, labelPen)
        text(fit(value, valuePen, maxWidth), x, valueBase, valuePen)
    }

    /** Колечко (значок градуса) с центром в ([cx], [cy]), радиус по оси линии [radius], толщина [strokeWidth], прозрачность [alpha]. */
    fun ring(cx: Float, cy: Float, radius: Float, strokeWidth: Float, alpha: Float) {
        linePaint.color = Color.argb((alpha * 255).roundToInt(), 255, 255, 255)
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

    companion object {
        /** Высота цифр Manrope в долях размера шрифта (по ней выравниваются блоки текста). */
        const val DIGIT_HEIGHT = 0.72f

        private const val SHADOW_RADIUS = 4f
        private const val SHADOW_COLOR = 0x99000000.toInt()
        private const val ARROW_BOX = 0.62f
        private const val ARROW_GAP = 0.22f
        private const val ARROW_STROKE = 0.09f
        private const val HEAD = 0.32f
        private const val HI_LO_GAP = 0.6f
        private const val RULE_ALPHA = 72
        private const val RULE_WIDTH = 1.5f
        private const val BAR_HEIGHT = 8f
        private const val TRACK_ALPHA = 51
        private const val COLD = 0xFF8FC6FF.toInt()
        private const val WARM = 0xFFFFD27A.toInt()
        private const val HIGH = 0xFFFFB070.toInt()
        private const val LOW = 0xFF8EC8FF.toInt()
        private const val FACT_LABEL = 36f
        private const val FACT_GAP = 16f
    }
}
