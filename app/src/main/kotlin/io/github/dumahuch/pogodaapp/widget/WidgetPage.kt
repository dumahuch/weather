package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import android.graphics.Paint
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.temperatureDigits
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.notify.precipitationPhrase
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.widget.WidgetDrawer.Companion.DIGIT_HEIGHT
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Сведения виджета на готовом фоне, по размеру и делению слайдера [detail] (решения Димы 14.11.2026, макеты от 14.11):
 * «Минимум» температура (и город, где есть место), «Обычно» ещё фраза про осадки и максимум с минимумом, «Подробно» ещё
 * «Ощущается как», ветер, влажность, осадки и дни. Часов нет. На небе ([sky]) значка нет: погоду показывают облака, солнце и луна.
 * Размеры [w] и [h] в «единицах макета»; что не помещается, убирается, а не уменьшается.
 */
internal class WidgetPage(
    private val context: Context,
    private val d: WidgetDrawer,
    private val m: WidgetModel,
    private val w: Float,
    private val h: Float,
    private val sky: Boolean,
    private val detail: WidgetDetail,
) {
    private val locale = context.resources.configuration.locales[0]
    private val dayNames: List<String> = context.resources.getStringArray(R.array.days_short).toList()
    private val minimum get() = detail == WidgetDetail.Minimum
    private val full get() = detail == WidgetDetail.Full

    fun draw(layout: WidgetLayout) = when (layout) {
        WidgetLayout.Tiny -> tiny()
        WidgetLayout.Narrow -> narrow()
        WidgetLayout.Tall -> tall()
        WidgetLayout.Strip -> strip()
        WidgetLayout.Square -> square()
        WidgetLayout.Wide -> wide()
        WidgetLayout.Large -> large()
    }

    // ---- 1×1: город сверху, цифра по центру (значок над её правым верхним углом, на небе его нет), максимум и минимум внизу;
    // на всех делениях одно и то же (договор Димы 14.11: цифра по центру, 14.11 19:16: город и максимум с минимумом вернуть).
    private fun tiny() {
        val digits = temperatureDigits(m.temperature)
        val size = fitSize(digits, 150f, w - 2 * TINY_SIDE)
        val iconSize = 66f
        val plan = TinyPlan.plan(h, size, if (sky) 0f else iconSize, DIGIT_HEIGHT)
        val textWidth = heroWidth(digits, plan.digitSize)
        val left = (w - textWidth) / 2
        val city = m.placeName.orEmpty()
        if (city.isNotEmpty()) {
            val cityPen = d.pen(TinyPlan.LABEL, 600)
            d.text(d.fit(city, cityPen, w - 2 * TINY_SIDE), w / 2, plan.cityBase, cityPen, Paint.Align.CENTER)
        }
        icon(left + textWidth - iconSize, plan.iconTop, iconSize)
        hero(digits, plan.digitSize, left, plan.digitsBase)
        d.hiLoCentered(w / 2, plan.hiLoBase, temperatureText(m.high), temperatureText(m.low), TinyPlan.LABEL, w - 2 * TINY_SIDE)
    }

    // ---- 2×1: «Минимум» цифра по центру (без города); иначе цифра слева, справа город, фраза, максимум и минимум.
    private fun narrow() {
        if (minimum) return centered(160f, 70f, w * NARROW_SHARE)
        val pad = SMALL_PAD
        val base = h - pad
        val digits = temperatureDigits(m.temperature)
        val size = fitSize(digits, 125f, NARROW_DIGITS)
        val left = pad - optical(size)
        hero(digits, size, left, base)
        val iconSize = 60f
        icon(left + heroWidth(digits, size) - iconSize, base - size * DIGIT_HEIGHT - 8f - iconSize, iconSize)
        val x0 = pad + NARROW_COLUMN
        val width = w - x0 - pad
        val cityPen = d.pen(36f, 600)
        val textPen = d.pen(36f, 400, 0.8f)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, width), x0, 76f, cityPen)
        d.text(d.fit(phrase(), textPen, width), x0, 124f, textPen)
        d.hiLo(x0, base, temperatureText(m.high), temperatureText(m.low), 36f)
    }

    // ---- 1×2: город сверху, значок (не на небе), цифра, под ней максимум и минимум столбиком; «Минимум» без них.
    private fun tall() {
        val pad = SMALL_PAD
        val base = h - pad
        val digits = temperatureDigits(m.temperature)
        val size = fitSize(digits, 150f, w - 2 * pad)
        val cityPen = d.pen(36f, 600)
        if (minimum) {
            if (sky) d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * pad), pad, pad + 36f, cityPen)
            hero(digits, size, pad - optical(size), base)
            icon(w - pad - 124f + 6f, pad - 4f, 124f)
            return
        }
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * pad), pad, pad + 36f, cityPen)
        val lowBase = base
        val highBase = lowBase - TALL_ROW
        hero(digits, size, pad - optical(size), highBase - 40f * DIGIT_HEIGHT - 36f)
        icon(w - pad - 100f + 4f, 90f, 100f)
        d.arrowText(pad, highBase, true, temperatureText(m.high), d.highPen(40f))
        d.arrowText(pad, lowBase, false, temperatureText(m.low), d.lowPen(40f))
    }

    // ---- 4×1: слева город и цифра, справа фраза про осадки, максимум и минимум; «Подробно» ещё «Ощущается как».
    private fun strip() {
        val pad = PAD
        val leftWidth = 330f
        val cityPen = d.pen(40f, 600)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, leftWidth), pad, pad + 32f, cityPen)
        val digits = temperatureDigits(m.temperature)
        val size = fitSize(digits, 140f, leftWidth - 80f)
        val base = h - pad
        hero(digits, size, pad - optical(size), base)
        val iconSize = 88f
        icon(pad + heroWidth(digits, size) + 14f, base - size * DIGIT_HEIGHT / 2 - iconSize / 2, iconSize)
        if (minimum) return
        val dividerX = pad + leftWidth + 14f
        d.verticalRule(dividerX, 36f, h - 36f)
        val x0 = dividerX + 20f
        val width = w - x0 - pad
        val textPen = d.pen(40f, 400, 0.85f)
        d.text(d.fit(stateLine(), textPen, width), x0, 78f, textPen)
        if (full) {
            val feelsPen = d.pen(40f, 400, 0.6f)
            d.text(d.fit(feelsLike(), feelsPen, width), x0, 126f, feelsPen)
        }
        d.hiLo(x0, base, temperatureText(m.high), temperatureText(m.low), 44f)
    }

    // ---- 2×2: город сверху; цифра внизу слева (значок справа вверху, не на небе); «Обычно» с фразой и максимумом и минимумом.
    private fun square() {
        val digits = temperatureDigits(m.temperature)
        val cityPen = d.pen(40f, 600)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD), PAD, 82f, cityPen)
        when (detail) {
            WidgetDetail.Minimum -> {
                val size = fitSize(digits, .62f * h, w - 2 * PAD)
                icon(w - PAD - 196f, 26f, 196f)
                hero(digits, size, PAD - optical(size), h - PAD)
            }
            WidgetDetail.Normal -> {
                phraseLine(120f, 40f)
                icon(w - PAD - 150f, 32f, 150f)
                val size = fitSize(digits, 170f, w - 2 * PAD)
                d.hiLo(PAD, h - PAD, temperatureText(m.high), temperatureText(m.low), 44f)
                hero(digits, size, PAD - optical(size), h - PAD - 44f * DIGIT_HEIGHT - 30f)
            }
            WidgetDetail.Full -> {
                phraseLine(120f, 40f)
                icon(w - PAD - 130f, 34f, 130f)
                val size = fitSize(digits, 150f, w - 2 * PAD)
                hero(digits, size, PAD - optical(size), 246f)
                d.hiLo(PAD, 310f, temperatureText(m.high), temperatureText(m.low), 40f)
                val half = (w - 2 * PAD) / 2
                val labelTop = h - PAD - 40f * DIGIT_HEIGHT - FACT_LABEL_RISE - 26f
                d.verticalRule(w / 2 + 8f, labelTop - 6f, h - PAD + 6f)
                d.fact(PAD, h - PAD, context.getString(R.string.tile_wind_now), windText(), 40f, half - 28f)
                d.fact(w / 2 + 36f, h - PAD, context.getString(R.string.tile_humidity), humidityText(), 40f, half - 36f)
            }
        }
    }

    // ---- 4×2: слева город, фраза и цифра, справа в «Подробно» три дня.
    private fun wide() {
        val digits = temperatureDigits(m.temperature)
        val cityPen = d.pen(40f, 600)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD), PAD, 82f, cityPen)
        when (detail) {
            WidgetDetail.Minimum -> {
                val size = fitSize(digits, .72f * h, w - 2 * PAD)
                icon(w - PAD - 340f, 88f, 300f)
                hero(digits, size, PAD - optical(size), h - PAD)
            }
            WidgetDetail.Normal -> {
                stateLine(128f, 40f, w * .55f)
                icon(w - PAD - 320f, 98f, 280f)
                val size = fitSize(digits, 200f, w - 2 * PAD)
                d.hiLo(PAD, h - PAD, temperatureText(m.high), temperatureText(m.low), 44f)
                hero(digits, size, PAD - optical(size), h - PAD - 44f * DIGIT_HEIGHT - 30f)
            }
            WidgetDetail.Full -> {
                val leftEdge = w - WIDE_DAYS_WIDTH - 44f
                stateLine(128f, 40f, leftEdge - PAD)
                feelsLine(176f, 40f, leftEdge - PAD)
                icon(leftEdge - 230f, 118f, 230f)
                val size = fitSize(digits, 200f, leftEdge - 230f - PAD - 20f)
                d.hiLo(PAD, h - PAD, temperatureText(m.high), temperatureText(m.low), 44f)
                hero(digits, size, PAD - optical(size), h - PAD - 44f * DIGIT_HEIGHT - 30f)
                days(leftEdge + 22f)
            }
        }
    }

    /** Колонка из трёх дней справа в 4×2: название, значок, максимум и минимум; от [x0] до правого поля. */
    private fun days(x0: Float) {
        val rows = min(3, m.days.size)
        d.verticalRule(x0 - 22f, 48f, h - 48f)
        val top = (h - WIDE_ROWS_HEIGHT) / 2 + 16f
        val namePen = d.pen(40f, 400, 0.85f)
        for (i in 0 until rows) {
            val day = m.days[i]
            val y0 = top + i * WIDE_ROW
            d.text(dayName(day.date, dayNames), x0, y0 + 76f, namePen)
            d.smallGlyph(day.condition, true, x0 + 80f, y0 + 38f, 58f)
            d.text(temperatureText(day.max), w - PAD, y0 + 58f, d.highPen(38f), Paint.Align.RIGHT)
            d.text(temperatureText(day.min), w - PAD, y0 + 104f, d.lowPen(38f), Paint.Align.RIGHT)
            if (i < rows - 1) d.rule(x0, y0 + 122f, w - PAD)
        }
    }

    // ---- 4×4: шапка с городом и состоянием, цифра, максимум и минимум, ветер, влажность, осадки, в «Подробно» пять дней.
    private fun large() {
        val digits = temperatureDigits(m.temperature)
        val cityPen = d.pen(44f, 600)
        d.text(d.fit(m.placeName.orEmpty(), cityPen, w - 2 * PAD), PAD, 86f, cityPen)
        when (detail) {
            WidgetDetail.Minimum -> {
                val statePen = d.pen(44f, 400, 0.8f)
                d.text(d.fit(condition(), statePen, w - 2 * PAD), PAD, 138f, statePen)
                val size = fitSize(digits, .45f * h, w - 2 * PAD)
                hero(digits, size, PAD - optical(size), h - PAD)
                iconTopRight(450f, 150f, h - PAD - size * DIGIT_HEIGHT - 12f)
            }
            WidgetDetail.Normal -> {
                stateLine(138f, 44f, w - 2 * PAD)
                feelsLine(188f, 40f, w - 2 * PAD)
                icon(w - PAD - 380f, 230f, 400f)
                val size = fitSize(digits, 215f, w - 2 * PAD)
                hero(digits, size, PAD - optical(size), 620f)
                d.hiLo(PAD, 706f, temperatureText(m.high), temperatureText(m.low), 48f)
                facts(h - PAD, 52f)
            }
            WidgetDetail.Full -> {
                stateLine(138f, 44f, w - 2 * PAD)
                feelsLine(188f, 40f, w - 2 * PAD)
                icon(w - PAD - 240f, 40f, 250f)
                val size = fitSize(digits, 170f, w - 2 * PAD)
                hero(digits, size, PAD - optical(size), 372f)
                d.hiLo(PAD, 432f, temperatureText(m.high), temperatureText(m.low), 44f)
                val daysTop = h - PAD - LARGE_DAYS_HEIGHT
                facts(daysTop - 26f, 46f)
                largeDays(daysTop)
            }
        }
    }

    /** Ветер, влажность и осадки тремя столбцами, значение на линии [valueBase]. */
    private fun facts(valueBase: Float, size: Float) {
        val span = w - 2 * PAD
        val columns = listOf(
            context.getString(R.string.tile_wind_now) to windText(),
            context.getString(R.string.tile_humidity) to humidityText(),
            context.getString(R.string.tile_precipitation) to precipitationText(),
        )
        val labelTop = valueBase - size * DIGIT_HEIGHT - FACT_LABEL_RISE - 26f
        columns.forEachIndexed { i, (label, value) ->
            val x = PAD + span * FACT_COLUMNS[i]
            val width = span * (if (i < 2) FACT_COLUMNS[i + 1] - FACT_COLUMNS[i] else 1f - FACT_COLUMNS[i]) - 30f
            d.fact(x, valueBase, label, value, size, width)
            if (i > 0) d.verticalRule(x - 20f, labelTop - 6f, valueBase + 8f)
        }
    }

    /** Пять дней 4×4: название, значок, минимум, полоска температур и максимум; блок высотой [LARGE_DAYS_HEIGHT] от [top]. */
    private fun largeDays(top: Float) {
        val barFrom = PAD + 218f
        val barTo = w - PAD - 112f
        val namePen = d.pen(40f, 400, 0.85f)
        val lowPen = d.lowPen(38f)
        val highPen = d.highPen(38f)
        for (i in 0 until min(WidgetModels.DAYS, m.days.size)) {
            val day = m.days[i]
            val y0 = top + i * LARGE_ROW
            val cy = y0 + LARGE_ROW / 2
            d.rule(PAD, y0, w - PAD)
            d.text(dayName(day.date, dayNames), PAD, cy + 40f * DIGIT_HEIGHT / 2, namePen)
            d.smallGlyph(day.condition, true, PAD + 96f, cy - 25f, 50f)
            d.text(temperatureText(day.min), barFrom - 14f, cy + 38f * DIGIT_HEIGHT / 2, lowPen, Paint.Align.RIGHT)
            d.rangeBar(barFrom, barTo, cy, m.ranges[i].start, m.ranges[i].width)
            d.text(temperatureText(day.max), w - PAD, cy + 38f * DIGIT_HEIGHT / 2, highPen, Paint.Align.RIGHT)
        }
    }

    // ---- общие части

    /** Цифра по центру окна и значок над её правым верхним углом (на небе значка нет): «Минимум» 2×1. */
    private fun centered(maxSize: Float, iconSize: Float, maxWidth: Float) {
        val digits = temperatureDigits(m.temperature)
        val size = fitSize(digits, maxSize, maxWidth)
        val textWidth = heroWidth(digits, size)
        val cap = size * DIGIT_HEIGHT
        val iconHeight = if (sky) 0f else iconSize
        val top = (h - (iconHeight + cap)) / 2
        val left = (w - textWidth) / 2
        icon(left + textWidth - iconSize, top, iconSize)
        hero(digits, size, left, top + iconHeight + cap)
    }

    /** Значок погоды в квадрате [size] с левым верхним углом ([x], [y]); на небе его нет. */
    private fun icon(x: Float, y: Float, size: Float) {
        if (!sky) d.glyph(m.condition, m.isDay, m.windy, x, y, size)
    }

    /** Значок в правом верхнем углу: верх [top], сторона не больше [size] и не ниже [limitBottom]. */
    private fun iconTopRight(size: Float, top: Float, limitBottom: Float) {
        val side = min(size, limitBottom - top)
        if (side > MIN_ICON) icon(w - PAD - side, top, side)
    }

    /** Фраза про осадки одной строкой ([size] текста, нижняя линия [base]). */
    private fun phraseLine(base: Float, size: Float) {
        val pen = d.pen(size, 400, 0.8f)
        d.text(d.fit(phrase(), pen, w - 2 * PAD), PAD, base, pen)
    }

    private fun stateLine(base: Float, size: Float, maxWidth: Float) {
        val pen = d.pen(size, 400, 0.8f)
        d.text(d.fit(stateLine(), pen, maxWidth), PAD, base, pen)
    }

    private fun feelsLine(base: Float, size: Float, maxWidth: Float) {
        val pen = d.pen(size, 400, 0.6f)
        d.text(d.fit(feelsLike(), pen, maxWidth), PAD, base, pen)
    }

    private fun condition(): String = context.getString(if (m.drizzle) R.string.wx_drizzle else conditionNameRes(m.condition))

    private fun phrase(): String = precipitationPhrase(context, m.caption, locale)

    /** «Дождь · осадки днём». */
    private fun stateLine(): String = "${condition()} · ${phrase().lowercase(locale)}"

    private fun feelsLike(): String = "${context.getString(R.string.feels_like)} ${temperatureText(m.feelsLike)}"

    private fun windText(): String = m.windKmh?.let { "${it.roundToInt()} ${context.getString(R.string.kmh_unit)}" } ?: DASH

    private fun humidityText(): String = m.humidity?.let { percentText(it) } ?: DASH

    private fun precipitationText(): String {
        val mm = m.precipitationMm ?: return DASH
        val text = dayPrecipitationText(mm, locale).ifEmpty { "0" }
        return "$text ${context.getString(R.string.mm_unit)}"
    }

    /** Размер крупных цифр: не больше [maxSize] и такой, чтобы [digits] со знаком градуса занимали не больше [maxWidth]. */
    private fun fitSize(digits: String, maxSize: Float, maxWidth: Float): Float {
        val perSize = d.width(digits, d.pen(MEASURE_SIZE, HERO_WEIGHT)) / MEASURE_SIZE + DEGREE_GAP + DEGREE_DIAMETER
        return min(maxSize, (maxWidth / perSize).toInt().toFloat())
    }

    /** Ширина крупной температуры: цифры, зазор и колечко градуса. */
    private fun heroWidth(digits: String, size: Float): Float =
        d.width(digits, d.pen(size, HERO_WEIGHT)) + size * (DEGREE_GAP + DEGREE_DIAMETER)

    /** На сколько цифры сдвинуты левее поля: у круглых цифр левый край виден левее края знака (оптическое выравнивание). */
    private fun optical(size: Float): Float = size * OPTICAL_SHIFT

    /**
     * Крупная температура: цифры и своё колечко градуса вместо знака шрифта (решение Димы 14.11.2026: градус легче, чем в
     * 0.8.10: диаметр 0,15, линия 0,03, прозрачность 80 %). Колечко вровень с верхом цифр, [x] слева, [baseline] нижняя линия цифр.
     */
    private fun hero(digits: String, size: Float, x: Float, baseline: Float) {
        val pen = d.pen(size, HERO_WEIGHT)
        d.text(digits, x, baseline, pen)
        val digitsWidth = d.width(digits, pen)
        val radius = size * DEGREE_DIAMETER / 2
        val stroke = size * DEGREE_STROKE
        d.ring(x + digitsWidth + size * DEGREE_GAP + radius, baseline - size * DIGIT_HEIGHT + radius + stroke / 2, radius - stroke / 2, stroke, DEGREE_ALPHA)
    }

    internal companion object {
        /** Колонка дней 4×2 и высота блока дней 4×4 (от них зависит вуаль под ними). */
        const val WIDE_DAYS_WIDTH = 174f
        const val LARGE_DAYS_HEIGHT = 290f

        private const val PAD = 40f
        private const val SMALL_PAD = 38f
        private const val TINY_SIDE = 22f
        private const val NARROW_SHARE = 0.62f
        private const val NARROW_DIGITS = 166f
        private const val NARROW_COLUMN = 178f
        private const val TALL_ROW = 56f
        private const val WIDE_ROW = 132f
        private const val WIDE_ROWS_HEIGHT = 3 * WIDE_ROW
        private const val LARGE_ROW = 58f
        private const val FACT_LABEL_RISE = 16f
        private const val MIN_ICON = 120f
        private val FACT_COLUMNS = floatArrayOf(0f, 0.367f, 0.734f)
        private const val HERO_WEIGHT = 300
        private const val MEASURE_SIZE = 100f
        private const val DEGREE_GAP = 0.045f
        private const val DEGREE_DIAMETER = 0.15f
        private const val DEGREE_STROKE = 0.03f
        private const val DEGREE_ALPHA = 0.8f
        private const val OPTICAL_SHIFT = 0.04f
        private const val DASH = "–"
    }
}
