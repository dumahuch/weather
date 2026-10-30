package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.PressureOutlook
import io.github.dumahuch.pogodaapp.forecast.PressurePoint
import io.github.dumahuch.pogodaapp.forecast.PressureTrend
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.forecast.gustBeaufort
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.fitArcHeight
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
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Вкладка «Подробно»: плитки с воздухом, пыльцой, ветром, давлением, УФ-индексом, влажностью, видимостью, солнцем и луной.
 * Макет: страница «Подробно» в живом макете. Пыльца (DWD, только Германия, версия 0.4.3) стоит рядом с воздухом.
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
    val density = LocalDensity.current
    val fit = remember { ArcFit() }
    var contentPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Окно прокрутки: высота экрана без строки состояния. Содержимое (заголовок, плитки, отступ под панель вкладок)
        // измеряется внутри прокрутки, а дуга солнца занимает ровно тот остаток, при котором всё помещается.
        val viewportPx = constraints.maxHeight - WindowInsets.statusBars.getTop(density)
        val gapPx = with(density) { ARC_GAP.roundToPx() }
        val minPx = with(density) { ARC_MIN_HEIGHT.roundToPx() }
        LaunchedEffect(viewportPx, contentPx, fit.current, fit.natural) {
            if (contentPx > 0 && fit.current > 0 && fit.natural > 0) {
                val target = fitArcHeight(fit.current, viewportPx - contentPx, gapPx, minPx, fit.natural)
                if (target != fit.current) fit.forcedPx = target
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.fillMaxWidth().onSizeChanged { contentPx = it.height }) {
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
                        DetailsContent(state.place, state.forecast, state.airQuality, state.pollen, fit, Modifier.padding(horizontal = 20.dp))
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
    }
}

/** Зазор между нижней плиткой и панелью вкладок сверх отступа под панель (его уже включает [bottomContentPadding]). */
private val ARC_GAP = 6.dp

/** Ниже этой высоты дугу не сжимаем: если и так не помещается, страница прокручивается. */
private val ARC_MIN_HEIGHT = 40.dp

private val TileSpacing = 8.dp

@Composable
private fun DetailsContent(
    place: Place,
    forecast: Forecast,
    air: AirQuality?,
    pollen: PollenForecast?,
    fit: ArcFit,
    modifier: Modifier = Modifier,
) {
    val now = remember(forecast) { Instant.now() }
    val today = remember(forecast, now) { now.atZone(forecast.zone).toLocalDate() }
    val details = remember(forecast, now) { forecast.detailsAt(now) }
    val pressurePoints = remember(forecast, now) { PressureOutlook.points(forecast.hourDetails, now) }
    val trend = remember(forecast, now) { PressureOutlook.trend(forecast.hourDetails, now) }
    // Место «сейчас» на графике давления: до него в ряду не больше шести часов.
    val nowIndex = remember(pressurePoints, now) { PressureOutlook.nowIndex(pressurePoints, now) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            AirTile(air, Modifier.weight(1f).fillMaxHeight())
            PollenTile(pollen, today, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            WindTile(details, Modifier.weight(1f).fillMaxHeight())
            PressureTile(details, pressurePoints, nowIndex, forecast.zone, trend, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            UvTile(details, Modifier.weight(1f).fillMaxHeight())
            HumidityTile(details, Modifier.weight(1f).fillMaxHeight())
        }
        SunMoonTile(place, forecast, now, fit, Modifier.fillMaxWidth())
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
            // Плитка в половину ширины, как в макете: число, ступень под ним, шкала внизу.
            BigValue(air.eaqi.toString(), stringResource(R.string.air_unit))
            SubText(levels[air.level.ordinal])
            Spacer(Modifier.weight(1f).heightIn(min = 2.dp))
            ScaleBar(fraction = (air.eaqi / 100f).coerceIn(0f, 1f), colors = AirScale, stepped = true)
        }
    }
}

/**
 * Плитка «Пыльца» как в макете: названия видов и по три точки справа (промежуточная ступень это наполовину закрашенная
 * точка). Показаны четыре вида с наибольшей нагрузкой на сегодня; если нагрузки нигде нет, берёза, злаки, полынь и амброзия.
 * Без данных (вне Германии, нет сети, нет файла границ) прочерк и «Нет данных».
 */
@Composable
private fun PollenTile(pollen: PollenForecast?, today: LocalDate, modifier: Modifier = Modifier) {
    val kinds = stringArrayResource(R.array.pollen_kinds)
    val levels = stringArrayResource(R.array.pollen_levels)
    val rows = pollen?.shown(today)
    DetailTile(stringResource(R.string.tile_pollen), modifier, tag = stringResource(R.string.pollen_source)) {
        if (rows == null) {
            BigValue("–")
            SubText(stringResource(R.string.air_no_data))
        } else {
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { (kind, level) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            kinds[kind.ordinal],
                            style = pogodaText(13f, 500),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        PollenDots(level, levels[level.halves])
                    }
                }
            }
        }
    }
}

/** Три точки нагрузки: закрашено столько, до какой ступени дошла нагрузка, промежуточная ступень закрашена наполовину. */
@Composable
private fun PollenDots(level: PollenLevel, description: String, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.size(width = 35.dp, height = 9.dp).semantics { contentDescription = description }) {
        val diameter = 9.dp.toPx()
        val radius = diameter / 2
        val gap = 4.dp.toPx()
        for (step in 1..3) {
            val cx = (step - 1) * (diameter + gap) + radius
            val center = Offset(cx, radius)
            drawCircle(palette.glassLine, radius, center)
            when {
                level.halves >= 2 * step -> drawCircle(palette.warning, radius, center)
                level.halves == 2 * step - 1 ->
                    clipRect(left = cx - radius, top = 0f, right = cx, bottom = diameter) {
                        drawCircle(palette.warning, radius, center)
                    }
            }
        }
    }
}

@Composable
private fun WindTile(details: HourDetails?, modifier: Modifier = Modifier) {
    val kmh = stringResource(R.string.kmh_unit)
    val directions = stringArrayResource(R.array.wind_directions)
    val strength = stringArrayResource(R.array.beaufort_names)
    val letters = stringArrayResource(R.array.compass_letters).toList()
    val speed = details?.windKmh?.roundToInt()
    val locale = LocalLocale.current.platformLocale
    DetailTile(stringResource(R.string.tile_wind_now), modifier) {
        // Компас справа от числа и слова силы ветра; подписи ниже занимают всю ширину плитки.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BigValue(speed?.toString() ?: "–", kmh)
                // Слово по шкале Бофорта по средней скорости, как число на плитке (штиль, лёгкий, слабый, ..., ураган).
                speed?.let { SubText(strength[beaufortOf(it)]) }
            }
            details?.windDirection?.let { Compass(blowsTowardDegrees = it + 180f, letters = letters) }
        }
        details?.gustKmh?.let { gust ->
            val gustRounded = gust.roundToInt()
            // Порывы пишутся, только когда есть что сказать: слово по шкале Бофорта выше слова среднего ветра.
            gustBeaufort(speed, gustRounded)?.let { step ->
                SubText(stringResource(R.string.gusts_with_word, gustRounded, kmh, continuationCase(strength[step], locale)))
            }
        }
        details?.windDirection?.let { SubText(directions[CompassPoint.of(it).ordinal]) }
    }
}

@Composable
private fun PressureTile(
    details: HourDetails?,
    points: List<PressurePoint>,
    nowIndex: Int,
    zone: ZoneId,
    trend: PressureTrend?,
    modifier: Modifier = Modifier,
) {
    DetailTile(stringResource(R.string.tile_pressure), modifier) {
        BigValue(details?.pressureHpa?.roundToInt()?.toString() ?: "–", stringResource(R.string.hpa_unit))
        trend?.let { SubText(stringResource(trendLabel(it))) }
        if (points.size >= 2) {
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            PressureChart(points, nowIndex, zone)
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
