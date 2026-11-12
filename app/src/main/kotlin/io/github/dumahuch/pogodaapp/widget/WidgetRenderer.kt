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
import io.github.dumahuch.pogodaapp.notify.WeatherNotice
import io.github.dumahuch.pogodaapp.notify.precipitationPhrase
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
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

    private fun precipitation(m: WidgetModel): String = precipitationPhrase(context, m.precipitation, locale)

    // ---- 1×1: значок над температурой, по центру.
    private fun tiny(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val icon = 100f
        val size = 84f
        val cap = size * DIGIT_HEIGHT
        val top = (h - (icon + 6f + cap)) / 2
        d.glyph(m.condition, m.isDay, m.windy, w / 2 - icon / 2, top, icon)
        d.text(temperatureText(m.temperature), w / 2, top + icon + 6f + cap, d.pen(size, 200), Paint.Align.CENTER)
    }

    // ---- 2×1: значок слева, справа город, температура, максимум и минимум; группа по центру, по краям воздух.
    private fun narrow(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val icon = 116f
        val gap = 20f
        val cityPen = d.pen(22f, 500)
        val tempPen = d.pen(92f, 200)
        val hiLoPen = d.pen(22f, 400, 0.92f)
        val temp = temperatureText(m.temperature)
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        val city = d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD - icon - gap)
        val tempCap = 92f * DIGIT_HEIGHT
        val hiLoCap = 22f * DIGIT_HEIGHT
        val columnHeight = 26f + 6f + tempCap + 10f + hiLoCap
        val top = (h - columnHeight) / 2
        val columnWidth = maxOf(d.width(city, cityPen), d.width(temp, tempPen), d.hiLoWidth(high, low, hiLoPen))
        val left = (w - (icon + gap + columnWidth)) / 2
        d.glyph(m.condition, m.isDay, m.windy, left, (h - icon) / 2, icon)
        val x = left + icon + gap
        d.text(city, x, top + 21f, cityPen)
        val tempBase = top + 26f + 6f + tempCap
        d.text(temp, x, tempBase, tempPen)
        d.hiLo(x, tempBase + 10f + hiLoCap, high, low, hiLoPen)
    }

    // ---- 4×1: слева значок, город, температура с максимумом и минимумом, описание; справа часы.
    private fun strip(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val icon = 112f
        val leftWidth = 380f
        val cityPen = d.pen(22f, 500)
        val tempPen = d.pen(96f, 200)
        val textPen = d.pen(22f, 400, 0.92f)
        val textWidth = leftWidth - icon - 20f
        val tempCap = 96f * DIGIT_HEIGHT
        val columnHeight = 26f + 6f + tempCap + 8f + 24f
        val top = (h - columnHeight) / 2
        d.glyph(m.condition, m.isDay, m.windy, PAD, (h - icon) / 2, icon)
        val x = PAD + icon + 20f
        d.text(d.fit(m.placeName.orEmpty(), cityPen, textWidth), x, top + 21f, cityPen)
        val tempBase = top + 26f + 6f + tempCap
        val temp = temperatureText(m.temperature)
        d.text(temp, x, tempBase, tempPen)
        val stackX = x + d.width(temp, tempPen) + 18f
        val stackPen = d.pen(22f, 400, 0.92f)
        d.arrowText(stackX, tempBase - tempCap / 2 - 4f, true, temperatureText(m.high), stackPen)
        d.arrowText(stackX, tempBase, false, temperatureText(m.low), stackPen)
        d.text(d.fit(condition(m), textPen, textWidth), x, tempBase + 8f + 20f, textPen)
        val dividerX = PAD + leftWidth + 22f
        d.verticalRule(dividerX, 28f, h - 28f)
        val columns = ((w - PAD - dividerX - 22f) / HOUR_COLUMN_MIN).toInt().coerceIn(2, WidgetModels.HOURS)
        val hoursTop = (h - hoursHeight(20f, 48f, 28f)) / 2
        hours(d, m, columns, dividerX + 22f, w - PAD, hoursTop, 20f, 48f, 28f)
    }

    // ---- 2×2: город и описание сверху, по центру значок слева и температура справа, внизу часы.
    private fun square(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val cityPen = d.pen(22f, 500)
        val descPen = d.pen(20f, 400, 0.92f)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD), PAD, PAD + 21f, cityPen)
        d.text(d.fit(condition(m), descPen, w - 2 * PAD), PAD, PAD + 21f + 26f, descPen)
        val hoursBlock = hoursHeight(18f, 40f, 24f)
        val hoursTop = h - PAD - hoursBlock
        val ruleY = hoursTop - 12f
        d.rule(PAD, ruleY, w - PAD)
        val columns = ((w - 2 * PAD) / HOUR_COLUMN_SQUARE).toInt().coerceIn(2, 4)
        hours(d, m, columns, PAD, w - PAD, hoursTop, 18f, 40f, 24f)
        val icon = 116f
        val tempPen = d.pen(128f, 200)
        val hiLoPen = d.pen(22f, 400, 0.92f)
        val temp = temperatureText(m.temperature)
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        val tempCap = 128f * DIGIT_HEIGHT
        val hiLoCap = 22f * DIGIT_HEIGHT
        val columnHeight = tempCap + 10f + hiLoCap
        val areaTop = PAD + 21f + 26f + 14f
        val rowTop = areaTop + (ruleY - 10f - areaTop - columnHeight) / 2
        val columnWidth = maxOf(d.width(temp, tempPen), d.hiLoWidth(high, low, hiLoPen))
        val left = (w - (icon + 16f + columnWidth)) / 2
        d.glyph(m.condition, m.isDay, m.windy, left, rowTop + (columnHeight - icon) / 2, icon)
        val x = left + icon + 16f
        d.text(temp, x, rowTop + tempCap, tempPen)
        d.hiLo(x, rowTop + tempCap + 10f + hiLoCap, high, low, hiLoPen)
    }

    // ---- 4×2: слева текущая погода, справа часы, до трёх дней и показатели.
    private fun wide(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val leftWidth = 380f
        val cityPen = d.pen(26f, 500)
        val textPen = d.pen(22f, 400, 0.92f)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, leftWidth), PAD, PAD + 26f, cityPen)
        d.text(d.fit(condition(m), textPen, leftWidth), PAD, PAD + 26f + 30f, textPen)
        val feelsBase = h - PAD - 30f
        val hiLoBase = h - PAD - 4f
        d.text(d.fit(feelsLike(m), textPen, leftWidth), PAD, feelsBase, textPen)
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        val hiLoWidth = d.hiLo(PAD, hiLoBase, high, low, textPen)
        val continuation = WeatherNotice.precipitationContinuation(precipitation(m), locale)
        d.text(d.fit("· $continuation", textPen, leftWidth - hiLoWidth - 8f), PAD + hiLoWidth + 8f, hiLoBase, textPen)
        val icon = 138f
        val tempPen = d.pen(176f, 200)
        val midTop = PAD + 26f + 30f + 10f
        val midBottom = feelsBase - 22f
        val midY = (midTop + midBottom) / 2
        d.glyph(m.condition, m.isDay, m.windy, PAD, midY - icon / 2, icon)
        d.text(temperatureText(m.temperature), PAD + leftWidth, midY + 176f * DIGIT_HEIGHT / 2, tempPen, Paint.Align.RIGHT)
        val dividerX = PAD + leftWidth + 26f
        d.verticalRule(dividerX, PAD + 6f, h - PAD - 6f)
        val x0 = dividerX + 26f
        val x1 = w - PAD
        val hoursBlock = hoursHeight(20f, 50f, 28f)
        val metricsHeight = 30f
        val rowHeight = 62f
        val space = h - 2 * PAD - hoursBlock - metricsHeight - 2 * 24f
        val rows = minOf(WidgetSpacing.rows(space, rowHeight, 3), m.days.size)
        val heights = listOf(hoursBlock, rows * rowHeight, metricsHeight).filterIndexed { i, _ -> i != 1 || rows > 0 }
        val tops = WidgetSpacing.tops(PAD, h - 2 * PAD, heights)
        val columns = ((x1 - x0) / HOUR_COLUMN_NARROW).toInt().coerceIn(2, WidgetModels.HOURS)
        hours(d, m, columns, x0, x1, tops[0], 20f, 50f, 28f)
        if (rows > 0) for (i in 0 until rows) dayRow(d, m, i, x0, x1, tops[1] + i * rowHeight, rowHeight, 24f)
        for (i in 1 until tops.size) {
            val gap = tops[i] - tops[i - 1] - heights[i - 1]
            d.rule(x0, tops[i] - gap / 2, x1)
        }
        metricsRow(d, m, x0, x1, tops.last() + metricsHeight / 2)
    }

    // ---- 4×4: всё сразу: шапка, часы, пять дней, шесть показателей.
    private fun large(d: WidgetDrawer, m: WidgetModel, w: Float, h: Float) {
        val heroHeight = 190f
        val hoursBlock = hoursHeight(21f, 60f, 30f)
        val gridHeight = 2 * 50f + 14f
        val rowHeight = 68f
        val space = h - 2 * PAD - heroHeight - hoursBlock - gridHeight - 3 * 28f
        val rows = minOf(WidgetSpacing.rows(space, rowHeight, WidgetModels.DAYS), m.days.size)
        val heights = listOf(heroHeight, hoursBlock, rows * rowHeight, gridHeight).filterIndexed { i, _ -> i != 2 || rows > 0 }
        val tops = WidgetSpacing.tops(PAD, h - 2 * PAD, heights)
        hero(d, m, w, tops[0], heroHeight)
        hours(d, m, WidgetModels.HOURS, PAD, w - PAD, tops[1], 21f, 60f, 30f)
        if (rows > 0) for (i in 0 until rows) dayRow(d, m, i, PAD, w - PAD, tops[2] + i * rowHeight, rowHeight, 26f)
        for (i in 1 until tops.size) {
            val gap = tops[i] - tops[i - 1] - heights[i - 1]
            d.rule(PAD, tops[i] - gap / 2, w - PAD)
        }
        metricsGrid(d, m, tops.last(), w)
    }

    private fun hero(d: WidgetDrawer, m: WidgetModel, w: Float, top: Float, height: Float) {
        val icon = 162f
        val tempPen = d.pen(176f, 200)
        d.glyph(m.condition, m.isDay, m.windy, PAD, top + (height - icon) / 2, icon)
        d.text(temperatureText(m.temperature), PAD + icon + 22f, top + height / 2 + 176f * DIGIT_HEIGHT / 2, tempPen)
        val right = w - PAD
        val maxWidth = w - 2 * PAD - icon - 22f - d.width(temperatureText(m.temperature), tempPen) - 20f
        val cityPen = d.pen(30f, 500)
        val textPen = d.pen(22f, 400, 0.92f)
        var base = top + 28f
        d.text(d.fit(m.placeName.orEmpty(), cityPen, maxWidth), right, base, cityPen, Paint.Align.RIGHT)
        base += 34f
        d.text(d.fit(condition(m), textPen, maxWidth), right, base, textPen, Paint.Align.RIGHT)
        base += 33f
        d.text(d.fit(feelsLike(m), textPen, maxWidth), right, base, textPen, Paint.Align.RIGHT)
        base += 33f
        d.text(d.fit(precipitation(m), textPen, maxWidth), right, base, textPen, Paint.Align.RIGHT)
        base += 36f
        val high = temperatureText(m.high)
        val low = temperatureText(m.low)
        d.hiLo(right - d.hiLoWidth(high, low, textPen), base, high, low, textPen)
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
        val icon = size * 1.6f
        val iconX = x0 + size * 2.2f
        d.text(dayName(day.date, dayNames), x0, base, pen)
        d.smallGlyph(day.condition, true, iconX, cy - icon / 2, icon)
        d.text(temperatureText(day.min), iconX + icon + size * 2.1f, base, dim, Paint.Align.RIGHT)
        d.rangeBar(iconX + icon + size * 2.5f, x1 - size * 2.3f, cy, range.start, range.width)
        d.text(temperatureText(day.max), x1, base, pen, Paint.Align.RIGHT)
    }

    /** Одна строка показателей 4×2: ветер, влажность, осадки за сегодня. */
    private fun metricsRow(d: WidgetDrawer, m: WidgetModel, x0: Float, x1: Float, cy: Float) {
        val pen = d.pen(22f, 400)
        val cell = (x1 - x0) / 3
        val values = listOf(
            MetricIcon.Wind to windText(m),
            MetricIcon.Humidity to (m.humidity?.let { percentText(it) } ?: DASH),
            MetricIcon.Rain to precipitationMmText(m),
        )
        values.forEachIndexed { i, (icon, value) ->
            val x = x0 + cell * i
            d.metricIcon(icon, x, cy, 28f)
            d.text(d.fit(value, pen, cell - 38f), x + 36f, cy + 22f * DIGIT_HEIGHT / 2, pen)
        }
    }

    /** Сетка показателей 4×4: три столбца, две строки, подпись и значение. */
    private fun metricsGrid(d: WidgetDrawer, m: WidgetModel, top: Float, w: Float) {
        val labelPen = d.pen(19f, 400, 0.7f)
        val valuePen = d.pen(27f, 400)
        val items = listOf(
            context.getString(R.string.tile_wind_now) to windText(m),
            context.getString(R.string.tile_humidity) to (m.humidity?.let { percentText(it) } ?: DASH),
            context.getString(R.string.tile_precipitation) to precipitationMmText(m),
            context.getString(R.string.tile_pressure) to (m.pressureHpa?.let { "${it.roundToInt()} ${context.getString(R.string.hpa_unit)}" } ?: DASH),
            context.getString(R.string.sunrise) to (m.sunrise?.let { clockText(it, m.zone) } ?: DASH),
            context.getString(R.string.sunset) to (m.sunset?.let { clockText(it, m.zone) } ?: DASH),
        )
        val cell = (w - 2 * PAD) / 3
        items.forEachIndexed { i, (label, value) ->
            val x = PAD + cell * (i % 3)
            val y = top + (i / 3) * 64f
            d.text(d.fit(label, labelPen, cell - 8f), x, y + 19f, labelPen)
            d.text(d.fit(value, valuePen, cell - 8f), x, y + 19f + 4f + 27f * DIGIT_HEIGHT + 6f, valuePen)
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
        const val PAD = 22f
        const val CORNER = 40f
        const val GLASS_R = 20
        const val GLASS_G = 40
        const val GLASS_B = 70
        const val RIM_ALPHA = 87
        const val SHADOW_BELOW_OPACITY = 50
        const val HOUR_COLUMN_MIN = 96f
        const val HOUR_COLUMN_SQUARE = 130f
        const val HOUR_COLUMN_NARROW = 68f
        const val DASH = "–"
    }
}
