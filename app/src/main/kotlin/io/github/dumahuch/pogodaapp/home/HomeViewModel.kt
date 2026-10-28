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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 *  3. прогноз запрашивается сразу по последнему известному месту, не дожидаясь свежего; свежее место определяется
 *     параллельно, и если оно дальше [SAME_PLACE_KM] от первого, прогноз запрашивается ещё раз;
 *  4. название места ищется параллельно с запросом прогноза и появляется, когда готово, прогноз его не ждёт;
 *  5. место неизвестно совсем: карточка «Не удалось определить место»;
 *  6. обновление удалось: свежие данные; не удалось: сохранённые с пометкой «нет сети», а если их нет, «нет данных».
 * Обновление протягиванием ([refresh]) делает то же самое сразу, без оглядки на 15 минут, и поднимает флаг [refreshing],
 * по которому экран показывает индикатор.
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

    private val mutableRefreshing = MutableStateFlow(false)

    /** Идёт обновление, запрошенное протягиванием экрана вниз (автоматическое при открытии сюда не входит). */
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    private var job: Job? = null
    private var lastSuccess: Instant? = null

    /** То, что сейчас показано свежим: нужно, чтобы дописать название места. */
    private class Snapshot(val place: Place, val forecast: Forecast, val raw: String, val savedAt: Instant)

    private var snapshot: Snapshot? = null

    /** Вызывается при каждом открытии экрана. Обновляет, если данные не свежие или были ошибки. */
    fun onResumed(permissionGranted: Boolean) {
        if (!permissionGranted) {
            job?.cancel()
            mutableRefreshing.value = false
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
            job?.cancel()
            mutableRefreshing.value = false
            mutableState.value = HomeUiState.NoPermission
            return
        }
        job?.cancel()
        start(manual = false)
    }

    /**
     * Обновление протягиванием экрана вниз: загружает заново, даже если данные свежие.
     * Пока уже идёт такое обновление, повторное протягивание ничего не делает.
     */
    fun refresh(permissionGranted: Boolean) {
        if (!permissionGranted) {
            job?.cancel()
            mutableRefreshing.value = false
            mutableState.value = HomeUiState.NoPermission
            return
        }
        if (mutableRefreshing.value) return
        job?.cancel()
        start(manual = true)
    }

    private fun start(manual: Boolean = false) {
        // Запуск отложен: нужно сначала записать задачу в [job], чтобы в `finally` отличить её от более новой.
        val started = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (manual) {
                    // Индикатор виден не меньше [MIN_REFRESH_SHOWN_MS]: иначе при быстром ответе он лишь мигнёт.
                    coroutineScope {
                        val minimum = launch { delay(MIN_REFRESH_SHOWN_MS) }
                        load()
                        minimum.join()
                    }
                } else {
                    load()
                }
            } finally {
                if (job === coroutineContext[Job]) mutableRefreshing.value = false
            }
        }
        job = started
        mutableRefreshing.value = manual
        started.start()
    }

    private suspend fun load() = coroutineScope {
        val cached = cache.load()
        val cachedForecast = cached?.let { parseOrNull(it.raw) }
        if (mutableState.value !is HomeUiState.Content) {
            mutableState.value = if (cached != null && cachedForecast != null) {
                content(cached.place, cachedForecast, cached.savedAt, offline = false)
            } else {
                HomeUiState.Loading
            }
        }

        // Свежее место определяется в фоне, а прогноз по последнему известному запрашивается сразу.
        val fresh = async { freshOrNull() }
        val known = lastKnownOrNull()
        val published = known != null && showFor(known, cached, cachedForecast, alreadyPublished = false)

        val latest = fresh.await()
        if (latest != null && (known == null || !nearby(known, latest))) {
            showFor(latest, cached, cachedForecast, alreadyPublished = published)
        }
        if (known == null && latest == null) {
            mutableState.value = HomeUiState.LocationUnavailable
        }
    }

    /**
     * Запрашивает и показывает прогноз для точки; название ищется параллельно и добавляется позже.
     * Возвращает true, если свежий прогноз показан. Неудача не затирает то, что уже показано в этом проходе.
     */
    private suspend fun CoroutineScope.showFor(
        point: GeoPoint,
        cached: ForecastCache.Entry?,
        cachedForecast: Forecast?,
        alreadyPublished: Boolean,
    ): Boolean {
        val knownName = cached?.place?.takeIf { it.name != null && nearby(it, point) }?.name
        val lookup: Deferred<String?>? = if (knownName == null) async { nameOrNull(point) } else null

        val fetched = try {
            val raw = source.fetchRaw(point.latitude, point.longitude)
            raw to OpenMeteoParser.parse(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (fetched == null) {
            lookup?.cancel()
            if (!alreadyPublished) showStaleOrNoData(cached, cachedForecast)
            return false
        }
        val (raw, forecast) = fetched

        val now = clock.instant()
        lastSuccess = now
        val place = Place(point.latitude, point.longitude, knownName)
        publish(place, forecast, raw, now)
        if (lookup != null) {
            launch { lookup.await()?.let { applyName(place, it) } }
        }
        return true
    }

    /** Сбой службы местоположения (например, доступ отозвали на ходу) считается «места нет», а не падением. */
    private suspend fun freshOrNull(): GeoPoint? = try {
        locations.fresh()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun lastKnownOrNull(): GeoPoint? = try {
        locations.lastKnown()
    } catch (e: Exception) {
        null
    }

    private suspend fun nameOrNull(point: GeoPoint): String? = try {
        namer.nameOf(point)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Добавляет название, только если на экране всё ещё этот прогноз для этого места и названия у него нет. */
    private fun applyName(place: Place, name: String) {
        val shown = snapshot ?: return
        val same = shown.place.latitude == place.latitude && shown.place.longitude == place.longitude
        if (!same || shown.place.name != null) return
        publish(shown.place.copy(name = name), shown.forecast, shown.raw, shown.savedAt)
    }

    private fun publish(place: Place, forecast: Forecast, raw: String, savedAt: Instant) {
        snapshot = Snapshot(place, forecast, raw, savedAt)
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

    private fun nearby(place: Place, point: GeoPoint): Boolean = nearby(GeoPoint(place.latitude, place.longitude), point)

    private fun nearby(a: GeoPoint, b: GeoPoint): Boolean = distanceKm(a, b) < SAME_PLACE_KM

    private companion object {
        /** Не дёргать сеть при каждом возвращении на экран. */
        val REFRESH_AFTER: Duration = Duration.ofMinutes(15)

        /** Сколько миллисекунд индикатор обновления остаётся на экране минимум. */
        const val MIN_REFRESH_SHOWN_MS = 700L

        /** В пределах этого расстояния название места берётся из сохранённого, без запроса к службе адресов. */
        const val SAME_PLACE_KM = 2.0
    }
}
