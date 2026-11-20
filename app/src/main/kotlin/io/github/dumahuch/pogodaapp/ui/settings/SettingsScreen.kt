package io.github.dumahuch.pogodaapp.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.CardShadow
import io.github.dumahuch.pogodaapp.settings.MapPreload
import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import io.github.dumahuch.pogodaapp.settings.LockScreenForm
import io.github.dumahuch.pogodaapp.settings.NotificationToggle
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.GlassCardBox
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import io.github.dumahuch.pogodaapp.update.UpdateStatus
import io.github.dumahuch.pogodaapp.update.UpdateUi
import io.github.dumahuch.pogodaapp.weather.PollenKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val CardShape = RoundedCornerShape(24.dp)

/** Нет открытого раздела. */
private const val NONE = -1
private const val SECTION_VIEW = 0
private const val SECTION_NOTIFICATIONS = 1
private const val SECTION_ABOUT = 2
private const val SECTION_UPDATES = 3

/** Действия раздела «Обновления»: выключатель автопроверки, «Проверить сейчас», «Установить». */
data class UpdateActions(
    val onAuto: (Boolean) -> Unit,
    val onCheck: () -> Unit,
    val onInstall: () -> Unit,
)

/**
 * Экран настроек. Разделы «Вид» (значки погоды, тень карточек), «Уведомления» (выключатели, виды пыльцы) и «О приложении»
 * (источники данных и лицензии, с 0.7.13) свёрнуты в
 * карточки с названием, строкой о состоянии и стрелкой; нажатие раскрывает раздел на месте, открыт один (08.11.2026).
 * Карточки стеклянные и лежат на небе, поэтому смена вида видна сразу. Открывается шестерёнкой на «Главной», закрывается
 * системным жестом «назад» (кнопки «назад» в приложении нет, решение Димы 08.11.2026).
 */
@Composable
fun SettingsScreen(
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
    noticeError: String?,
    update: UpdateUi,
    updateActions: UpdateActions,
    openUpdates: Boolean,
    onOpenedUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableIntStateOf(NONE) }
    fun toggleSection(section: Int) {
        open = if (open == section) NONE else section
    }
    // Нажали уведомление «Доступна версия»: раздел «Обновления» сразу раскрыт.
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            open = SECTION_UPDATES
            onOpenedUpdates()
        }
    }
    // Небо едет вместе с прокруткой, как на остальных экранах (0.8.71): на неподвижном небе размытый фон под карточками
    // рвался при прокрутке (12.11, 36–58 px), из-за этого стекло в настройках было заменено плитой (0.7.17, 0.8.68).
    val scroll = rememberScrollState()
    ReportSkyScroll(scroll)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = pogodaText(20f, 600, lineHeight = 1.2f), maxLines = 1)
        val iconName = stringResource(
            if (iconStyle == WeatherIconStyle.Glass) R.string.settings_icons_glass else R.string.settings_icons_outline,
        )
        val shadowName = stringResource(
            if (cardShadow == CardShadow.Soft) R.string.settings_shadow_soft else R.string.settings_shadow_strong,
        )
        SectionCard(
            title = stringResource(R.string.settings_section_view),
            summary = "$iconName · $shadowName",
            expanded = open == SECTION_VIEW,
            onToggle = { toggleSection(SECTION_VIEW) },
        ) {
            SectionHeading(stringResource(R.string.settings_icons))
            Column(Modifier.selectableGroup()) {
                ChoiceOption(
                    title = stringResource(R.string.settings_icons_glass),
                    hint = stringResource(R.string.settings_icons_glass_hint),
                    selected = iconStyle == WeatherIconStyle.Glass,
                    onClick = { onIconStyle(WeatherIconStyle.Glass) },
                )
                ChoiceOption(
                    title = stringResource(R.string.settings_icons_outline),
                    hint = stringResource(R.string.settings_icons_outline_hint),
                    selected = iconStyle == WeatherIconStyle.Outline,
                    onClick = { onIconStyle(WeatherIconStyle.Outline) },
                )
            }
            SectionHeading(stringResource(R.string.settings_card_shadow))
            Column(Modifier.selectableGroup()) {
                ChoiceOption(
                    title = stringResource(R.string.settings_shadow_soft),
                    hint = stringResource(R.string.settings_shadow_soft_hint),
                    selected = cardShadow == CardShadow.Soft,
                    onClick = { onCardShadow(CardShadow.Soft) },
                )
                ChoiceOption(
                    title = stringResource(R.string.settings_shadow_strong),
                    hint = stringResource(R.string.settings_shadow_strong_hint),
                    selected = cardShadow == CardShadow.Strong,
                    onClick = { onCardShadow(CardShadow.Strong) },
                )
            }
            SectionHeading(stringResource(R.string.settings_map_preload))
            Column(Modifier.selectableGroup()) {
                ChoiceOption(
                    title = stringResource(R.string.settings_map_preload_always),
                    hint = stringResource(R.string.settings_map_preload_always_hint),
                    selected = mapPreload == MapPreload.Always,
                    onClick = { onMapPreload(MapPreload.Always) },
                )
                ChoiceOption(
                    title = stringResource(R.string.settings_map_preload_wifi),
                    hint = stringResource(R.string.settings_map_preload_wifi_hint),
                    selected = mapPreload == MapPreload.WifiOnly,
                    onClick = { onMapPreload(MapPreload.WifiOnly) },
                )
            }
        }
        val enabled = notifications.enabledCount
        SectionCard(
            title = stringResource(R.string.settings_notifications),
            summary = if (enabled == 0) {
                stringResource(R.string.settings_summary_off)
            } else {
                stringResource(R.string.settings_summary_on, enabled, NotificationPrefs.TOGGLE_COUNT)
            },
            expanded = open == SECTION_NOTIFICATIONS,
            onToggle = { toggleSection(SECTION_NOTIFICATIONS) },
        ) {
            // Уведомления запрещены в системе: выключатели ничего не дадут, нажатие ведёт в системные настройки (аудит У1).
            if (notificationsBlocked) {
                Text(
                    stringResource(R.string.settings_notify_blocked),
                    style = pogodaText(14f, 500, lineHeight = 1.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenNotificationSettings)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            GroupHeading(stringResource(R.string.settings_group_weather), first = true)
            ToggleOption(
                title = stringResource(R.string.settings_notify_shade),
                hint = stringResource(R.string.settings_notify_shade_hint),
                checked = notifications.inShade,
                onChange = { onNotification(NotificationToggle.Shade, it) },
            )
            ToggleOption(
                title = stringResource(R.string.settings_notify_status),
                hint = stringResource(R.string.settings_notify_status_hint),
                checked = notifications.inStatusBar,
                onChange = { onNotification(NotificationToggle.StatusBar, it) },
            )
            // Вид записи на заблокированном экране нужен, только пока запись о погоде вообще включена (решение Димы 19.11.2026).
            if (notifications.wanted) {
                GroupHeading(stringResource(R.string.settings_group_lock))
                Column(Modifier.selectableGroup()) {
                    LockScreenForm.entries.forEachIndexed { index, form ->
                        if (index > 0) Rule(start = SUB_INDENT + 18.dp, alpha = RULE_ALPHA_ITEM)
                        ChoiceOption(
                            title = stringResource(lockTitle(form)),
                            hint = stringResource(lockHint(form)),
                            selected = notifications.lockScreen == form,
                            onClick = { onLockScreen(form) },
                            nested = true,
                        )
                    }
                }
            }
            GroupHeading(stringResource(R.string.settings_group_events))
            ToggleOption(
                title = stringResource(R.string.settings_notify_alerts),
                hint = stringResource(R.string.settings_notify_alerts_hint),
                checked = notifications.alerts,
                onChange = { onNotification(NotificationToggle.Alerts, it) },
            )
            ToggleOption(
                title = stringResource(R.string.settings_notify_rain),
                hint = stringResource(R.string.settings_notify_rain_hint),
                checked = notifications.rainSoon,
                onChange = { onNotification(NotificationToggle.RainSoon, it) },
            )
            ToggleOption(
                title = stringResource(R.string.settings_notify_pollen),
                hint = stringResource(R.string.settings_notify_pollen_hint),
                checked = notifications.pollen,
                onChange = { onNotification(NotificationToggle.Pollen, it) },
            )
            // Виды пыльцы нужны только при включённом уведомлении о ней.
            if (notifications.pollen) {
                SectionHeading(stringResource(R.string.settings_pollen_kinds))
                val names = stringArrayResource(R.array.pollen_kinds)
                PollenKind.entries.forEach { kind ->
                    CheckOption(
                        title = names[kind.ordinal],
                        checked = kind in pollenKinds,
                        onChange = { onPollenKind(kind, it) },
                    )
                }
            }
            GroupHeading(stringResource(R.string.settings_group_summary))
            ToggleOption(
                title = stringResource(R.string.settings_notify_summary),
                hint = stringResource(R.string.settings_notify_summary_hint),
                checked = notifications.summary,
                onChange = { onNotification(NotificationToggle.Summary, it) },
            )
            // Время нужно только при включённой сводке.
            if (notifications.summary) {
                TimeOption(
                    title = stringResource(R.string.settings_notify_summary_time),
                    minutes = notifications.summaryMinutes,
                    onPick = onSummaryTime,
                )
            }
            // Ошибка уведомления (0.7.2): текст для снимка экрана, пока журнала падений у приложения нет.
            noticeError?.let {
                Text(
                    stringResource(R.string.settings_notify_error, it),
                    style = pogodaText(12f, 400, color = LocalPogodaPalette.current.ink2),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        SectionCard(
            title = stringResource(R.string.settings_section_updates),
            summary = if (update.offer != null) {
                stringResource(R.string.settings_updates_summary_available, update.offer.version)
            } else {
                stringResource(R.string.settings_updates_summary_installed, update.installed)
            },
            expanded = open == SECTION_UPDATES,
            onToggle = { toggleSection(SECTION_UPDATES) },
        ) {
            UpdatesContent(update, updateActions)
        }
        SectionCard(
            title = stringResource(R.string.settings_section_about),
            summary = stringResource(R.string.settings_about_summary),
            expanded = open == SECTION_ABOUT,
            onToggle = { toggleSection(SECTION_ABOUT) },
        ) {
            SourceLine(stringResource(R.string.about_forecast_title), stringResource(R.string.about_forecast_text))
            // Слово в слово по условию лицензии DWD (подпись нужна при любом использовании данных): не переводится.
            SourceLine(stringResource(R.string.about_dwd_title), stringResource(R.string.about_dwd_text))
            SourceLine(stringResource(R.string.about_places_title), stringResource(R.string.about_places_text))
            SourceLine(stringResource(R.string.about_updates_title), stringResource(R.string.about_updates_text))
            // Подложка BKG и пометка DWD о переработке (облака пересчитаны и перекрашены): по условиям, не переводится.
            SourceLine(
                stringResource(R.string.about_map_title),
                stringResource(R.string.about_map_text, LocalDate.now().year),
            )
        }
    }
}

/**
 * Раздел «Обновления» (0.8.69): автопроверка раз в сутки, «Проверить сейчас» со строкой состояния и, если вышла новая
 * версия, «Установить X» с размером, процентами скачивания или просьбой выдать разрешение на установку.
 */
@Composable
private fun UpdatesContent(update: UpdateUi, actions: UpdateActions) {
    ToggleOption(
        title = stringResource(R.string.settings_update_auto),
        hint = stringResource(R.string.settings_update_auto_hint),
        checked = update.auto,
        onChange = actions.onAuto,
    )
    val checked = update.checkedAt?.let { checkedText(it) }
    val status = when {
        update.status == UpdateStatus.Checking -> stringResource(R.string.settings_update_checking)
        update.status == UpdateStatus.Failed && update.offer == null && update.error != null ->
            stringResource(R.string.settings_update_failed, update.error)
        update.offer == null && checked != null -> stringResource(R.string.settings_update_latest, checked)
        update.offer == null -> stringResource(R.string.settings_update_never)
        else -> checked?.let { stringResource(R.string.settings_update_latest_checked, it) } ?: ""
    }
    ActionOption(
        title = stringResource(R.string.settings_update_check),
        hint = status,
        enabled = !update.busy,
        onClick = actions.onCheck,
    )
    val offer = update.offer ?: return
    val megabytes = remember(offer.size) { String.format(Locale.getDefault(), "%.1f", offer.size / BYTES_PER_MB) }
    val hint = when (update.status) {
        UpdateStatus.Downloading -> stringResource(R.string.settings_update_downloading, update.progress)
        UpdateStatus.Installing -> stringResource(R.string.settings_update_installing)
        UpdateStatus.NeedsPermission -> stringResource(R.string.settings_update_permission)
        UpdateStatus.Failed -> stringResource(R.string.settings_update_failed, update.error.orEmpty())
        else -> stringResource(R.string.settings_update_install_hint, megabytes)
    }
    ActionOption(
        title = stringResource(R.string.settings_update_install, offer.version),
        hint = hint,
        enabled = !update.busy,
        accent = true,
        onClick = actions.onInstall,
    )
}

private const val BYTES_PER_MB = 1024.0 * 1024.0

/** «18:20» для сегодняшней проверки, иначе дата и время коротко. */
private fun checkedText(at: Instant): String {
    val zone = ZoneId.systemDefault()
    val time = at.atZone(zone)
    val formatter = if (time.toLocalDate() == LocalDate.now(zone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return formatter.format(time)
}

/** Строка-кнопка: название (у главного действия цветом акцента), под ним состояние; недоступная бледнее. */
@Composable
private fun ActionOption(title: String, hint: String, enabled: Boolean, onClick: () -> Unit, accent: Boolean = false) {
    val palette = LocalPogodaPalette.current
    val titleColor = if (accent) palette.accent else LocalContentColor.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = pogodaText(16f, if (accent) 600 else 500, color = dimmed(titleColor, enabled)))
        if (hint.isNotEmpty()) Text(hint, style = pogodaText(13f, 400, color = dimmed(palette.ink2, enabled)))
    }
}

/** Строка раздела «О приложении»: источник и условие его использования. */
@Composable
private fun SourceLine(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = pogodaText(16f, 500))
        Text(text, style = pogodaText(13f, 400, color = LocalPogodaPalette.current.ink2))
    }
}

/**
 * Раздел настроек: свёрнутый это название, строка о состоянии и стрелка вниз, развёрнутый та же карточка, увеличенная
 * на месте (стрелка повёрнута вверх). Нажатие по всей шапке. Строки раскрываются и сворачиваются вместе с высотой карточки и
 * гаснут по пути (с 0.7.13: раньше при сворачивании они пропадали сразу, и пустая карточка секунду висела прежнего размера).
 * Стекло с размытием, как на остальных экранах (0.8.71): высокая раскрытая карточка делит размытие на куски ([GlassCardBox]).
 */
@Composable
private fun SectionCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "settings-arrow")
    GlassCardBox(CardShape, LocalGlass.current, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = pogodaText(17f, 600))
                    Text(summary, style = pogodaText(13f, 400, color = palette.ink2), maxLines = 1)
                }
                LineIconView(LineIcons.ChevronDown, 22.dp, palette.ink2, Modifier.rotate(angle))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(SECTION_MILLIS, easing = FastOutSlowInEasing)) + fadeIn(tween(SECTION_MILLIS)),
                exit = shrinkVertically(tween(SECTION_MILLIS, easing = FastOutSlowInEasing)) + fadeOut(tween(SECTION_MILLIS / 2)),
            ) {
                // animateContentSize: строки, которые появляются внутри раздела (виды пыльцы), сдвигают низ плавно.
                Column(Modifier.animateContentSize().padding(bottom = 10.dp), content = content)
            }
        }
    }
}

/** Длительность раскрытия и сворачивания раздела настроек, мс. */
private const val SECTION_MILLIS = 260

/**
 * Заголовок группы в карточке «Уведомления» (0.8.68): мелкие жирные заглавные буквы, у всех групп кроме первой тонкая
 * линия на всю ширину и увеличенный отступ сверху, чтобы группы не сливались в сплошной текст.
 */
@Composable
private fun GroupHeading(text: String, first: Boolean = false) {
    val ink2 = LocalPogodaPalette.current.ink2
    Column(Modifier.fillMaxWidth().padding(top = if (first) 6.dp else 14.dp)) {
        if (!first) Rule(start = 18.dp, alpha = RULE_ALPHA_GROUP)
        Text(
            text.uppercase(),
            style = pogodaText(11.5f, 700, color = ink2, letterSpacing = 0.8.sp),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = if (first) 0.dp else 14.dp, bottom = 4.dp),
        )
    }
}

/** Тонкая линия-разделитель: от [start] до правого поля 18 dp. */
@Composable
private fun Rule(start: Dp, alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start, end = 18.dp)
            .height(1.dp)
            .background(LocalPogodaPalette.current.ink2.copy(alpha = alpha)),
    )
}

/** Прозрачность линии между группами и между пунктами подгруппы (вторая слабее). */
private const val RULE_ALPHA_GROUP = 0.28f
private const val RULE_ALPHA_ITEM = 0.16f

private fun lockTitle(form: LockScreenForm): Int = when (form) {
    LockScreenForm.WithPlace -> R.string.settings_lock_place
    LockScreenForm.NoPlace -> R.string.settings_lock_no_place
    LockScreenForm.Compact -> R.string.settings_lock_compact
    LockScreenForm.Hidden -> R.string.settings_lock_hidden
}

private fun lockHint(form: LockScreenForm): Int = when (form) {
    LockScreenForm.WithPlace -> R.string.settings_lock_place_hint
    LockScreenForm.NoPlace -> R.string.settings_lock_no_place_hint
    LockScreenForm.Compact -> R.string.settings_lock_compact_hint
    LockScreenForm.Hidden -> R.string.settings_lock_hidden_hint
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = pogodaText(15f, 600),
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp),
    )
}

/**
 * Строка с галочкой для выбора нескольких из списка; нажатие по всей строке (высота не меньше 48 dp). У строк настроек нет
 * подсветки нажатия, наведения и фокуса (0.7.15): при прокрутке длинных карточек на строках появлялись серые полосы (Дима 11.11.2026).
 */
@Composable
private fun CheckOption(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, interactionSource = null, indication = null, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = pogodaText(16f, if (checked) 600 else 500), modifier = Modifier.weight(1f))
        if (checked) {
            LineIconView(LineIcons.Check, 22.dp, palette.accent)
        } else {
            Spacer(Modifier.size(22.dp))
        }
    }
}

/** Строка со временем: название слева, время справа; нажатие открывает системное окно выбора времени (формат 12 или 24 часа как в системе). */
@Composable
private fun TimeOption(title: String, minutes: Int, onPick: (Int) -> Unit) {
    val palette = LocalPogodaPalette.current
    val context = LocalContext.current
    val clock = remember(minutes) { LocalTime.of(minutes / 60, minutes % 60) }
    val text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(clock)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(hour * 60 + minute) },
                    minutes / 60,
                    minutes % 60,
                    DateFormat.is24HourFormat(context),
                ).show()
            }
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = pogodaText(16f, 500), modifier = Modifier.weight(1f))
        Text(text, style = pogodaText(16f, 600, color = palette.accent))
    }
}

/** Выключатель: название, пояснение и переключатель справа. Нажатие по всей строке; недоступная строка бледнее. */
@Composable
private fun ToggleOption(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, interactionSource = null, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = pogodaText(16f, 500, color = if (enabled) Color.Unspecified else dimmed(LocalContentColor.current, false)))
            Text(hint, style = pogodaText(13f, 400, color = dimmed(palette.ink2, enabled)))
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent,
                checkedBorderColor = palette.accent,
                uncheckedThumbColor = palette.ink2,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = palette.ink2,
                disabledCheckedThumbColor = dimmed(Color.White, enabled),
                disabledCheckedTrackColor = dimmed(palette.accent, enabled),
                disabledCheckedBorderColor = dimmed(palette.accent, enabled),
                disabledUncheckedThumbColor = dimmed(palette.ink2, enabled),
                disabledUncheckedTrackColor = Color.Transparent,
                disabledUncheckedBorderColor = dimmed(palette.ink2, enabled),
            ),
        )
    }
}

/** Прозрачность недоступного выключателя. Бледнеют цвета, а не вся строка: отдельный слой на строке давал серые полосы при прокрутке. */
private const val DISABLED_ALPHA = 0.45f

/** Цвет недоступной строки: тот же, но прозрачнее. */
private fun dimmed(color: Color, enabled: Boolean): Color = if (enabled) color else color.copy(alpha = color.alpha * DISABLED_ALPHA)

/** Сдвиг пунктов подгруппы вправо от обычного поля. */
private val SUB_INDENT = 12.dp

/**
 * Один вариант: название, пояснение и галочка у выбранного. Нажатие по всей строке (высота не меньше 60 dp).
 * [nested]: пункт подгруппы (0.8.68): сдвинут вправо, текст и интервал меньше, высота 52 dp, чтобы пункты читались одной группой.
 */
@Composable
private fun ChoiceOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit, nested: Boolean = false) {
    val palette = LocalPogodaPalette.current
    val titleSize = if (nested) 15f else 16f
    val hintSize = if (nested) 12f else 13f
    val lineHeight = if (nested) 1.15f else 1.25f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (nested) 52.dp else 60.dp)
            .selectable(selected = selected, interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (nested) SUB_INDENT + 18.dp else 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (nested) 1.dp else 2.dp)) {
            Text(title, style = pogodaText(titleSize, if (selected) (if (nested) 700 else 600) else 500, lineHeight = lineHeight))
            Text(hint, style = pogodaText(hintSize, 400, color = palette.ink2, lineHeight = lineHeight))
        }
        if (selected) {
            LineIconView(LineIcons.Check, 22.dp, palette.accent)
        } else {
            Spacer(Modifier.size(22.dp))
        }
    }
}
