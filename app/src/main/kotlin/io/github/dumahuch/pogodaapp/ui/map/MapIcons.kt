package io.github.dumahuch.pogodaapp.ui.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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

    private const val HALO = 0xD9101820.toInt()
    private const val TEXT_DP = 13f
    private const val HALO_DP = 2.4f

    /** Длина стрелки класса силы ветра, dp: чем сильнее ветер, тем длиннее. */
    private fun arrowLengthDp(speedClass: Int): Float = 20f + 3.5f * speedClass

    /** Поле вокруг стрелки и точки под размытую тень, dp. */
    private const val SHADOW_ROOM_DP = 2.5f
    private const val SHADOW_BLUR_DP = 1.6f
    private const val SHADOW = 0x4D101820

    private const val CALM_DP = 13f
    private const val CALM_RADIUS_DP = 3.5f

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

    /**
     * Стрелка вверх (на север) цвета класса: острая головка и тонкий хвост, сужающийся к концу (вариант «Б», выбор
     * Димы 14.11). Без тёмной обводки, только мягкая тень; карта сама поворачивает её по направлению ветра, поэтому
     * стрелка стоит в центре квадрата, а тень без сдвига.
     */
    private fun arrow(speedClass: Int): Bitmap {
        val length = arrowLengthDp(speedClass) * SCALE
        val shaft = (1.6f + 0.5f * speedClass) * SCALE
        val head = (3.2f + 0.7f * speedClass) * SCALE
        val side = ceil(length + 2 * SHADOW_ROOM_DP * SCALE).toInt()
        val out = bitmap(side, side)
        val canvas = Canvas(out)
        val c = side / 2f
        val tip = c - length / 2
        val tail = c + length / 2
        val base = tip + head * 1.6f
        val neck = base - 1f * SCALE
        val bend = c + length * 0.1f
        val path = Path().apply {
            moveTo(c, tip)
            lineTo(c + head, base)
            lineTo(c + shaft / 2, neck)
            quadTo(c + shaft * 0.25f, bend, c, tail)
            quadTo(c - shaft * 0.25f, bend, c - shaft / 2, neck)
            lineTo(c - head, base)
            close()
        }
        canvas.drawPath(path, shadowPaint())
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() or WindField.ClassColors[speedClass] })
        return out
    }

    /** Мягкая тень под стрелкой и точкой штиля: размытый тёмный силуэт той же формы. */
    private fun shadowPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SHADOW
        maskFilter = BlurMaskFilter(SHADOW_BLUR_DP * SCALE, BlurMaskFilter.Blur.NORMAL)
    }

    /** Штиль: точка без направления, с той же мягкой тенью. */
    private fun calm(): Bitmap {
        val side = ceil(CALM_DP * SCALE).toInt()
        val out = bitmap(side, side)
        val canvas = Canvas(out)
        val c = side / 2f
        val radius = CALM_RADIUS_DP * SCALE
        canvas.drawCircle(c, c, radius, shadowPaint())
        canvas.drawCircle(c, c, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() or WindField.ClassColors[0] })
        return out
    }
}
