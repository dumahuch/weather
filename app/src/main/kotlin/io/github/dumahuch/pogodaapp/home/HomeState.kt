package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.Place

/** Всё, что может показывать вкладка «Главная». */
sealed interface HomeUiState {

    /** Первый запуск или смена места: вместо чисел заглушки, пока не пришли данные. */
    data object Loading : HomeUiState

    /** Нет разрешения на местоположение: карточка «Разрешить». */
    data object NoPermission : HomeUiState

    /** Разрешение есть, но место определить не удалось (геолокация выключена или нет сигнала). */
    data object LocationUnavailable : HomeUiState

    /** Нет сети и нет сохранённых данных. */
    data object NoData : HomeUiState

    /**
     * Есть прогноз. [offline] означает, что обновить не вышло и показаны сохранённые данные;
     * [staleHours] сколько полных часов им, 0 если меньше часа.
     */
    data class Content(
        val place: Place,
        val forecast: Forecast,
        val strip: List<HourPoint>,
        val offline: Boolean,
        val staleHours: Int,
    ) : HomeUiState
}
