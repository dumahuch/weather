package io.github.dumahuch.pogodaapp.map

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Облачность в картинку для карты: серые полупрозрачные облака. Сетка модели лежит в градусах, карта в проекции
 * Меркатора, поэтому каждая строка картинки берёт свою широту ([Mercator.lat]), иначе облака в середине Германии
 * съехали бы на десятки километров. Значение между узлами сетки сглаживается (билинейно).
 */
object CloudRaster {

    /** Облачность ниже этого порога не рисуется: чистое небо остаётся прозрачным. */
    private const val CLEAR = 0.05f

    /** Непрозрачность сплошной облачности: подложка и подписи городов остаются видны. */
    private const val MAX_ALPHA = 0.72f

    /** Цвет облаков (светло-серый). */
    private const val CLOUD_RGB = 0xD9DEE3

    /**
     * Доля облачности 0…1 из значения поля. DWD отдаёт `clct_mod` долей 0…1; если вдруг придут проценты (больше 1,5),
     * они делятся на 100.
     */
    fun normalize(grid: LatLonGrid): LatLonGrid {
        var max = 0f
        for (v in grid.values) if (!v.isNaN() && v > max) max = v
        if (max <= 1.5f) return grid
        val scaled = FloatArray(grid.values.size) { grid.values[it] / 100f }
        return LatLonGrid(grid.columns, grid.rows, grid.west, grid.south, grid.stepLon, grid.stepLat, scaled)
    }

    /** Непрозрачность для облачности [cover] (0…1). */
    fun alphaOf(cover: Float): Float {
        if (cover.isNaN() || cover <= CLEAR) return 0f
        val share = ((cover - CLEAR) / (1f - CLEAR)).coerceIn(0f, 1f)
        return MAX_ALPHA * share
    }

    /**
     * Картинка [width] × [height] на прямоугольник [box] (в проекции карты): цвета ARGB построчно сверху вниз.
     * Вне сетки прозрачно.
     */
    fun render(grid: LatLonGrid, box: GeoBox, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0) { "пустая картинка" }
        val pixels = IntArray(width * height)
        val top = Mercator.y(box.north)
        val bottom = Mercator.y(box.south)
        val lons = DoubleArray(width) { x -> box.west + (box.east - box.west) * (x + 0.5) / width }
        for (y in 0 until height) {
            val lat = Mercator.lat(top + (bottom - top) * (y + 0.5) / height)
            val gy = (lat - grid.south) / grid.stepLat
            if (gy < 0 || gy > grid.rows - 1) continue
            for (x in 0 until width) {
                val gx = (lons[x] - grid.west) / grid.stepLon
                if (gx < 0 || gx > grid.columns - 1) continue
                val alpha = alphaOf(sample(grid, gx, gy))
                if (alpha <= 0f) continue
                pixels[y * width + x] = ((alpha * 255).roundToInt() shl 24) or CLOUD_RGB
            }
        }
        return pixels
    }

    /**
     * Только непрозрачность пикселей: цвет у облаков один, поэтому запас кадров держит байт на пиксель, а не четыре
     * (сутки облаков по часам занимают около 16 МБ вместо 65).
     */
    fun alphaOnly(pixels: IntArray): ByteArray = ByteArray(pixels.size) { (pixels[it] ushr 24).toByte() }

    /** Обратно из [alphaOnly] в цвета ARGB. */
    fun fromAlpha(alpha: ByteArray): IntArray = IntArray(alpha.size) { i ->
        val a = alpha[i].toInt() and 0xFF
        if (a == 0) 0 else (a shl 24) or CLOUD_RGB
    }

    /** Значение сетки в дробной точке (столбец [gx], строка [gy]); пропуски в узлах дают NaN. */
    internal fun sample(grid: LatLonGrid, gx: Double, gy: Double): Float {
        val i = floor(gx).toInt().coerceIn(0, grid.columns - 2)
        val j = floor(gy).toInt().coerceIn(0, grid.rows - 2)
        val fx = (gx - i).toFloat()
        val fy = (gy - j).toFloat()
        val a = grid.at(i, j)
        val b = grid.at(i + 1, j)
        val c = grid.at(i, j + 1)
        val d = grid.at(i + 1, j + 1)
        val lower = a + (b - a) * fx
        val upper = c + (d - c) * fx
        return lower + (upper - lower) * fy
    }
}
