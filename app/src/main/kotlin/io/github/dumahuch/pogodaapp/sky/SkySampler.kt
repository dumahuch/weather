package io.github.dumahuch.pogodaapp.sky

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Что видно в кадре: доли картин (не больше двух времён суток), погода, цвета погодной вуали
 * и положение слоя облаков [cloudPosition] (0…1 от крайнего левого до крайнего правого положения).
 */
internal class SkyLook(
    val weights: SceneWeights,
    val weather: SkyWeather,
    val veil: Veil,
    val cloudPosition: Double,
) {
    /** Насколько погодная вуаль перекрывает небо (0…0,85). */
    val veilStrength: Double get() = clamp01(weather.gray * 0.8 + weather.fog * 0.3).coerceAtMost(0.85)

    /** Насколько небо обесцвечено непогодой (0…1). */
    val grayAmount: Double get() = clamp01(weather.gray * 1.05)

    /** Прозрачность тёмной накладки (гроза темнее всего). */
    val darkAlpha: Double get() = min(0.9, weather.dark * 1.1)

    /** Прозрачность приглушающей накладки: облака ночью, на рассвете и закате не светятся. */
    val nightDim: Double
        get() {
            val w = weights
            val twilight = w[SkyScene.Night] + 0.7 * w[SkyScene.Dusk] + 0.55 * w[SkyScene.Dawn] +
                0.45 * w[SkyScene.Sunset] + 0.1 * w[SkyScene.Morning]
            return twilight * weather.cloud * (0.2 + 0.4 * weather.gray)
        }
}

/** Цвета фона в одном месте экрана: среднее и самые тёмные и самые светлые пятна. */
internal class RegionColors(val mean: Rgb, val underDarkInk: Rgb, val underLightInk: Rgb) {
    fun blend(other: RegionColors, t: Double) = RegionColors(
        mix(mean, other.mean, t),
        mix(underDarkInk, other.underDarkInk, t),
        mix(underLightInk, other.underLightInk, t),
    )
}

/** Фон под разными частями главного экрана и три цвета неба: сверху, посередине, у горизонта. */
internal class Backdrops(
    val top: Rgb,
    val mid: Rgb,
    val hor: Rgb,
    val hero: RegionColors,
    val alert: RegionColors,
    val card: RegionColors,
    val dock: RegionColors,
    /**
     * Полоса со строками под температурой: состояние, максимум и минимум, «Ощущается», подписи (на экране 412×892 dp с
     * версии 0.5.4 это примерно от 360 до 510 dp от верха: блок стоит посередине свободного места). Макет такого снимка
     * не делал, поэтому у значений макета по умолчанию берётся [alert].
     */
    val info: RegionColors = alert,
) {
    fun blend(other: Backdrops, t: Double) = Backdrops(
        mix(top, other.top, t), mix(mid, other.mid, t), mix(hor, other.hor, t),
        hero.blend(other.hero, t), alert.blend(other.alert, t), card.blend(other.card, t), dock.blend(other.dock, t),
        info.blend(other.info, t),
    )
}

/**
 * Собирает по картам яркости маленький кадр 41×89 (тот же порядок слоёв, что при рисовании на экране)
 * и снимает с него цвета под цифрами, предупреждениями, карточкой и нижней панелью.
 * Порядок слоёв как в макете: небо → погодная вуаль → облака → обесцвечивание → затемнение.
 * Кадр рисуется для экрана 412×892 dp (Pixel 6 Pro); на других экранах те же доли высоты.
 */
internal class SkySampler(private val maps: SkyMaps) {

    private val pixels = DoubleArray(GRID_W * GRID_H * 3)
    private val tmp = DoubleArray(4)

    fun backdrops(look: SkyLook): Backdrops {
        render(look)
        val bands = listOf(region(0, 0, GRID_W, 22), region(0, 22, GRID_W, 54), region(0, 54, GRID_W, GRID_H))
        // «Герой» (большие цифры) смотрит на два куска, берётся худший из них.
        val hero = worse(region(2, 13, 39, 30), region(2, 30, 39, 34))
        return Backdrops(
            top = bands[0].mean, mid = bands[1].mean, hor = bands[2].mean,
            hero = cooked(hero, 0.85),
            alert = cooked(region(2, 35, 39, 46), 0.7),
            card = cooked(region(2, 60, 39, 80), 0.7),
            dock = cooked(region(6, 81, 35, 86), 0.7),
            info = cooked(region(2, 36, 39, 51), 0.7),
        )
    }

    // ---- сборка кадра ----

    private fun render(look: SkyLook) {
        val layers = look.weights.visible()
        val weather = look.weather
        val veilStrength = look.veilStrength
        val gray = look.grayAmount
        val darkAlpha = look.darkAlpha
        val nightDim = look.nightDim
        val w = look.weights
        val cloudAlpha = weather.cloudLayerAlpha
        val plateWidth = maps.width * SCENE_H / maps.height
        val plateX0 = -w.pan * (plateWidth - SCENE_W)
        val cloudScale = CLOUD_SCALE * SCENE_H / maps.height
        val cloudWidth = maps.width * cloudScale
        val cloudHeight = maps.height * cloudScale
        val cloudX0 = -look.cloudPosition * (cloudWidth - SCENE_W)
        val cloudY0 = SCENE_H - cloudHeight

        for (py in 0 until GRID_H) {
            val sy = (py + 0.5) / GRID_H * SCENE_H
            val rowVeil = veilAt(look.veil, sy / SCENE_H)
            for (px in 0 until GRID_W) {
                val sx = (px + 0.5) / GRID_W * SCENE_W
                // 1) небо без облаков: среднее картин с весами
                var r = 0.0
                var g = 0.0
                var b = 0.0
                var acc = 0.0
                for (scene in layers) {
                    val weight = look.weights[scene]
                    acc += weight
                    bilinear(maps.layer(scene, false), (sx - plateX0) / plateWidth * maps.width, sy / SCENE_H * maps.height)
                    val k = weight / acc
                    r = lerp(r, tmp[0], k); g = lerp(g, tmp[1], k); b = lerp(b, tmp[2], k)
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
                    val weight = look.weights[scene]
                    acc += weight
                    bilinear(maps.layer(scene, true), (sx - cloudX0) / cloudScale, (sy - cloudY0) / cloudScale)
                    val k = weight / acc
                    val keep = 1 - k * tmp[3] / 255.0
                    cr = k * tmp[0] + keep * cr; cg = k * tmp[1] + keep * cg; cb = k * tmp[2] + keep * cb
                    ca = k * tmp[3] + keep * ca
                }
                val under = 1 - cloudAlpha * ca / 255.0
                r = cloudAlpha * cr + under * r; g = cloudAlpha * cg + under * g; b = cloudAlpha * cb + under * b
                // 4) серость непогоды
                if (gray > 0.01) {
                    val l = r * 0.3 + g * 0.59 + b * 0.11
                    r = lerp(r, l, gray); g = lerp(g, l, gray); b = lerp(b, l, gray)
                }
                // 5) затемнение
                if (weather.dark > 0.01) {
                    r = lerp(r, 10.0, darkAlpha); g = lerp(g, 16.0, darkAlpha); b = lerp(b, 32.0, darkAlpha)
                }
                // 6) приглушение света облаков ночью, на рассвете и закате
                if (nightDim > 0.01) {
                    r = lerp(r, 8.0, nightDim); g = lerp(g, 12.0, nightDim); b = lerp(b, 26.0, nightDim)
                }
                val i = (py * GRID_W + px) * 3
                pixels[i] = r; pixels[i + 1] = g; pixels[i + 2] = b
            }
        }
    }

    private fun veilAt(veil: Veil, t: Double): Rgb =
        if (t < VEIL_MID) mix(veil.top, veil.mid, t / VEIL_MID) else mix(veil.mid, veil.hor, (t - VEIL_MID) / (1 - VEIL_MID))

    /** Билинейная выборка карты в точке (x, y), заданной в пикселях карты; результат в [tmp] (R, G, B, A). */
    private fun bilinear(layer: ByteArray, x: Double, y: Double) {
        val fx = x - 0.5
        val fy = y - 0.5
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val ax = fx - x0
        val ay = fy - y0
        tmp.fill(0.0)
        for (dy in 0..1) {
            val yy = (y0 + dy).coerceIn(0, maps.height - 1)
            val wy = if (dy == 0) 1 - ay else ay
            for (dx in 0..1) {
                val xx = (x0 + dx).coerceIn(0, maps.width - 1)
                val weight = wy * (if (dx == 0) 1 - ax else ax)
                val o = (yy * maps.width + xx) * 4
                for (c in 0..3) tmp[c] += weight * (layer[o + c].toInt() and 0xFF)
            }
        }
    }

    // ---- снятие цветов ----

    private class Raw(val mean: Rgb, val darkest: Rgb, val lightest: Rgb)

    private fun pixel(x: Int, y: Int): Rgb {
        val i = (y * GRID_W + x) * 3
        return Rgb(pixels[i], pixels[i + 1], pixels[i + 2])
    }

    private fun meanOf(list: List<Rgb>): Rgb = Rgb(list.sumOf { it.r } / list.size, list.sumOf { it.g } / list.size, list.sumOf { it.b } / list.size)

    /** Среднее по месту и самые тёмные и самые светлые пятна 2×2 (пять процентов тех и других). */
    private fun region(x0: Int, y0: Int, x1: Int, y1: Int): Raw {
        val all = ArrayList<Rgb>((x1 - x0) * (y1 - y0))
        for (y in y0 until y1) for (x in x0 until x1) all.add(pixel(x, y))
        val blocks = ArrayList<Rgb>()
        var y = y0
        while (y + 2 <= y1) {
            var x = x0
            while (x + 2 <= x1) {
                blocks.add(meanOf(listOf(pixel(x, y), pixel(x + 1, y), pixel(x, y + 1), pixel(x + 1, y + 1))))
                x += 2
            }
            y += 2
        }
        val sorted = blocks.sortedBy { luminance(it) }
        val n = max(1, Math.round(sorted.size * 0.05).toInt())
        return Raw(meanOf(all), meanOf(sorted.take(n)), meanOf(sorted.takeLast(n)))
    }

    private fun worse(a: Raw, b: Raw) = Raw(
        mean = a.mean,
        darkest = if (luminance(a.darkest) < luminance(b.darkest)) a.darkest else b.darkest,
        lightest = if (luminance(a.lightest) > luminance(b.lightest)) a.lightest else b.lightest,
    )

    private fun cooked(r: Raw, f: Double) = RegionColors(r.mean, mix(r.mean, r.darkest, f), mix(r.mean, r.lightest, f))

    companion object {
        const val GRID_W = 41
        const val GRID_H = 89
        const val SCENE_W = 412.0
        const val SCENE_H = 892.0

        /** Слой облаков на 12 % крупнее неба и прижат к нижнему краю. */
        const val CLOUD_SCALE = 1.12
        private const val VEIL_MID = 0.55
    }
}
