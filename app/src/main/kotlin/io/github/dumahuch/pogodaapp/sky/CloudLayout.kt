package io.github.dumahuch.pogodaapp.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Одно облако фона «Ди» на листе облаков: какое ([rect] на листе), где лежит его центр ([cx], [cy], пиксели листа),
 * во сколько раз растянуто ([scale]), насколько непрозрачно ([alpha]) и отражено ли по горизонтали ([mirrored]).
 */
internal class PlacedCloud(
    val rect: CloudSprites.Rect,
    val cx: Double,
    val cy: Double,
    val scale: Double,
    val alpha: Double,
    val mirrored: Boolean,
) {
    val width: Double get() = rect.w * scale
    val height: Double get() = rect.h * scale
    val left: Double get() = cx - width / 2
    val top: Double get() = cy - height / 2
}

/**
 * Раскладка облаков фона «Ди» по виду погоды: число облаков, их размер и непрозрачность растут с густотой
 * [SkyWeather.density], так что «малооблачно» это несколько отдельных облаков с просветами, а «облачно» и дождь
 * густая гряда. Раскладка постоянная (без случайных чисел): одна погода даёт один и тот же рисунок, и рисование
 * ([io.github.dumahuch.pogodaapp.ui.sky.DiPainter]) совпадает с расчётом цветов интерфейса ([DiRaster]).
 *
 * Координаты облаков в пикселях листа (2048×768); сколько листа видно, зависит от экрана, а положение окна
 * задаёт [SkyDrift] ([DiArt.placeClouds]).
 */
internal object CloudLayout {

    /** Облаков при нулевой густоте и сколько добавляется на единицу густоты. */
    private const val BASE_COUNT = 3
    private const val EXTRA_COUNT = 22

    /** Центры облаков по высоте листа: от дальнего ряда (вверху, мельче) до ближнего (внизу, крупнее). */
    private const val ROW_TOP = 150.0
    private const val ROW_BOTTOM = 600.0

    private const val GOLDEN = 0.6180339887498949

    fun count(weather: SkyWeather): Int = (BASE_COUNT + EXTRA_COUNT * clamp01(weather.density)).roundToInt()

    /** Размер облаков листа (множитель к их исходному размеру) при густоте [density]: густая облачность из крупных облаков. */
    fun size(density: Double): Double = 0.7 + 0.45 * clamp01(density)

    /** Непрозрачность облаков при густоте [density]: редкие облака чуть прозрачнее, плотная гряда полностью непрозрачна. */
    fun alpha(density: Double): Double = 0.85 + 0.15 * clamp01(density)

    /** Облака для погоды [weather] на светлом или грозовом листе, дальние первыми (рисуются раньше). */
    fun of(weather: SkyWeather, storm: Boolean): List<PlacedCloud> {
        val sprites = if (storm) CloudSprites.storm else CloudSprites.light
        val n = count(weather)
        val size = size(weather.density)
        val alpha = alpha(weather.density)
        val clouds = ArrayList<PlacedCloud>(n)
        for (i in 0 until n) {
            val along = (i + 0.5) / n + (unit(i, 1) - 0.5) * 0.7 / n
            // Ряд (0 дальний, 1 ближний) по золотому сечению: соседние по горизонтали облака лежат на разной высоте.
            val row = fract(i * GOLDEN + 0.13)
            val depth = 0.8 + 0.4 * row
            clouds += PlacedCloud(
                rect = sprites[(i * 5) % sprites.size],
                cx = DiArt.SHEET_W * along,
                cy = ROW_TOP + (ROW_BOTTOM - ROW_TOP) * row + (unit(i, 2) - 0.5) * 40.0,
                scale = size * depth * (0.9 + 0.2 * unit(i, 3)),
                alpha = alpha * (0.9 + 0.1 * row),
                mirrored = unit(i, 4) > 0.5,
            )
        }
        return clouds.sortedBy { it.cy }
    }

    private fun fract(x: Double): Double = x - floor(x)

    /** Постоянное «случайное» число от 0 до 1 для облака [i] и назначения [k]. */
    private fun unit(i: Int, k: Int): Double {
        var h = i * 374761393 + k * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216.0
    }
}
