package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.data.AlertsSource
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.ForecastSource
import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.LocationSource
import io.github.dumahuch.pogodaapp.location.PlaceNamer
import io.github.dumahuch.pogodaapp.location.PollenRegionLocator
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

/** Предупреждения DWD на «Главной»: приходят вместе с прогнозом, только для Германии, их сбой ничего не ломает. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelAlertsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class Locations(var point: GeoPoint?) : LocationSource {
        override fun lastKnown(): GeoPoint? = point
        override suspend fun fresh(): GeoPoint? = point
    }

    private class Namer : PlaceNamer {
        override suspend fun nameOf(point: GeoPoint): String = "Hannover"
    }

    private class Forecasts(var raw: String?) : ForecastSource {
        override suspend fun fetchRaw(latitude: Double, longitude: Double): String = raw ?: throw ForecastException("нет сети")
    }

    private class Alerts(var raw: String?) : AlertsSource {
        var calls = 0

        override suspend fun fetchRaw(latitude: Double, longitude: Double): String {
            calls++
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private class Germany(var inside: Boolean) : PollenRegionLocator {
        override suspend fun keyOf(latitude: Double, longitude: Double): Int? = if (inside) 92 else null
    }

    private class FixedClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val hannover = GeoPoint(52.3745, 9.7386)
    private val now = Instant.parse("2026-10-28T11:20:00Z")

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource(path)).readText(Charsets.UTF_8)

    private lateinit var locations: Locations
    private lateinit var forecasts: Forecasts
    private lateinit var alerts: Alerts
    private lateinit var germany: Germany

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        locations = Locations(hannover)
        forecasts = Forecasts(resource("open-meteo/hannover.json"))
        alerts = Alerts(resource("brightsky/alerts-hannover.json"))
        germany = Germany(inside = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model() = HomeViewModel(
        locations = locations,
        namer = Namer(),
        source = forecasts,
        cache = ForecastCache(File(folder.root, "cache.json")),
        clock = FixedClock(now),
        pollenRegions = germany,
        alerts = alerts,
    )

    private fun HomeViewModel.content(): HomeUiState.Content {
        val current = state.value
        assertTrue("ожидалось содержимое, а есть $current", current is HomeUiState.Content)
        return current as HomeUiState.Content
    }

    @Test
    fun alertsArriveWithTheForecast() {
        val model = model()
        model.onResumed(permissionGranted = true)

        assertEquals(1, alerts.calls)
        val set = model.content().alerts
        assertNotNull(set)
        assertEquals("Stadt Hannover", set!!.area)
        assertEquals(3, set.alerts.size)
        // Показ: самое серьёзное (гроза, уровень 3) первым.
        assertTrue(set.shownAt(now).first().id.endsWith("storm-1"))
    }

    @Test
    fun outsideGermanyNothingIsRequested() {
        germany.inside = false
        val model = model()
        model.onResumed(permissionGranted = true)

        assertEquals(0, alerts.calls)
        val set = model.content().alerts
        assertTrue("вне Германии пусто: ${set?.alerts}", set == null || set.alerts.isEmpty())
    }

    @Test
    fun failureDoesNotBreakTheForecast() {
        alerts.raw = null
        val model = model()
        model.onResumed(permissionGranted = true)

        val content = model.content()
        assertFalse(content.offline)
        assertNull(content.alerts)
    }

    @Test
    fun brokenAnswerDoesNotBreakTheForecast() {
        alerts.raw = "не json"
        val model = model()
        model.onResumed(permissionGranted = true)
        assertNull(model.content().alerts)
    }

    @Test
    fun lastAlertsStayWhenTheNextRequestFails() {
        val model = model()
        model.onResumed(permissionGranted = true)
        assertNotNull(model.content().alerts)

        alerts.raw = null
        model.refresh(permissionGranted = true)
        assertEquals(2, alerts.calls)
        assertEquals(3, model.content().alerts?.alerts?.size)
    }

    @Test
    fun withoutSourceThereAreNoAlerts() {
        val model = HomeViewModel(
            locations = locations,
            namer = Namer(),
            source = forecasts,
            cache = ForecastCache(File(folder.root, "cache.json")),
            clock = FixedClock(now),
            pollenRegions = germany,
        )
        model.onResumed(permissionGranted = true)
        assertNull(model.content().alerts)
    }
}
