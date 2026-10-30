package io.github.dumahuch.pogodaapp.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dumahuch.pogodaapp.data.AirQualityParser
import io.github.dumahuch.pogodaapp.data.AirQualitySource
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.data.GeocodingSource
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.data.PollenParser
import io.github.dumahuch.pogodaapp.data.PollenSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.location.PollenRegionLocator
import io.github.dumahuch.pogodaapp.location.distanceKm
import io.github.dumahuch.pogodaapp.places.MemoryPinnedPlacesStorage
import io.github.dumahuch.pogodaapp.places.PinResult
import io.github.dumahuch.pogodaapp.places.PinnedForecastCaches
import io.github.dumahuch.pogodaapp.places.PinnedPlace
import io.github.dumahuch.pogodaapp.places.PinnedPlacesStorage
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlaceSearchModel
import io.github.dumahuch.pogodaapp.places.PlaceSuggestion
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.places.PlacesMessage
import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import io.github.dumahuch.pogodaapp.weather.PollenForecast
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
import java.time.ZoneId
import java.util.Locale

/**
 * Логика вкладки «Главная»: место → прогноз → сохранение → состояние для экрана.
 * Порядок выбора, что показать:
 *  1. нет разрешения: карточка «Разрешить»;
 *  2. сохранённые данные показываются сразу, пока идёт обновление;
 *  3. прогноз запрашивается сразу по последнему известному месту, не дожидаясь свежего; свежее место определяется
 *     параллельно, и если оно дальше [PlacesLogic.SAME_PLACE_KM] от первого, прогноз запрашивается ещё раз;
 *  4. название места ищется параллельно с запросом прогноза и появляется, когда готово, прогноз его не ждёт;
 *  5. место неизвестно совсем: карточка «Не удалось определить место»;
 *  6. обновление удалось: свежие данные; не удалось: сохранённые с пометкой «нет сети», а если их нет, «нет данных».
 * Качество воздуха для вкладки «Подробно» запрашивается отдельно от прогноза, после него и без ожидания: его сбой или
 * отсутствие (нет сети, не Европа) ничего не ломает, плитка показывает прочерк. Так же отдельно и без ожидания
 * загружается пыльца DWD: сначала по месту определяется район ([PollenRegionLocator]), потом берётся его строка из файла
 * DWD для всей Германии; вне Германии, без сети и без файла границ плитка показывает «Нет данных».
 * Обновление протягиванием ([refresh]) делает то же самое сразу, без оглядки на 15 минут, и поднимает флаг [refreshing],
 * по которому экран показывает индикатор.
 *
 * С версии 0.5.0 приложение показывает либо текущее место ([PlaceChoice.Current], всё описанное выше), либо одно из
 * закреплённых ([PlaceChoice.Pinned], до [PlacesLogic.MAX_PINNED]). Для закреплённого места не нужны ни разрешение,
 * ни определение места, ни название от службы адресов: координаты и название уже известны, прогноз и его сохранённая
 * копия свои у каждого места. При запуске всегда выбрано текущее место; выбор живёт, пока жив процесс.
 * Найденный поиском город открывается для просмотра ([PlaceChoice.Preview]) и нигде не сохраняется, пока его не закрепят
 * ([pinPreview]).
 */
class HomeViewModel(
    private val locations: LocationSource,
    private val namer: PlaceNamer,
    private val source: ForecastSource,
    private val cache: ForecastCache,
    private val clock: Clock = Clock.systemUTC(),
    private val air: AirQualitySource? = null,
    private val pollenSource: PollenSource? = null,
    private val pollenRegions: PollenRegionLocator? = null,
    private val pinnedStorage: PinnedPlacesStorage = MemoryPinnedPlacesStorage(),
    private val pinnedCaches: PinnedForecastCaches? = null,
    geocoding: GeocodingSource? = null,
    language: () -> String = { "en" },
) : ViewModel() {

    private val mutableState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val mutableSelection = MutableStateFlow<PlaceChoice>(PlaceChoice.Current)

    /** Какое место сейчас показано. */
    val selection: StateFlow<PlaceChoice> = mutableSelection.asStateFlow()

    private val mutablePinned = MutableStateFlow(pinnedStorage.load().take(PlacesLogic.MAX_PINNED))

    /** Закреплённые места по порядку добавления. */
    val pinned: StateFlow<List<PinnedPlace>> = mutablePinned.asStateFlow()

    private val mutableMessage = MutableStateFlow<PlacesMessage?>(null)

    /** Сообщение экрана мест (список полон, место определяется, не удалось определить). */
    val message: StateFlow<PlacesMessage?> = mutableMessage.asStateFlow()

    /** Поиск города с подсказками. */
    val search = PlaceSearchModel(viewModelScope, geocoding, language, { homeCountry() })

    private val mutableRefreshing = MutableStateFlow(false)

    /** Идёт обновление, запрошенное протягиванием экрана вниз (автоматическое при открытии сюда не входит). */
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    private var job: Job? = null
    private var locateJob: Job? = null
    private var lastSuccess: Instant? = null

    /** То, что сейчас показано свежим: нужно, чтобы дописать название места. */
    private class Snapshot(val place: Place, val forecast: Forecast, val raw: String, val savedAt: Instant)

    private var snapshot: Snapshot? = null

    /** Последнее полученное качество воздуха: для какой точки, когда и что. Живёт [AIR_VALID_FOR], дальше не показывается. */
    private class AirEntry(val point: GeoPoint, val at: Instant, val quality: AirQuality)

    private var airEntry: AirEntry? = null

    /** Последний полученный индекс пыльцы: для какой точки, когда и что. Живёт [POLLEN_VALID_FOR]. */
    private class PollenEntry(val point: GeoPoint, val at: Instant, val forecast: PollenForecast)

    private var pollenEntry: PollenEntry? = null

    /** Чем кончилась последняя попытка получить пыльцу и для какой точки: по этому экран проверки объясняет пустую плитку. */
    private class PollenOutcome(val point: GeoPoint, val status: PollenStatus)

    private var pollenOutcome: PollenOutcome? = null

    /** Вызывается при каждом открытии экрана. Обновляет, если данные не свежие или были ошибки. */
    fun onResumed(permissionGranted: Boolean) {
        if (blockedByPermission(permissionGranted)) return
        val current = mutableState.value
        val fresh = lastSuccess?.let { Duration.between(it, clock.instant()) < REFRESH_AFTER } == true
        if (current is HomeUiState.Content && !current.offline && fresh) return
        if (job?.isActive == true) return
        start()
    }

    /** Кнопка «Повторить». */
    fun retry(permissionGranted: Boolean) {
        if (blockedByPermission(permissionGranted)) return
        job?.cancel()
        start(manual = false)
    }

    /**
     * Обновление протягиванием экрана вниз: загружает заново, даже если данные свежие.
     * Пока уже идёт такое обновление, повторное протягивание ничего не делает.
     */
    fun refresh(permissionGranted: Boolean) {
        if (blockedByPermission(permissionGranted)) return
        if (mutableRefreshing.value) return
        job?.cancel()
        start(manual = true)
    }

    /**
     * Переключает приложение на другое место. То же место: ведёт себя как обычное возвращение на экран.
     * Другое: прежняя загрузка отменяется, показывается сохранённый прогноз нового места (если есть), идёт обновление.
     * Разрешение нужно только для текущего места.
     */
    fun select(choice: PlaceChoice, permissionGranted: Boolean) {
        if (choice == mutableSelection.value) {
            onResumed(permissionGranted)
            return
        }
        mutableSelection.value = choice
        job?.cancel()
        mutableRefreshing.value = false
        snapshot = null
        lastSuccess = null
        if (blockedByPermission(permissionGranted)) return
        mutableState.value = HomeUiState.Loading
        start()
    }

    /**
     * Открывает найденное поиском место для просмотра: в список оно не попадает и нигде не сохраняется (в том числе его
     * прогноз). Если такое место (в пределах [PlacesLogic.SAME_PLACE_KM]) уже закреплено, открывается закреплённое.
     */
    fun preview(suggestion: PlaceSuggestion) {
        val place = PinnedPlace.of(suggestion)
        mutableMessage.value = null
        search.clear()
        val same = PlacesLogic.findSame(mutablePinned.value, place.latitude, place.longitude)
        select(if (same != null) PlaceChoice.Pinned(same.id) else PlaceChoice.Preview(place), permissionGranted = true)
    }

    /**
     * Кнопка «Закрепить» при просмотре: место попадает в список (если в нём меньше [PlacesLogic.MAX_PINNED] мест) и остаётся
     * открытым без повторной загрузки; уже показанный прогноз сохраняется как прогноз закреплённого места. Список полон:
     * ничего не меняется, появляется сообщение. Если сейчас открыт не просмотр, возвращает null.
     */
    fun pinPreview(): PinResult? {
        val preview = mutableSelection.value as? PlaceChoice.Preview ?: return null
        val place = preview.place
        val same = PlacesLogic.findSame(mutablePinned.value, place.latitude, place.longitude)
        if (same != null) {
            select(PlaceChoice.Pinned(same.id), permissionGranted = true)
            return PinResult.AlreadyPinned
        }
        if (mutablePinned.value.size >= PlacesLogic.MAX_PINNED) {
            mutableMessage.value = PlacesMessage.ListFull
            return PinResult.ListFull
        }
        val updated = mutablePinned.value + place
        mutablePinned.value = updated
        pinnedStorage.save(updated)
        mutableMessage.value = null
        val shown = snapshot
        if (shown != null && mutableState.value is HomeUiState.Content && job?.isActive != true) {
            // Прогноз уже на экране: просто переименовываем выбор и сохраняем его копию, ничего не загружая заново.
            mutableSelection.value = PlaceChoice.Pinned(place.id)
            pinnedCaches?.cacheFor(place.id)?.save(ForecastCache.Entry(shown.savedAt, shown.place, shown.raw))
        } else {
            select(PlaceChoice.Pinned(place.id), permissionGranted = true)
        }
        return PinResult.Added
    }

    /**
     * Кнопка «Определить место»: телефон сам определяет, где он, и закрепляет это место, поиск вводить не нужно.
     * Если службе адресов название неизвестно, место подписывается координатами. [onDone] вызывается, когда место
     * закреплено (или оказалось уже закреплённым, или список полон); при неудаче появляется сообщение, [onDone] не вызывается.
     */
    fun pinCurrentLocation(onDone: (PinResult) -> Unit) {
        if (locateJob?.isActive == true) return
        mutableMessage.value = PlacesMessage.Locating
        locateJob = viewModelScope.launch {
            val point = freshOrNull() ?: lastKnownOrNull()
            if (point == null) {
                mutableMessage.value = PlacesMessage.LocateFailed
                return@launch
            }
            val name = nameOrNull(point) ?: coordinatesLabel(point)
            onDone(pinPlace(PinnedPlace.ofPoint(point, name)))
        }
    }

    /** Убирает место из списка вместе с его сохранённым прогнозом. Если оно было открыто, открывается текущее место. */
    fun unpin(id: String, permissionGranted: Boolean) {
        val updated = mutablePinned.value.filterNot { it.id == id }
        if (updated.size == mutablePinned.value.size) return
        mutablePinned.value = updated
        pinnedStorage.save(updated)
        pinnedCaches?.delete(id)
        mutableMessage.value = null
        if ((mutableSelection.value as? PlaceChoice.Pinned)?.id == id) {
            select(PlaceChoice.Current, permissionGranted)
        }
    }

    /** Закрыть сообщение экрана мест (при выходе с экрана или новом поиске). */
    fun dismissMessage() {
        if (locateJob?.isActive == true) return
        mutableMessage.value = null
    }

    private fun pinPlace(place: PinnedPlace): PinResult {
        val list = mutablePinned.value
        val same = PlacesLogic.findSame(list, place.latitude, place.longitude)
        if (same != null) {
            mutableMessage.value = null
            search.clear()
            select(PlaceChoice.Pinned(same.id), permissionGranted = true)
            return PinResult.AlreadyPinned
        }
        if (list.size >= PlacesLogic.MAX_PINNED) {
            mutableMessage.value = PlacesMessage.ListFull
            return PinResult.ListFull
        }
        val updated = list + place
        mutablePinned.value = updated
        pinnedStorage.save(updated)
        mutableMessage.value = null
        search.clear()
        select(PlaceChoice.Pinned(place.id), permissionGranted = true)
        return PinResult.Added
    }

    /** Для текущего места без разрешения показывается карточка «Разрешить»; у закреплённых разрешения не требуется. */
    private fun blockedByPermission(permissionGranted: Boolean): Boolean {
        if (permissionGranted || mutableSelection.value !is PlaceChoice.Current) return false
        job?.cancel()
        mutableRefreshing.value = false
        mutableState.value = HomeUiState.NoPermission
        return true
    }

    private fun start(manual: Boolean = false) {
        val choice = mutableSelection.value
        // Запуск отложен: нужно сначала записать задачу в [job], чтобы в `finally` отличить её от более новой.
        val started = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (manual) {
                    // Индикатор виден не меньше [MIN_REFRESH_SHOWN_MS]: иначе при быстром ответе он лишь мигнёт.
                    coroutineScope {
                        val minimum = launch { delay(MIN_REFRESH_SHOWN_MS) }
                        load(choice)
                        minimum.join()
                    }
                } else {
                    load(choice)
                }
            } finally {
                if (job === coroutineContext[Job]) mutableRefreshing.value = false
            }
        }
        job = started
        mutableRefreshing.value = manual
        started.start()
    }

    private suspend fun load(choice: PlaceChoice) {
        when (choice) {
            PlaceChoice.Current -> loadCurrent()
            is PlaceChoice.Pinned -> {
                val place = mutablePinned.value.firstOrNull { it.id == choice.id }
                if (place == null) mutableState.value = HomeUiState.NoData else loadPinned(place, persist = true)
            }
            is PlaceChoice.Preview -> loadPinned(choice.place, persist = false)
        }
    }

    /** [persist] false у просмотра: ни сохранённой копии прогноза, ни чтения её, место ещё не закреплено. */
    private suspend fun loadPinned(place: PinnedPlace, persist: Boolean) = coroutineScope {
        val store = if (persist) pinnedCaches?.cacheFor(place.id) else null
        val cached = store?.load()
        val cachedForecast = cached?.let { parseOrNull(it.raw) }
        if (mutableState.value !is HomeUiState.Content) {
            mutableState.value = if (cached != null && cachedForecast != null) {
                content(cached.place, cachedForecast, cached.savedAt, offline = false)
            } else {
                HomeUiState.Loading
            }
        }
        val fetched = try {
            val raw = source.fetchRaw(place.latitude, place.longitude)
            raw to OpenMeteoParser.parse(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (fetched == null) {
            showStaleOrNoData(cached, cachedForecast)
            return@coroutineScope
        }
        val (raw, forecast) = fetched
        val now = clock.instant()
        lastSuccess = now
        publish(Place(place.latitude, place.longitude, place.name), forecast, raw, now, store)
        fetchAirQuality(place.point)
        fetchPollen(place.point)
    }

    private suspend fun loadCurrent() = coroutineScope {
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
        publish(place, forecast, raw, now, cache)
        fetchAirQuality(point)
        fetchPollen(point)
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

    /**
     * Страна телефона для упорядочивания подсказок: по последнему известному месту, а если его нет или служба адресов
     * молчит, по региону, выбранному в настройках телефона.
     */
    private suspend fun homeCountry(): String? {
        val byPlace = lastKnownOrNull()?.let {
            try {
                namer.countryCodeOf(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return byPlace ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
    }

    /** Подпись места, у которого службе адресов название неизвестно: «52.37, 9.74». */
    private fun coordinatesLabel(point: GeoPoint): String =
        String.format(Locale.ROOT, "%.2f, %.2f", point.latitude, point.longitude)

    /** Добавляет название, только если на экране всё ещё этот прогноз для этого места и названия у него нет. */
    private fun applyName(place: Place, name: String) {
        val shown = snapshot ?: return
        val same = shown.place.latitude == place.latitude && shown.place.longitude == place.longitude
        if (!same || shown.place.name != null) return
        publish(shown.place.copy(name = name), shown.forecast, shown.raw, shown.savedAt, cache)
    }

    /** [store] куда сохранять копию прогноза; null, если сохранять некуда (закреплённые места в тестах без папки). */
    private fun publish(place: Place, forecast: Forecast, raw: String, savedAt: Instant, store: ForecastCache?) {
        snapshot = Snapshot(place, forecast, raw, savedAt)
        store?.save(ForecastCache.Entry(savedAt, place, raw))
        mutableState.value = content(place, forecast, savedAt, offline = false)
    }

    private fun showStaleOrNoData(cached: ForecastCache.Entry?, cachedForecast: Forecast?) {
        mutableState.value = if (cached != null && cachedForecast != null) {
            content(cached.place, cachedForecast, cached.savedAt, offline = true)
        } else {
            HomeUiState.NoData
        }
    }

    /** Запрашивает качество воздуха вне области задач загрузки: индикатор обновления его не ждёт. */
    private fun fetchAirQuality(point: GeoPoint) {
        val source = air ?: return
        viewModelScope.launch {
            val quality: AirQuality? = try {
                AirQualityParser.parse(source.fetchRaw(point.latitude, point.longitude), clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (quality == null) return@launch
            airEntry = AirEntry(point, clock.instant(), quality)
            val shown = mutableState.value
            if (shown is HomeUiState.Content && nearby(shown.place, point)) {
                mutableState.value = shown.copy(airQuality = quality)
            }
        }
    }

    /**
     * Запрашивает пыльцу вне области задач загрузки. Если для этого места данные ещё свежие, повторно не качает
     * (файл DWD выходит раз в сутки). Район в файле находится по границам, без них или вне Германии пыльцы нет.
     * Чем кончилась попытка, записывается в [PollenStatus] (для экрана проверки).
     */
    private fun fetchPollen(point: GeoPoint) {
        val source = pollenSource ?: return
        val locator = pollenRegions ?: return
        if (pollenFor(Place(point.latitude, point.longitude, null)) != null) return
        viewModelScope.launch {
            val key: Int? = try {
                locator.keyOf(point.latitude, point.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportPollen(point, PollenStatus.Unavailable, null)
                return@launch
            }
            if (key == null) {
                reportPollen(point, PollenStatus.OutsideGermany, null)
                return@launch
            }
            val now = clock.instant()
            val forecast: PollenForecast? = try {
                PollenParser.parse(source.fetchRaw(), key, now.atZone(PollenZone).toLocalDate())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (forecast == null) {
                reportPollen(point, PollenStatus.Unavailable, null)
                return@launch
            }
            pollenEntry = PollenEntry(point, now, forecast)
            reportPollen(point, PollenStatus.Loaded, forecast)
        }
    }

    private fun reportPollen(point: GeoPoint, status: PollenStatus, forecast: PollenForecast?) {
        pollenOutcome = PollenOutcome(point, status)
        val shown = mutableState.value
        if (shown is HomeUiState.Content && nearby(shown.place, point)) {
            mutableState.value = shown.copy(pollen = forecast ?: shown.pollen, pollenStatus = status)
        }
    }

    private fun pollenFor(place: Place): PollenForecast? {
        val entry = pollenEntry ?: return null
        val fresh = Duration.between(entry.at, clock.instant()) < POLLEN_VALID_FOR
        return if (fresh && nearby(place, entry.point)) entry.forecast else null
    }

    private fun pollenStatusFor(place: Place): PollenStatus {
        if (pollenFor(place) != null) return PollenStatus.Loaded
        val outcome = pollenOutcome ?: return PollenStatus.Pending
        return if (outcome.status != PollenStatus.Loaded && nearby(place, outcome.point)) outcome.status else PollenStatus.Pending
    }

    private fun airFor(place: Place): AirQuality? {
        val entry = airEntry ?: return null
        val fresh = Duration.between(entry.at, clock.instant()) < AIR_VALID_FOR
        return if (fresh && nearby(place, entry.point)) entry.quality else null
    }

    private fun content(place: Place, forecast: Forecast, savedAt: Instant, offline: Boolean): HomeUiState.Content {
        val now = clock.instant()
        return HomeUiState.Content(
            place = place,
            forecast = forecast,
            strip = forecast.strip(now),
            offline = offline,
            staleHours = Duration.between(savedAt, now).toHours().toInt().coerceAtLeast(0),
            airQuality = airFor(place),
            pollen = pollenFor(place),
            pollenStatus = pollenStatusFor(place),
        )
    }

    private fun parseOrNull(raw: String): Forecast? = try {
        OpenMeteoParser.parse(raw)
    } catch (e: ForecastException) {
        null
    }

    private fun nearby(place: Place, point: GeoPoint): Boolean = nearby(GeoPoint(place.latitude, place.longitude), point)

    private fun nearby(a: GeoPoint, b: GeoPoint): Boolean = distanceKm(a, b) < PlacesLogic.SAME_PLACE_KM

    private companion object {
        /** Не дёргать сеть при каждом возвращении на экран. */
        val REFRESH_AFTER: Duration = Duration.ofMinutes(15)

        /** Сколько миллисекунд индикатор обновления остаётся на экране минимум. */
        const val MIN_REFRESH_SHOWN_MS = 700L

        /** Качество воздуха старше этого срока не показывается: индекс меняется по часам. */
        val AIR_VALID_FOR: Duration = Duration.ofHours(3)

        /** Файл DWD выходит раз в сутки, поэтому пыльцу перезапрашиваем не чаще чем раз в шесть часов. */
        val POLLEN_VALID_FOR: Duration = Duration.ofHours(6)

        /** День выпуска файла DWD считается по немецкому времени. */
        val PollenZone: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
