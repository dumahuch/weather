package io.github.dumahuch.pogodaapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.notify.AlertNotices
import io.github.dumahuch.pogodaapp.notify.AlertRequest
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.settings.CardShadow
import io.github.dumahuch.pogodaapp.settings.MapPreload
import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import io.github.dumahuch.pogodaapp.settings.NotificationToggle
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.components.AppTab
import io.github.dumahuch.pogodaapp.ui.components.BottomBar
import io.github.dumahuch.pogodaapp.ui.components.Glass
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.details.DetailsScreen
import io.github.dumahuch.pogodaapp.ui.forecast.DayDetailScreen
import io.github.dumahuch.pogodaapp.ui.forecast.ForecastScreen
import io.github.dumahuch.pogodaapp.ui.home.AlertSheet
import io.github.dumahuch.pogodaapp.ui.home.AlertSheetHost
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.HomeScreen
import io.github.dumahuch.pogodaapp.ui.map.MapPalette
import io.github.dumahuch.pogodaapp.ui.map.MapScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesActions
import io.github.dumahuch.pogodaapp.ui.places.PlacesScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesUiState
import io.github.dumahuch.pogodaapp.ui.settings.SettingsScreen
import io.github.dumahuch.pogodaapp.ui.sky.SkyPause
import io.github.dumahuch.pogodaapp.ui.sky.rememberAnimationsEnabled
import io.github.dumahuch.pogodaapp.weather.PollenKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Сколько суток показывает список «Прогноза» сразу после запуска. */
private const val DEFAULT_DAY_COUNT = 7

/**
 * Приложение целиком: страница выбранной вкладки и внизу окна панель вкладок с строкой источника данных.
 * Небо рисует хозяин неба под этим экраном. Выбранная вкладка, открытый день и число дней в списке
 * переживают поворот и пересоздание окна. Кнопка «назад»: из поиска города в список мест, из списка мест на вкладку,
 * из подробностей дня в список, с другой вкладки на «Главную».
 *
 * Экран настроек ([SettingsScreen]) открывается шестерёнкой на «Главной», тоже на всё окно; «назад» возвращает на «Главную».
 * Экран мест ([PlacesScreen]) открывается плашкой места на «Главной» и занимает окно целиком (панель вкладок скрыта).
 * Кнопка «Найти город» на карточках без места открывает его сразу в режиме поиска.
 * Предупреждения открываются шторкой поверх «Главной» и панели вкладок ([AlertSheetHost]); «назад» закрывает её, а из
 * предупреждения, открытого в списке, возвращает к списку.
 */
@Composable
fun PogodaApp(
    state: HomeUiState,
    refreshing: Boolean,
    permanentlyDenied: Boolean,
    versionLabel: String,
    actions: HomeActions,
    places: PlacesUiState,
    placesActions: PlacesActions,
    onVersionTap: () -> Unit,
    cardShadow: CardShadow,
    onCardShadow: (CardShadow) -> Unit,
    iconStyle: WeatherIconStyle,
    onIconStyle: (WeatherIconStyle) -> Unit,
    mapPreload: MapPreload,
    onMapPreload: (MapPreload) -> Unit,
    notifications: NotificationPrefs,
    onNotification: (NotificationToggle, Boolean) -> Unit,
    onSummaryTime: (Int) -> Unit,
    pollenKinds: Set<PollenKind>,
    onPollenKind: (PollenKind, Boolean) -> Unit,
    noticeError: String?,
    alertRequest: AlertRequest?,
    onAlertRequestDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // День хранится числом (дни от 1970 года): дата сама по себе в сохранённое состояние не кладётся.
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var dayCount by rememberSaveable { mutableIntStateOf(DEFAULT_DAY_COUNT) }

    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Экран настроек (шестерёнка на «Главной») занимает окно целиком, как экран мест.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Шторка предупреждений: весь список («Ещё N») или одно предупреждение (по идентификатору DWD).
    var alertsListOpen by rememberSaveable { mutableStateOf(false) }
    var openAlert by rememberSaveable { mutableStateOf<String?>(null) }
    var alertFromList by rememberSaveable { mutableStateOf(false) }

    fun closeAlerts() {
        alertsListOpen = false
        openAlert = null
        alertFromList = false
    }

    fun closePlaces() {
        placesOpen = false
        searching = false
        placesActions.onLeave()
    }

    // Нажали уведомление о предупреждении: «Главная» и на ней это предупреждение, как только предупреждения для места
    // загрузятся. Если его уже нет (DWD уточнил или отменил), открывается предупреждение того же явления или весь список.
    val alertSet = (state as? HomeUiState.Content)?.alerts
    val alertRequestDone by rememberUpdatedState(onAlertRequestDone)
    LaunchedEffect(alertRequest, alertSet) {
        val request = alertRequest ?: return@LaunchedEffect
        val shown = alertSet?.shownAt(Instant.now()) ?: return@LaunchedEffect
        if (placesOpen) closePlaces()
        settingsOpen = false
        openDay = null
        tab = AppTab.Home
        closeAlerts()
        val match = AlertNotices.pick(request, shown)
        if (match != null) openAlert = match.id else alertsListOpen = shown.isNotEmpty()
        alertRequestDone()
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    // Из предупреждения, открытого в списке, «назад» возвращает к списку.
    BackHandler(enabled = openAlert != null || alertsListOpen) {
        if (openAlert != null && alertFromList) openAlert = null else closeAlerts()
    }
    BackHandler(enabled = placesOpen && searching) {
        searching = false
        placesActions.onLeave()
    }
    BackHandler(enabled = placesOpen && !searching) { closePlaces() }
    BackHandler(enabled = !placesOpen && openDay != null) { openDay = null }

    // Название выбранного не текущего места (закреплённого или открытого для просмотра) и признак просмотра.
    val chosenName = when (val choice = places.selection) {
        PlaceChoice.Current -> null
        is PlaceChoice.Pinned -> places.pinned.firstOrNull { it.id == choice.id }?.name
        is PlaceChoice.Preview -> choice.place.name
    }
    val previewing = places.selection is PlaceChoice.Preview
    val listFull = places.pinned.size >= PlacesLogic.MAX_PINNED
    val linked = remember(actions, listFull) {
        actions.copy(
            onOpenPlaces = { placesOpen = true },
            onOpenPreferences = { settingsOpen = true },
            onOpenAlert = { id ->
                openAlert = id
                alertFromList = false
                alertsListOpen = false
            },
            onOpenAlerts = { alertsListOpen = true },
            // Список полон: «Закрепить» ведёт в список, где видно сообщение и можно открепить одно из мест.
            onPinPreview = {
                if (listFull) {
                    placesOpen = true
                } else {
                    placesActions.onPinPreview()
                }
            },
            onFindCity = {
                placesOpen = true
                searching = true
            },
        )
    }

    // Какое окно на экране: от него зависит анимация перехода (см. [transitionKind]).
    val content = state as? HomeUiState.Content
    val screen = when {
        settingsOpen -> ScreenKey(Layer.Settings)
        placesOpen -> ScreenKey(Layer.Places)
        tab == AppTab.Forecast && openDay != null && content != null ->
            ScreenKey(Layer.Day, tab.ordinal, day = openDay ?: 0L)
        else -> ScreenKey(Layer.Tab, tab.ordinal, isMap = tab == AppTab.Map)
    }
    val animate by rememberAnimationsEnabled()
    // Небо стоит, пока идёт переход: кадры нужны ему. Вкладки переключаются мгновенно, им стоять не нужно.
    var previousScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        val kind = transitionKind(previousScreen, screen)
        previousScreen = screen
        if (!animate || kind == TransitionKind.None) return@LaunchedEffect
        SkyPause.active = true
        delay(TRANSITION_MAX_MS)
        SkyPause.active = false
    }
    DisposableEffect(Unit) { onDispose { SkyPause.active = false } }

    // Смена вкладки «через небо»: прежняя уходит картинкой, новая собирается под небом и приходит картинкой (см. [TabMotion]).
    val tabMotion = remember { TabMotion() }
    val tabLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // Куда идёт смена: плашка внизу едет туда сразу по нажатию, а сама вкладка меняется после ухода прежней.
    var tabHeading by remember { mutableStateOf<AppTab?>(null) }

    fun showTab(target: AppTab) {
        tab = target
        openDay = null
        closeAlerts()
    }

    fun selectTab(target: AppTab) {
        val from = tab
        // Без движения: анимации выключены, нажата та же вкладка или на экране не вкладка (из дня уходит переход «назад»).
        if (!animate || target == from || screen.layer != Layer.Tab) {
            showTab(target)
            return
        }
        tabMotion.job?.cancel()
        tabHeading = target
        tabMotion.job = scope.launch {
            // Небо и погода стоят, пока идёт смена: кадры нужны ей.
            SkyPause.active = true
            try {
                // Карта рисуется отдельной поверхностью и в картинку не попадает: с неё уходят и на неё приходят без картинки.
                val old = if (from == AppTab.Map) null else tabMotion.capture(tabLayer)
                tabMotion.run(old, tabLayer, arriveAsPicture = target != AppTab.Map, "${from.name} > ${target.name}") {
                    showTab(target)
                }
            } finally {
                SkyPause.active = false
                // Новое нажатие во время смены уже поставило свою цель: её не трогаем.
                if (tab == tabHeading) tabHeading = null
            }
        }
    }
    BackHandler(enabled = !placesOpen && openDay == null && tab != AppTab.Home) { selectTab(AppTab.Home) }

    // На карте свои тёмные цвета и стекло без размытия (под стеклом карта, а не небо), вместе с панелью вкладок.
    val skyPalette = LocalPogodaPalette.current
    val skyGlass = LocalGlass.current
    val skyContent = LocalContentColor.current
    val mapGlass = remember { Glass.flat(MapPalette) }
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Обычно содержимое рисуется как есть; по просьбе [TabMotion] кадр записывается в слой для картинки.
                    // Пока идёт смена, живое содержимое спрятано: на экране только небо и картинка.
                    val request = tabMotion.request
                    val hidden = tabMotion.hidden
                    if (request != null) {
                        tabLayer.record { this@drawWithContent.drawContent() }
                        if (!hidden) drawLayer(tabLayer)
                        request.complete(Unit)
                    } else if (!hidden) {
                        drawContent()
                    }
                },
            transitionSpec = {
                if (animate) {
                    screenTransition(transitionKind(initialState, targetState))
                } else {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                }
            },
            label = "screen",
            // Вкладки с небом живут в одной колоде [TabDeck]: при смене между ними окно не пересобирается.
            contentKey = { if (it.layer == Layer.Tab && !it.isMap) SKY_TABS_KEY else it },
        ) { key ->
            when (key.layer) {
                Layer.Settings -> SettingsScreen(
                    cardShadow = cardShadow,
                    onCardShadow = onCardShadow,
                    iconStyle = iconStyle,
                    onIconStyle = onIconStyle,
                    mapPreload = mapPreload,
                    onMapPreload = onMapPreload,
                    notifications = notifications,
                    onNotification = onNotification,
                    onSummaryTime = onSummaryTime,
                    pollenKinds = pollenKinds,
                    onPollenKind = onPollenKind,
                    noticeError = noticeError,
                )

                Layer.Places -> PlacesScreen(
                    ui = places,
                    actions = placesActions,
                    searching = searching,
                    onSearching = {
                        searching = it
                        if (!it) placesActions.onLeave()
                    },
                    onClose = ::closePlaces,
                )

                Layer.Tab -> if (key.isMap) {
                    CompositionLocalProvider(
                        LocalPogodaPalette provides MapPalette,
                        LocalGlass provides mapGlass,
                        LocalContentColor provides MapPalette.ink,
                    ) {
                        MapScreen(
                            place = content?.place,
                            skyDarkInk = skyPalette.darkInk,
                            wifiOnly = mapPreload == MapPreload.WifiOnly,
                        )
                    }
                } else {
                    TabDeck(
                        active = key.tabIndex,
                        all = SKY_TABS,
                        ready = content != null,
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        when (AppTab.entries[index]) {
                            AppTab.Home -> HomeScreen(
                                state = state,
                                refreshing = refreshing,
                                permanentlyDenied = permanentlyDenied,
                                actions = linked,
                                pinnedName = chosenName,
                                preview = previewing,
                            )

                            AppTab.Details -> DetailsScreen(
                                state = state,
                                permanentlyDenied = permanentlyDenied,
                                actions = linked,
                            )

                            AppTab.Forecast -> ForecastScreen(
                                state = state,
                                permanentlyDenied = permanentlyDenied,
                                dayCount = dayCount,
                                actions = linked,
                                onDayCount = { dayCount = it },
                                onOpenDay = { openDay = it.toEpochDay() },
                            )

                            AppTab.Map -> Unit
                        }
                    }
                }

                // День открывается из «Прогноза», всегда на небе.
                Layer.Day -> if (content != null) {
                    DayDetailScreen(
                        state = content,
                        date = LocalDate.ofEpochDay(key.day),
                        onBack = { openDay = null },
                    )
                }
            }
        }
        // Картинка уходящей или приходящей вкладки, под панелью вкладок. Пока живое содержимое спрятано, нажатия до него
        // не доходят (панель вкладок выше и нажимается).
        if (tabMotion.hidden) {
            val picture = tabMotion.picture
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .drawBehind {
                        if (picture != null) {
                            scale(tabMotion.scale.value) { drawImage(picture, alpha = tabMotion.alpha.value) }
                        }
                    },
            )
        }
        // Панель вкладок не двигается при переходах между вкладками; на местах и в настройках её нет (плавно гаснет).
        val onMap = tab == AppTab.Map
        CompositionLocalProvider(
            LocalPogodaPalette provides if (onMap) MapPalette else skyPalette,
            LocalGlass provides if (onMap) mapGlass else skyGlass,
            LocalContentColor provides if (onMap) MapPalette.ink else skyContent,
        ) {
            AnimatedVisibility(
                visible = screen.layer == Layer.Tab || screen.layer == Layer.Day,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(BAR_FADE_IN_MS)),
                exit = fadeOut(tween(BAR_FADE_OUT_MS)),
            ) {
                BottomBar(
                    selected = tabHeading ?: tab,
                    onSelect = { selectTab(it) },
                    versionLabel = versionLabel,
                    showAttribution = state is HomeUiState.Content,
                    onVersionTap = onVersionTap,
                )
            }
        }
        val now = remember(content) { Instant.now() }
        val sheet = when {
            openAlert != null -> AlertSheet.One(openAlert.orEmpty(), alertFromList)
            alertsListOpen -> AlertSheet.All
            else -> null
        }
        AlertSheetHost(
            sheet = if (tab == AppTab.Home && screen.layer == Layer.Tab) sheet else null,
            alerts = remember(content, now) { content?.alerts?.shownAt(now).orEmpty() },
            area = content?.alerts?.area,
            zone = content?.forecast?.zone ?: ZoneId.systemDefault(),
            now = now,
            onOpen = { opened ->
                if (opened is AlertSheet.One) {
                    openAlert = opened.id
                    alertFromList = opened.fromList
                }
            },
            onClose = ::closeAlerts,
        )
    }
}

/** Панель вкладок гаснет и проявляется при входе в места и настройки и выходе из них. */
private const val BAR_FADE_IN_MS = 220
private const val BAR_FADE_OUT_MS = 160

/** Ключ окна для всех вкладок с небом: между ними окно не меняется, меняется выбранная в колоде [TabDeck]. */
private const val SKY_TABS_KEY = "sky-tabs"

/** Вкладки с небом, которые держатся собранными. */
private val SKY_TABS = listOf(AppTab.Home.ordinal, AppTab.Details.ordinal, AppTab.Forecast.ordinal)
