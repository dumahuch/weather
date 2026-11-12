package io.github.dumahuch.pogodaapp.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/** Прямоугольник в градусах: запад, юг, восток, север. */
data class GeoBox(val west: Double, val south: Double, val east: Double, val north: Double)

/** Проекция карты (веб-Меркатор, EPSG:3857): метры по оси x и y. */
object Mercator {
    private const val EARTH_RADIUS = 6_378_137.0

    fun x(lon: Double): Double = Math.toRadians(lon) * EARTH_RADIUS

    fun y(lat: Double): Double = ln(tan(PI / 4 + Math.toRadians(lat) / 2)) * EARTH_RADIUS

    fun lat(y: Double): Double = Math.toDegrees(2 * atan(exp(y / EARTH_RADIUS)) - PI / 2)
}

/**
 * Области карты (только Германия, 0.8.0). Слои дождя и облаков рисуются одной картинкой на всю область [Overlay]:
 * она покрывает весь радар DWD (45,68–56,22 с.ш., 1,46–18,73 в.д.) с небольшим обрезом по краям и лежит внутри
 * сетки ICON-D2. Камера не уходит центром за [CameraTarget].
 */
object MapArea {
    val Overlay = GeoBox(west = 2.5, south = 45.7, east = 18.5, north = 56.2)

    /** Куда может смотреть центр карты. */
    val CameraTarget = GeoBox(west = 5.0, south = 46.8, east = 15.9, north = 55.4)

    /** Ширина картинки дождя в пикселях: около 0,9 км на пиксель в середине Германии, как сам радар (1 км). */
    const val RAIN_WIDTH = 1200

    /** Ширина картинки облаков: сетка ICON-D2 вдвое грубее радара (0,02°, около 2 км). */
    const val CLOUD_WIDTH = 800

    /** Высота картинки для ширины [width], чтобы пиксели [Overlay] в проекции карты были квадратными. */
    fun heightFor(width: Int, box: GeoBox = Overlay): Int {
        val spanX = Mercator.x(box.east) - Mercator.x(box.west)
        val spanY = Mercator.y(box.north) - Mercator.y(box.south)
        return (width * spanY / spanX).roundToInt()
    }
}
