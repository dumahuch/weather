package io.github.dumahuch.pogodaapp.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.location.distanceKm
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Логика вкладки «Главная»: место → прогноз → сохранение → состояние для экрана.
 * Порядок выбора, что показать:
 *  1. нет разрешения: карточка «Разрешить»;
 *  2. сохранённые данные показываются сразу, пока идёт обновление;
 *  3. место не определилось: карточка «Не удалось определить место»;
 *  4. обновление удалось: свежие данные; не удалось: сохранённые с пометкой «нет сети», а если их нет, «нет данных».
 */
class HomeViewModel(
    private val locations: LocationSource,
    private val namer: PlaceNamer,
    private val source: ForecastSource,
    private val cache: ForecastCache,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val mutableState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var lastSuccess: Instant? = null

    /** Вызывается при каждом открытии экрана. Обновляет, если данные не свежие или были ошибки. */
    fun onResumed(permissionGranted: Boolean) {
        if (!permissionGranted) {
            job?.cancel()
            mutableState.value = HomeUiState.NoPermission
            return
        }
        val current = mutableState.value
        val fresh = lastSuccess?.let { Duration.between(it, clock.instant()) < REFRESH_AFTER } == true
        if (current is HomeUiState.Content && !current.offline && fresh) return
        if (job?.isActive == true) return
        start()
    }

    /** Кнопка «Повторить». */
    fun retry(permissionGranted: Boolean) {
        if (!permissionGranted) {
            mutableState.value = HomeUiState.NoPermission
            return
        }
        job?.cancel()
        start()
    }

    private fun start() {
        job = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val cached = cache.load()
        val cachedForecast = cached?.let { parseOrNull(it.raw) }
        if (mutableState.value !is HomeUiState.Content) {
            mutableState.value = if (cached != null && cachedForecast != null) {
                content(cached.place, cachedForecast, cached.savedAt, offline = false)
            } else {
                HomeUiState.Loading
            }
        }

        val point = locations.currentLocation()
        if (point == null) {
            mutableState.value = HomeUiState.LocationUnavailable
            return
        }

        val fetched = try {
            val raw = source.fetchRaw(point.latitude, point.longitude)
            raw to OpenMeteoParser.parse(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (fetched == null) {
            showStaleOrNoData(cached, cachedForecast)
            return
        }
        val (raw, forecast) = fetched

        val now = clock.instant()
        lastSuccess = now
        val knownName = cached?.place?.takeIf { it.name != null && nearby(it, point) }?.name
        var place = Place(point.latitude, point.longitude, knownName)
        publish(place, forecast, raw, now)
        if (knownName == null) {
            val name = try {
                namer.nameOf(point)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (name != null) {
                place = place.copy(name = name)
                publish(place, forecast, raw, now)
            }
        }
    }

    private fun publish(place: Place, forecast: Forecast, raw: String, savedAt: Instant) {
        cache.save(ForecastCache.Entry(savedAt, place, raw))
        mutableState.value = content(place, forecast, savedAt, offline = false)
    }

    private fun showStaleOrNoData(cached: ForecastCache.Entry?, cachedForecast: Forecast?) {
        mutableState.value = if (cached != null && cachedForecast != null) {
            content(cached.place, cachedForecast, cached.savedAt, offline = true)
        } else {
            HomeUiState.NoData
        }
    }

    private fun content(place: Place, forecast: Forecast, savedAt: Instant, offline: Boolean): HomeUiState.Content {
        val now = clock.instant()
        return HomeUiState.Content(
            place = place,
            forecast = forecast,
            strip = forecast.strip(now),
            offline = offline,
            staleHours = Duration.between(savedAt, now).toHours().toInt().coerceAtLeast(0),
        )
    }

    private fun parseOrNull(raw: String): Forecast? = try {
        OpenMeteoParser.parse(raw)
    } catch (e: ForecastException) {
        null
    }

    private fun nearby(place: Place, point: GeoPoint): Boolean =
        distanceKm(GeoPoint(place.latitude, place.longitude), point) < SAME_PLACE_KM

    private companion object {
        /** Не дёргать сеть при каждом возвращении на экран. */
        val REFRESH_AFTER: Duration = Duration.ofMinutes(15)

        /** В пределах этого расстояния название места берётся из сохранённого, без запроса к службе адресов. */
        const val SAME_PLACE_KM = 2.0
    }
}
