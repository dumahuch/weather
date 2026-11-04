package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.data.AlertsParser
import io.github.dumahuch.pogodaapp.data.AlertsSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.PollenRegionLocator
import io.github.dumahuch.pogodaapp.location.distanceKm
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.weather.AlertSet
import kotlinx.coroutines.CancellationException

/**
 * Официальные предупреждения DWD для места на экране. Запрашиваются только для мест в Германии: границы Германии
 * берутся из тех же районов DWD, что и у пыльцы ([PollenRegionLocator]). Последний ответ хранится только в памяти (на
 * телефон не пишется): без сети показывается то, что пришло раньше для этого же места, а истёкшие предупреждения
 * отсеивает сам экран ([AlertSet.shownAt]).
 */
internal class AlertFeed(
    private val source: AlertsSource?,
    private val germany: PollenRegionLocator?,
) {
    private class Entry(val point: GeoPoint, val set: AlertSet)

    private var entry: Entry? = null

    /** Последние полученные предупреждения для места рядом с [point]; null, если для него ещё ничего не приходило. */
    fun setFor(point: GeoPoint): AlertSet? = entry?.takeIf { distanceKm(it.point, point) < PlacesLogic.SAME_PLACE_KM }?.set

    /**
     * Запрашивает предупреждения для [point]. Возвращает новый набор, если он получен; null, если запроса не было
     * (вне Германии, нет источника) или он не удался. Вне Германии запоминается пустой набор: прежние немецкие
     * предупреждения к другому месту не относятся.
     */
    suspend fun fetch(point: GeoPoint): AlertSet? {
        val alerts = source ?: return null
        val borders = germany ?: return null
        val inGermany = try {
            borders.keyOf(point.latitude, point.longitude) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!inGermany) {
            entry = Entry(point, AlertSet(null, emptyList()))
            return null
        }
        val set = try {
            AlertsParser.parse(alerts.fetchRaw(point.latitude, point.longitude))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        entry = Entry(point, set)
        return set
    }
}
