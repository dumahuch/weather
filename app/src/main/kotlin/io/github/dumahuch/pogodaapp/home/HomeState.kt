package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import io.github.dumahuch.pogodaapp.weather.Place
import io.github.dumahuch.pogodaapp.weather.PollenForecast

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
     * [staleHours] сколько полных часов им, 0 если меньше часа. [airQuality] приходит отдельным запросом позже прогноза
     * (и совсем не приходит без сети или за пределами Европы); пусто, пока его нет. [pollen] приходит так же отдельно:
     * индекс пыльцы DWD для района места (только Германия), пусто вне Германии, без сети и пока он не пришёл.
     */
    data class Content(
        val place: Place,
        val forecast: Forecast,
        val strip: List<HourPoint>,
        val offline: Boolean,
        val staleHours: Int,
        val airQuality: AirQuality? = null,
        val pollen: PollenForecast? = null,
    ) : HomeUiState
}
