package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.data.GeocodingSource
import io.github.dumahuch.pogodaapp.data.PollenSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.location.PollenRegionLocator
import io.github.dumahuch.pogodaapp.places.MemoryPinnedPlacesStorage
import io.github.dumahuch.pogodaapp.places.PinResult
import io.github.dumahuch.pogodaapp.places.PinnedForecastCaches
import io.github.dumahuch.pogodaapp.places.PinnedPlace
import io.github.dumahuch.pogodaapp.places.PinnedPlacesStorage
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlaceSuggestion
import io.github.dumahuch.pogodaapp.places.PlacesMessage
import io.github.dumahuch.pogodaapp.places.SearchResults
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Закреплённые места, переключение между ними и текущим, «Определить место», статус пыльцы (версия 0.5.0). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPlacesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeLocations(var point: GeoPoint?, var known: GeoPoint? = null) : LocationSource {
        var calls = 0

        override fun lastKnown(): GeoPoint? = known

        override suspend fun fresh(): GeoPoint? {
            calls++
            return point
        }
    }

    private class FakeNamer(var name: String?, var country: String? = null) : PlaceNamer {
        var calls = 0

        override suspend fun nameOf(point: GeoPoint): String? {
            calls++
            return name
        }

        override suspend fun countryCodeOf(point: GeoPoint): String? = country
    }

    private class FakeSource(var raw: String?) : ForecastSource {
        val requests = mutableListOf<Pair<Double, Double>>()

        override suspend fun fetchRaw(latitude: Double, longitude: Double): String {
            requests += latitude to longitude
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private class FakePollen(var raw: String?) : PollenSource {
        override suspend fun fetchRaw(): String = raw ?: throw ForecastException("нет сети")
    }

    private class FakeRegions(var key: Int?) : PollenRegionLocator {
        override suspend fun keyOf(latitude: Double, longitude: Double): Int? = key
    }

    private class FakeGeocoding(val raw: String) : GeocodingSource {
        var language: String? = null

        override suspend fun searchRaw(query: String, language: String): String {
            this.language = language
            return raw
        }
    }

    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val hannoverPoint = GeoPoint(52.3745, 9.7386)
    private val munichPoint = GeoPoint(48.1374, 11.5755)
    private val munichId = "48.14_11.58"
    private val now = Instant.parse("2026-10-28T11:20:00Z")
    private val dispatcher = UnconfinedTestDispatcher()

    private val fixture: String by lazy {
        checkNotNull(javaClass.classLoader?.getResource("open-meteo/hannover.json")).readText(Charsets.UTF_8)
    }

    private lateinit var locations: FakeLocations
    private lateinit var namer: FakeNamer
    private lateinit var source: FakeSource
    private lateinit var cache: ForecastCache
    private lateinit var caches: PinnedForecastCaches
    private lateinit var cachesDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        locations = FakeLocations(hannoverPoint)
        namer = FakeNamer("Hannover")
        source = FakeSource(fixture)
        cache = ForecastCache(File(folder.root, "cache.json"))
        cachesDir = folder.newFolder("pinned")
        caches = PinnedForecastCaches(cachesDir)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        storage: PinnedPlacesStorage = MemoryPinnedPlacesStorage(),
        pollen: PollenSource? = null,
        regions: PollenRegionLocator? = null,
        geocoding: GeocodingSource? = null,
    ) = HomeViewModel(
        locations, namer, source, cache, TestClock(now), null, pollen, regions, storage, caches, geocoding, { "de" },
    )

    private fun suggestion(name: String, point: GeoPoint) =
        PlaceSuggestion(name, "Bayern", "Deutschland", "DE", point.latitude, point.longitude)

    private val munich get() = suggestion("München", munichPoint)

    /** Найденный город закрепляется за два шага: просмотр, затем «Закрепить» (так делает и экран). */
    private fun HomeViewModel.pin(suggestion: PlaceSuggestion): PinResult {
        preview(suggestion)
        return pinPreview() ?: PinResult.AlreadyPinned
    }

    private fun HomeViewModel.content(): HomeUiState.Content {
        val current = state.value
        assertTrue("ожидалось содержимое, а есть $current", current is HomeUiState.Content)
        return current as HomeUiState.Content
    }

    private fun pollenJson(level: String): String {
        val kinds = listOf("Hasel", "Erle", "Esche", "Birke", "Graeser", "Roggen", "Beifuss", "Ambrosia")
            .joinToString(",") { "\"$it\":{\"today\":\"$level\",\"tomorrow\":\"0\",\"dayafter_to\":\"0\"}" }
        return """{"last_update":"2026-10-28 11:00 Uhr","content":[{"partregion_id":92,"region_id":90,"Pollen":{$kinds}}]}"""
    }

    // --- Закрепление и переключение ---

    @Test
    fun startsOnTheCurrentPlaceEvenWithSavedPinnedPlaces() {
        val model = viewModel(MemoryPinnedPlacesStorage(listOf(PinnedPlace.of(munich))))
        assertEquals(PlaceChoice.Current, model.selection.value)
        assertEquals(1, model.pinned.value.size)
    }

    @Test
    fun pinningOpensThePlaceAtItsOwnCoordinatesWithoutAskingTheLocationService() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(1, locations.calls)

        assertEquals(PinResult.Added, model.pin(munich))

        assertEquals(PlaceChoice.Pinned(munichId), model.selection.value)
        assertEquals("München", model.content().place.name)
        assertEquals(munichPoint.latitude, model.content().place.latitude, 0.0)
        assertEquals(munichPoint.latitude to munichPoint.longitude, source.requests.last())
        assertEquals(1, locations.calls)
        assertEquals(1, namer.calls)
    }

    @Test
    fun pinnedPlaceDoesNotNeedThePermission() {
        val model = viewModel(MemoryPinnedPlacesStorage(listOf(PinnedPlace.of(munich))))
        model.select(PlaceChoice.Pinned(munichId), permissionGranted = false)
        assertEquals("München", model.content().place.name)
        assertEquals(0, locations.calls)

        model.refresh(permissionGranted = false)
        model.retry(permissionGranted = false)
        assertEquals("München", model.content().place.name)
    }

    @Test
    fun pinnedListIsSavedAndLoadedByTheNextStart() {
        val storage = MemoryPinnedPlacesStorage()
        viewModel(storage).pin(munich)
        assertEquals(listOf(munichId), storage.load().map { it.id })
        assertEquals(listOf(munichId), viewModel(storage).pinned.value.map { it.id })
    }

    @Test
    fun theSamePlaceIsNotPinnedTwiceAndIsOpenedInstead() {
        val model = viewModel()
        model.pin(munich)
        model.select(PlaceChoice.Current, permissionGranted = true)

        val nearby = suggestion("München Mitte", GeoPoint(48.1400, 11.5800))
        assertEquals(PinResult.AlreadyPinned, model.pin(nearby))
        assertEquals(1, model.pinned.value.size)
        assertEquals(PlaceChoice.Pinned(munichId), model.selection.value)
    }

    @Test
    fun listHoldsFivePlacesAndRefusesTheSixth() {
        val model = viewModel()
        val points = (1..6).map { GeoPoint(it * 10.0, it * 10.0) }
        points.take(5).forEachIndexed { index, point ->
            assertEquals(PinResult.Added, model.pin(suggestion("Ort $index", point)))
        }

        assertEquals(PinResult.ListFull, model.pin(suggestion("Ort 5", points[5])))

        // шестой город остаётся открытым для просмотра, но в список не попадает
        assertEquals(5, model.pinned.value.size)
        assertTrue(model.selection.value is PlaceChoice.Preview)
        assertEquals(PlacesMessage.ListFull, model.message.value)
        model.dismissMessage()
        assertNull(model.message.value)
    }

    // --- Просмотр без сохранения (версия 0.5.1) ---

    @Test
    fun previewOpensTheCityWithoutSavingAnything() {
        val storage = MemoryPinnedPlacesStorage()
        val model = viewModel(storage)
        model.onResumed(permissionGranted = true)

        model.preview(munich)

        assertEquals(PlaceChoice.Preview(PinnedPlace.of(munich)), model.selection.value)
        assertEquals("München", model.content().place.name)
        assertEquals(munichPoint.latitude to munichPoint.longitude, source.requests.last())
        assertTrue(model.pinned.value.isEmpty())
        assertTrue(storage.load().isEmpty())
        assertFalse(File(cachesDir, "forecast_cache_$munichId.json").exists())
    }

    @Test
    fun previewNeedsNeitherThePermissionNorTheLocationService() {
        val model = viewModel()
        model.select(PlaceChoice.Current, permissionGranted = false)
        model.preview(munich)
        assertEquals("München", model.content().place.name)
        assertEquals(0, locations.calls)
    }

    @Test
    fun previewWithoutNetworkShowsNoDataAndStillSavesNothing() {
        source.raw = null
        val model = viewModel()
        model.preview(munich)
        assertEquals(HomeUiState.NoData, model.state.value)
        assertFalse(File(cachesDir, "forecast_cache_$munichId.json").exists())
    }

    @Test
    fun previewOfAnAlreadyPinnedPlaceOpensThePinnedOne() {
        val model = viewModel(MemoryPinnedPlacesStorage(listOf(PinnedPlace.of(munich))))
        model.preview(suggestion("München Mitte", GeoPoint(48.1400, 11.5800)))
        assertEquals(PlaceChoice.Pinned(munichId), model.selection.value)
        assertNull(model.pinPreview())
    }

    @Test
    fun returningToTheCurrentPlaceDropsThePreview() {
        val storage = MemoryPinnedPlacesStorage()
        val model = viewModel(storage)
        model.preview(munich)
        model.select(PlaceChoice.Current, permissionGranted = true)
        assertEquals(PlaceChoice.Current, model.selection.value)
        assertTrue(model.pinned.value.isEmpty())
        assertTrue(storage.load().isEmpty())
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun anotherPreviewReplacesThePreviousOne() {
        val model = viewModel()
        model.preview(munich)
        model.preview(suggestion("Berlin", GeoPoint(52.52, 13.41)))
        assertEquals("Berlin", (model.selection.value as PlaceChoice.Preview).place.name)
        assertTrue(model.pinned.value.isEmpty())
    }

    @Test
    fun pinningThePreviewKeepsTheShownForecastWithoutLoadingAgain() {
        val storage = MemoryPinnedPlacesStorage()
        val model = viewModel(storage)
        model.preview(munich)
        val requests = source.requests.size

        assertEquals(PinResult.Added, model.pinPreview())

        assertEquals(requests, source.requests.size)
        assertEquals(PlaceChoice.Pinned(munichId), model.selection.value)
        assertEquals(listOf(munichId), storage.load().map { it.id })
        assertEquals("München", model.content().place.name)
        // показанный прогноз сохранён как прогноз закреплённого места
        assertTrue(File(cachesDir, "forecast_cache_$munichId.json").isFile)
    }

    @Test
    fun pinningWithoutAPreviewDoesNothing() {
        val model = viewModel()
        assertNull(model.pinPreview())
        assertTrue(model.pinned.value.isEmpty())
    }

    @Test
    fun unpinningTheOpenedPlaceReturnsToCurrentAndRemovesItsSavedForecast() {
        val storage = MemoryPinnedPlacesStorage()
        val model = viewModel(storage)
        model.onResumed(permissionGranted = true)
        model.pin(munich)
        assertTrue(File(cachesDir, "forecast_cache_$munichId.json").isFile)

        model.unpin(munichId, permissionGranted = true)

        assertEquals(PlaceChoice.Current, model.selection.value)
        assertTrue(model.pinned.value.isEmpty())
        assertTrue(storage.load().isEmpty())
        assertFalse(File(cachesDir, "forecast_cache_$munichId.json").exists())
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun unpinningAnotherPlaceKeepsTheSelection() {
        val model = viewModel()
        model.pin(munich)
        model.pin(suggestion("Berlin", GeoPoint(52.52, 13.41)))
        model.unpin(munichId, permissionGranted = true)
        assertEquals(PlaceChoice.Pinned("52.52_13.41"), model.selection.value)
        assertEquals(1, model.pinned.value.size)
    }

    @Test
    fun unpinningUnknownIdChangesNothing() {
        val model = viewModel()
        model.pin(munich)
        model.unpin("0.00_0.00", permissionGranted = true)
        assertEquals(1, model.pinned.value.size)
    }

    @Test
    fun switchingToCurrentWithoutPermissionShowsTheCard() {
        val model = viewModel()
        model.pin(munich)
        model.select(PlaceChoice.Current, permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
    }

    @Test
    fun switchingBackShowsTheCurrentPlaceWithItsOwnSavedForecast() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        model.pin(munich)
        model.select(PlaceChoice.Current, permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
        assertEquals("Hannover", cache.load()?.place?.name)
    }

    @Test
    fun pinnedForecastNeverOverwritesTheCurrentPlaceCache() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        model.pin(munich)
        assertEquals("Hannover", cache.load()?.place?.name)
        assertEquals("München", caches.cacheFor(munichId).load()?.place?.name)
    }

    @Test
    fun withoutNetworkPinnedPlaceShowsItsSavedForecastAsOffline() {
        val model = viewModel()
        model.pin(munich)
        model.select(PlaceChoice.Current, permissionGranted = true)
        source.raw = null

        model.select(PlaceChoice.Pinned(munichId), permissionGranted = true)

        val content = model.content()
        assertTrue(content.offline)
        assertEquals("München", content.place.name)
    }

    @Test
    fun withoutNetworkAndWithoutSavedForecastPinnedPlaceShowsNoData() {
        source.raw = null
        val model = viewModel(MemoryPinnedPlacesStorage(listOf(PinnedPlace.of(munich))))
        model.select(PlaceChoice.Pinned(munichId), permissionGranted = true)
        assertEquals(HomeUiState.NoData, model.state.value)
    }

    @Test
    fun selectingTheShownPlaceAgainDoesNotLoadAgain() {
        val model = viewModel()
        model.pin(munich)
        val requests = source.requests.size
        model.select(PlaceChoice.Pinned(munichId), permissionGranted = true)
        assertEquals(requests, source.requests.size)
    }

    // --- Определить место ---

    @Test
    fun locateButtonPinsThePlaceWhereThePhoneIsByItsName() {
        locations.point = munichPoint
        namer.name = "München"
        val model = viewModel()
        var result: PinResult? = null

        model.pinCurrentLocation { result = it }

        assertEquals(PinResult.Added, result)
        assertEquals("München", model.pinned.value.single().name)
        assertEquals(PlaceChoice.Pinned(munichId), model.selection.value)
        assertNull(model.message.value)
    }

    @Test
    fun locateButtonFallsBackToTheLastKnownPlace() {
        locations.point = null
        locations.known = munichPoint
        val model = viewModel()
        var result: PinResult? = null
        model.pinCurrentLocation { result = it }
        assertEquals(PinResult.Added, result)
    }

    @Test
    fun locateButtonLabelsThePlaceWithCoordinatesWhenTheNameIsUnknown() {
        locations.point = munichPoint
        namer.name = null
        val model = viewModel()
        model.pinCurrentLocation { }
        assertEquals("48.14, 11.58", model.pinned.value.single().name)
    }

    @Test
    fun locateButtonFailureShowsAMessageAndPinsNothing() {
        locations.point = null
        locations.known = null
        val model = viewModel()
        var called = false

        model.pinCurrentLocation { called = true }

        assertFalse(called)
        assertEquals(PlacesMessage.LocateFailed, model.message.value)
        assertTrue(model.pinned.value.isEmpty())
        assertEquals(PlaceChoice.Current, model.selection.value)
    }

    @Test
    fun locateButtonOnAFullListShowsTheFullMessage() {
        val model = viewModel()
        (1..5).forEach { model.pin(suggestion("Ort $it", GeoPoint(it * 10.0, it * 10.0))) }
        locations.point = munichPoint
        var result: PinResult? = null

        model.pinCurrentLocation { result = it }

        assertEquals(PinResult.ListFull, result)
        assertEquals(PlacesMessage.ListFull, model.message.value)
    }

    // --- Статус пыльцы для экрана проверки ---

    @Test
    fun pollenStatusIsPendingWithoutSources() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(PollenStatus.Pending, model.content().pollenStatus)
    }

    @Test
    fun pollenStatusOutsideGermanyWhenThePlaceIsNotInAnyRegion() {
        val model = viewModel(pollen = FakePollen(pollenJson("1")), regions = FakeRegions(null))
        model.onResumed(permissionGranted = true)
        assertEquals(PollenStatus.OutsideGermany, model.content().pollenStatus)
        assertNull(model.content().pollen)
    }

    @Test
    fun pollenStatusLoadedWhenTheFileIsReadEvenOutOfSeason() {
        val model = viewModel(pollen = FakePollen(pollenJson("0")), regions = FakeRegions(92))
        model.onResumed(permissionGranted = true)
        assertEquals(PollenStatus.Loaded, model.content().pollenStatus)
        assertNotNull(model.content().pollen)
    }

    @Test
    fun pollenStatusUnavailableWhenTheFileCannotBeDownloaded() {
        val model = viewModel(pollen = FakePollen(null), regions = FakeRegions(92))
        model.onResumed(permissionGranted = true)
        assertEquals(PollenStatus.Unavailable, model.content().pollenStatus)
    }

    @Test
    fun pollenWorksForAPinnedPlaceToo() {
        val model = viewModel(pollen = FakePollen(pollenJson("1")), regions = FakeRegions(92))
        model.pin(suggestion("Frankfurt", GeoPoint(50.11, 8.68)))
        assertEquals(PollenStatus.Loaded, model.content().pollenStatus)
    }

    // --- Поиск, подключённый к модели ---

    @Test
    fun searchUsesTheInterfaceLanguageAndPutsThePhoneCountryFirst() {
        locations.known = munichPoint
        namer.country = "DE"
        val geocoding = FakeGeocoding(
            """{"results":[
                {"name":"Munich","latitude":42.0,"longitude":-100.0,"country_code":"US"},
                {"name":"München","latitude":48.14,"longitude":11.58,"country_code":"DE"}]}""",
        )
        val model = viewModel(geocoding = geocoding)

        model.search.onQuery("Mün")
        dispatcher.scheduler.advanceUntilIdle()

        val found = model.search.results.value as SearchResults.Found
        assertEquals(listOf("München", "Munich"), found.items.map { it.name })
        assertEquals("de", geocoding.language)
    }

    @Test
    fun pinningFromSearchClearsTheSearchField() {
        val model = viewModel()
        model.search.onQuery("Mün")
        model.pin(munich)
        assertEquals("", model.search.query.value)
        assertEquals(SearchResults.Idle, model.search.results.value)
    }
}
