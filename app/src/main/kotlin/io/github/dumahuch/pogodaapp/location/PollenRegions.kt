package io.github.dumahuch.pogodaapp.location

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.cos
import kotlin.math.hypot

/** Определяет район пыльцы DWD по месту. Результат это `partregion_id` (у районов без частей `region_id`) или null вне Германии. */
interface PollenRegionLocator {
    suspend fun keyOf(latitude: Double, longitude: Double): Int?
}

/**
 * Границы 27 районов индекса пыльцы DWD. Точка внутри района даёт его номер; точка вне всех районов, но не дальше
 * [NEAR_KM] от границы (берег, упрощённая граница), даёт ближайший район; дальше это уже не Германия, и ответ null.
 *
 * Формат файла `assets/pollen/regions.json`: `{"regions":[{"id":11,"polygons":[[ring, hole, …], …]}, …]}`, где каждое
 * кольцо это плоский список `[lon, lat, lon, lat, …]` (первая точка не повторяется в конце). Район может состоять из
 * нескольких многоугольников (острова); первое кольцо многоугольника внешнее, остальные вырезаны из него.
 */
class PollenRegions internal constructor(private val regions: List<Region>) {

    internal class Polygon(val rings: List<DoubleArray>)

    internal class Region(val key: Int, val polygons: List<Polygon>)

    /** Район, в который попадает точка, или ближайший не дальше [NEAR_KM]; null, если места нет в Германии. */
    fun keyOf(latitude: Double, longitude: Double): Int? {
        for (region in regions) {
            if (region.polygons.any { contains(it, longitude, latitude) }) return region.key
        }
        var best: Int? = null
        var bestKm = NEAR_KM
        for (region in regions) {
            for (polygon in region.polygons) {
                for (ring in polygon.rings) {
                    val d = distanceToRingKm(ring, latitude, longitude)
                    if (d < bestKm) {
                        bestKm = d
                        best = region.key
                    }
                }
            }
        }
        return best
    }

    private fun contains(polygon: Polygon, lon: Double, lat: Double): Boolean {
        // Чётность числа пересечённых колец: внутри внешнего и не внутри дыры.
        var inside = false
        for (ring in polygon.rings) if (ringContains(ring, lon, lat)) inside = !inside
        return inside
    }

    private fun ringContains(ring: DoubleArray, lon: Double, lat: Double): Boolean {
        val n = ring.size / 2
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i]
            val yi = ring[2 * i + 1]
            val xj = ring[2 * j]
            val yj = ring[2 * j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Расстояние от точки до ломаной (замкнутой), км; в малом масштабе плоскость с поправкой на широту. */
    private fun distanceToRingKm(ring: DoubleArray, lat: Double, lon: Double): Double {
        val n = ring.size / 2
        val kx = KM_PER_DEGREE * cos(Math.toRadians(lat))
        var best = Double.MAX_VALUE
        var j = n - 1
        for (i in 0 until n) {
            val ax = (ring[2 * j] - lon) * kx
            val ay = (ring[2 * j + 1] - lat) * KM_PER_DEGREE
            val bx = (ring[2 * i] - lon) * kx
            val by = (ring[2 * i + 1] - lat) * KM_PER_DEGREE
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + t * dx, ay + t * dy))
            j = i
        }
        return best
    }

    companion object {
        /** Дальше этого расстояния от границы места считаются вне Германии. */
        const val NEAR_KM = 10.0

        private const val KM_PER_DEGREE = 111.195

        /** Разбор файла границ; null, если файл повреждён. */
        fun parse(json: String): PollenRegions? = try {
            val array = JSONObject(json).getJSONArray("regions")
            val regions = (0 until array.length()).map { i ->
                val region = array.getJSONObject(i)
                val polygons = region.getJSONArray("polygons")
                Region(
                    region.getInt("id"),
                    (0 until polygons.length()).map { p ->
                        val rings = polygons.getJSONArray(p)
                        Polygon(
                            (0 until rings.length()).map { r ->
                                val flat = rings.getJSONArray(r)
                                DoubleArray(flat.length()) { flat.getDouble(it) }
                            },
                        )
                    },
                )
            }
            PollenRegions(regions)
        } catch (e: JSONException) {
            null
        }
    }
}

/** Читает границы из ресурсов приложения при первом обращении; если файла нет или он повреждён, всегда null. */
class AssetPollenRegionLocator(private val context: Context) : PollenRegionLocator {

    private val regions: PollenRegions? by lazy {
        try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { PollenRegions.parse(it.readText()) }
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun keyOf(latitude: Double, longitude: Double): Int? = withContext(Dispatchers.Default) {
        regions?.keyOf(latitude, longitude)
    }

    private companion object {
        const val ASSET = "pollen/regions.json"
    }
}
