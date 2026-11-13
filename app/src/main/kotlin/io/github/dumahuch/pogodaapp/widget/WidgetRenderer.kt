package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.hourLabel
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.widget.WidgetDrawer.Companion.DIGIT_HEIGHT
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Рисует виджет целиком в картинку ([Bitmap]) по макету Димы от 12.11.2026 (значок слева, цифра справа, чем больше
 * размер, тем больше сведений). Картинкой, а не набором элементов Glance: тонкие цифры Manrope и стеклянные значки
 * приложения средствами виджета не показать, а картинка даёт ровно тот вид, что в макете. Всё рисуется в «единицах
 * макета» ([WidgetLayout.designWidth]) и масштабируется под настоящий размер; ширина и высота холста в этих единицах
 * ([draw]: `w`, `h`) могут быть больше номинальных, лишнее место делится между блоками.
 */
internal class WidgetRenderer(private val context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private val dayNames: List<String> = context.resources.getStringArray(R.array.days_short).toList()

    fun render(model: WidgetModel?, layout: WidgetLayout, widthPx: Int, heightPx: Int, style: WidgetStyle, iconStyle: WeatherIconStyle): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = min(widthPx / layout.designWidth, heightPx / layout.designHeight)
        canvas.scale(scale, scale)
        val w = widthPx / scale
        val h = heightPx / scale
        if (style.background == WidgetBackground.Glass) drawGlass(canvas, w, h, style.opacity)
        val shadow = style.background == WidgetBackground.None || style.opacity < SHADOW_BELOW_OPACITY
        val d = WidgetDrawer(context, canvas, scale, shadow, iconStyle)
        if (model == null) {
            d.text(context.getString(R.string.widget_no_data), w / 2, h / 2 + 9f, d.pen(26f, 400, 0.9f), Paint.Align.CENTER)
            return bitmap
        }
        when (layout) {
            WidgetLayout.Tiny -> tiny(d, model, w, h)
            WidgetLayout.Narrow -> narrow(d, model, w, h)
            WidgetLayout.Strip -> strip(d, model, w, h)
            WidgetLayout.Square -> square(d, model, w, h)
            WidgetLayout.Wide -> wide(d, model, w, h)
            WidgetLayout.Large -> large(d, model, w, h)
        }
        return bitmap
    }

    /** Тёмное стекло цвета интерфейса с тонкой светлой кромкой; насколько оно непрозрачно, решает человек. */
    private fun drawGlass(canvas: Canvas, w: Float, h: Float, opacity: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((opacity * 255 / 100f).roundToInt(), GLASS_R, GLASS_G, GLASS_B)
        canvas.drawRoundRect(rect, CORNER, CORNER, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(RIM_ALPHA, 255, 255, 255)
        canvas.drawRoundRect(rect, CORNER, CORNER, paint)
    }

    private fun condition(m: WidgetModel): String =
        context.getString(if (m.drizzle) R.string.wx_drizzle else conditionNameRes(m.condition))

    private fun feelsLike(m: WidgetModel): String = "${context.getString(R.string.feels_like)} ${temperatureText(m.feelsLike)}"

    /**
     * Крупная температура по центру и значок над её правым верхним углом (над градусом): 1×1 и 2×1. Цифры
     * сужаются, пока не влезут в [maxWidth], но не крупнее [maxSize].
     */
    private fun cornerIcon(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float, maxSize: Float, icon: Float, maxWidth: Float) {
        val temp = temperatureText(m.temperature)
        val size = fitSize(d, temp, maxSize, maxWidth)
        val pen = d.pen(size, HERO_WEIGHT)
        val textWidth = d.width(temp, pen)
        val cap = size * DIGIT_HEIGHT
        val iconSpace = icon * ICON_SPACE
        val top = (h - (iconSpace + cap)) / 2
        val left = (w - textWidth) / 2
        d.glyph(m.condition, m.isDay, m.windy, left + textWidth - icon, top, icon)
        d.text(temp, left, top + iconSpace + cap, pen)
    }

    // ---- 1×1 и 2×1: только крупная температура и значок над её углом, по краям воздух.
    private fun tiny(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) = cornerIcon(d, m, w, h, 172f, 72f, w - 2 * TINY_SIDE)

    private fun narrow(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) = cornerIcon(d, m, w, h, 176f, 72f, w * NARROW_SHARE)

    // ---- 4×1: слева город, значок и температура, справа часы.
    private fun strip(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val icon = 88f
        val leftWidth = 330f
        val cityPen = d.pen(40f, 500)
        val cityCap = 40f * DIGIT_HEIGHT
        val temp = temperatureText(m.temperature)
        val size = fitSize(d, temp, 150f, leftWidth - icon - 14f)
        val cap = size * DIGIT_HEIGHT
        val top = (h - (cityCap + 14f + cap)) / 2
        d.text(d.fit(m.placeName.orEmpty(), cityPen, leftWidth), PAD, top + cityCap, cityPen)
        val tempBase = top + cityCap + 14f + cap
        d.glyph(m.condition, m.isDay, m.windy, PAD, tempBase - cap / 2 - icon / 2, icon)
        d.text(temp, PAD + icon + 14f, tempBase, d.pen(size, HERO_WEIGHT))
        val dividerX = PAD + leftWidth + 14f
        d.verticalRule(dividerX, 28f, h - 28f)
        val x0 = dividerX + 14f
        val columns = ((w - PAD - x0) / HOUR_COLUMN_MIN).toInt().coerceIn(2, WidgetModels.HOURS)
        hours(d, m, columns, x0, w - PAD, (h - hoursHeight(HOUR_LABEL, 52f, HOUR_TEMP)) / 2, HOUR_LABEL, 52f, HOUR_TEMP)
    }

    // ---- 2×2: город сверху, по центру значок слева и температура справа, под ней максимум и минимум, внизу часы.
    private fun square(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val cityPen = d.pen(44f, 500)
        val cityBase = PAD + 44f * DIGIT_HEIGHT
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD), PAD, cityBase, cityPen)
        val hoursTop = h - PAD - hoursHeight(HOUR_LABEL, 52f, HOUR_TEMP)
        val ruleY = hoursTop - 12f
        d.rule(PAD, ruleY, w - PAD)
        val columns = ((w - 2 * PAD) / HOUR_COLUMN_SQUARE).toInt().coerceIn(2, 4)
        hours(d, m, columns, PAD, w - PAD, hoursTop, HOUR_LABEL, 52f, HOUR_TEMP)
        val icon = 88f
        val gap = 12f
        val temp = temperatureText(m.temperature)
        val size = fitSize(d, temp, 148f, w - 2 * PAD - icon - gap)
        val tempPen = d.pen(size, HERO_WEIGHT)
        val hiLoPen = d.pen(40f, 400, 0.92f)
        val cap = size * DIGIT_HEIGHT
        val hiLoCap = 40f * DIGIT_HEIGHT
        val blockHeight = cap + 10f + hiLoCap
        val areaTop = cityBase + 8f
        val rowTop = areaTop + (ruleY - 10f - areaTop - blockHeight) / 2
        val left = (w - (icon + gap + d.width(temp, tempPen))) / 2
        d.glyph(m.condition, m.isDay, m.windy, left, rowTop + cap / 2 - icon / 2, icon)
        d.text(temp, left + icon + gap, rowTop + cap, tempPen)
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        d.hiLo((w - d.hiLoWidth(high, low, hiLoPen)) / 2, rowTop + cap + 10f + hiLoCap, high, low, hiLoPen)
    }

    // ---- 4×2: слева текущая погода, справа часы и до трёх дней.
    private fun wide(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val leftWidth = 352f
        val cityPen = d.pen(44f, 500)
        val textPen = d.pen(36f, 400, 0.85f)
        val cityBase = PAD + 44f * DIGIT_HEIGHT
        d.text(d.fit(m.placeName.orEmpty(), cityPen, leftWidth), PAD, cityBase, cityPen)
        val descBase = cityBase + 42f
        d.text(d.fit(condition(m), textPen, leftWidth), PAD, descBase, textPen)
        val windPen = d.pen(40f, 400, 0.9f)
        val hiLoPen = d.pen(44f, 400, 0.92f)
        val windBase = h - PAD
        val hiLoBase = windBase - 40f * DIGIT_HEIGHT - 14f
        d.text(d.fit(windText(m), windPen, leftWidth), PAD, windBase, windPen)
        d.hiLo(PAD, hiLoBase, temperatureText(m.high), temperatureText(m.low), hiLoPen)
        val icon = 96f
        val gap = 12f
        val temp = temperatureText(m.temperature)
        val size = fitSize(d, temp, 170f, leftWidth - icon - gap)
        val cap = size * DIGIT_HEIGHT
        val midY = (descBase + 12f + hiLoBase - 44f * DIGIT_HEIGHT) / 2
        d.glyph(m.condition, m.isDay, m.windy, PAD, midY - icon / 2, icon)
        d.text(temp, PAD + icon + gap, midY + cap / 2, d.pen(size, HERO_WEIGHT))
        val dividerX = PAD + leftWidth + 14f
        d.verticalRule(dividerX, PAD + 6f, h - PAD - 6f)
        val x0 = dividerX + 14f
        val x1 = w - PAD
        val hoursBlock = hoursHeight(HOUR_LABEL, 52f, HOUR_TEMP)
        val rowHeight = 66f
        val rows = minOf(WidgetSpacing.rows(h - 2 * PAD - hoursBlock - 24f, rowHeight, 3), m.days.size)
        val heights = listOf(hoursBlock, rows * rowHeight).filterIndexed { i, _ -> i == 0 || rows > 0 }
        val tops = WidgetSpacing.tops(PAD, h - 2 * PAD, heights)
        val columns = ((x1 - x0) / HOUR_COLUMN_MIN).toInt().coerceIn(2, WidgetModels.HOURS)
        hours(d, m, columns, x0, x1, tops[0], HOUR_LABEL, 52f, HOUR_TEMP)
        if (rows > 0) {
            for (i in 0 until rows) dayRow(d, m, i, x0, x1, tops[1] + i * rowHeight, rowHeight, 40f)
            d.rule(x0, (tops[0] + hoursBlock + tops[1]) / 2, x1)
        }
    }

    // ---- 4×4: шапка, часы, пять дней и строка показателей.
    private fun large(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val heroHeight = 172f
        val hoursBlock = hoursHeight(40f, 64f, 48f)
        val metricsHeight = 40f
        val rowHeight = 66f
        val space = h - 2 * PAD - heroHeight - hoursBlock - metricsHeight - 3 * 24f
        val rows = minOf(WidgetSpacing.rows(space, rowHeight, WidgetModels.DAYS), m.days.size)
        val heights = listOf(heroHeight, hoursBlock, rows * rowHeight, metricsHeight).filterIndexed { i, _ -> i != 2 || rows > 0 }
        val tops = WidgetSpacing.tops(PAD, h - 2 * PAD, heights)
        hero(d, m, w, tops[0], heroHeight)
        hours(d, m, WidgetModels.HOURS, PAD, w - PAD, tops[1], 40f, 64f, 48f)
        if (rows > 0) for (i in 0 until rows) dayRow(d, m, i, PAD, w - PAD, tops[2] + i * rowHeight, rowHeight, 44f)
        for (i in 1 until tops.size) {
            val gap = tops[i] - tops[i - 1] - heights[i - 1]
            d.rule(PAD, tops[i] - gap / 2, w - PAD)
        }
        metricsRow(d, m, PAD, w - PAD, tops.last() + metricsHeight / 2, 40f)
    }

    private fun hero(d: WidgetDrawer, m: WidgetModel, w: Float, top: Float, height: Float) {
        val icon = 150f
        val gap = 18f
        val temp = temperatureText(m.temperature)
        val size = fitSize(d, temp, 215f, (w - 2 * PAD - icon - gap) * 0.6f)
        val tempPen = d.pen(size, HERO_WEIGHT)
        val tempWidth = d.width(temp, tempPen)
        d.glyph(m.condition, m.isDay, m.windy, PAD, top + (height - icon) / 2, icon)
        d.text(temp, PAD + icon + gap, top + height / 2 + size * DIGIT_HEIGHT / 2, tempPen)
        val right = w - PAD
        val maxWidth = w - 2 * PAD - icon - gap - tempWidth - 20f
        val cityPen = d.pen(48f, 500)
        val textPen = d.pen(38f, 400, 0.9f)
        val hiLoPen = d.pen(42f, 400, 0.92f)
        var base = top + 4f + 48f * DIGIT_HEIGHT
        d.text(d.fit(m.placeName.orEmpty(), cityPen, maxWidth), right, base, cityPen, Paint.Align.RIGHT)
        base += 44f
        d.text(d.fit(condition(m), textPen, maxWidth), right, base, textPen, Paint.Align.RIGHT)
        base += 42f
        d.text(d.fit(feelsLike(m), textPen, maxWidth), right, base, textPen, Paint.Align.RIGHT)
        base += 46f
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        d.hiLo(right - d.hiLoWidth(high, low, hiLoPen), base, high, low, hiLoPen)
    }

    /** Размер крупных цифр: не больше [maxSize] и такой, чтобы [text] занимал не больше [maxWidth]. */
    private fun fitSize(d: WidgetDrawer, text: String, maxSize: Float, maxWidth: Float): Float {
        val natural = d.width(text, d.pen(MEASURE_SIZE, HERO_WEIGHT))
        return min(maxSize, (MEASURE_SIZE * maxWidth / natural).toInt().toFloat())
    }

    private fun hoursHeight(labelSize: Float, iconSize: Float, tempSize: Float): Float =
        labelSize * 1.25f + 6f + iconSize + 6f + tempSize * DIGIT_HEIGHT

    /** [count] часов ленты равными столбцами между [x0] и [x1]: подпись, значок, температура. */
    private fun hours(d: WidgetDrawer, m: WidgetModel, count: Int, x0: Float, x1: Float, top: Float, labelSize: Float, iconSize: Float, tempSize: Float) {
        val columnWidth = (x1 - x0) / count
        val labelPen = d.pen(labelSize, 400, 0.8f)
        val tempPen = d.pen(tempSize, 300)
        m.hours.take(count).forEachIndexed { i, hour ->
            val cx = x0 + columnWidth * (i + 0.5f)
            val iconTop = top + labelSize * 1.25f + 6f
            d.text(hourLabel(hour.time, m.zone, dayNames), cx, top + labelSize, labelPen, Paint.Align.CENTER)
            d.smallGlyph(hour.condition, hour.isDay, cx - iconSize / 2, iconTop, iconSize)
            d.text(temperatureText(hour.temperature), cx, iconTop + iconSize + 6f + tempSize * DIGIT_HEIGHT, tempPen, Paint.Align.CENTER)
        }
    }

    /** Строка дня: название, значок, минимум, полоска температур, максимум. Размеры в долях размера текста [size]. */
    private fun dayRow(d: WidgetDrawer, m: WidgetModel, index: Int, x0: Float, x1: Float, top: Float, rowHeight: Float, size: Float) {
        val day = m.days[index]
        val range = m.ranges[index]
        val pen = d.pen(size, 400)
        val dim = d.pen(size, 400, 0.75f)
        val cy = top + rowHeight / 2
        val base = cy + size * DIGIT_HEIGHT / 2
        val icon = size * 1.15f
        val iconX = x0 + size * 1.9f
        d.text(dayName(day.date, dayNames), x0, base, pen)
        d.smallGlyph(day.condition, true, iconX, cy - icon / 2, icon)
        d.text(temperatureText(day.min), iconX + icon + size * 2.1f, base, dim, Paint.Align.RIGHT)
        d.rangeBar(iconX + icon + size * 2.5f, x1 - size * 2.3f, cy, range.start, range.width)
        d.text(temperatureText(day.max), x1, base, pen, Paint.Align.RIGHT)
    }

    /** Строка показателей 4×4 высотой текста [size]: ветер, влажность, осадки за сегодня. */
    private fun metricsRow(d: WidgetDrawer, m: WidgetModel, x0: Float, x1: Float, cy: Float, size: Float) {
        val pen = d.pen(size, 400)
        val cell = (x1 - x0) / 3
        val icon = size * 1.1f
        val values = listOf(
            MetricIcon.Wind to windText(m),
            MetricIcon.Humidity to (m.humidity?.let { percentText(it) } ?: DASH),
            MetricIcon.Rain to precipitationMmText(m),
        )
        values.forEachIndexed { i, (kind, value) ->
            val x = x0 + cell * i
            d.metricIcon(kind, x, cy, icon)
            d.text(d.fit(value, pen, cell - icon - 14f), x + icon + 10f, cy + size * DIGIT_HEIGHT / 2, pen)
        }
    }

    private fun windText(m: WidgetModel): String =
        m.windKmh?.let { "${it.roundToInt()} ${context.getString(R.string.kmh_unit)}" } ?: DASH

    private fun precipitationMmText(m: WidgetModel): String {
        val mm = m.precipitationMm ?: return DASH
        val text = dayPrecipitationText(mm, locale).ifEmpty { "0" }
        return "$text ${context.getString(R.string.mm_unit)}"
    }

    private companion object {
        const val PAD = 32f
        const val CORNER = 40f
        const val GLASS_R = 20
        const val GLASS_G = 40
        const val GLASS_B = 70
        const val RIM_ALPHA = 87
        const val SHADOW_BELOW_OPACITY = 50
        const val HOUR_COLUMN_MIN = 88f
        const val HOUR_COLUMN_SQUARE = 90f
        const val HOUR_LABEL = 36f
        const val HOUR_TEMP = 44f
        const val HERO_WEIGHT = 300
        const val MEASURE_SIZE = 100f
        const val ICON_SPACE = 0.8f
        const val TINY_SIDE = 14f
        const val NARROW_SHARE = 0.62f
        const val DASH = "–"
    }
}
