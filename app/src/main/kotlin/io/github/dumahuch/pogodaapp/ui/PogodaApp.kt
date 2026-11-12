package io.github.dumahuch.pogodaapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.notify.AlertNotices
import io.github.dumahuch.pogodaapp.notify.AlertRequest
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.settings.CardShadow
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
import io.github.dumahuch.pogodaapp.weather.PollenKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    notifications: NotificationPrefs,
    onNotification: (NotificationToggle, Boolean) -> Unit,
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
    BackHandler(enabled = !placesOpen && openDay == null && tab != AppTab.Home) { tab = AppTab.Home }

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

    if (settingsOpen) {
        SettingsScreen(
            cardShadow = cardShadow,
            onCardShadow = onCardShadow,
            iconStyle = iconStyle,
            onIconStyle = onIconStyle,
            notifications = notifications,
            onNotification = onNotification,
            pollenKinds = pollenKinds,
            onPollenKind = onPollenKind,
            noticeError = noticeError,
            modifier = modifier,
        )
        return
    }

    if (placesOpen) {
        PlacesScreen(
            ui = places,
            actions = placesActions,
            searching = searching,
            onSearching = {
                searching = it
                if (!it) placesActions.onLeave()
            },
            onClose = ::closePlaces,
            modifier = modifier,
        )
        return
    }

    // На карте свои тёмные цвета и стекло без размытия (под стеклом карта, а не небо), вместе с панелью вкладок.
    val skyPalette = LocalPogodaPalette.current
    val onMap = tab == AppTab.Map
    val mapGlass = remember { Glass.flat(MapPalette) }
    CompositionLocalProvider(
        LocalPogodaPalette provides if (onMap) MapPalette else skyPalette,
        LocalGlass provides if (onMap) mapGlass else LocalGlass.current,
        LocalContentColor provides if (onMap) MapPalette.ink else LocalContentColor.current,
    ) {
        Box(modifier.fillMaxSize()) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    state = state,
                    refreshing = refreshing,
                    permanentlyDenied = permanentlyDenied,
                    actions = linked,
                    pinnedName = chosenName,
                    preview = previewing,
                )

                AppTab.Forecast -> {
                    val day = openDay
                    if (day != null && state is HomeUiState.Content) {
                        DayDetailScreen(
                            state = state,
                            date = LocalDate.ofEpochDay(day),
                            onBack = { openDay = null },
                        )
                    } else {
                        ForecastScreen(
                            state = state,
                            permanentlyDenied = permanentlyDenied,
                            dayCount = dayCount,
                            actions = linked,
                            onDayCount = { dayCount = it },
                            onOpenDay = { openDay = it.toEpochDay() },
                        )
                    }
                }

                AppTab.Details -> DetailsScreen(
                    state = state,
                    permanentlyDenied = permanentlyDenied,
                    actions = linked,
                )

                AppTab.Map -> MapScreen(
                    place = (state as? HomeUiState.Content)?.place,
                    skyDarkInk = skyPalette.darkInk,
                )
            }
            BottomBar(
                selected = tab,
                onSelect = {
                    tab = it
                    openDay = null
                    closeAlerts()
                },
                versionLabel = versionLabel,
                showAttribution = state is HomeUiState.Content,
                onVersionTap = onVersionTap,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            val content = state as? HomeUiState.Content
            val now = remember(content) { Instant.now() }
            val sheet = when {
                openAlert != null -> AlertSheet.One(openAlert.orEmpty(), alertFromList)
                alertsListOpen -> AlertSheet.All
                else -> null
            }
            AlertSheetHost(
                sheet = if (tab == AppTab.Home) sheet else null,
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
}
