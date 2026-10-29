package io.github.dumahuch.pogodaapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.ui.components.AppTab
import io.github.dumahuch.pogodaapp.ui.components.BottomBar
import io.github.dumahuch.pogodaapp.ui.forecast.DayDetailScreen
import io.github.dumahuch.pogodaapp.ui.forecast.ForecastScreen
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.HomeScreen
import java.time.LocalDate

/** Сколько суток показывает список «Прогноза» сразу после запуска. */
private const val DEFAULT_DAY_COUNT = 7

/**
 * Приложение целиком: страница выбранной вкладки и внизу окна панель вкладок с строкой источника данных.
 * Небо рисует хозяин неба под этим экраном. Выбранная вкладка, открытый день и число дней в списке
 * переживают поворот и пересоздание окна. Кнопка «назад»: из подробностей дня в список, с другой вкладки на «Главную».
 */
@Composable
fun PogodaApp(
    state: HomeUiState,
    refreshing: Boolean,
    permanentlyDenied: Boolean,
    versionLabel: String,
    actions: HomeActions,
    onVersionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // День хранится числом (дни от 1970 года): дата сама по себе в сохранённое состояние не кладётся.
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var dayCount by rememberSaveable { mutableIntStateOf(DEFAULT_DAY_COUNT) }

    BackHandler(enabled = openDay != null) { openDay = null }
    BackHandler(enabled = openDay == null && tab != AppTab.Home) { tab = AppTab.Home }

    Box(modifier.fillMaxSize()) {
        when (tab) {
            AppTab.Home -> HomeScreen(
                state = state,
                refreshing = refreshing,
                permanentlyDenied = permanentlyDenied,
                actions = actions,
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
                        actions = actions,
                        onDayCount = { dayCount = it },
                        onOpenDay = { openDay = it.toEpochDay() },
                    )
                }
            }

            // Эти вкладки ещё не сделаны: кнопки в панели недоступны, сюда попасть нельзя.
            AppTab.Details, AppTab.Map -> Unit
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
