package io.github.dumahuch.pogodaapp.ui.metric

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
import io.github.dumahuch.pogodaapp.forecast.Metric
import io.github.dumahuch.pogodaapp.forecast.MetricDays
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.forecast.PageTitle
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.fullDate
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Страница подробностей одного показателя «Главной» (влажность, ветер, осадки; решение Димы 20.11.2026, превью
 * https://claude.ai/artifact/JctK99dLV2y258xD3xHbSb). Открывается нажатием на показатель под температурой, закрывается
 * жестом «назад». Сверху карточка «Сейчас» (или выбранного дня) в виде плиток «Подробно», ниже лента по часам как на
 * «Главной», последним список дней как на «Прогнозе». Нажатие на день в списке показывает его в карточке и ленте.
 * С 0.8.83 в едином стиле страниц «Подробно» (макет https://claude.ai/artifact/KcpRCjaazEbEVi9YBNSPKP).
 */
@Composable
fun MetricScreen(
    state: HomeUiState.Content,
    metric: Metric,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forecast = state.forecast
    val zone = forecast.zone
    val now = remember(forecast) { Instant.now() }
    val today = remember(forecast, now) { now.atZone(zone).toLocalDate() }
    val dates = remember(forecast, today) { MetricDays.dates(forecast, today) }
    // День хранится числом (дни от 1970 года), как открытый день «Прогноза».
    var chosen by rememberSaveable(metric) { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(chosen).takeIf { it in dates } ?: today
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    ReportSkyScroll(scroll)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll),
    ) {
        val title = stringResource(
            when (metric) {
                Metric.Humidity -> R.string.tile_humidity
                Metric.Wind -> R.string.tile_wind_now
                Metric.Precipitation -> R.string.tile_precipitation
            },
        )
        PageTitle(title, "${dayName(date, dayNames)}, ${fullDate(date, locale)}", Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp))
        Spacer(Modifier.height(12.dp))
        val side = Modifier.padding(horizontal = 20.dp)
        if (forecast.hourDetails.isEmpty() || dates.isEmpty()) {
            StateCard(
                icon = LineIcons.Grid,
                title = stringResource(R.string.details_missing_title),
                text = stringResource(R.string.details_missing_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = onRetry,
                modifier = side,
            )
        } else {
            val ctx = PageContext(
                forecast, date, today, now, dates,
                onSelect = { picked ->
                    chosen = picked.toEpochDay()
                    scope.launch { scroll.animateScrollTo(0) }
                },
            )
            Column(side, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (metric) {
                    Metric.Wind -> WindPage(ctx)
                    Metric.Precipitation -> RainPage(ctx)
                    Metric.Humidity -> HumidityPage(ctx)
                }
            }
        }
        Spacer(Modifier.height(bottomContentPadding()))
    }
}
