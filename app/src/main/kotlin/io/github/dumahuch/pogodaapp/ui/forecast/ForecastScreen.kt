package io.github.dumahuch.pogodaapp.ui.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.PrecipitationOutlook
import io.github.dumahuch.pogodaapp.forecast.PrecipitationOutlooks
import io.github.dumahuch.pogodaapp.forecast.RangeSpan
import io.github.dumahuch.pogodaapp.forecast.TemperatureRange
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.DayRowsSkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.components.iconFor
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.NoPermissionTop
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.home.StateTop
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Строк в списке по дням и их плотность: 7 дней просторно, 14 дней плотно. */
private const val COMPACT_DAYS = 14

/** С какого дня (счёт с нуля) прогноз считается менее надёжным: строки приглушаются. */
private const val FAR_FROM_DAY = 10

/** Столько часов показывает карточка осадков. */
private const val OUTLOOK_HOURS = 24

private val NoteMaxWidth = 150.dp

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
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
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

            is HomeUiState.Content -> if (upcomingDays(state.forecast, checkNotNull(today)).isEmpty()) {
                StateCard(
                    icon = LineIcons.Calendar,
                    title = stringResource(R.string.forecast_missing_title),
                    text = stringResource(R.string.forecast_missing_text),
                    buttonLabel = stringResource(R.string.retry),
                    onButton = actions.onRetry,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                DaysCard(
                    days = upcomingDays(state.forecast, checkNotNull(today)),
                    dayCount = dayCount,
                    onDayCount = onDayCount,
                    onOpenDay = onOpenDay,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlookCard(
                    points = state.strip.take(OUTLOOK_HOURS),
                    zone = state.forecast.zone,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            HomeUiState.NoPermission -> NoPermissionTop(permanentlyDenied, actions)
            HomeUiState.LocationUnavailable -> StateTop(
                icon = LineIcons.NavigationOff,
                title = stringResource(R.string.locfail_title),
                text = stringResource(R.string.locfail_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = actions.onRetry,
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
    val compact = dayCount == COMPACT_DAYS
    val shown = remember(days, dayCount) { days.take(dayCount) }
    val spans = remember(shown) { TemperatureRange.layout(shown) }
    val today = remember(days) { days.first().date }
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayCountSwitch(dayCount, onDayCount)
            if (compact) {
                Text(
                    stringResource(R.string.forecast_far_note),
                    style = pogodaText(11.5f, 500, color = palette.ink2, lineHeight = 1.25f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .widthIn(max = NoteMaxWidth),
                )
            }
        }
        shown.forEachIndexed { index, day ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
            DayRow(
                day = day,
                isToday = day.date == today,
                compact = compact,
                span = spans[index],
                far = compact && index >= FAR_FROM_DAY,
                onClick = { onOpenDay(day.date) },
            )
        }
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
 * полоска температур и наибольшая температура.
 */
@Composable
private fun DayRow(
    day: DayForecast,
    isToday: Boolean,
    compact: Boolean,
    span: RangeSpan,
    far: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val name = if (isToday) stringResource(R.string.today) else dayName(day.date, dayNames)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 28.dp else 50.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (compact) {
            Row(Modifier.width(DayColumnWidth), verticalAlignment = Alignment.Bottom) {
                Text(name, style = pogodaText(13f, 700), maxLines = 1, softWrap = false)
                if (!isToday) {
                    Text(
                        " ${day.date.dayOfMonth}",
                        style = pogodaText(11f, 500, color = palette.ink2),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        } else {
            Column(Modifier.width(DayColumnWidth)) {
                Text(name, style = pogodaText(15f, 700, lineHeight = 1.15f), maxLines = 1, softWrap = false)
                Text(
                    shortDate(day.date, locale),
                    style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.15f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            LineIconView(iconFor(day.condition, isDay = true), if (compact) 19.dp else 24.dp, palette.ink)
        }
        PrecipitationCell(day, compact, locale, Modifier.width(PrecipitationColumnWidth))
        Text(
            temperatureText(day.min),
            style = pogodaText(if (compact) 13f else 15f, 500, color = palette.ink2, tabular = true),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(TemperatureColumnWidth),
        )
        RangeBar(span, far, Modifier.weight(1f))
        Text(
            temperatureText(day.max),
            style = pogodaText(if (compact) 13f else 15f, 700, tabular = true),
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

/** Вероятность осадков (жирным) и их количество в миллиметрах; в плотном виде одной строкой. */
@Composable
private fun PrecipitationCell(day: DayForecast, compact: Boolean, locale: Locale, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val probability = day.precipitationProbability?.let { percentText(it) } ?: "–"
    val amount = dayPrecipitationText(day.precipitationMm, locale)
    val unit = stringResource(R.string.mm_unit)
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(probability, style = pogodaText(12f, 700, tabular = true), maxLines = 1, softWrap = false)
            if (amount.isNotEmpty()) {
                Text(
                    " $amount",
                    style = pogodaText(11f, 500, color = palette.ink2, tabular = true),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    } else {
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
}

/** Полоска температур дня на общей шкале: от синего (холодно) к оранжевому (тепло); у дальних дней бледнее. */
@Composable
private fun RangeBar(span: RangeSpan, far: Boolean, modifier: Modifier = Modifier) {
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
            alpha = if (far) 0.55f else 1f,
        )
    }
}

/** Карточка «Ближайшие 24 часа»: итог словами, столбики осадков по часам и подписи времени. */
@Composable
private fun OutlookCard(points: List<HourPoint>, zone: ZoneId, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val outlook = remember(points) { PrecipitationOutlooks.of(points) }
    val total = remember(points) { PrecipitationOutlooks.totalMm(points) }
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.next24_title), style = pogodaText(13f, 700))
            val totalText = dayPrecipitationText(total, locale)
            Text(
                stringResource(R.string.next24_total, totalText.ifEmpty { "0" }),
                style = pogodaText(12f, 400, color = palette.ink2, tabular = true),
            )
        }
        Text(outlookText(outlook, zone), style = pogodaText(15f, 600, lineHeight = 1.3f))
        HourBars(points, Modifier.padding(top = 4.dp))
        HourAxis(points, zone)
    }
}

@Composable
private fun outlookText(outlook: PrecipitationOutlook, zone: ZoneId): String = when (outlook) {
    PrecipitationOutlook.None -> stringResource(R.string.next24_none)
    is PrecipitationOutlook.Ongoing -> {
        val kind = kindName(outlook.kind)
        if (outlook.until == null) {
            stringResource(R.string.outlook_all_day, kind)
        } else {
            stringResource(R.string.outlook_until, kind, clockText(outlook.until, zone))
        }
    }
    is PrecipitationOutlook.Starts -> stringResource(R.string.outlook_from, kindName(outlook.kind), clockText(outlook.at, zone))
}

@Composable
private fun kindName(kind: PrecipitationKind): String = stringResource(
    when (kind) {
        PrecipitationKind.Generic -> R.string.kind_generic
        PrecipitationKind.Rain -> R.string.kind_rain
        PrecipitationKind.Storm -> R.string.kind_storm
        PrecipitationKind.Snow -> R.string.kind_snow
        PrecipitationKind.Drizzle -> R.string.kind_drizzle
    },
)

private val BarsHeight = 58.dp

/** Подписи часов под столбиками: четыре, через шесть часов; пятое место пустое. */
private const val AXIS_LABELS = 4
private const val AXIS_STEP_HOURS = 6

/** Столбики осадков по часам; первый (текущий час) обведён. Высота от наибольшего значения, но не меньше шкалы в 1 мм. */
@Composable
private fun HourBars(points: List<HourPoint>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val scale = maxOf(1.0, points.maxOfOrNull { it.precipitationMm } ?: 0.0)
    Row(
        modifier
            .fillMaxWidth()
            .height(BarsHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEachIndexed { index, point ->
            val wet = point.precipitationMm > 0.0
            val fraction = if (wet) maxOf(0.06, point.precipitationMm / scale) else 0.03
            val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height((BarsHeight.value * fraction).dp.coerceAtLeast(2.dp))
                    .then(if (index == 0) Modifier.border(1.5.dp, palette.ink, shape) else Modifier)
                    .background(if (wet) palette.bar else palette.bar.copy(alpha = 0.28f), shape),
            )
        }
    }
}

/** Подписи часов под столбиками: начало, 6, 12 и 18 часов от текущего; пятая пустая, чтобы подписи легли на четверти ширины. */
@Composable
private fun HourAxis(points: List<HourPoint>, zone: ZoneId) {
    val palette = LocalPogodaPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (step in 0..AXIS_LABELS) {
            val point = if (step < AXIS_LABELS) points.getOrNull(step * AXIS_STEP_HOURS) else null
            val label = point?.let { "%02d".format(Locale.ROOT, it.time.atZone(zone).hour) }.orEmpty()
            Text(label, style = pogodaText(11f, 500, color = palette.ink2, tabular = true))
        }
    }
}
