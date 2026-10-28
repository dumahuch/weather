package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeLocations(var point: GeoPoint?, var known: GeoPoint? = null) : LocationSource {
        /** Сколько раз спросили свежее место. */
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        override fun lastKnown(): GeoPoint? = known

        override suspend fun fresh(): GeoPoint? {
            calls++
            gate?.await()
            return point
        }
    }

    private class FakeNamer(var name: String?) : PlaceNamer {
        var calls = 0

        /** Ответы по очереди: пока ответ не выдан, поиск названия «идёт». Если очередь пуста, отвечает сразу. */
        val pending = ArrayDeque<CompletableDeferred<String?>>()

        override suspend fun nameOf(point: GeoPoint): String? {
            calls++
            val gate = pending.removeFirstOrNull()
            return if (gate != null) gate.await() else name
        }
    }

    private class FakeSource(var raw: String?) : ForecastSource {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        /** Номера запросов (с единицы), на которых «пропадает сеть». */
        var failOn: Set<Int> = emptySet()

        override suspend fun fetchRaw(latitude: Double, longitude: Double): String {
            calls++
            gate?.await()
            if (calls in failOn) throw ForecastException("нет сети")
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val hannoverPoint = GeoPoint(52.3745, 9.7386)
    private val munichPoint = GeoPoint(48.1374, 11.5755)
    private val fixture: String by lazy {
        checkNotNull(javaClass.classLoader?.getResource("open-meteo/hannover.json")).readText(Charsets.UTF_8)
    }

    /** 11:20 по Гринвичу, 28 октября 2026: в тот же час, что и «сейчас» в файле ответа. */
    private val now = Instant.parse("2026-10-28T11:20:00Z")

    private lateinit var locations: FakeLocations
    private lateinit var namer: FakeNamer
    private lateinit var source: FakeSource
    private lateinit var clock: TestClock
    private lateinit var cache: ForecastCache

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        locations = FakeLocations(hannoverPoint)
        namer = FakeNamer("Hannover")
        source = FakeSource(fixture)
        clock = TestClock(now)
        cache = ForecastCache(File(folder.root, "cache.json"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(locations, namer, source, cache, clock)

    private fun HomeViewModel.content(): HomeUiState.Content {
        val current = state.value
        assertTrue("ожидалось содержимое, а есть $current", current is HomeUiState.Content)
        return current as HomeUiState.Content
    }

    @Test
    fun withoutPermissionShowsTheCardAndDoesNotTouchLocationOrNetwork() {
        val model = viewModel()
        model.onResumed(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
        assertEquals(0, locations.calls)
        assertEquals(0, source.calls)
    }

    @Test
    fun startsWithLoading() {
        assertEquals(HomeUiState.Loading, viewModel().state.value)
    }

    @Test
    fun freshDataShowsContentWithPlaceNameAndSavesIt() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        val content = model.content()
        assertEquals("Hannover", content.place.name)
        assertFalse(content.offline)
        assertEquals(0, content.staleHours)
        assertEquals(48, content.strip.size)
        assertEquals(12.7, content.strip.first().temperature, 0.0)
        assertEquals(Condition.Clear, content.forecast.current.condition)

        val saved = cache.load()
        assertNotNull(saved)
        assertEquals("Hannover", saved?.place?.name)
        assertEquals(now, saved?.savedAt)
    }

    @Test
    fun locationFailureShowsLocationUnavailable() {
        locations.point = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.LocationUnavailable, model.state.value)
        assertEquals(0, source.calls)
    }

    @Test
    fun networkFailureWithoutCacheShowsNoData() {
        source.raw = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.NoData, model.state.value)
    }

    @Test
    fun corruptedAnswerWithoutCacheShowsNoData() {
        source.raw = "{\"это\":\"не прогноз\"}"
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.NoData, model.state.value)
    }

    @Test
    fun networkFailureWithCacheShowsOldDataMarkedOffline() {
        cache.save(ForecastCache.Entry(now.minus(Duration.ofHours(3)), Place(52.37, 9.74, "Hannover"), fixture))
        source.raw = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        val content = model.content()
        assertTrue(content.offline)
        assertEquals(3, content.staleHours)
        assertEquals("Hannover", content.place.name)
    }

    @Test
    fun lessThanAnHourOldDataHasZeroStaleHours() {
        cache.save(ForecastCache.Entry(now.minus(Duration.ofMinutes(40)), Place(52.37, 9.74, "Hannover"), fixture))
        source.raw = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(0, model.content().staleHours)
        assertTrue(model.content().offline)
    }

    @Test
    fun cachedDataIsShownImmediatelyWhileRefreshing() {
        cache.save(ForecastCache.Entry(now.minus(Duration.ofHours(2)), Place(52.37, 9.74, "Hannover"), fixture))
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        // Ответ ещё не пришёл, но на экране уже сохранённые данные, без пометки «нет сети».
        val before = model.content()
        assertFalse(before.offline)
        assertEquals(2, before.staleHours)

        gate.complete(Unit)
        val after = model.content()
        assertFalse(after.offline)
        assertEquals(0, after.staleHours)
    }

    @Test
    fun cachedNameIsReusedNearbyWithoutAskingTheNamer() {
        cache.save(ForecastCache.Entry(now.minus(Duration.ofHours(1)), Place(52.3745, 9.7386, "Hannover"), fixture))
        namer.name = "Другое название"
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
        assertEquals(0, namer.calls)
    }

    @Test
    fun farFromTheCachedPlaceTheNameIsLookedUpAgain() {
        cache.save(ForecastCache.Entry(now.minus(Duration.ofHours(1)), Place(48.14, 11.58, "München"), fixture))
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
        assertEquals(1, namer.calls)
    }

    @Test
    fun failedNameLookupStillShowsWeatherWithoutName() {
        namer.name = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertNull(model.content().place.name)
    }

    @Test
    fun returningSoonDoesNotRefetchButReturningLaterDoes() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(1, source.calls)

        clock.now = now.plus(Duration.ofMinutes(10))
        model.onResumed(permissionGranted = true)
        assertEquals(1, source.calls)

        clock.now = now.plus(Duration.ofMinutes(16))
        model.onResumed(permissionGranted = true)
        assertEquals(2, source.calls)
    }

    @Test
    fun afterAnOfflineResultReturningRefetchesRightAway() {
        source.raw = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.NoData, model.state.value)

        source.raw = fixture
        model.onResumed(permissionGranted = true)
        assertEquals(2, source.calls)
        assertFalse(model.content().offline)
    }

    @Test
    fun retryAfterLocationFailureWorksOnceLocationIsBack() {
        locations.point = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.LocationUnavailable, model.state.value)

        locations.point = hannoverPoint
        model.retry(permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun grantingPermissionAfterTheCardLoadsTheWeather() {
        val model = viewModel()
        model.onResumed(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)

        model.retry(permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun retryWithoutPermissionKeepsTheCard() {
        val model = viewModel()
        model.retry(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
        assertEquals(0, locations.calls)
    }

    @Test
    fun revokedPermissionReplacesContentWithTheCard() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        model.content()

        model.onResumed(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
    }

    @Test
    fun forecastIsShownByTheLastKnownPlaceWithoutWaitingForTheFreshOne() {
        locations.known = hannoverPoint
        val gate = CompletableDeferred<Unit>()
        locations.gate = gate
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        // Свежее место ещё не определено, а прогноз уже на экране.
        assertEquals("Hannover", model.content().place.name)
        assertEquals(1, source.calls)

        // Свежее место рядом с первым: второго запроса нет.
        gate.complete(Unit)
        assertEquals(1, source.calls)
        assertFalse(model.content().offline)
    }

    @Test
    fun freshPlaceFarFromTheLastKnownOneFetchesTheForecastAgain() {
        locations.known = munichPoint
        locations.point = hannoverPoint
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        assertEquals(2, source.calls)
        val place = model.content().place
        assertEquals(hannoverPoint.latitude, place.latitude, 0.0)
        assertEquals("Hannover", place.name)
        assertEquals(hannoverPoint.latitude, cache.load()?.place?.latitude)
    }

    @Test
    fun forecastDoesNotWaitForThePlaceName() {
        locations.known = hannoverPoint
        val answer = CompletableDeferred<String?>()
        namer.pending.add(answer)
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        assertNull(model.content().place.name)
        assertEquals(48, model.content().strip.size)

        answer.complete("Hannover")
        assertEquals("Hannover", model.content().place.name)
        assertEquals("Hannover", cache.load()?.place?.name)
    }

    @Test
    fun aLateNameOfAnEarlierPlaceDoesNotReplaceTheCurrentOne() {
        locations.known = munichPoint
        locations.point = hannoverPoint
        val munichName = CompletableDeferred<String?>()
        val hannoverName = CompletableDeferred<String?>()
        namer.pending.add(munichName)
        namer.pending.add(hannoverName)
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        munichName.complete("München")
        assertNull(model.content().place.name)

        hannoverName.complete("Hannover")
        assertEquals("Hannover", model.content().place.name)
        assertEquals("Hannover", cache.load()?.place?.name)
    }

    @Test
    fun failedFirstFetchDoesNotStopTheSecondPlaceFromSucceeding() {
        locations.known = munichPoint
        locations.point = hannoverPoint
        source.failOn = setOf(1)
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        assertEquals(2, source.calls)
        assertFalse(model.content().offline)
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun failedSecondFetchKeepsTheFreshDataAlreadyShown() {
        locations.known = munichPoint
        locations.point = hannoverPoint
        source.failOn = setOf(2)
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        assertEquals(2, source.calls)
        assertFalse(model.content().offline)
        assertEquals(munichPoint.latitude, model.content().place.latitude, 0.0)
    }

    @Test
    fun noLastKnownPlaceWaitsForTheFreshOne() {
        locations.known = null
        val gate = CompletableDeferred<Unit>()
        locations.gate = gate
        val model = viewModel()
        model.onResumed(permissionGranted = true)

        assertEquals(HomeUiState.Loading, model.state.value)
        assertEquals(0, source.calls)

        gate.complete(Unit)
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun lastKnownPlaceIsEnoughWhenTheFreshOneFails() {
        locations.known = hannoverPoint
        locations.point = null
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals("Hannover", model.content().place.name)
        assertEquals(1, source.calls)
    }
}
