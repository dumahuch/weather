package io.github.dumahuch.pogodaapp.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import io.github.dumahuch.pogodaapp.map.TemperatureField
import io.github.dumahuch.pogodaapp.map.WindField
import kotlin.math.ceil

/**
 * Значки для слоёв «Температура» и «Ветер»: числа градусов и стрелки ветра. Рисуются здесь, а не подписями карты, чтобы
 * не зависеть от шрифтов подложки; стоят на карте символами (скрываются, когда не помещаются рядом).
 *
 * Все значки нарисованы втрое крупнее и помечены плотностью 480 (3 пикселя на dp): размер на экране в dp один и тот же
 * на любом телефоне.
 */
internal object MapIcons {
    private const val SCALE = 3f
    private const val DENSITY = 480

    private const val INK = 0xFF101820.toInt()
    private const val HALO = 0xD9101820.toInt()
    private const val TEXT_DP = 13f
    private const val HALO_DP = 2.4f

    /** Размер стрелки класса силы ветра, dp: чем сильнее ветер, тем крупнее. */
    private fun arrowDp(speedClass: Int): Float = 18f + 3.5f * speedClass

    private const val CALM_DP = 13f

    /** Все значки по именам: числа температуры и стрелки ветра. */
    fun all(): Map<String, Bitmap> {
        val icons = LinkedHashMap<String, Bitmap>()
        for (degrees in TemperatureField.IconRange) icons[TemperatureField.iconName(degrees)] = number(degrees)
        for (speedClass in WindField.ClassColors.indices) {
            icons[WindField.iconName(speedClass)] = arrow(speedClass)
        }
        icons[WindField.CALM_ICON] = calm()
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

    /** Стрелка вверх (на север) цвета класса; карта сама поворачивает её по направлению ветра. */
    private fun arrow(speedClass: Int): Bitmap {
        val side = ceil(arrowDp(speedClass) * SCALE).toInt()
        val out = bitmap(side, side)
        val canvas = Canvas(out)
        val s = side.toFloat()
        val margin = 0.07f
        fun x(v: Float) = s * (margin + v * (1 - 2 * margin))
        fun y(v: Float) = s * (margin + v * (1 - 2 * margin))
        val path = Path().apply {
            moveTo(x(0.5f), y(0f))
            lineTo(x(1f), y(0.48f))
            lineTo(x(0.64f), y(0.48f))
            lineTo(x(0.64f), y(1f))
            lineTo(x(0.36f), y(1f))
            lineTo(x(0.36f), y(0.48f))
            lineTo(x(0f), y(0.48f))
            close()
        }
        val color = 0xFF000000.toInt() or WindField.ClassColors[speedClass]
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = INK
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * SCALE
                strokeJoin = Paint.Join.ROUND
            },
        )
        return out
    }

    /** Штиль: точка без направления. */
    private fun calm(): Bitmap {
        val side = ceil(CALM_DP * SCALE).toInt()
        val out = bitmap(side, side)
        val canvas = Canvas(out)
        val c = side / 2f
        val radius = side * 0.28f
        canvas.drawCircle(c, c, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() or WindField.ClassColors[0] })
        canvas.drawCircle(
            c,
            c,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * SCALE
            },
        )
        return out
    }
}
