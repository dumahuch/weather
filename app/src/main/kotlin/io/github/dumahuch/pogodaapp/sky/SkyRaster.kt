package io.github.dumahuch.pogodaapp.sky

import kotlin.math.floor

/**
 * Собирает маленький кадр [SkySampler.GRID_W]×[SkySampler.GRID_H] по картам яркости фона тем же порядком слоёв,
 * что рисует экран. Пишет в [out] по три числа (R, G, B, 0…255) на точку, строками сверху вниз.
 */
internal interface SkyRaster {
    fun render(look: SkyLook, out: DoubleArray)
}

/** Общие куски сборки кадра. */
internal object RasterKit {

    /** Цвет погодной вуали на доле высоты [t] (0 верх, 1 низ). */
    fun veilAt(veil: Veil, t: Double): Rgb {
        val m = SkySampler.VEIL_MID
        return if (t < m) mix(veil.top, veil.mid, t / m) else mix(veil.mid, veil.hor, (t - m) / (1 - m))
    }

    /**
     * Билинейная выборка карты [layer] размером [w]×[h] в точке (x, y), заданной в пикселях карты; края тянутся.
     * Результат в [out] (R, G, B, A с цветом, умноженным на прозрачность).
     */
    fun bilinear(layer: ByteArray, w: Int, h: Int, x: Double, y: Double, out: DoubleArray) {
        val fx = x - 0.5
        val fy = y - 0.5
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val ax = fx - x0
        val ay = fy - y0
        out.fill(0.0, 0, 4)
        for (dy in 0..1) {
            val yy = (y0 + dy).coerceIn(0, h - 1)
            val wy = if (dy == 0) 1 - ay else ay
            for (dx in 0..1) {
                val xx = (x0 + dx).coerceIn(0, w - 1)
                val weight = wy * (if (dx == 0) 1 - ax else ax)
                val o = (yy * w + xx) * 4
                for (c in 0..3) out[c] += weight * (layer[o + c].toInt() and 0xFF)
            }
        }
    }

    /** Как [bilinear], но за краями карты прозрачно (для листа облаков, который не покрывает весь кадр). */
    fun bilinearOrClear(layer: ByteArray, w: Int, h: Int, x: Double, y: Double, out: DoubleArray) {
        if (x < 0 || y < 0 || x > w || y > h) {
            out.fill(0.0, 0, 4)
            return
        }
        bilinear(layer, w, h, x, y, out)
    }

    /**
     * Последние шаги: серость непогоды, затемнение грозы, приглушение света ночью.
     * [rgb] меняется на месте.
     */
    fun finish(look: SkyLook, rgb: DoubleArray) = tone(look, rgb, 1.0)

    /**
     * Те же три шага для слоя с прозрачностью [alpha] (цвет [rgb] умножен на неё): затемнение и приглушение красят только
     * нарисованное, как накладки со смешиванием «по нарисованному» на экране. Так фон «Ди» не затемняет солнце и луну (step-30).
     */
    fun tone(look: SkyLook, rgb: DoubleArray, alpha: Double) {
        var r = rgb[0]
        var g = rgb[1]
        var b = rgb[2]
        val gray = look.grayAmount
        if (gray > 0.01) {
            val l = r * 0.3 + g * 0.59 + b * 0.11
            r = lerp(r, l, gray); g = lerp(g, l, gray); b = lerp(b, l, gray)
        }
        if (look.weather.dark > 0.01) {
            val a = look.darkAlpha
            r = lerp(r, 10.0 * alpha, a); g = lerp(g, 16.0 * alpha, a); b = lerp(b, 32.0 * alpha, a)
        }
        val dim = look.nightDim
        if (dim > 0.01) {
            r = lerp(r, 8.0 * alpha, dim); g = lerp(g, 12.0 * alpha, dim); b = lerp(b, 26.0 * alpha, dim)
        }
        rgb[0] = r; rgb[1] = g; rgb[2] = b
    }
}
