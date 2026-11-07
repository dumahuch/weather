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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import io.github.dumahuch.pogodaapp.forecast.precipitationCaption
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.home.heroDigitsSize
import io.github.dumahuch.pogodaapp.home.heroIconFor
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.home.temperatureDigits
import io.github.dumahuch.pogodaapp.ui.ExtremeTints
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import io.github.dumahuch.pogodaapp.ui.QuietInk
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
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Condition
import java.time.Instant
import kotlin.math.roundToInt
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll

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
    /** Кнопка «Закрепить» рядом с названием города, открытого для просмотра. */
    val onPinPreview: () -> Unit = {},
    /** Шестерёнка вверху справа: открывает экран настроек приложения (не системные настройки доступа, их открывает [onOpenSettings]). */
    val onOpenPreferences: () -> Unit = {},
    /** Плашка предупреждения (по идентификатору) открывает его подробности, строка «Ещё N» весь список. */
    val onOpenAlert: (String) -> Unit = {},
    val onOpenAlerts: () -> Unit = {},
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
    preview: Boolean = false,
) {
    val pull = rememberPullRefreshState(
        refreshing = refreshing,
        // Пока идёт первая загрузка или нет разрешения, тянуть нечего.
        enabled = state is HomeUiState.Content ||
            state == HomeUiState.NoData ||
            state == HomeUiState.LocationUnavailable,
        onRefresh = actions.onRefresh,
    )
    val layout = remember { HomeLayout() }
    val scroll = rememberScrollState()
    // Прокрутка и протягивание вниз двигают гряды фона «Ди».
    ReportSkyScroll(scroll) { pull.offset }
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(pull.connection),
        ) {
            val viewport = maxHeight // внутри колонки область BoxWithConstraints уже не видна
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = maxHeight)
                    .onSizeChanged { layout.column = it.height },
            ) {
                val pulled = Modifier.graphicsLayer { translationY = pull.offset }
                Column(Modifier.fillMaxWidth().then(pulled)) {
                    TopRow(
                        name = placeName(state, pinnedName),
                        pinned = pinnedName != null,
                        preview = preview,
                        onClick = actions.onOpenPlaces,
                        onPin = actions.onPinPreview,
                        onPreferences = actions.onOpenPreferences,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
                    )
                }
                when (state) {
                    // Заглушка стоит посередине свободного места между плашкой места и лентой «По часам»: свободное место
                    // делится поровну над блоком и под ним (ограничение снизу и сверху 12 dp).
                    HomeUiState.Loading -> {
                        Spacer(Modifier.height(CENTER_EDGE))
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.fillMaxWidth().then(pulled)) { LoadingTop() }
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.height(CENTER_EDGE))
                    }
                    // С данными: блок погоды, под ним предупреждения; как делится свободное место, решает [HeroFit].
                    is HomeUiState.Content -> ContentMiddle(state, layout, viewport, pulled, actions)
                    // Карточки состояний остаются наверху, как были.
                    else -> {
                        Column(Modifier.fillMaxWidth().then(pulled)) {
                            when (state) {
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
                                else -> StateTop(
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
                    }
                }
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

/**
 * Плашка с названием места. Нажатие открывает список мест; у закреплённого места вместо стрелки навигации значок метки,
 * у города, открытого для просмотра, значок поиска и рядом кнопка «Закрепить» ([onPin]).
 */
@Composable
private fun TopRow(
    name: String,
    pinned: Boolean,
    preview: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onPreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val label = stringResource(R.string.places_open)
    val previewLabel = stringResource(R.string.places_preview)
    val settingsLabel = stringResource(R.string.settings_title)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Слева плашка места (и «Закрепить»), справа шестерёнка; длинное название обрезается раньше, чем шестерёнка уйдёт.
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .height(44.dp)
                    .weight(1f, fill = false)
                    .glassButton(RoundedCornerShape(22.dp), glass)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                    .semantics { if (preview) stateDescription = previewLabel }
                    .padding(start = 15.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val icon = when {
                    preview -> LineIcons.Search
                    pinned -> LineIcons.Pin
                    else -> LineIcons.Navigation
                }
                LineIconView(icon, 18.dp, palette.ink)
                Text(
                    name,
                    style = pogodaText(15f, 600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                LineIconView(LineIcons.ChevronDown, 18.dp, palette.ink2)
            }
            if (preview) {
                Row(
                    Modifier
                        .height(44.dp)
                        .glassButton(RoundedCornerShape(22.dp), glass)
                        .clickable(role = Role.Button, onClick = onPin)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LineIconView(LineIcons.Pin, 18.dp, palette.ink)
                    Text(stringResource(R.string.places_pin), style = pogodaText(15f, 600), maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .glassButton(CircleShape, glass)
                .clickable(role = Role.Button, onClick = onPreferences)
                .semantics { contentDescription = settingsLabel },
            contentAlignment = Alignment.Center,
        ) {
            LineIconView(LineIcons.Sliders, 20.dp, palette.ink)
        }
    }
}

@Composable
private fun LoadingTop() {
    HeroSkeleton(Modifier.padding(top = 6.dp))
}

/**
 * Верхняя часть «Главной» с данными (вариант «В», всё по центру): огромная температура со значком справа, состояние,
 * максимум и минимум календарного дня, «Ощущается как» и одна тонкая строка подписей (влажность, ветер, осадки до конца
 * дня). Лента «По часам» стоит ниже и не сдвигается.
 */
@Composable
internal fun ContentTop(state: HomeUiState.Content, heroScale: Float, heroModifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val forecast = state.forecast
    val current = forecast.current
    val now = remember(forecast) { Instant.now() }
    val details = remember(forecast, now) { forecast.detailsAt(now) }
    val rain = remember(forecast, now) { precipitationCaption(forecast, now) }
    // Идут осадки сейчас (по коду погоды), а в часовых данных их нет или слишком мало для части суток: «Нет» тут было бы ложью.
    val wetNow = current.condition == Condition.Rain || current.condition == Condition.Storm || current.condition == Condition.Snow
    val noRainAhead = rain == PrecipitationCaption.None
    val dash = "–"
    val kmh = stringResource(R.string.kmh_unit)
    val humidityText = details?.humidity?.let(::percentText) ?: dash
    val windText = details?.windKmh?.let { "${it.roundToInt()}\u00A0$kmh" } ?: dash
    val captions = listOf(
        Caption(LineIcons.Drop, humidityText, "${stringResource(R.string.tile_humidity)} $humidityText"),
        Caption(LineIcons.Wind, windText, "${stringResource(R.string.tile_wind_now)} $windText"),
        Caption(
            LineIcons.Rain,
            if (noRainAhead && wetNow) stringResource(R.string.precip_short_now) else precipitationShortText(rain),
            if (noRainAhead && wetNow) stringResource(R.string.precip_today_now) else precipitationText(rain),
            faded = noRainAhead && !wetNow,
            wide = true,
        ),
    )
    HeroBlock(
        digits = temperatureDigits(current.temperature),
        digitsSize = heroDigitsSize(current.temperature),
        description = temperatureText(current.temperature),
        icon = heroIconFor(
            LocalHeroConditionOverride.current ?: current.condition,
            isDay = LocalHeroSolarHourOverride.current?.let { it in DAY_FROM_HOUR..DAY_TO_HOUR } ?: current.isDay,
        ),
        iconStyle = LocalWeatherIconStyle.current,
        modifier = heroModifier,
        scale = heroScale,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (current.drizzle) stringResource(R.string.wx_drizzle) else conditionName(current.condition),
            style = pogodaText(21f, 600),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        HighLowRow(forecast.today.max, forecast.today.min, Modifier.padding(top = 6.dp))
        Text(
            text = "${stringResource(R.string.feels_like)} ${temperatureText(current.feelsLike)}",
            style = pogodaText(13f, 400, color = QuietInk.text(palette.ink, palette.infoBackdrop), lineHeight = 1.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        CaptionRow(captions, Modifier.padding(top = 12.dp))
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

/**
 * Максимум и минимум календарного дня: тёплый оттенок со стрелкой вверх и холодный со стрелкой вниз (правила надписей,
 * раздел 4). Слов «Макс» и «Мин» на экране нет, они в описании для чтения экрана. Оттенок ослабляется там, где небо под
 * строкой не оставляет запаса контраста ([ExtremeTints]); число дополнительно приглушено ([QuietInk]).
 */
@Composable
private fun HighLowRow(max: Double, min: Double, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val description = "${stringResource(R.string.temp_high)} ${temperatureText(max)}, " +
        "${stringResource(R.string.temp_low)} ${temperatureText(min)}"
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtremeValue(LineIcons.ArrowUp, temperatureText(max), palette, warm = true)
        ExtremeValue(LineIcons.ArrowDown, temperatureText(min), palette, warm = false)
    }
}

@Composable
private fun ExtremeValue(icon: LineIcon, text: String, palette: PogodaPalette, warm: Boolean) {
    val arrow = ExtremeTints.arrow(palette.ink, palette.smoke, warm, palette.infoBackdrop)
    // Число бледнее основного текста, как остальные второстепенные надписи, но с оттенком; ореола у него нет.
    val number = QuietInk.text(ExtremeTints.text(palette.ink, palette.smoke, warm, palette.infoBackdrop), palette.infoBackdrop)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        LineIconView(icon, 16.dp, arrow, strokeWidth = 2f)
        Text(text, style = pogodaText(17f, 600, color = number, tabular = true), maxLines = 1)
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

/** Солнечные часы, между которыми на экране проверки считается день (для значка у температуры). */
private const val DAY_FROM_HOUR = 6.0
private const val DAY_TO_HOUR = 18.0
