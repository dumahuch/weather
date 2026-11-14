package io.github.dumahuch.pogodaapp.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Одно облако фона «Ди» на листе облаков: какое ([rect] на листе), где лежит его центр ([cx], [cy], пиксели листа),
 * во сколько раз растянуто ([scale]), насколько непрозрачно ([alpha]) и отражено ли по горизонтали ([mirrored]).
 * [storm] облако лежит на тёмном (грозовом) листе, иначе на светлом.
 */
internal class PlacedCloud(
    val storm: Boolean,
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
 * Раскладка облаков фона «Ди» по виду погоды: число облаков и их размер растут с густотой, нижние облака чуть прозрачнее
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

    /**
     * Центры облаков по высоте листа: от верха (0) до низа (740 из 768). Облака у краёв листа закрывают его меньше, чем
     * в середине (с одной стороны соседей нет, верхние ещё и уходят за край экрана), поэтому центры доходят до самого
     * верха и низа. Лист начинается у самого верха экрана (2 %), пустой полосы над облаками нет.
     */
    const val ROW_TOP = 0.0
    const val ROW_BOTTOM = 740.0

    /**
     * Размер облака по высоте листа (step-23, замечание Димы): вверху крупные облака, к нижнему краю всё мельче, так
     * середина экрана не тяжелеет, а низ набран мелкими облачками. Множитель к размеру по густоте: [DEPTH_TOP] у верха
     * листа, [DEPTH_BOTTOM] у низа (по площади разница около четырёх раз). Высота распределена равномерно; в step-22
     * размер от высоты не зависел, до step-21 было наоборот (внизу крупнее), и низ сливался в сплошную гряду.
     */
    const val DEPTH_TOP = 1.25
    const val DEPTH_BOTTOM = 0.65

    /** Шаг последовательности, по которой облака (в порядке слева направо) делятся на тёмные и светлые. */
    private const val DARK_STEP = 0.41421356237309503

    private const val GOLDEN = 0.6180339887498949

    fun count(weather: SkyWeather): Int = (BASE_COUNT + EXTRA_COUNT * clamp01(weather.density)).roundToInt()

    /** Размер облаков листа (множитель к их исходному размеру) при густоте [density]: густая облачность из крупных облаков. */
    fun size(density: Double): Double = 0.7 + 0.45 * clamp01(density)

    /**
     * Непрозрачность облака в ряду [row] (0 верх листа, 1 низ): верхние и дальние облака плотные, нижние и ближние
     * чуть прозрачнее ([MIN_ALPHA] у самого низа): сквозь них видна молния, что бьёт между слоями туч (решение Димы 14.11).
     */
    fun alpha(row: Double): Double = 1.0 - (1.0 - MIN_ALPHA) * clamp01(row)

    const val MIN_ALPHA = 0.75

    /**
     * Облака для погоды [weather], верхние первыми (рисуются раньше, нижние мелкие поверх). Каждое облако лежит на светлом или грозовом листе:
     * на грозовом столько, сколько задаёт [SkyWeather.darkShare], какие именно, решает равномерная последовательность по номеру облака.
     */
    fun of(weather: SkyWeather): List<PlacedCloud> {
        val n = count(weather)
        val size = size(weather.density)
        val clouds = ArrayList<PlacedCloud>(n)
        for (i in 0 until n) {
            val along = (i + 0.5) / n + (unit(i, 1) - 0.5) * 0.7 / n
            // Ряд (0 верх листа, 1 низ) по золотому сечению: соседние по горизонтали облака лежат на разной высоте,
            // а по высоте облака распределены равномерно.
            val row = fract(i * GOLDEN + 0.13)
            // Тёмные среди светлых равномерно по ширине листа (а не кучами): другой шаг, чем у рядов, так что высота и цвет не связаны.
            val storm = fract(i * DARK_STEP + 0.27) < weather.darkShare
            val sprites = if (storm) CloudSprites.storm else CloudSprites.light
            clouds += PlacedCloud(
                storm = storm,
                rect = sprites[(i * 5) % sprites.size],
                cx = DiArt.SHEET_W * along,
                cy = ROW_TOP + (ROW_BOTTOM - ROW_TOP) * row + (unit(i, 2) - 0.5) * 40.0,
                scale = size * (DEPTH_TOP + (DEPTH_BOTTOM - DEPTH_TOP) * row) * (0.9 + 0.2 * unit(i, 3)),
                alpha = alpha(row),
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
