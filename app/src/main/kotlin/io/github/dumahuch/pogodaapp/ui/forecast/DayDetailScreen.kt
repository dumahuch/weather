package io.github.dumahuch.pogodaapp.ui.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.isStormWet
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.home.HourlyFrame
import io.github.dumahuch.pogodaapp.ui.home.HourlyStrip
import io.github.dumahuch.pogodaapp.ui.home.SmallWeatherGlyph
import io.github.dumahuch.pogodaapp.ui.home.conditionName
import io.github.dumahuch.pogodaapp.ui.pages.SourceNote
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import java.time.Instant
import java.time.LocalDate

/**
 * Подробности одного дня: сводка и фраза, почасовой прогноз этого дня, плитки осадков, ветра, давления, влажности и облачности.
 * Открывается нажатием на день во вкладке «Прогноз», системный жест «назад» возвращает к списку.
 * Макета у этой страницы не было: решено только, что в ней есть (документ решений, раздел «Продукт»); вид выбран по образцу
 * плиток вкладки «Подробно» и карточек «Главной».
 */
@Composable
fun DayDetailScreen(
    state: HomeUiState.Content,
    date: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = state.forecast.zone
    val day = remember(state.forecast, date) { state.forecast.days.firstOrNull { it.date == date } }
    // Дня нет в прогнозе (например, прогноз обновился и список сдвинулся): возвращаемся к списку.
    if (day == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val now = remember { Instant.now() }
    val isToday = date == now.atZone(zone).toLocalDate()
    val hours = remember(state.forecast, date) { state.forecast.hoursOf(date) }
    // Сегодняшняя лента сразу прокручена к текущему часу, но предыдущий час остаётся виден слева.
    val firstColumn = if (isToday) (now.atZone(zone).hour - 1).coerceAtLeast(0) else 0

    val scroll = rememberScrollState()
    ReportSkyScroll(scroll)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll),
    ) {
        val title = if (isToday) stringResource(R.string.today) else dayName(date, dayNames)
        Text(
            "$title, ${fullDate(date, locale)}",
            style = pogodaText(20f, 600, lineHeight = 1.2f),
            maxLines = 1,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
        )

        Row(
            Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SmallWeatherGlyph(day.condition, isDay = true, wet = isStormWet(day.precipitationMm), size = 64.dp)
            Column {
                Text(conditionName(day.condition), style = pogodaText(21f, 600, lineHeight = 1.2f))
                Text(
                    "${stringResource(R.string.temp_high)} ${temperatureText(day.max)} · " +
                        "${stringResource(R.string.temp_low)} ${temperatureText(day.min)}",
                    style = pogodaText(14f, 500, color = palette.ink2, lineHeight = 1.4f),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Text(
            dayPhrase(state.forecast, day),
            style = pogodaText(15f, 500, lineHeight = 1.4f),
            modifier = Modifier.padding(start = 24.dp, top = 14.dp, end = 24.dp),
        )

        Spacer(Modifier.height(18.dp))
        if (hours.isNotEmpty()) {
            HourlyFrame(
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                HourlyStrip(
                    points = hours,
                    zone = zone,
                    markNow = false,
                    dayBreaks = false,
                    initialColumn = firstColumn,
                    details = state.forecast.hourDetails,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        DayTiles(state.forecast, day, Modifier.padding(horizontal = 20.dp))
        SourceNote(stringResource(R.string.day_note), Modifier.padding(start = 26.dp, top = 10.dp, end = 26.dp))
        Spacer(Modifier.height(bottomContentPadding()))
    }
}

internal fun uvLevelLabel(level: UvLevel): Int = when (level) {
    UvLevel.Low -> R.string.uv_low
    UvLevel.Moderate -> R.string.uv_moderate
    UvLevel.High -> R.string.uv_high
    UvLevel.VeryHigh -> R.string.uv_very_high
    UvLevel.Extreme -> R.string.uv_extreme
}
