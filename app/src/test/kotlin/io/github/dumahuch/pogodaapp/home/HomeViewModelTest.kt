package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.data.AirQualitySource
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.data.PollenSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.location.PollenRegionLocator
import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
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

    private class FakeAir(var raw: String?) : AirQualitySource {
        var calls = 0

        override suspend fun fetchRaw(latitude: Double, longitude: Double): String {
            calls++
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private class FakePollen(var raw: String?) : PollenSource {
        var calls = 0

        override suspend fun fetchRaw(): String {
            calls++
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private class FakeRegions(var key: Int?) : PollenRegionLocator {
        override suspend fun keyOf(latitude: Double, longitude: Double): Int? = key
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

    /** Диспетчер главного потока с виртуальным временем: им двигаем часы, когда проверяем минимальное время индикатора. */
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var locations: FakeLocations
    private lateinit var namer: FakeNamer
    private lateinit var source: FakeSource
    private lateinit var clock: TestClock
    private lateinit var cache: ForecastCache

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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

    private fun viewModel(air: AirQualitySource) = HomeViewModel(locations, namer, source, cache, clock, air)

    private fun viewModel(pollen: PollenSource, regions: PollenRegionLocator) =
        HomeViewModel(locations, namer, source, cache, clock, null, pollen, regions)

    /** Файл DWD с одним районом Рейн-Майн (92): у всех видов на сегодня одна и та же [level], выпуск 28 октября. */
    private fun pollenJson(level: String): String {
        val kinds = listOf("Hasel", "Erle", "Esche", "Birke", "Graeser", "Roggen", "Beifuss", "Ambrosia")
            .joinToString(",") { "\"$it\":{\"today\":\"$level\",\"tomorrow\":\"0\",\"dayafter_to\":\"0\"}" }
        return """{"last_update":"2026-10-28 11:00 Uhr","content":[{"partregion_id":92,"region_id":90,"Pollen":{$kinds}}]}"""
    }

    /** Ответ качества воздуха с одним и тем же индексом в текущем и следующем часе. */
    private fun airJson(value: Int): String {
        val hour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).epochSecond
        return """{"hourly":{"time":[$hour,${hour + 3600}],"european_aqi":[$value,$value]}}"""
    }

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

    @Test
    fun pullRefreshFetchesAgainEvenWhenTheDataIsFresh() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(1, source.calls)
        assertFalse(model.refreshing.value)

        model.refresh(permissionGranted = true)
        assertEquals(2, source.calls)
        assertEquals("Hannover", model.content().place.name)
    }

    @Test
    fun pullRefreshIndicatorStaysForTheMinimumTimeEvenWhenTheAnswerIsInstant() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertFalse("обычная загрузка при открытии индикатор не включает", model.refreshing.value)

        model.refresh(permissionGranted = true)
        assertTrue(model.refreshing.value)
        dispatcher.scheduler.advanceTimeBy(699)
        assertTrue(model.refreshing.value)
        dispatcher.scheduler.advanceTimeBy(2)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun pullRefreshIndicatorStaysWhileTheAnswerIsPending() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        val gate = CompletableDeferred<Unit>()
        source.gate = gate

        model.refresh(permissionGranted = true)
        dispatcher.scheduler.advanceTimeBy(10_000)
        assertTrue(model.refreshing.value)

        gate.complete(Unit)
        assertFalse(model.refreshing.value)
        assertFalse(model.content().offline)
    }

    @Test
    fun secondPullWhileRefreshingDoesNothing() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        val gate = CompletableDeferred<Unit>()
        source.gate = gate

        model.refresh(permissionGranted = true)
        model.refresh(permissionGranted = true)
        gate.complete(Unit)
        assertEquals(2, source.calls)
    }

    @Test
    fun failedPullRefreshKeepsTheDataAndMarksItOffline() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        clock.now = now.plus(Duration.ofHours(2))
        source.raw = null

        model.refresh(permissionGranted = true)
        val content = model.content()
        assertTrue(content.offline)
        assertEquals(2, content.staleHours)
        assertEquals("Hannover", content.place.name)

        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.refreshing.value)
    }

    @Test
    fun pullRefreshWithoutPermissionShowsTheCard() {
        val model = viewModel()
        model.refresh(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
        assertFalse(model.refreshing.value)
        assertEquals(0, locations.calls)
    }

    @Test
    fun revokedPermissionDuringPullRefreshTurnsTheIndicatorOff() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        source.gate = CompletableDeferred()

        model.refresh(permissionGranted = true)
        assertTrue(model.refreshing.value)

        model.refresh(permissionGranted = false)
        assertEquals(HomeUiState.NoPermission, model.state.value)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun retryDuringPullRefreshTurnsTheIndicatorOff() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        source.gate = CompletableDeferred()

        model.refresh(permissionGranted = true)
        assertTrue(model.refreshing.value)

        model.retry(permissionGranted = true)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun pullRefreshDuringTheFirstLoadRestartsItAndFinishesCleanly() {
        locations.known = null
        val gate = CompletableDeferred<Unit>()
        locations.gate = gate
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertEquals(HomeUiState.Loading, model.state.value)

        model.refresh(permissionGranted = true)
        assertTrue(model.refreshing.value)

        gate.complete(Unit)
        assertEquals("Hannover", model.content().place.name)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.refreshing.value)
    }

    @Test
    fun airQualityArrivesWithTheForecast() {
        val air = FakeAir(airJson(23))
        val model = viewModel(air)
        model.onResumed(permissionGranted = true)

        assertEquals(1, air.calls)
        assertEquals(AirQuality(23), model.content().airQuality)
    }

    @Test
    fun withoutAirQualitySourceTheTileStaysEmpty() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertNull(model.content().airQuality)
    }

    @Test
    fun airQualityFailureDoesNotBreakTheForecast() {
        val air = FakeAir(null)
        val model = viewModel(air)
        model.onResumed(permissionGranted = true)

        val content = model.content()
        assertFalse(content.offline)
        assertEquals(12.7, content.strip.first().temperature, 0.0)
        assertNull(content.airQuality)
    }

    @Test
    fun oldAirQualityIsNotShownAfterThreeHours() {
        val air = FakeAir(airJson(23))
        val model = viewModel(air)
        model.onResumed(permissionGranted = true)
        assertEquals(AirQuality(23), model.content().airQuality)

        // Через четыре часа воздух не получается, прогноз обновляется: старый индекс уже не показывается.
        clock.now = now.plus(Duration.ofHours(4))
        air.raw = null
        model.retry(permissionGranted = true)
        assertNull(model.content().airQuality)
    }

    @Test
    fun pollenArrivesWithTheForecast() {
        val pollen = FakePollen(pollenJson("1-2"))
        val model = viewModel(pollen, FakeRegions(92))
        model.onResumed(permissionGranted = true)

        assertEquals(1, pollen.calls)
        val shown = model.content().pollen
        assertNotNull(shown)
        assertEquals(92, shown!!.regionKey)
        assertEquals(PollenLevel(3), shown.on(java.time.LocalDate.of(2026, 10, 28))?.get(PollenKind.Birch))
    }

    @Test
    fun placeOutsideGermanyHasNoPollenAndDownloadsNothing() {
        val pollen = FakePollen(pollenJson("1"))
        val model = viewModel(pollen, FakeRegions(null))
        model.onResumed(permissionGranted = true)

        assertEquals(0, pollen.calls)
        assertNull(model.content().pollen)
    }

    @Test
    fun withoutPollenSourceTheTileStaysEmpty() {
        val model = viewModel()
        model.onResumed(permissionGranted = true)
        assertNull(model.content().pollen)
    }

    @Test
    fun pollenFailureDoesNotBreakTheForecast() {
        val model = viewModel(FakePollen(null), FakeRegions(92))
        model.onResumed(permissionGranted = true)

        val content = model.content()
        assertFalse(content.offline)
        assertEquals(12.7, content.strip.first().temperature, 0.0)
        assertNull(content.pollen)
    }

    @Test
    fun brokenPollenFileDoesNotBreakTheForecast() {
        val model = viewModel(FakePollen("не json"), FakeRegions(92))
        model.onResumed(permissionGranted = true)
        assertFalse(model.content().offline)
        assertNull(model.content().pollen)
    }

    @Test
    fun freshPollenIsNotDownloadedAgainForTheSamePlace() {
        val pollen = FakePollen(pollenJson("1"))
        val model = viewModel(pollen, FakeRegions(92))
        model.onResumed(permissionGranted = true)
        assertEquals(1, pollen.calls)

        clock.now = now.plus(Duration.ofHours(2))
        model.retry(permissionGranted = true)
        assertEquals(1, pollen.calls)
        assertNotNull(model.content().pollen)
    }

    @Test
    fun oldPollenIsDownloadedAgainAfterSixHours() {
        val pollen = FakePollen(pollenJson("1"))
        val model = viewModel(pollen, FakeRegions(92))
        model.onResumed(permissionGranted = true)

        clock.now = now.plus(Duration.ofHours(7))
        pollen.raw = null
        model.retry(permissionGranted = true)
        assertEquals(2, pollen.calls)
        assertNull(model.content().pollen)
    }
}
