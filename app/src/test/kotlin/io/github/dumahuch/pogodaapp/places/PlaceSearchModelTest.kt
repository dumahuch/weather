package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.data.GeocodingSource
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchModelTest {

    private class FakeGeocoding(var raw: String? = ANSWER) : GeocodingSource {
        val queries = mutableListOf<String>()
        val languages = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun searchRaw(query: String, language: String): String {
            queries += query
            languages += language
            gate?.await()
            return raw ?: throw ForecastException("нет сети")
        }
    }

    private companion object {
        const val ANSWER = """{"results":[
            {"name":"Hanover","latitude":43.7,"longitude":-72.3,"country_code":"US","country":"United States"},
            {"name":"Hannover","latitude":52.37,"longitude":9.73,"country_code":"DE","country":"Deutschland"}]}"""
    }

    @Test
    fun startsIdle() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding(), { "de" }, { "DE" })
        assertEquals(SearchResults.Idle, model.results.value)
        assertEquals("", model.query.value)
    }

    @Test
    fun oneLetterSendsNothing() = runTest {
        val source = FakeGeocoding()
        val model = PlaceSearchModel(backgroundScope, source, { "de" }, { "DE" })
        model.onQuery("H")
        advanceUntilIdle()
        assertEquals(SearchResults.Idle, model.results.value)
        assertTrue(source.queries.isEmpty())
        assertEquals("H", model.query.value)
    }

    @Test
    fun fastTypingSendsOnlyTheLastQuery() = runTest {
        val source = FakeGeocoding()
        val model = PlaceSearchModel(backgroundScope, source, { "de" }, { "DE" })
        model.onQuery("Han")
        advanceTimeBy(200)
        model.onQuery("Hann")
        advanceTimeBy(200)
        assertTrue(source.queries.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("Hann"), source.queries)
    }

    @Test
    fun queryIsTrimmedAndLanguageIsPassed() = runTest {
        val source = FakeGeocoding()
        val model = PlaceSearchModel(backgroundScope, source, { "ru" }, { "DE" })
        model.onQuery("  Ханновер ")
        advanceUntilIdle()
        assertEquals(listOf("Ханновер"), source.queries)
        assertEquals(listOf("ru"), source.languages)
    }

    @Test
    fun loadingIsShownWhileWaitingAndThenTheRankedAnswer() = runTest {
        val source = FakeGeocoding().apply { gate = CompletableDeferred() }
        val model = PlaceSearchModel(backgroundScope, source, { "de" }, { "DE" })
        model.onQuery("Han")
        advanceTimeBy(PlaceSearchModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertEquals(SearchResults.Loading, model.results.value)

        source.gate?.complete(Unit)
        advanceUntilIdle()
        val found = model.results.value as SearchResults.Found
        // страна телефона (DE) впереди, хотя служба вернула Hanover (US) первым
        assertEquals(listOf("Hannover", "Hanover"), found.items.map { it.name })
    }

    @Test
    fun nothingFoundIsEmpty() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding("""{"generationtime_ms":0.1}"""), { "de" }, { null })
        model.onQuery("Xyzzy")
        advanceUntilIdle()
        assertEquals(SearchResults.Empty, model.results.value)
    }

    @Test
    fun noNetworkIsFailed() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding(null), { "de" }, { null })
        model.onQuery("Han")
        advanceUntilIdle()
        assertEquals(SearchResults.Failed, model.results.value)
    }

    @Test
    fun unreadableAnswerIsFailed() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding("<html>"), { "de" }, { null })
        model.onQuery("Han")
        advanceUntilIdle()
        assertEquals(SearchResults.Failed, model.results.value)
    }

    @Test
    fun withoutSourceItIsFailed() = runTest {
        val model = PlaceSearchModel(backgroundScope, null, { "de" }, { null })
        model.onQuery("Han")
        advanceUntilIdle()
        assertEquals(SearchResults.Failed, model.results.value)
    }

    @Test
    fun silentAddressServiceKeepsTheServiceOrder() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding(), { "de" }, { throw IllegalStateException("служба молчит") })
        model.onQuery("Han")
        advanceUntilIdle()
        val found = model.results.value as SearchResults.Found
        assertEquals(listOf("Hanover", "Hannover"), found.items.map { it.name })
    }

    @Test
    fun clearResetsFieldAndResultsAndCancelsThePendingRequest() = runTest {
        val source = FakeGeocoding()
        val model = PlaceSearchModel(backgroundScope, source, { "de" }, { "DE" })
        model.onQuery("Han")
        model.clear()
        advanceUntilIdle()
        assertEquals("", model.query.value)
        assertEquals(SearchResults.Idle, model.results.value)
        assertTrue(source.queries.isEmpty())
    }

    @Test
    fun shorteningTheQueryBackToOneLetterDropsOldResults() = runTest {
        val model = PlaceSearchModel(backgroundScope, FakeGeocoding(), { "de" }, { "DE" })
        model.onQuery("Han")
        advanceUntilIdle()
        assertTrue(model.results.value is SearchResults.Found)
        model.onQuery("H")
        assertEquals(SearchResults.Idle, model.results.value)
    }
}
