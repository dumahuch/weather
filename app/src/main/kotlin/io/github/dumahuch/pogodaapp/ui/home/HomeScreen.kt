package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.heroDigitsSize
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.home.temperatureDigits
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.HeroSkeleton
import io.github.dumahuch.pogodaapp.ui.components.HourlySkeleton
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.PullRefreshIndicator
import io.github.dumahuch.pogodaapp.ui.components.rememberPullRefreshState
import io.github.dumahuch.pogodaapp.ui.components.glassButton
import io.github.dumahuch.pogodaapp.ui.components.iconFor
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Действия экрана: что делать по нажатиям кнопок и по протягиванию вниз. [onOpenPlaces] открывает список мест (плашка
 * места), [onFindCity] открывает сразу поиск города (вторая кнопка карточек «нет доступа» и «место не определено»).
 * Оба задаёт [io.github.dumahuch.pogodaapp.ui.PogodaApp], потому что экран мест живёт в нём.
 */
data class HomeActions(
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenPlaces: () -> Unit = {},
    val onFindCity: () -> Unit = {},
)

/**
 * Вкладка «Главная». Фон (небо) рисует хозяин неба [io.github.dumahuch.pogodaapp.ui.sky.SkyHost] под этим экраном,
 * цвета текста и стекла приходят из [LocalPogodaPalette]. Содержимое прокручивается, если не помещается (крупный шрифт системы); лента «По часам» прижата вниз.
 * Протягивание вниз сдвигает верхнюю часть (плашку места и текущую погоду) и запускает обновление;
 * лента «По часам» и нижняя часть окна (панель вкладок и строка источника, см. [io.github.dumahuch.pogodaapp.ui.PogodaApp]) остаются на месте.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    refreshing: Boolean,
    permanentlyDenied: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    pinnedName: String? = null,
) {
    val pull = rememberPullRefreshState(
        refreshing = refreshing,
        // Пока идёт первая загрузка или нет разрешения, тянуть нечего.
        enabled = state is HomeUiState.Content ||
            state == HomeUiState.NoData ||
            state == HomeUiState.LocationUnavailable,
        onRefresh = actions.onRefresh,
    )
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(pull.connection),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = pull.offset },
                ) {
                    TopRow(
                        name = placeName(state, pinnedName),
                        pinned = pinnedName != null,
                        onClick = actions.onOpenPlaces,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
                    )
                    when (state) {
                        HomeUiState.Loading -> LoadingTop()
                        is HomeUiState.Content -> ContentTop(state)
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
                }
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.weight(1f))
                when (state) {
                    HomeUiState.Loading -> HourlyFrame(
                        caption = "",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) { HourlySkeleton() }
                    is HomeUiState.Content -> HourlyFrame(
                        caption = stringResource(R.string.hourly_caption),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) { HourlyStrip(points = state.strip, zone = state.forecast.zone) }
                    HomeUiState.NoPermission, HomeUiState.LocationUnavailable, HomeUiState.NoData -> Unit
                }
                Spacer(Modifier.height(bottomContentPadding()))
            }
        }
        // Индикатор лежит там, где стояла плашка места: плашка при протягивании сдвигается вниз и освобождает его.
        PullRefreshIndicator(
            state = pull,
            refreshing = refreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
        )
    }
}

@Composable
internal fun placeName(state: HomeUiState, pinnedName: String? = null): String = when (state) {
    // У закреплённого места название известно сразу, ждать определения не нужно.
    HomeUiState.Loading -> pinnedName ?: stringResource(R.string.place_locating)
    is HomeUiState.Content -> state.place.name ?: stringResource(R.string.place_current)
    HomeUiState.NoPermission, HomeUiState.LocationUnavailable, HomeUiState.NoData ->
        pinnedName ?: stringResource(R.string.place_none)
}

/** Плашка с названием места. Нажатие открывает список мест; у закреплённого места вместо стрелки навигации значок метки. */
@Composable
private fun TopRow(name: String, pinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val label = stringResource(R.string.places_open)
    Row(
        modifier
            .height(44.dp)
            .glassButton(RoundedCornerShape(22.dp), glass)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 15.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIconView(if (pinned) LineIcons.Pin else LineIcons.Navigation, 18.dp, palette.ink)
        Text(
            name,
            style = pogodaText(15f, 600),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        LineIconView(LineIcons.ChevronDown, 18.dp, palette.ink2)
    }
}

@Composable
private fun LoadingTop() {
    HeroSkeleton(Modifier.padding(top = 26.dp))
}

/** Верхняя часть «Главной» с данными: текущая погода. Лента «По часам» стоит ниже и не сдвигается. */
@Composable
private fun ContentTop(state: HomeUiState.Content) {
    val palette = LocalPogodaPalette.current
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
            style = pogodaText(14f, 500, color = palette.ink2, lineHeight = 1.4f),
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
}

@Composable
internal fun conditionName(condition: Condition): String = stringResource(
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
internal fun NoPermissionTop(permanentlyDenied: Boolean, actions: HomeActions) {
    // После отказа с галочкой «больше не спрашивать» системное окно уже не появится: ведём в настройки.
    StateTop(
        icon = LineIcons.NavigationOff,
        title = stringResource(R.string.noloc_title),
        text = stringResource(R.string.noloc_text),
        buttonLabel = stringResource(if (permanentlyDenied) R.string.noloc_settings else R.string.noloc_allow),
        onButton = if (permanentlyDenied) actions.onOpenSettings else actions.onAllow,
        secondaryLabel = stringResource(R.string.places_find),
        onSecondary = actions.onFindCity,
    )
}

@Composable
internal fun StateTop(
    icon: LineIcon,
    title: String,
    text: String,
    buttonLabel: String,
    onButton: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    StateCard(
        icon = icon,
        title = title,
        text = text,
        buttonLabel = buttonLabel,
        onButton = onButton,
        modifier = Modifier.padding(start = 24.dp, top = 38.dp, end = 24.dp),
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
    )
}
