package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.data.GeocodingParser
import io.github.dumahuch.pogodaapp.data.GeocodingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Что показывает строка поиска под полем ввода. */
sealed interface SearchResults {
    /** Запрос короче [PlacesLogic.MIN_QUERY_LENGTH]: показывается подсказка «введите название или индекс». */
    data object Idle : SearchResults

    /** Запрос ушёл, ответа ещё нет. */
    data object Loading : SearchResults

    data class Found(val items: List<PlaceSuggestion>) : SearchResults

    /** Ответ пришёл, подходящих мест нет. */
    data object Empty : SearchResults

    /** Нет связи или ответ неразборчив. */
    data object Failed : SearchResults
}

/**
 * Поиск города с подсказками: запрос уходит не на каждую букву, а после паузы [debounceMillis] без ввода, предыдущий
 * недоставленный запрос отменяется. Подсказки упорядочиваются по стране телефона ([homeCountry], может вернуть null).
 * [language] возвращает двухбуквенный код языка интерфейса.
 */
class PlaceSearchModel(
    private val scope: CoroutineScope,
    private val source: GeocodingSource?,
    private val language: () -> String,
    private val homeCountry: suspend () -> String?,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
) {
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private val mutableResults = MutableStateFlow<SearchResults>(SearchResults.Idle)
    val results: StateFlow<SearchResults> = mutableResults.asStateFlow()

    private var job: Job? = null

    /** Пользователь изменил текст в поле. */
    fun onQuery(text: String) {
        mutableQuery.value = text
        job?.cancel()
        val trimmed = text.trim()
        if (trimmed.length < PlacesLogic.MIN_QUERY_LENGTH) {
            mutableResults.value = SearchResults.Idle
            return
        }
        job = scope.launch {
            delay(debounceMillis)
            mutableResults.value = SearchResults.Loading
            mutableResults.value = try {
                val client = source ?: throw IllegalStateException("поиск не подключён")
                val found = GeocodingParser.parse(client.searchRaw(trimmed, language()))
                if (found.isEmpty()) SearchResults.Empty else SearchResults.Found(PlacesLogic.rank(found, homeCountryOrNull()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchResults.Failed
            }
        }
    }

    /** Закрыть поиск: поле и подсказки очищаются. */
    fun clear() {
        job?.cancel()
        mutableQuery.value = ""
        mutableResults.value = SearchResults.Idle
    }

    /** Служба адресов может не ответить: тогда подсказки остаются в порядке службы поиска. */
    private suspend fun homeCountryOrNull(): String? = try {
        homeCountry()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Пауза после последнего знака, мс: быстрый набор не засыпает службу запросами. */
        const val DEBOUNCE_MILLIS = 350L
    }
}
