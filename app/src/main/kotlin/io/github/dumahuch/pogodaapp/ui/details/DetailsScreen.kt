package io.github.dumahuch.pogodaapp.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.dumahuch.pogodaapp.forecast.PressureOutlook
import io.github.dumahuch.pogodaapp.forecast.fitArcHeight
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.DetailsSkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.forecast.PageTitle
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.fullDate
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.NoPermissionTop
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.home.StateTop
import io.github.dumahuch.pogodaapp.ui.pages.AirTile
import io.github.dumahuch.pogodaapp.ui.pages.DetailPage
import io.github.dumahuch.pogodaapp.ui.pages.PollenTile
import io.github.dumahuch.pogodaapp.ui.pages.PressureTile
import io.github.dumahuch.pogodaapp.ui.pages.UvTile
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.Place
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import java.time.Instant
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll

/**
 * Вкладка «Подробно»: плитки с воздухом, пыльцой, давлением, УФ-индексом, солнцем и луной (ветер и влажность с 0.8.70
 * открываются с «Главной» своими страницами).
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
        val scroll = rememberScrollState()
        ReportSkyScroll(scroll)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll),
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
                        DetailsContent(
                            state.place,
                            state.forecast,
                            state.airQuality,
                            state.pollen,
                            fit,
                            actions.onOpenPage,
                            Modifier.padding(horizontal = 20.dp),
                        )
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

/** Ниже этой высоты кривую не сжимаем: если и так не помещается, страница прокручивается. */
private val ARC_MIN_HEIGHT = 40.dp

private val TileSpacing = 8.dp

@Composable
private fun DetailsContent(
    place: Place,
    forecast: Forecast,
    air: AirQuality?,
    pollen: PollenForecast?,
    fit: ArcFit,
    onOpenPage: (DetailPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember(forecast) { Instant.now() }
    val today = remember(forecast, now) { now.atZone(forecast.zone).toLocalDate() }
    val details = remember(forecast, now) { forecast.detailsAt(now) }
    val trend = remember(forecast, now) { PressureOutlook.trend(forecast.hourDetails, now) }
    // Каждая плитка открывает свою страницу; на плитке тот же символ, что на странице (0.8.80).
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            AirTile(air, { onOpenPage(DetailPage.Air) }, Modifier.weight(1f).fillMaxHeight())
            PollenTile(pollen, today, { onOpenPage(DetailPage.Pollen) }, Modifier.weight(1f).fillMaxHeight())
        }
        // Ветер и влажность с 0.8.70 открываются с «Главной» своими страницами (решение Димы 20.11): здесь их плиток нет.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            PressureTile(details, trend, { onOpenPage(DetailPage.Pressure) }, Modifier.weight(1f).fillMaxHeight())
            UvTile(details, { onOpenPage(DetailPage.Uv) }, Modifier.weight(1f).fillMaxHeight())
        }
        SunMoonTile(place, forecast, now, fit, { onOpenPage(DetailPage.SunMoon) }, Modifier.fillMaxWidth())
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

/** Три точки нагрузки: закрашено столько, до какой ступени дошла нагрузка, промежуточная ступень закрашена наполовину. */
@Composable
internal fun PollenDots(level: PollenLevel, description: String, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.size(width = 35.dp, height = 9.dp).semantics { contentDescription = description }) {
        val diameter = 9.dp.toPx()
        val radius = diameter / 2
        val gap = 4.dp.toPx()
        for (step in 1..3) {
            val cx = (step - 1) * (diameter + gap) + radius
            val center = Offset(cx, radius)
            drawCircle(palette.ink.copy(alpha = 0.20f), radius, center)
            when {
                level.halves >= 2 * step -> drawCircle(palette.warning, radius, center)
                level.halves == 2 * step - 1 ->
                    clipRect(left = cx - radius, top = 0f, right = cx, bottom = diameter) {
                        drawCircle(palette.warning, radius, center)
                    }
            }
            // Тонкий контур у каждой точки: пустая точка видна на любом небе.
            drawCircle(palette.ink.copy(alpha = 0.55f), radius - 0.5.dp.toPx(), center, style = Stroke(width = 1.dp.toPx()))
        }
    }
}
