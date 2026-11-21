package io.github.dumahuch.pogodaapp.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.forecast.PageTitle
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.fullDate
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.PollenKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Страницы плиток «Подробно» (0.8.77 «Солнце и Луна», 0.8.80 остальные; макет https://claude.ai/artifact/LcjikZKRqfX7zLgcFrTUnJ). */
enum class DetailPage { SunMoon, Pressure, Uv, Air, Pollen }

/** Что нужно странице: состояние, сегодня, сейчас, выбранный день и его выбор (страница при этом прокручивается вверх). */
internal class PageState(
    val content: HomeUiState.Content,
    val today: LocalDate,
    val now: Instant,
    val date: LocalDate,
    val onSelect: (LocalDate) -> Unit,
) {
    val forecast: Forecast get() = content.forecast
    val zone: ZoneId get() = content.forecast.zone
    val isToday: Boolean get() = date == today
}

/** Страница давления, УФ, воздуха или пыльцы: заголовок с выбранным днём, карточки, пояснение шкал в конце. */
@Composable
internal fun DetailPageScreen(
    state: HomeUiState.Content,
    page: DetailPage,
    pollenKinds: Set<PollenKind>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = state.forecast.zone
    val now = remember(state.forecast) { Instant.now() }
    val today = remember(state.forecast, now) { now.atZone(zone).toLocalDate() }
    var chosen by rememberSaveable(page) { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(chosen)
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    ReportSkyScroll(scroll)
    val title = when (page) {
        DetailPage.SunMoon -> R.string.tile_sun_moon
        DetailPage.Pressure -> R.string.tile_pressure
        DetailPage.Uv -> R.string.tile_uv
        DetailPage.Air -> R.string.tile_air
        DetailPage.Pollen -> R.string.tile_pollen
    }
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll),
    ) {
        PageTitle(
            stringResource(title),
            "${dayName(date, dayNames)}, ${fullDate(date, locale)}",
            Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        val side = Modifier.padding(horizontal = 20.dp)
        if (page != DetailPage.Pollen && page != DetailPage.Air && state.forecast.hourDetails.isEmpty()) {
            StateCard(
                icon = LineIcons.Grid,
                title = stringResource(R.string.details_missing_title),
                text = stringResource(R.string.details_missing_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = onRetry,
                modifier = side,
            )
        } else {
            val ctx = PageState(state, today, now, date) { picked ->
                chosen = picked.toEpochDay()
                scope.launch { scroll.animateScrollTo(0) }
            }
            Column(side, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    DetailPage.Pressure -> PressurePage(ctx)
                    DetailPage.Uv -> UvPage(ctx)
                    DetailPage.Air -> AirPage(ctx)
                    DetailPage.Pollen -> PollenPage(ctx, pollenKinds)
                    DetailPage.SunMoon -> Unit
                }
            }
        }
        Spacer(Modifier.height(bottomContentPadding()))
    }
}
