package io.github.dumahuch.pogodaapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.PogodaTheme
import kotlin.math.roundToInt

/**
 * Настройка виджета при добавлении (и позже, виджет объявлен «reconfigurable»): сколько сведений (слайдер из трёх делений),
 * фон «Небо», «Стекло» или «Без фона» (затемнение неба или непрозрачность стекла) и набор значков (на небе значка нет).
 * Выбор хранится для каждого виджета отдельно ([WidgetStyleStore]). Если человек вышел без «Готово», система не добавляет
 * виджет. Окно открывает лаунчер, поэтому активность объявлена доступной снаружи (только для действия настройки).
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        // Окно открыто снаружи (его зовёт лаунчер, но может и чужое приложение): чужой или выдуманный номер не принимаем.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || !isOurWidget(widgetId)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val store = WidgetStyleStore(this)
        val appIcons = AppSettings(PreferencesSettingsStorage(this)).iconStyle.value
        setContent {
            PogodaTheme {
                ConfigScreen(initial = store.read(widgetId), appIcons = appIcons) { style ->
                    store.write(widgetId, style)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    WidgetUpdater.renderInBackground(this, intArrayOf(widgetId))
                    finish()
                }
            }
        }
    }
}

/** Номер выдан системой одному из наших виджетов (в том числе только что добавляемому, пока он ждёт настройки). */
private fun Context.isOurWidget(widgetId: Int): Boolean =
    widgetId in AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, WeatherWidgetProvider::class.java))

@Composable
private fun ConfigScreen(initial: WidgetStyle, appIcons: WeatherIconStyle, onDone: (WidgetStyle) -> Unit) {
    var background by remember { mutableStateOf(initial.background) }
    val opacity = remember {
        SliderState(initial.opacity.toFloat(), 0, WidgetStyle.MIN_OPACITY.toFloat()..WidgetStyle.MAX_OPACITY.toFloat())
    }
    val shade = remember {
        SliderState(initial.shade.toFloat(), 0, WidgetStyle.MIN_SHADE.toFloat()..WidgetStyle.MAX_SHADE.toFloat())
    }
    var detail by remember { mutableStateOf(initial.detail) }
    var icons by remember { mutableStateOf(initial.icons ?: appIcons) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.widget_detail_title), style = MaterialTheme.typography.titleMedium)
            DetailSlider(detail) { detail = it }
            BackgroundOption(R.string.widget_bg_sky, background == WidgetBackground.Sky) { background = WidgetBackground.Sky }
            BackgroundOption(R.string.widget_bg_glass, background == WidgetBackground.Glass) { background = WidgetBackground.Glass }
            BackgroundOption(R.string.widget_bg_none, background == WidgetBackground.None) { background = WidgetBackground.None }
            when (background) {
                WidgetBackground.Sky -> {
                    Text(stringResource(R.string.widget_shade, shade.value.roundToInt()))
                    Slider(state = shade)
                    Text(stringResource(R.string.widget_icons_none_on_sky), style = MaterialTheme.typography.bodyMedium)
                }
                WidgetBackground.Glass -> {
                    Text(stringResource(R.string.widget_opacity, opacity.value.roundToInt()))
                    Slider(state = opacity)
                }
                WidgetBackground.None -> Unit
            }
            if (background != WidgetBackground.Sky) {
                Text(stringResource(R.string.settings_icons), style = MaterialTheme.typography.titleMedium)
                BackgroundOption(R.string.settings_icons_glass, icons == WeatherIconStyle.Glass) { icons = WeatherIconStyle.Glass }
                BackgroundOption(R.string.settings_icons_outline, icons == WeatherIconStyle.Outline) { icons = WeatherIconStyle.Outline }
            }
        }
        Button(
            onClick = {
                onDone(WidgetStyle(background, opacity.value.roundToInt(), shade.value.roundToInt(), detail, icons.takeIf { it != appIcons }))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.widget_done))
        }
    }
}

/** Слайдер из трёх делений «Минимум, Обычно, Подробно»: подписи под ним, выбранная жирная. */
@Composable
private fun DetailSlider(detail: WidgetDetail, onChange: (WidgetDetail) -> Unit) {
    val labels = listOf(R.string.widget_detail_minimum, R.string.widget_detail_normal, R.string.widget_detail_full)
    val state = remember {
        SliderState(detail.ordinal.toFloat(), WidgetDetail.entries.size - 2, 0f..(WidgetDetail.entries.size - 1).toFloat())
    }
    val report by rememberUpdatedState(onChange)
    LaunchedEffect(state) { snapshotFlow { state.value }.collect { report(WidgetDetail.fromIndex(it.roundToInt())) } }
    Column {
        Slider(state = state)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == detail.ordinal) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BackgroundOption(label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label))
    }
}
