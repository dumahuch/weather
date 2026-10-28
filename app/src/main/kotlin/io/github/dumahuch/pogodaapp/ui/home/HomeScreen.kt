package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.heroDigitsSize
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.home.temperatureDigits
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.components.Dock
import io.github.dumahuch.pogodaapp.ui.components.DockHeight
import io.github.dumahuch.pogodaapp.ui.components.HeroSkeleton
import io.github.dumahuch.pogodaapp.ui.components.HourlySkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.components.iconFor
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Condition

/** Действия экрана: что делать по нажатиям кнопок. */
class HomeActions(
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRetry: () -> Unit,
)

private const val OPEN_METEO_URL = "https://open-meteo.com/"
private val DockBottomGap = 8.dp

/**
 * Вкладка «Главная». Фон пока спокойный дневной градиент: картины неба придут на следующем шаге.
 * Содержимое прокручивается, если не помещается (крупный шрифт системы); лента «По часам» прижата вниз.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    permanentlyDenied: Boolean,
    versionLabel: String,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PogodaColors.SkyBrush)) {
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                TopRow(
                    name = placeName(state),
                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
                )
                when (state) {
                    HomeUiState.Loading -> LoadingBody()
                    is HomeUiState.Content -> ContentBody(state)
                    HomeUiState.NoPermission -> NoPermissionBody(permanentlyDenied, actions)
                    HomeUiState.LocationUnavailable -> StateBody(
                        icon = LineIcons.NavigationOff,
                        title = stringResource(R.string.locfail_title),
                        text = stringResource(R.string.locfail_text),
                        buttonLabel = stringResource(R.string.retry),
                        onButton = actions.onRetry,
                    )
                    HomeUiState.NoData -> StateBody(
                        icon = LineIcons.CloudOff,
                        title = stringResource(R.string.nodata_title),
                        text = stringResource(R.string.nodata_text),
                        buttonLabel = stringResource(R.string.retry),
                        onButton = actions.onRetry,
                    )
                }
                SourceFooter(versionLabel = versionLabel, showAttribution = state is HomeUiState.Content)
                Spacer(Modifier.height(bottomInset + DockBottomGap + DockHeight + 6.dp))
            }
        }
        Dock(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = DockBottomGap),
        )
    }
}

@Composable
private fun placeName(state: HomeUiState): String = when (state) {
    HomeUiState.Loading -> stringResource(R.string.place_locating)
    is HomeUiState.Content -> state.place.name ?: stringResource(R.string.place_current)
    HomeUiState.NoPermission, HomeUiState.LocationUnavailable, HomeUiState.NoData -> stringResource(R.string.place_none)
}

/** Плашка с названием места. Поиска города пока нет, поэтому она не нажимается. */
@Composable
private fun TopRow(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(44.dp)
            .glassCard(RoundedCornerShape(22.dp))
            .padding(start = 15.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIconView(LineIcons.Navigation, 18.dp, PogodaColors.Ink)
        Text(
            name,
            style = pogodaText(15f, 600),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun ColumnScope.LoadingBody() {
    HeroSkeleton(Modifier.padding(top = 26.dp))
    Spacer(Modifier.height(16.dp))
    Spacer(Modifier.weight(1f))
    HourlyFrame(caption = "", modifier = Modifier.padding(horizontal = 20.dp)) { HourlySkeleton() }
}

@Composable
private fun ColumnScope.ContentBody(state: HomeUiState.Content) {
    val current = state.forecast.current
    Spacer(Modifier.height(26.dp))
    HeroRow(
        digits = temperatureDigits(current.temperature),
        digitsSize = heroDigitsSize(current.temperature),
        description = temperatureText(current.temperature),
        icon = iconFor(current.condition, current.isDay),
    )
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            conditionName(current.condition),
            style = pogodaText(21f, 600),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "${stringResource(R.string.feels_like)} ${temperatureText(current.feelsLike)} · " +
                "${stringResource(R.string.temp_high)} ${temperatureText(state.forecast.today.max)}, " +
                "${stringResource(R.string.temp_low)} ${temperatureText(state.forecast.today.min)}",
            style = pogodaText(14f, 500, color = PogodaColors.Ink2, lineHeight = 1.4f),
            modifier = Modifier.padding(top = 5.dp),
        )
        if (state.offline) {
            OfflinePill(
                title = stringResource(R.string.offline_title),
                age = if (state.staleHours < 1) {
                    stringResource(R.string.data_age_under_hour)
                } else {
                    pluralStringResource(R.plurals.data_age_hours, state.staleHours, state.staleHours)
                },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Spacer(Modifier.weight(1f))
    HourlyFrame(
        caption = stringResource(R.string.hourly_caption),
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        HourlyStrip(points = state.strip, zone = state.forecast.zone)
    }
}

@Composable
private fun conditionName(condition: Condition): String = stringResource(
    when (condition) {
        Condition.Clear -> R.string.wx_clear
        Condition.Partly -> R.string.wx_partly
        Condition.Cloudy -> R.string.wx_cloudy
        Condition.Rain -> R.string.wx_rain
        Condition.Storm -> R.string.wx_storm
        Condition.Snow -> R.string.wx_snow
        Condition.Fog -> R.string.wx_fog
    },
)

@Composable
private fun ColumnScope.NoPermissionBody(permanentlyDenied: Boolean, actions: HomeActions) {
    // После отказа с галочкой «больше не спрашивать» системное окно уже не появится: ведём в настройки.
    StateBody(
        icon = LineIcons.NavigationOff,
        title = stringResource(R.string.noloc_title),
        text = stringResource(R.string.noloc_text),
        buttonLabel = stringResource(if (permanentlyDenied) R.string.noloc_settings else R.string.noloc_allow),
        onButton = if (permanentlyDenied) actions.onOpenSettings else actions.onAllow,
    )
}

@Composable
private fun ColumnScope.StateBody(
    icon: LineIcon,
    title: String,
    text: String,
    buttonLabel: String,
    onButton: () -> Unit,
) {
    StateCard(
        icon = icon,
        title = title,
        text = text,
        buttonLabel = buttonLabel,
        onButton = onButton,
        modifier = Modifier.padding(start = 24.dp, top = 38.dp, end = 24.dp),
    )
    Spacer(Modifier.weight(1f))
}

/** Источник данных (условие лицензии Open-Meteo) и номер версии. */
@Composable
private fun SourceFooter(versionLabel: String, showAttribution: Boolean) {
    val uriHandler = LocalUriHandler.current
    val style = pogodaText(11f, 500, color = PogodaColors.Ink2)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAttribution) {
            Text(
                stringResource(R.string.attribution_open_meteo),
                style = style.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        try {
                            uriHandler.openUri(OPEN_METEO_URL)
                        } catch (e: IllegalArgumentException) {
                            // Нет приложения для открытия ссылок: ничего не делаем.
                        }
                    }
                    .padding(vertical = 8.dp),
            )
            Text(" · ", style = style)
        }
        Text(versionLabel, style = style, textAlign = TextAlign.Center)
    }
}
