package io.github.dumahuch.pogodaapp.ui.forecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.forecast.daylight
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.glassButton
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.components.iconFor
import io.github.dumahuch.pogodaapp.ui.home.HourlyFrame
import io.github.dumahuch.pogodaapp.ui.home.HourlyStrip
import io.github.dumahuch.pogodaapp.ui.home.conditionName
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.DayForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Подробности одного дня: сводка, почасовой прогноз этого дня, осадки, ветер, УФ-индекс, восход и закат.
 * Открывается нажатием на день во вкладке «Прогноз», кнопка «назад» (и системный жест) возвращает к списку.
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

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackButton(onBack)
            val title = if (isToday) stringResource(R.string.today) else dayName(date, dayNames)
            Text(
                "$title, ${fullDate(date, locale)}",
                style = pogodaText(20f, 600, lineHeight = 1.2f),
                maxLines = 1,
            )
        }

        Row(
            Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LineIconView(iconFor(day.condition, isDay = true), 64.dp, palette.ink, strokeWidth = 1.1f)
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

        Spacer(Modifier.height(18.dp))
        if (hours.isNotEmpty()) {
            HourlyFrame(
                caption = stringResource(R.string.hourly_caption),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                HourlyStrip(
                    points = hours,
                    zone = zone,
                    markNow = false,
                    dayBreaks = false,
                    initialColumn = firstColumn,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        DayTiles(day, zone, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(bottomContentPadding()))
    }
}

/** Круглая кнопка «назад» из материала кнопок. */
@Composable
internal fun BackButton(onClick: () -> Unit) {
    val palette = LocalPogodaPalette.current
    val label = stringResource(R.string.back)
    Box(
        Modifier
            .size(44.dp)
            .glassButton(CircleShape, LocalGlass.current)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LineIconView(LineIcons.ChevronLeft, 22.dp, palette.ink)
    }
}

/** Плитки дня парами: осадки и ветер, УФ-индекс и световой день. */
@Composable
private fun DayTiles(day: DayForecast, zone: ZoneId, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val unit = stringResource(R.string.mm_unit)
    val kmh = stringResource(R.string.kmh_unit)
    val amount = dayPrecipitationText(day.precipitationMm, locale)
    val uv = day.uvMax.roundToInt()
    val daylight = daylight(day)
    val dash = "–"
    // Восход и закат одной строкой: «07:11 – 16:49»; без одного из них (полярный день или ночь) подписи нет.
    val sunSub = if (day.sunrise != null && day.sunset != null) {
        "${clockText(day.sunrise, zone)} – ${clockText(day.sunset, zone)}"
    } else {
        null
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTile(
                title = stringResource(R.string.tile_precipitation),
                value = "${amount.ifEmpty { "0" }} $unit",
                sub = day.precipitationProbability?.let { stringResource(R.string.precip_chance, percentText(it)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            DayTile(
                title = stringResource(R.string.tile_wind),
                value = "${day.windMaxKmh.roundToInt()} $kmh",
                sub = stringResource(R.string.gusts_up_to, day.gustMaxKmh.roundToInt(), kmh),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTile(
                title = stringResource(R.string.tile_uv),
                value = uv.toString(),
                sub = stringResource(uvLevelLabel(UvLevel.of(uv))),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            DayTile(
                title = stringResource(R.string.tile_daylight),
                value = daylight?.let {
                    val (hours, minutes) = hoursAndMinutes(it)
                    stringResource(R.string.hours_minutes, hours, minutes)
                } ?: dash,
                sub = sunSub,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

internal fun uvLevelLabel(level: UvLevel): Int = when (level) {
    UvLevel.Low -> R.string.uv_low
    UvLevel.Moderate -> R.string.uv_moderate
    UvLevel.High -> R.string.uv_high
    UvLevel.VeryHigh -> R.string.uv_very_high
    UvLevel.Extreme -> R.string.uv_extreme
}

/** Плитка с заголовком, крупным значением и пояснением под ним. */
@Composable
private fun DayTile(title: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .glassCard(RoundedCornerShape(22.dp), LocalGlass.current)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(title, style = pogodaText(12f, 700, color = palette.ink2))
        Text(value, style = pogodaText(24f, 300, lineHeight = 1.2f, tabular = true), modifier = Modifier.padding(top = 6.dp))
        if (sub != null) {
            Text(sub, style = pogodaText(12f, 500, color = palette.ink2, lineHeight = 1.3f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}
