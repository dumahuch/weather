package io.github.dumahuch.pogodaapp.sky

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
    /** Насколько слои «Ди» подняты прокруткой экрана (доля высоты экрана, [DiArt.liftOf]); «Нейтральный» его не замечает. */
    val lift: Double = 0.0,
    /** Солнце и луна на небе ([Celestial.bodies]): между небом и облаками, за грядами. */
    val bodies: List<SkyBody> = emptyList(),
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
 * Снимает с маленького кадра 41×89 цвета под цифрами, предупреждениями, карточкой и нижней панелью.
 * Кадр собирает [SkyRaster] по картам яркости выбранного фона тем же порядком слоёв, что на экране
 * («Нейтральный»: [NeutralRaster], «Ди»: [DiRaster]). Кадр для экрана 412×892 dp (Pixel 6 Pro); на других экранах те же доли высоты.
 */
internal class SkySampler(private val raster: SkyRaster) {

    constructor(maps: SkyMaps) : this(NeutralRaster(maps))

    private val pixels = DoubleArray(GRID_W * GRID_H * 3)

    fun backdrops(look: SkyLook): Backdrops {
        raster.render(look, pixels)
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
        const val VEIL_MID = 0.55
    }
}
