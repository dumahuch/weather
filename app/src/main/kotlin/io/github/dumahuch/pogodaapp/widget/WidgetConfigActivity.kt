package io.github.dumahuch.pogodaapp.widget

import android.appwidget.AppWidgetManager
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.PogodaTheme
import kotlin.math.roundToInt

/**
 * Настройка виджета при добавлении (и позже, виджет объявлен «reconfigurable»): фон «Стекло» или «Без фона» и, для стекла,
 * его непрозрачность. Выбор хранится для каждого виджета отдельно ([WidgetStyleStore]). Если человек вышел без «Готово»,
 * система не добавляет виджет. Окно открывает лаунчер, поэтому активность объявлена доступной снаружи (только для действия настройки).
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val store = WidgetStyleStore(this)
        setContent {
            PogodaTheme {
                ConfigScreen(initial = store.read(widgetId)) { style ->
                    store.write(widgetId, style)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    WidgetUpdater.renderInBackground(this, intArrayOf(widgetId))
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(initial: WidgetStyle, onDone: (WidgetStyle) -> Unit) {
    var background by remember { mutableStateOf(initial.background) }
    var opacity by remember { mutableFloatStateOf(initial.opacity.toFloat()) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
        BackgroundOption(R.string.widget_bg_glass, background == WidgetBackground.Glass) { background = WidgetBackground.Glass }
        BackgroundOption(R.string.widget_bg_none, background == WidgetBackground.None) { background = WidgetBackground.None }
        if (background == WidgetBackground.Glass) {
            Text(stringResource(R.string.widget_opacity, opacity.roundToInt()))
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = WidgetStyle.MIN_OPACITY.toFloat()..WidgetStyle.MAX_OPACITY.toFloat(),
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { onDone(WidgetStyle(background, opacity.roundToInt())) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.widget_done))
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
