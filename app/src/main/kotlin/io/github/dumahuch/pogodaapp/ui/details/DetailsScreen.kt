package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.PressureOutlook
import io.github.dumahuch.pogodaapp.forecast.PressureTrend
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.forecast.visibilityText
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.DetailsSkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.PageTitle
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.fullDate
import io.github.dumahuch.pogodaapp.ui.forecast.uvLevelLabel
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.NoPermissionTop
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.home.StateTop
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Вкладка «Подробно»: плитки с воздухом, ветром, давлением, УФ-индексом, влажностью, видимостью, солнцем и луной.
 * Макет: страница «Подробно» в живом макете. Пыльцы пока нет (версия 0.4.1), её плитка встанет рядом с воздухом.
 * Значения берутся для текущего часа из прогноза; качество воздуха приходит отдельным запросом ([AirQuality]).
 * Если места или данных нет, показывается та же карточка состояния, что на «Главной».
 */
@Composable
fun DetailsScreen(
    state: HomeUiState,
    permanentlyDenied: Boolean,
    actions: HomeActions,
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
        PageTitle(stringResource(R.string.tab_details), dateLine, Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp))
        Spacer(Modifier.height(12.dp))
        when (state) {
            HomeUiState.Loading -> DetailsSkeleton(Modifier.padding(horizontal = 20.dp))

            is HomeUiState.Content -> if (state.forecast.hourDetails.isEmpty()) {
                StateCard(
                    icon = LineIcons.Grid,
                    title = stringResource(R.string.details_missing_title),
                    text = stringResource(R.string.details_missing_text),
                    buttonLabel = stringResource(R.string.retry),
                    onButton = actions.onRetry,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                DetailsContent(state.place, state.forecast, state.airQuality, Modifier.padding(horizontal = 20.dp))
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

private val TileSpacing = 8.dp

@Composable
private fun DetailsContent(place: Place, forecast: Forecast, air: AirQuality?, modifier: Modifier = Modifier) {
    val now = remember(forecast) { Instant.now() }
    val details = remember(forecast, now) { forecast.detailsAt(now) }
    val series = remember(forecast, now) { PressureOutlook.series(forecast.hourDetails, now) }
    val trend = remember(forecast, now) { PressureOutlook.trend(forecast.hourDetails, now) }
    // Место «сейчас» на графике давления: до него в ряду не больше шести часов.
    val nowIndex = remember(forecast, now) {
        forecast.hourDetails.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0).coerceAtMost(PressureOutlook.HOURS_BEFORE)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        AirTile(air, Modifier.fillMaxWidth())
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            WindTile(details, Modifier.weight(1f).fillMaxHeight())
            PressureTile(details, series, nowIndex, trend, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            UvTile(details, Modifier.weight(1f).fillMaxHeight())
            HumidityTile(details, Modifier.weight(1f).fillMaxHeight())
        }
        SunMoonTile(place, forecast, now, Modifier.fillMaxWidth())
    }
}

/** Плитка: заголовок (с подписью источника справа, если есть) и содержимое. */
@Composable
internal fun DetailTile(
    title: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .glassCard(RoundedCornerShape(22.dp), LocalGlass.current)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 13.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = pogodaText(12.5f, 700, color = palette.ink2), maxLines = 1)
            if (tag != null) Text(tag, style = pogodaText(10.5f, 600, color = palette.ink2), maxLines = 1)
        }
        content()
    }
}

/** Крупное число и мелкая единица рядом. */
@Composable
internal fun BigValue(value: String, unit: String? = null) {
    val palette = LocalPogodaPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = pogodaText(34f, 200, lineHeight = 1.05f, letterSpacing = (-0.85f).sp, tabular = true))
        if (unit != null) {
            Text(
                unit,
                style = pogodaText(13f, 600, color = palette.ink2),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SubText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = pogodaText(12.5f, 500, color = LocalPogodaPalette.current.ink2, lineHeight = 1.35f), modifier = modifier)
}

@Composable
private fun AirTile(air: AirQuality?, modifier: Modifier = Modifier) {
    val levels = stringArrayResource(R.array.air_levels)
    DetailTile(stringResource(R.string.tile_air), modifier, tag = stringResource(R.string.air_source)) {
        if (air == null) {
            BigValue("–")
            SubText(stringResource(R.string.air_no_data))
        } else {
            // Число и ступень в одной строке: плитка ниже, все плитки помещаются на экран без прокрутки.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BigValue(air.eaqi.toString(), stringResource(R.string.air_unit))
                Spacer(Modifier.weight(1f))
                SubText(levels[air.level.ordinal], Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(2.dp))
            ScaleBar(fraction = (air.eaqi / 100f).coerceIn(0f, 1f), colors = AirScale, stepped = true)
        }
    }
}

@Composable
private fun WindTile(details: HourDetails?, modifier: Modifier = Modifier) {
    val kmh = stringResource(R.string.kmh_unit)
    val directions = stringArrayResource(R.array.wind_directions)
    val speed = details?.windKmh
    DetailTile(stringResource(R.string.tile_wind_now), modifier) {
        // Компас стоит справа от числа, подписи под ним занимают всю ширину плитки и не налезают на него.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            BigValue(speed?.roundToInt()?.toString() ?: "–", kmh)
            Spacer(Modifier.weight(1f))
            details?.windDirection?.let { Compass(blowsTowardDegrees = it + 180f) }
        }
        details?.gustKmh?.let { SubText(stringResource(R.string.gusts_up_to, it.roundToInt(), kmh)) }
        details?.windDirection?.let { SubText(directions[CompassPoint.of(it).ordinal]) }
    }
}

@Composable
private fun PressureTile(
    details: HourDetails?,
    series: List<Double>,
    nowIndex: Int,
    trend: PressureTrend?,
    modifier: Modifier = Modifier,
) {
    DetailTile(stringResource(R.string.tile_pressure), modifier) {
        BigValue(details?.pressureHpa?.roundToInt()?.toString() ?: "–", stringResource(R.string.hpa_unit))
        trend?.let { SubText(stringResource(trendLabel(it))) }
        if (series.size >= 2) {
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            Sparkline(series, nowIndex)
        }
    }
}

private fun trendLabel(trend: PressureTrend): Int = when (trend) {
    PressureTrend.Rising -> R.string.trend_rising
    PressureTrend.Steady -> R.string.trend_steady
    PressureTrend.Falling -> R.string.trend_falling
    PressureTrend.FallingFast -> R.string.trend_falling_fast
}

@Composable
private fun UvTile(details: HourDetails?, modifier: Modifier = Modifier) {
    val uv = details?.uvIndex?.roundToInt()
    DetailTile(stringResource(R.string.tile_uv), modifier) {
        BigValue(uv?.toString() ?: "–")
        if (uv != null) {
            SubText(stringResource(uvLevelLabel(UvLevel.of(uv))))
            Spacer(Modifier.height(2.dp))
            ScaleBar(fraction = (uv.coerceIn(0, UV_SCALE_MAX) / UV_SCALE_MAX.toFloat()), colors = UvScale, stepped = false)
        }
    }
}

@Composable
private fun HumidityTile(details: HourDetails?, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    DetailTile(stringResource(R.string.tile_humidity), modifier) {
        BigValue(details?.humidity?.toString() ?: "–", "%")
        details?.visibilityM?.let { SubText(stringResource(R.string.visibility_line, visibilityText(it, locale))) }
    }
}

/** Высшая ступень шкалы УФ на полоске. */
private const val UV_SCALE_MAX = 11
