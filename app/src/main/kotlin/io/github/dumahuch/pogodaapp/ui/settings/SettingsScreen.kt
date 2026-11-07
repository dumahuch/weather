package io.github.dumahuch.pogodaapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.CardShadow
import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import io.github.dumahuch.pogodaapp.settings.NotificationToggle
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.BackButton
import io.github.dumahuch.pogodaapp.ui.pogodaText

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Экран настроек (с версии 0.5.4): «Значки погоды» (step-27) и «Тень карточек», по два варианта, и «Уведомления» (0.7.0) с тремя
 * выключателями. Карточки с выбором сами стеклянные и лежат на небе, поэтому смена видна сразу. Открывается шестерёнкой
 * на «Главной», закрывается кнопкой «назад».
 */
@Composable
fun SettingsScreen(
    cardShadow: CardShadow,
    onCardShadow: (CardShadow) -> Unit,
    iconStyle: WeatherIconStyle,
    onIconStyle: (WeatherIconStyle) -> Unit,
    notifications: NotificationPrefs,
    onNotification: (NotificationToggle, Boolean) -> Unit,
    noticeError: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose)
            Text(stringResource(R.string.settings_title), style = pogodaText(20f, 600, lineHeight = 1.2f), maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().glassCard(CardShape, glass).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_icons),
                style = pogodaText(15f, 600),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
            )
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
        }
        Column(Modifier.fillMaxWidth().glassCard(CardShape, glass).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_card_shadow),
                style = pogodaText(15f, 600),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
            )
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
        }
        Column(Modifier.fillMaxWidth().glassCard(CardShape, glass).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_notifications),
                style = pogodaText(15f, 600),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
            )
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
            // Название места на заблокированном экране бывает только у записи со значком в строке состояния.
            ToggleOption(
                title = stringResource(R.string.settings_notify_lock),
                hint = stringResource(R.string.settings_notify_lock_hint),
                checked = notifications.placeOnLockScreen,
                enabled = notifications.inStatusBar,
                onChange = { onNotification(NotificationToggle.LockScreenPlace, it) },
            )
            // Ошибка уведомления (0.7.2): текст для снимка экрана, пока журнала падений у приложения нет.
            noticeError?.let {
                Text(
                    stringResource(R.string.settings_notify_error, it),
                    style = pogodaText(12f, 400, color = LocalPogodaPalette.current.ink2),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
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
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = pogodaText(16f, 500))
            Text(hint, style = pogodaText(13f, 400, color = palette.ink2))
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
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = palette.accent,
                disabledUncheckedThumbColor = palette.ink2,
                disabledUncheckedTrackColor = Color.Transparent,
                disabledUncheckedBorderColor = palette.ink2,
            ),
        )
    }
}

/** Прозрачность недоступного выключателя (строка сама бледнеет, цвета переключателя прежние). */
private const val DISABLED_ALPHA = 0.45f

/** Один вариант: название, пояснение и галочка у выбранного. Нажатие по всей строке (высота не меньше 60 dp). */
@Composable
private fun ChoiceOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = pogodaText(16f, if (selected) 600 else 500))
            Text(hint, style = pogodaText(13f, 400, color = palette.ink2))
        }
        if (selected) {
            LineIconView(LineIcons.Check, 22.dp, palette.accent)
        } else {
            Spacer(Modifier.size(22.dp))
        }
    }
}
