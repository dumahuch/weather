package io.github.dumahuch.pogodaapp.sky

import kotlin.math.floor

/**
 * Собирает маленький кадр [SkySampler.GRID_W]×[SkySampler.GRID_H] по картам яркости одного фона тем же порядком слоёв,
 * что рисует экран. Пишет в [out] по три числа (R, G, B, 0…255) на точку, строками сверху вниз.
 */
internal interface SkyRaster {
    fun render(look: SkyLook, out: DoubleArray)
}

/** Общие куски сборки кадра для обоих фонов. */
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
     * Последние шаги, общие для обоих фонов: серость непогоды, затемнение грозы, приглушение света ночью.
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

/** Фон «Нейтральный»: картина без облаков и слой облаков каждого времени суток. Порядок как в макете. */
internal class NeutralRaster(private val maps: SkyMaps) : SkyRaster {

    private val tmp = DoubleArray(4)
    private val rgb = DoubleArray(3)

    override fun render(look: SkyLook, out: DoubleArray) {
        val gw = SkySampler.GRID_W
        val gh = SkySampler.GRID_H
        val sceneW = SkySampler.SCENE_W
        val sceneH = SkySampler.SCENE_H
        val layers = look.weights.visible()
        val veilStrength = look.veilStrength
        val w = look.weights
        val cloudAlpha = look.weather.cloudLayerAlpha
        val plateWidth = maps.width * sceneH / maps.height
        val plateX0 = -w.pan * (plateWidth - sceneW)
        val cloudScale = SkySampler.CLOUD_SCALE * sceneH / maps.height
        val cloudWidth = maps.width * cloudScale
        val cloudHeight = maps.height * cloudScale
        val cloudX0 = -look.cloudPosition * (cloudWidth - sceneW)
        val cloudY0 = sceneH - cloudHeight
        val discs = Celestial.discs(look.bodies, Celestial.Frame.Neutral, sceneW, sceneH)

        for (py in 0 until gh) {
            val sy = (py + 0.5) / gh * sceneH
            val rowVeil = RasterKit.veilAt(look.veil, sy / sceneH)
            for (px in 0 until gw) {
                val sx = (px + 0.5) / gw * sceneW
                // 1) небо без облаков: среднее картин с весами
                var r = 0.0
                var g = 0.0
                var b = 0.0
                var acc = 0.0
                for (scene in layers) {
                    val weight = w[scene]
                    acc += weight
                    RasterKit.bilinear(
                        maps.layer(scene, false), maps.width, maps.height,
                        (sx - plateX0) / plateWidth * maps.width, sy / sceneH * maps.height, tmp,
                    )
                    val k = weight / acc
                    r = lerp(r, tmp[0], k); g = lerp(g, tmp[1], k); b = lerp(b, tmp[2], k)
                }
                // 1б) солнце и луна лежат на небе под вуалью и облаками
                if (discs.isNotEmpty()) {
                    rgb[0] = r; rgb[1] = g; rgb[2] = b
                    Celestial.blend(discs, sx, sy, rgb)
                    r = rgb[0]; g = rgb[1]; b = rgb[2]
                }
                // 2) погодная вуаль
                if (veilStrength > 0.01) {
                    r = lerp(r, rowVeil.r, veilStrength); g = lerp(g, rowVeil.g, veilStrength); b = lerp(b, rowVeil.b, veilStrength)
                }
                // 3) облака: слои накладываются друг на друга, затем целиком с прозрачностью слоя
                var cr = 0.0
                var cg = 0.0
                var cb = 0.0
                var ca = 0.0
                acc = 0.0
                for (scene in layers) {
                    val weight = w[scene]
                    acc += weight
                    RasterKit.bilinear(maps.layer(scene, true), maps.width, maps.height, (sx - cloudX0) / cloudScale, (sy - cloudY0) / cloudScale, tmp)
                    val k = weight / acc
                    val keep = 1 - k * tmp[3] / 255.0
                    cr = k * tmp[0] + keep * cr; cg = k * tmp[1] + keep * cg; cb = k * tmp[2] + keep * cb
                    ca = k * tmp[3] + keep * ca
                }
                val under = 1 - cloudAlpha * ca / 255.0
                rgb[0] = cloudAlpha * cr + under * r; rgb[1] = cloudAlpha * cg + under * g; rgb[2] = cloudAlpha * cb + under * b
                // 4–6) серость, затемнение, приглушение
                RasterKit.finish(look, rgb)
                val i = (py * gw + px) * 3
                out[i] = rgb[0]; out[i + 1] = rgb[1]; out[i + 2] = rgb[2]
            }
        }
    }
}
