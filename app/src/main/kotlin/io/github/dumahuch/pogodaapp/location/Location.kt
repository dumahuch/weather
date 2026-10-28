package io.github.dumahuch.pogodaapp.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка на карте в градусах. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Определяет, где сейчас телефон. Возвращает null, если место определить не удалось. */
interface LocationSource {
    suspend fun currentLocation(): GeoPoint?
}

/** Находит название населённого пункта по координатам (null, если не вышло). */
interface PlaceNamer {
    suspend fun nameOf(point: GeoPoint): String?
}

/** Расстояние между точками по поверхности Земли, км (формула гаверсинусов). */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * earthRadiusKm * asin(sqrt(h))
}
