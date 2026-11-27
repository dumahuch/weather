package io.github.dumahuch.pogodaapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.dumahuch.pogodaapp.forecast.Metric
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.notify.AlertNotices
import io.github.dumahuch.pogodaapp.notify.AlertRequest
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.settings.CardShadow
import io.github.dumahuch.pogodaapp.settings.LockScreenForm
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
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.HomeScreen
import io.github.dumahuch.pogodaapp.ui.map.MapPalette
import io.github.dumahuch.pogodaapp.ui.metric.MetricScreen
import io.github.dumahuch.pogodaapp.ui.pages.DetailPage
import io.github.dumahuch.pogodaapp.ui.pages.DetailPageScreen
import io.github.dumahuch.pogodaapp.ui.sunmoon.SunMoonScreen
import io.github.dumahuch.pogodaapp.ui.map.MapScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesActions
import io.github.dumahuch.pogodaapp.ui.places.PlacesScreen
import io.github.dumahuch.pogodaapp.ui.places.PlacesUiState
import io.github.dumahuch.pogodaapp.ui.settings.SettingsScreen
import io.github.dumahuch.pogodaapp.ui.settings.UpdateActions
import io.github.dumahuch.pogodaapp.ui.settings.VersionEntry
import io.github.dumahuch.pogodaapp.ui.sky.SkyCover
import io.github.dumahuch.pogodaapp.ui.sky.SkyPause
import io.github.dumahuch.pogodaapp.ui.sky.rememberAnimationsEnabled
import io.github.dumahuch.pogodaapp.update.UpdateUi
import io.github.dumahuch.pogodaapp.weather.AlertFilter
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.PollenKind
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Какая неделя открыта на «Прогнозе» после запуска: 0 эта, 1 следующая. */
private const val DEFAULT_WEEK = 0

/**
 * Приложение целиком: страница выбранной вкладки и внизу окна панель вкладок с строкой источника данных.
 * Небо рисует хозяин неба под этим экраном. Выбранная вкладка, открытый день и число дней в списке
 * переживают поворот и пересоздание окна. Кнопка «назад»: из поиска города в список мест, из списка мест на вкладку,
 * из подробностей дня в список, с другой вкладки на «Главную».
 *
 * Экран настроек ([SettingsScreen]) открывается шестерёнкой на «Главной», тоже на всё окно; «назад» возвращает на «Главную».
 * Экран мест ([PlacesScreen]) открывается плашкой места на «Главной» и занимает окно целиком (панель вкладок скрыта).
 * Кнопка «Найти город» на карточках без места открывает его сразу в режиме поиска.
 * Предупреждения раскрываются на «Главной» на месте (плашка с подробностями, «Ещё N» раскрывает весь список), шторки нет.
 */
@Composable
fun PogodaApp(
    state: HomeUiState,
    refreshing: Boolean,
    permanentlyDenied: Boolean,
    actions: HomeActions,
    places: PlacesUiState,
    placesActions: PlacesActions,
    version: VersionEntry,
    cardShadow: CardShadow,
    onCardShadow: (CardShadow) -> Unit,
    iconStyle: WeatherIconStyle,
    onIconStyle: (WeatherIconStyle) -> Unit,
    mapPreload: MapPreload,
    onMapPreload: (MapPreload) -> Unit,
    notifications: NotificationPrefs,
    onNotification: (NotificationToggle, Boolean) -> Unit,
    onLockScreen: (LockScreenForm) -> Unit,
    notificationsBlocked: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onSummaryTime: (Int) -> Unit,
    pollenKinds: Set<PollenKind>,
    onPollenKind: (PollenKind, Boolean) -> Unit,
    alertFilter: AlertFilter,
    onAlertMinLevel: (Int) -> Unit,
    onAlertKind: (AlertKind, Boolean) -> Unit,
    noticeError: String?,
    alertRequest: AlertRequest?,
    onAlertRequestDone: () -> Unit,
    update: UpdateUi,
    updateActions: UpdateActions,
    updateRequest: Boolean,
    onUpdateRequestDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // День хранится числом (дни от 1970 года): дата сама по себе в сохранённое состояние не кладётся.
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var forecastWeek by rememberSaveable { mutableIntStateOf(DEFAULT_WEEK) }
    // Страница показателя «Главной» (влажность, ветер, осадки), открытая нажатием под температурой.
    var openMetric by rememberSaveable { mutableStateOf<Metric?>(null) }
    // Страница плитки «Подробно» («Солнце и Луна», давление, УФ, воздух, пыльца), открытая нажатием на плитку.
    var openPage by rememberSaveable { mutableStateOf<DetailPage?>(null) }

    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Экран настроек (шестерёнка на «Главной») занимает окно целиком, как экран мест.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Предупреждения раскрываются на «Главной» на месте: одно раскрытое (по идентификатору DWD) и весь список («Ещё N»).
    var alertsListOpen by rememberSaveable { mutableStateOf(false) }
    var openAlert by rememberSaveable { mutableStateOf<String?>(null) }

    fun closeAlerts() {
        alertsListOpen = false
        openAlert = null
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
        openMetric = null
        openPage = null
        tab = AppTab.Home
        closeAlerts()
        val match = AlertNotices.pick(request, shown)
        if (match != null) openAlert = match.id else alertsListOpen = shown.isNotEmpty()
        alertRequestDone()
    }

    // Нажали уведомление «Доступна версия»: настройки, раздел «Обновления» раскрыт.
    var openUpdates by remember { mutableStateOf(false) }
    val updateRequestDone by rememberUpdatedState(onUpdateRequestDone)
    LaunchedEffect(updateRequest) {
        if (!updateRequest) return@LaunchedEffect
        if (placesOpen) closePlaces()
        closeAlerts()
        settingsOpen = true
        openUpdates = true
        updateRequestDone()
    }

    // Закрытие страницы жестом с предпросмотром ([PageBack]); на «Карте» и без анимаций «назад» закрывает сразу.
    val back = remember { PageBack() }
    val topIsPage = settingsOpen || (placesOpen && !searching) || (!placesOpen && (openDay != null || openMetric != null || openPage != null))
    fun closeTopPage() {
        when {
            settingsOpen -> settingsOpen = false
            placesOpen -> closePlaces()
            openDay != null -> openDay = null
            openMetric != null -> openMetric = null
            openPage != null -> openPage = null
        }
    }
    val animate by rememberAnimationsEnabled()
    val previewBack = topIsPage && animate && tab != AppTab.Map
    PredictiveBackHandler(enabled = previewBack) { events ->
        SkyPause.active = true
        try {
            events.collect { back.drag(it.progress) }
            back.commit(::closeTopPage)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                back.cancel()
                SkyPause.active = false
            }
            throw cancelled
        }
    }
    BackHandler(enabled = topIsPage && !previewBack) { closeTopPage() }
    BackHandler(enabled = placesOpen && searching) {
        searching = false
        placesActions.onLeave()
    }

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
            onOpenAlert = { id -> openAlert = if (openAlert == id) null else id },
            onOpenAlerts = { alertsListOpen = true },
            onCollapseAlerts = ::closeAlerts,
            onOpenMetric = { openMetric = it },
            onOpenPage = { openPage = it },
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
        tab == AppTab.Home && openMetric != null && content != null ->
            ScreenKey(Layer.Metric, tab.ordinal, metric = openMetric?.ordinal ?: 0)
        tab == AppTab.Details && openPage != null && content != null ->
            ScreenKey(Layer.Page, tab.ordinal, metric = openPage?.ordinal ?: 0)
        tab == AppTab.Forecast && openDay != null && content != null ->
            ScreenKey(Layer.Day, tab.ordinal, day = openDay ?: 0L)
        else -> ScreenKey(Layer.Tab, tab.ordinal, isMap = tab == AppTab.Map)
    }
    // Небо стоит, пока идёт переход: кадры нужны ему. Вкладки переключаются мгновенно, им стоять не нужно.
    var previousScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        val kind = transitionKind(previousScreen, screen)
        previousScreen = screen
        if (!animate || kind == TransitionKind.None || back.silent) return@LaunchedEffect
        SkyPause.active = true
        delay(TRANSITION_MAX_MS)
        SkyPause.active = false
    }
    DisposableEffect(Unit) { onDispose { SkyPause.active = false } }

    // Колода вкладок собрана всегда и лежит под окнами-страницами: при возврате с них она не собирается заново (см. [TabUnderlay]).
    val tabsOnTop = tabsAreOnTop(screen)
    val underlay = remember { TabUnderlay(tabsOnTop) }
    LaunchedEffect(tabsOnTop, animate) {
        if (animate && !back.silent) underlay.animateTo(tabsOnTop) else underlay.snapTo(tabsOnTop)
    }

    // Страница закрыта жестом: колода уже встала на место (см. выше), окно и небо возвращаются в обычный вид.
    LaunchedEffect(screen) {
        if (!back.silent) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        back.finish()
        SkyPause.active = false
    }

    // Смена вкладки: прежняя уходит картинкой, новая ставится под ней и приходит картинкой (см. [TabMotion]).
    val tabMotion = remember { TabMotion() }
    // Два слоя: уходящая вкладка рисуется из одного, приходящая записывается в другой.
    val leavingLayer = rememberGraphicsLayer()
    val arrivingLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // Куда идёт смена: плашка внизу едет туда сразу по нажатию.
    var tabHeading by remember { mutableStateOf<AppTab?>(null) }

    fun showTab(target: AppTab) {
        tab = target
        openDay = null
        openMetric = null
        openPage = null
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
                val old = if (from == AppTab.Map) null else tabMotion.capture(leavingLayer)
                tabMotion.run(old, arrivingLayer, arriveAsPicture = target != AppTab.Map, "${from.name} > ${target.name}") {
                    showTab(target)
                }
            } finally {
                SkyPause.active = false
                // Новое нажатие во время смены уже поставило свою цель: её не трогаем.
                if (tab == tabHeading) tabHeading = null
            }
        }
    }
    BackHandler(enabled = !placesOpen && openDay == null && openMetric == null && openPage == null && tab != AppTab.Home) { selectTab(AppTab.Home) }

    // На карте свои тёмные цвета и стекло без размытия (под стеклом карта, а не небо), вместе с панелью вкладок.
    val skyPalette = LocalPogodaPalette.current
    val skyGlass = LocalGlass.current
    val skyContent = LocalContentColor.current
    val mapGlass = remember { Glass.flat(MapPalette) }
    Box(modifier.fillMaxSize()) {
        // Колода вкладок с небом вне перехода окон: ниже всех окон, собрана всегда. По просьбе [TabMotion] её кадр
        // записывается в слой для картинки; пока идёт смена вкладки, живая колода спрятана: на экране небо и картинка.
        Box(
            Modifier
                .fillMaxSize()
                // Сдвиг раскладкой, а не слоем: размытие стекла (Haze) запоминает положение карточек при раскладке, и
                // сдвиг одним слоем после быстрого жеста «назад» оставлял зерно стекла сдвинутым («трещина», 27.11).
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(deckOffsetPx(back.shift.value, underlay.shift.value, placeable.width), 0)
                    }
                }
                .graphicsLayer {
                    alpha = deckAlphaWith(back.shift.value, underlay.alpha.value)
                    // Без отдельной картинки во весь экран, пока колода гаснет (см. [screenFade]).
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .then(if (tabsOnTop) Modifier else Modifier.clearAndSetSemantics { })
                .drawWithContent {
                    val request = tabMotion.request
                    val hidden = tabMotion.hidden
                    if (request != null) {
                        val layer = request.layer
                        // Как у прежнего снимка: всё за краем экрана обрезано, прозрачность с прошлой смены сброшена.
                        layer.clip = true
                        layer.alpha = 1f
                        layer.record { this@drawWithContent.drawContent() }
                        if (!hidden) drawLayer(layer)
                        request.drawn.complete(Unit)
                    } else if (!hidden) {
                        drawContent()
                    }
                },
        ) {
            TabDeck(
                active = tab.ordinal,
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
                        expandedAlert = openAlert,
                        alertsAll = alertsListOpen,
                    )

                    AppTab.Details -> DetailsScreen(
                        state = state,
                        permanentlyDenied = permanentlyDenied,
                        actions = linked,
                    )

                    AppTab.Forecast -> ForecastScreen(
                        state = state,
                        permanentlyDenied = permanentlyDenied,
                        week = forecastWeek,
                        actions = linked,
                        onWeek = { forecastWeek = it },
                        onOpenDay = { openDay = it.toEpochDay() },
                    )

                    AppTab.Map -> Unit
                }
            }
        }
        // Под страницей колода не принимает нажатий: окна-страницы прозрачны, и без этого нажатия шли бы сквозь них.
        if (!tabsOnTop) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
        // Вид последнего перехода окон: по нему окна проявляются и гаснут ([screenFade]).
        val moves = remember { ScreenMoves(screen) }
        val moveKind = remember(screen) { moves.move(screen) }
        AnimatedContent(
            targetState = screen,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Окно теперь держит только карту; пока идёт смена вкладки, она спрятана вместе с колодой.
                    if (!tabMotion.hidden) drawContent()
                },
            transitionSpec = {
                if (animate && !back.silent) {
                    screenTransition(transitionKind(initialState, targetState))
                } else {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                }
            },
            label = "screen",
            // Все вкладки живут в одном окне: при смене между ними (и с картой) окно не пересобирается.
            contentKey = { if (it.layer == Layer.Tab) SKY_TABS_KEY else it },
        ) { key ->
            val fade = screenFade(moveKind, animate && !back.silent)
            Box(Modifier.fillMaxSize().fadeLayer { fade.value }.pageBack(back, key.layer.depth > 0)) {
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
                        onLockScreen = onLockScreen,
                        notificationsBlocked = notificationsBlocked,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onSummaryTime = onSummaryTime,
                        pollenKinds = pollenKinds,
                        onPollenKind = onPollenKind,
                        alertFilter = alertFilter,
                        onAlertMinLevel = onAlertMinLevel,
                        onAlertKind = onAlertKind,
                        noticeError = noticeError,
                        update = update,
                        updateActions = updateActions,
                        openUpdates = openUpdates,
                        onOpenedUpdates = { openUpdates = false },
                        version = version,
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

                    // Колода вкладок вне перехода (см. выше); здесь только карта: она рисуется своей поверхностью.
                    Layer.Tab -> Box(Modifier.fillMaxSize()) {
                        if (key.isMap) {
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
                        }
                    }

                    // Страница показателя открывается с «Главной», всегда на небе.
                    Layer.Metric -> if (content != null) {
                        MetricScreen(
                            state = content,
                            metric = Metric.entries[key.metric],
                            onRetry = actions.onRetry,
                        )
                    }

                    // Страницы плиток «Подробно» открываются с этой вкладки, всегда на небе.
                    Layer.Page -> if (content != null) {
                        when (DetailPage.entries[key.metric]) {
                            DetailPage.SunMoon -> SunMoonScreen(state = content, onRetry = actions.onRetry)
                            else -> DetailPageScreen(
                                state = content,
                                page = DetailPage.entries[key.metric],
                                pollenKinds = pollenKinds,
                                onRetry = actions.onRetry,
                            )
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
        }
        // Картинка уходящей или приходящей вкладки, под панелью вкладок. Пока живое содержимое спрятано, нажатия до него
        // не доходят (панель вкладок выше и нажимается).
        if (tabMotion.hidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .drawBehind {
                        val picture = tabMotion.picture
                        if (picture != null) {
                            picture.alpha = tabMotion.alpha.value
                            scale(tabMotion.scale.value) { drawLayer(picture) }
                        }
                    },
            )
        }
        // Панель вкладок не двигается при переходах между вкладками; на местах и в настройках её нет (плавно гаснет).
        val onMap = tab == AppTab.Map
        // Небо закрыто картой только когда карта на виду целиком: ни перехода, ни окон поверх неё.
        val skyCovered = onMap && screen.layer == Layer.Tab && !tabMotion.hidden
        DisposableEffect(skyCovered) {
            SkyCover.covered = skyCovered
            onDispose { SkyCover.covered = false }
        }
        CompositionLocalProvider(
            LocalPogodaPalette provides if (onMap) MapPalette else skyPalette,
            LocalGlass provides if (onMap) mapGlass else skyGlass,
            LocalContentColor provides if (onMap) MapPalette.ink else skyContent,
        ) {
            AnimatedVisibility(
                visible = screen.layer == Layer.Tab || screen.layer == Layer.Day || screen.layer == Layer.Metric ||
                    screen.layer == Layer.Page,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(BAR_FADE_IN_MS)),
                exit = fadeOut(tween(BAR_FADE_OUT_MS)),
            ) {
                BottomBar(
                    selected = tabHeading ?: tab,
                    onSelect = { selectTab(it) },
                )
            }
        }
    }
}

/** Панель вкладок гаснет и проявляется при входе в места и настройки и выходе из них. */
private const val BAR_FADE_IN_MS = 220
private const val BAR_FADE_OUT_MS = 160

/** Ключ окна для всех вкладок с небом: между ними окно не меняется, меняется выбранная в колоде [TabDeck]. */
private const val SKY_TABS_KEY = "sky-tabs"

/** Вкладки с небом, которые держатся собранными. */
private val SKY_TABS = listOf(AppTab.Home.ordinal, AppTab.Details.ordinal, AppTab.Forecast.ordinal)
