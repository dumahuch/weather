package io.github.dumahuch.pogodaapp.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка на карте в градусах. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Определяет, где сейчас телефон. Оба способа возвращают null, если место неизвестно или доступ закрыт. */
interface LocationSource {
    /** Последнее известное место: отвечает сразу, без ожидания спутников и сети. */
    fun lastKnown(): GeoPoint?

    /** Свежее место: может занять несколько секунд. */
    suspend fun fresh(): GeoPoint?
}

/** Находит название населённого пункта по координатам (null, если не вышло). */
interface PlaceNamer {
    suspend fun nameOf(point: GeoPoint): String?

    /** Двухбуквенный код страны точки (например «DE»); по нему подсказки поиска упорядочиваются по стране телефона. */
    suspend fun countryCodeOf(point: GeoPoint): String? = null
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
