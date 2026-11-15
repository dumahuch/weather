package io.github.dumahuch.pogodaapp.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import io.github.dumahuch.pogodaapp.map.TemperatureField
import kotlin.math.ceil

/**
 * Значки слоя «Температура»: числа градусов (стрелок ветра с 0.8.41 нет, ветер рисуют линии). Рисуются здесь, а не подписями карты, чтобы
 * не зависеть от шрифтов подложки; стоят на карте символами (скрываются, когда не помещаются рядом).
 *
 * Все значки нарисованы втрое крупнее и помечены плотностью 480 (3 пикселя на dp): размер на экране в dp один и тот же
 * на любом телефоне.
 */
internal object MapIcons {
    private const val SCALE = 3f
    private const val DENSITY = 480

    private const val HALO = 0xD9101820.toInt()
    private const val TEXT_DP = 13f
    private const val HALO_DP = 2.4f

    /** Все значки по именам: числа температуры. */
    fun all(): Map<String, Bitmap> {
        val icons = LinkedHashMap<String, Bitmap>()
        for (degrees in TemperatureField.IconRange) icons[TemperatureField.iconName(degrees)] = number(degrees)
        return icons
    }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.density = DENSITY }

    /** Число градусов белым с тёмной обводкой: читается и на синем, и на красном. */
    private fun number(degrees: Int): Bitmap {
        val text = (if (degrees < 0) "−" else "") + kotlin.math.abs(degrees)
        val size = TEXT_DP * SCALE
        val halo = HALO_DP * SCALE
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val outline = Paint(fill).apply {
            color = HALO
            style = Paint.Style.STROKE
            strokeWidth = halo * 2
            strokeJoin = Paint.Join.ROUND
        }
        val metrics = fill.fontMetrics
        val width = ceil(fill.measureText(text) + halo * 2 + 2).toInt()
        val height = ceil(metrics.descent - metrics.ascent + halo * 2 + 2).toInt()
        val out = bitmap(width, height)
        val canvas = Canvas(out)
        val baseline = halo + 1 - metrics.ascent
        canvas.drawText(text, width / 2f, baseline, outline)
        canvas.drawText(text, width / 2f, baseline, fill)
        return out
    }
}
