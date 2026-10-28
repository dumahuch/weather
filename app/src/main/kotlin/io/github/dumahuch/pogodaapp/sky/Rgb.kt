package io.github.dumahuch.pogodaapp.sky

import kotlin.math.pow

/**
 * Цвет в долях от 0 до 255 без прозрачности. Расчёты неба и темы идут в дробных числах,
 * как в макете, чтобы результат совпадал с тем, что Дима видел и принял.
 */
internal data class Rgb(val r: Double, val g: Double, val b: Double) {
    companion object {
        val White = Rgb(255.0, 255.0, 255.0)

        /** Цвет из числа вида 0xRRGGBB. */
        fun hex(rgb: Int) = Rgb(
            r = ((rgb shr 16) and 0xFF).toDouble(),
            g = ((rgb shr 8) and 0xFF).toDouble(),
            b = (rgb and 0xFF).toDouble(),
        )
    }
}

internal fun clamp01(x: Double): Double = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x

internal fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

/** Плавная ступенька: 0 до [a], 1 после [b], между ними сглаженный переход. */
internal fun smooth(a: Double, b: Double, x: Double): Double {
    val t = clamp01((x - a) / (b - a))
    return t * t * (3 - 2 * t)
}

internal fun mix(p: Rgb, q: Rgb, t: Double) = Rgb(lerp(p.r, q.r, t), lerp(p.g, q.g, t), lerp(p.b, q.b, t))

/** Относительная яркость по WCAG 2: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance */
internal fun luminance(c: Rgb): Double {
    fun channel(v: Double): Double {
        val x = v / 255.0
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
}

/** Контраст двух цветов по WCAG 2, от 1 до 21. */
internal fun contrast(a: Rgb, b: Rgb): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
