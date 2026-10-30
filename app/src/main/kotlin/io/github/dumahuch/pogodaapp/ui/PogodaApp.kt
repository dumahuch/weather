package io.github.dumahuch.pogodaapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.ui.components.AppTab
import io.github.dumahuch.pogodaapp.ui.components.BottomBar
import io.github.dumahuch.pogodaapp.ui.details.DetailsScreen
import io.github.dumahuch.pogodaapp.ui.forecast.DayDetailScreen
import io.github.dumahuch.pogodaapp.ui.forecast.ForecastScreen
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.HomeScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesActions
import io.github.dumahuch.pogodaapp.ui.places.PlacesScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesUiState
import java.time.LocalDate

/** Сколько суток показывает список «Прогноза» сразу после запуска. */
private const val DEFAULT_DAY_COUNT = 7

/**
 * Приложение целиком: страница выбранной вкладки и внизу окна панель вкладок с строкой источника данных.
 * Небо рисует хозяин неба под этим экраном. Выбранная вкладка, открытый день и число дней в списке
 * переживают поворот и пересоздание окна. Кнопка «назад»: из поиска города в список мест, из списка мест на вкладку,
 * из подробностей дня в список, с другой вкладки на «Главную».
 *
 * Экран мест ([PlacesScreen]) открывается плашкой места на «Главной» и занимает окно целиком (панель вкладок скрыта).
 * Кнопка «Найти город» на карточках без места открывает его сразу в режиме поиска.
 */
@Composable
fun PogodaApp(
    state: HomeUiState,
    refreshing: Boolean,
    permanentlyDenied: Boolean,
    versionLabel: String,
    actions: HomeActions,
    places: PlacesUiState,
    placesActions: PlacesActions,
    onVersionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // День хранится числом (дни от 1970 года): дата сама по себе в сохранённое состояние не кладётся.
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var dayCount by rememberSaveable { mutableIntStateOf(DEFAULT_DAY_COUNT) }

    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }

    fun closePlaces() {
        placesOpen = false
        searching = false
        placesActions.onLeave()
    }

    BackHandler(enabled = placesOpen && searching) {
        searching = false
        placesActions.onLeave()
    }
    BackHandler(enabled = placesOpen && !searching) { closePlaces() }
    BackHandler(enabled = !placesOpen && openDay != null) { openDay = null }
    BackHandler(enabled = !placesOpen && openDay == null && tab != AppTab.Home) { tab = AppTab.Home }

    // Название выбранного не текущего места (закреплённого или открытого для просмотра) и признак просмотра.
    val chosenName = when (val choice = places.selection) {
        PlaceChoice.Current -> null
        is PlaceChoice.Pinned -> places.pinned.firstOrNull { it.id == choice.id }?.name
        is PlaceChoice.Preview -> choice.place.name
    }
    val previewing = places.selection is PlaceChoice.Preview
    val listFull = places.pinned.size >= PlacesLogic.MAX_PINNED
    val linked = remember(actions, listFull) {
        actions.copy(
            onOpenPlaces = { placesOpen = true },
            // Список полон: «Закрепить» ведёт в список, где видно сообщение и можно открепить одно из мест.
            onPinPreview = {
                if (listFull) {
                    placesOpen = true
                } else {
                    placesActions.onPinPreview()
                }
            },
            onFindCity = {
                placesOpen = true
                searching = true
            },
        )
    }

    if (placesOpen) {
        PlacesScreen(
            ui = places,
            actions = placesActions,
            searching = searching,
            onSearching = {
                searching = it
                if (!it) placesActions.onLeave()
            },
            onClose = ::closePlaces,
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize()) {
        when (tab) {
            AppTab.Home -> HomeScreen(
                state = state,
                refreshing = refreshing,
                permanentlyDenied = permanentlyDenied,
                actions = linked,
                pinnedName = chosenName,
                preview = previewing,
            )

            AppTab.Forecast -> {
                val day = openDay
                if (day != null && state is HomeUiState.Content) {
                    DayDetailScreen(
                        state = state,
                        date = LocalDate.ofEpochDay(day),
                        onBack = { openDay = null },
                    )
                } else {
                    ForecastScreen(
                        state = state,
                        permanentlyDenied = permanentlyDenied,
                        dayCount = dayCount,
                        actions = linked,
                        onDayCount = { dayCount = it },
                        onOpenDay = { openDay = it.toEpochDay() },
                    )
                }
            }

            AppTab.Details -> DetailsScreen(
                state = state,
                permanentlyDenied = permanentlyDenied,
                actions = linked,
            )

            // Эта вкладка ещё не сделана: кнопка в панели недоступна, сюда попасть нельзя.
            AppTab.Map -> Unit
        }
        BottomBar(
            selected = tab,
            onSelect = {
                tab = it
                openDay = null
            },
            versionLabel = versionLabel,
            showAttribution = state is HomeUiState.Content,
            onVersionTap = onVersionTap,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
