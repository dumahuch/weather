package io.github.dumahuch.pogodaapp.ui.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.RangeSpan
import io.github.dumahuch.pogodaapp.forecast.TemperatureRange
import io.github.dumahuch.pogodaapp.forecast.WeekSummaries
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.isStormWet
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.DayRowsSkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.NoPermissionTop
import io.github.dumahuch.pogodaapp.ui.home.SmallWeatherGlyph
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.home.StateTop
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.Forecast
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Дней в первой неделе: перед восьмым днём в списке на 14 дней стоит разделитель «Следующая неделя». */
private const val WEEK_DAYS = 7

/**
 * Вкладка «Прогноз»: список дней с переключателем «7 дней» и «14 дней» и карточка осадков на ближайшие сутки.
 * Нажатие на день открывает его подробности ([DayDetailScreen]). Макет: страница «Осадки» в живом макете.
 * Если места или данных нет, показывается та же карточка состояния, что на «Главной».
 */
@Composable
fun ForecastScreen(
    state: HomeUiState,
    permanentlyDenied: Boolean,
    dayCount: Int,
    actions: HomeActions,
    onDayCount: (Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val scroll = rememberScrollState()
    ReportSkyScroll(scroll)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll),
    ) {
        val today = (state as? HomeUiState.Content)?.let { Instant.now().atZone(it.forecast.zone).toLocalDate() }
        val dateLine = today?.let { "${dayName(it, dayNames)}, ${fullDate(it, locale)}" }
        PageTitle(stringResource(R.string.tab_forecast), dateLine, Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp))
        Spacer(Modifier.height(12.dp))
        when (state) {
            HomeUiState.Loading -> Box(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(RoundedCornerShape(24.dp), LocalGlass.current),
            ) { DayRowsSkeleton(rows = 7) }

            is HomeUiState.Content -> {
                val todayDate = checkNotNull(today)
                val upcoming = upcomingDays(state.forecast, todayDate)
                if (upcoming.isEmpty()) {
                    StateCard(
                        icon = LineIcons.Calendar,
                        title = stringResource(R.string.forecast_missing_title),
                        text = stringResource(R.string.forecast_missing_text),
                        buttonLabel = stringResource(R.string.retry),
                        onButton = actions.onRetry,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeekSummaries.of(upcoming)?.let { WeekSummaryCard(it, todayDate) }
                        DaysCard(days = upcoming, dayCount = dayCount, onDayCount = onDayCount, onOpenDay = onOpenDay)
                    }
                }
            }

            HomeUiState.NoPermission -> NoPermissionTop(permanentlyDenied, actions)
            HomeUiState.LocationUnavailable -> StateTop(
                icon = LineIcons.NavigationOff,
                title = stringResource(R.string.locfail_title),
                text = stringResource(R.string.locfail_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = actions.onRetry,
                secondaryLabel = stringResource(R.string.places_find),
                onSecondary = actions.onFindCity,
            )
            HomeUiState.NoData -> StateTop(
                icon = LineIcons.CloudOff,
                title = stringResource(R.string.nodata_title),
                text = stringResource(R.string.nodata_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = actions.onRetry,
            )
        }
        Spacer(Modifier.height(bottomContentPadding()))
    }
}

/**
 * Дни от сегодняшнего: прогноз из памяти может быть старым, и его вчерашние дни показывать незачем.
 * Пусто, если прогноза по дням нет совсем (сохранён версией до 0.3.0) или он устарел больше чем на две недели.
 */
internal fun upcomingDays(forecast: Forecast, today: LocalDate): List<DayForecast> =
    forecast.days.dropWhile { it.date.isBefore(today) }

/** Крупный заголовок страницы и справа от него дата. */
@Composable
internal fun PageTitle(title: String, date: String?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = pogodaText(34f, 300, lineHeight = 1.1f, letterSpacing = (-0.68f).sp))
        if (date != null) {
            Text(
                date,
                style = pogodaText(13f, 500, color = palette.ink2),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** Карточка со списком дней. [dayCount] равен 7 или 14. */
@Composable
private fun DaysCard(
    days: List<DayForecast>,
    dayCount: Int,
    onDayCount: (Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val shown = remember(days, dayCount) { days.take(dayCount) }
    val spans = remember(shown) { TemperatureRange.layout(shown) }
    val today = remember(days) { days.first().date }
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            DayCountSwitch(dayCount, onDayCount)
        }
        shown.forEachIndexed { index, day ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
            if (index == WEEK_DAYS) NextWeekDivider()
            DayRow(
                day = day,
                isToday = day.date == today,
                span = spans[index],
                onClick = { onOpenDay(day.date) },
            )
        }
    }
}

/** Разделитель недель в списке на 14 дней: точка и подпись, как «пояснение» стиля (11,5 sp, 600, ink2). */
@Composable
private fun NextWeekDivider() {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.ink2.copy(alpha = 0.6f)))
        Text(stringResource(R.string.forecast_next_week), style = pogodaText(11.5f, 600, color = palette.ink2))
    }
}

/** Переключатель «7 дней» / «14 дней». */
@Composable
private fun DayCountSwitch(dayCount: Int, onDayCount: (Int) -> Unit) {
    val palette = LocalPogodaPalette.current
    val options = listOf(7 to R.string.forecast_7_days, 14 to R.string.forecast_14_days)
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassLine)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (count, label) ->
            val selected = count == dayCount
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .widthIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .then(if (selected) Modifier.background(palette.button2) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onDayCount(count) })
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = pogodaText(13f, 600, color = if (selected) palette.ink else palette.ink2),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Строка дня: название и дата, значок, вероятность и количество осадков, наименьшая температура,
 * полоска температур и наибольшая температура. Одинаковая на 7 и на 14 днях (Дима 26.11.2026: сжатые строки
 * 14 дней были нечитаемы, хотя места хватает).
 */
@Composable
private fun DayRow(
    day: DayForecast,
    isToday: Boolean,
    span: RangeSpan,
    onClick: () -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val name = if (isToday) stringResource(R.string.today) else dayName(day.date, dayNames)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column(Modifier.width(DayColumnWidth)) {
            Text(name, style = pogodaText(15f, 700, lineHeight = 1.15f), maxLines = 1, softWrap = false)
            Text(
                shortDate(day.date, locale),
                style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.15f),
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            SmallWeatherGlyph(day.condition, isDay = true, wet = isStormWet(day.precipitationMm), size = 24.dp)
        }
        PrecipitationCell(day, locale, Modifier.width(PrecipitationColumnWidth))
        Text(
            temperatureText(day.min),
            style = pogodaText(15f, 500, color = palette.ink2, tabular = true),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(TemperatureColumnWidth),
        )
        RangeBar(span, Modifier.weight(1f))
        Text(
            temperatureText(day.max),
            style = pogodaText(15f, 700, tabular = true),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(TemperatureColumnWidth),
        )
    }
}

private val DayColumnWidth = 62.dp
private val PrecipitationColumnWidth = 66.dp
private val TemperatureColumnWidth = 36.dp

/** Вероятность осадков (жирным) и их количество в миллиметрах. */
@Composable
private fun PrecipitationCell(day: DayForecast, locale: Locale, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val probability = day.precipitationProbability?.let { percentText(it) } ?: "–"
    val amount = dayPrecipitationText(day.precipitationMm, locale)
    val unit = stringResource(R.string.mm_unit)
    Column(modifier) {
        Text(probability, style = pogodaText(13f, 700, lineHeight = 1.15f, tabular = true), maxLines = 1, softWrap = false)
        Text(
            if (amount.isEmpty()) "–" else "$amount $unit",
            style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.15f, tabular = true),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Полоска температур дня на общей шкале: от синего (холодно) к оранжевому (тепло). */
@Composable
private fun RangeBar(span: RangeSpan, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.height(5.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(palette.glassLine, size = size, cornerRadius = radius)
        val start = span.start * size.width
        val width = span.width * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(palette.accent, palette.warning),
                startX = start,
                endX = start + width,
            ),
            topLeft = Offset(start, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}
