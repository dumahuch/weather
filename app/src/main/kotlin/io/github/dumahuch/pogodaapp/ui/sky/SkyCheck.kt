package io.github.dumahuch.pogodaapp.ui.sky

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Состояние экрана проверки: открыт ли он и что подставлено вместо настоящих времени и погоды. */
internal class SkyCheckModel : ViewModel() {
    private val mutableOpen = MutableStateFlow(false)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

    private val mutableOverride = MutableStateFlow(SkyOverride())
    val override: StateFlow<SkyOverride> = mutableOverride.asStateFlow()

    private val mutableGlassBlur = MutableStateFlow(true)

    /** Размытие под стеклом включено. Выключатель нужен, чтобы сравнить кадры на телефоне с размытием и без. */
    val glassBlur: StateFlow<Boolean> = mutableGlassBlur.asStateFlow()

    fun show() {
        mutableOpen.value = true
    }

    /** При закрытии небо возвращается к настоящему времени и погоде, размытие включается. */
    fun close() {
        mutableOpen.value = false
        mutableOverride.value = SkyOverride()
        mutableGlassBlur.value = true
    }

    fun setGlassBlur(on: Boolean) {
        mutableGlassBlur.value = on
    }

    fun set(value: SkyOverride) {
        mutableOverride.value = value
    }
}

private val PanelBackground = Color(0xF5101722)
private val PanelText = Color(0xFFF4F7FB)
private val PanelTextDim = Color(0xFFB8C4D3)
private val ButtonIdle = Color(0x24FFFFFF)
private val ButtonSelected = Color(0x5CFFFFFF)
private val Russian: Locale = Locale.forLanguageTag("ru")

private val Scenes = listOf(
    SkyScene.Dawn to R.string.check_scene_dawn,
    SkyScene.Morning to R.string.check_scene_morning,
    SkyScene.Afternoon to R.string.check_scene_afternoon,
    SkyScene.Sunset to R.string.check_scene_sunset,
    SkyScene.Dusk to R.string.check_scene_dusk,
    SkyScene.Night to R.string.check_scene_night,
)

private val Weathers = listOf(
    Condition.Clear to R.string.check_wx_clear,
    Condition.Partly to R.string.check_wx_partly,
    Condition.Cloudy to R.string.check_wx_cloudy,
    Condition.Rain to R.string.check_wx_rain,
    Condition.Storm to R.string.check_wx_storm,
    Condition.Snow to R.string.check_wx_snow,
    Condition.Fog to R.string.check_wx_fog,
)

private fun Double.format(digits: Int): String = String.format(Russian, "%.${digits}f", this)

private fun clockText(solarHour: Double): String {
    val minutes = (solarHour * 60).roundToInt().coerceIn(0, 24 * 60)
    return String.format(Russian, "%02d:%02d", minutes / 60, minutes % 60)
}

/**
 * Экран проверки неба: шесть кнопок времени суток, ползунок солнечного времени, семь видов погоды,
 * «Сейчас» (вернуть настоящее), сведения о том, что видно, и счётчик кадров. Лежит внизу поверх «Главной»,
 * сверху остаётся небо с цифрами и значками. Служебный, только по-русски.
 */
@Composable
internal fun SkyCheckPanel(
    input: SkyInput?,
    override: SkyOverride,
    diagnostics: SkyDiagnostics,
    glassBlur: Boolean,
    onOverride: (SkyOverride) -> Unit,
    onGlassBlur: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    if (collapsed) {
        CollapsedBar(onExpand = { collapsed = false }, onClose = onClose, modifier = modifier)
    } else {
        ExpandedPanel(
            input = input,
            override = override,
            diagnostics = diagnostics,
            glassBlur = glassBlur,
            onOverride = onOverride,
            onGlassBlur = onGlassBlur,
            onCollapse = { collapsed = true },
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/** Свёрнутая панель оставляет на виду весь экран: видно, как текст и стекло лежат на небе. */
@Composable
private fun CollapsedBar(onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.check_title),
            style = pogodaText(14f, 700, color = PanelText),
            modifier = Modifier.weight(1f),
        )
        CheckButton(stringResource(R.string.check_expand), selected = false, onClick = onExpand)
        CheckButton(stringResource(R.string.check_close), selected = false, onClick = onClose)
    }
}

@Composable
private fun ExpandedPanel(
    input: SkyInput?,
    override: SkyOverride,
    diagnostics: SkyDiagnostics,
    glassBlur: Boolean,
    onOverride: (SkyOverride) -> Unit,
    onGlassBlur: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = diagnostics.snapshot
    Column(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.check_title),
                style = pogodaText(16f, 700, color = PanelText),
                modifier = Modifier.weight(1f),
            )
            CheckButton(stringResource(R.string.check_collapse), selected = false, onClick = onCollapse)
            CheckButton(stringResource(R.string.check_close), selected = false, onClick = onClose)
        }
        if (input == null) {
            Text(stringResource(R.string.check_no_forecast), style = pogodaText(13f, 500, color = PanelTextDim))
        }

        Caption(stringResource(R.string.check_scene_title))
        val strongest = snapshot?.weights?.maxByOrNull { it.second }?.first
        ButtonGrid(columns = 3, items = Scenes) { (scene, label) ->
            CheckButton(
                text = stringResource(label),
                selected = scene == strongest,
                onClick = {
                    if (input != null) {
                        val hour = SkyState.dominantSolarHour(scene, input.latitude, Instant.now())
                        onOverride(override.copy(solarHour = hour))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        val hour = override.solarHour ?: snapshot?.solarHour ?: 12.0
        Caption(stringResource(R.string.check_time_title, clockText(hour)))
        Slider(
            value = hour.toFloat(),
            onValueChange = { if (input != null) onOverride(override.copy(solarHour = it.toDouble())) },
            valueRange = 0f..24f,
        )

        Caption(stringResource(R.string.check_weather_title))
        ButtonGrid(columns = 3, items = Weathers) { (condition, label) ->
            CheckButton(
                text = stringResource(label),
                selected = override.condition == condition,
                onClick = { onOverride(override.copy(condition = condition)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            CheckButton(
                text = stringResource(R.string.check_now),
                selected = !override.active,
                onClick = { onOverride(SkyOverride()) },
                modifier = Modifier.weight(1f),
            )
        }

        if (snapshot != null) {
            Caption(stringResource(R.string.check_info_title))
            val names = Scenes.associate { it.first to stringResource(it.second) }
            val scenesText = snapshot.weights.joinToString(", ") { (scene, weight) ->
                "${names.getValue(scene)} ${(weight * 100).roundToInt()} %"
            }
            val ink = stringResource(if (snapshot.darkInk) R.string.check_info_ink_dark else R.string.check_info_ink_light)
            Info(stringResource(R.string.check_info_sun, snapshot.elevation.format(1)) + " · " + clockText(snapshot.solarHour))
            Info(stringResource(R.string.check_info_scenes, scenesText))
            Info(stringResource(R.string.check_info_clouds, snapshot.cloudPosition.format(2)))
            snapshot.load?.let { load ->
                Info(stringResource(R.string.check_info_load, load.scenes, load.firstMillis, load.totalMillis))
            }
            Info(
                stringResource(
                    R.string.check_info_contrast,
                    ink,
                    snapshot.hero.format(1),
                    snapshot.cards.format(1),
                    snapshot.buttons.format(1),
                ),
            )
        }
        FrameSection(glassBlur = glassBlur, onGlassBlur = onGlassBlur)
    }
}

@Composable
private fun FrameSection(glassBlur: Boolean, onGlassBlur: (Boolean) -> Unit) {
    val view = LocalView.current
    val refreshRate = view.display?.refreshRate ?: DEFAULT_REFRESH_RATE
    val stats = remember(refreshRate) { FrameStats(budgetMillis = 1000.0 / refreshRate) }
    var summary by remember(stats) { mutableStateOf(stats.summary(SystemClock.elapsedRealtimeNanos())) }
    DisposableEffect(view, stats) {
        val window = view.context.findActivity()?.window
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            stats.add(
                SystemClock.elapsedRealtimeNanos(),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION) / NANOS_PER_MILLI,
            )
        }
        window?.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        onDispose { window?.removeOnFrameMetricsAvailableListener(listener) }
    }
    LaunchedEffect(stats) {
        while (true) {
            delay(1000)
            summary = stats.summary(SystemClock.elapsedRealtimeNanos())
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Caption(stringResource(R.string.check_frames_title), modifier = Modifier.weight(1f))
        // Выключатель размытия рядом со счётчиком: так видно, как меняются кадры. Подпись показывает, что сейчас;
        // при переключении счётчик начинается заново, чтобы цифры относились только к одному положению.
        CheckButton(
            stringResource(if (glassBlur) R.string.check_glass_on else R.string.check_glass_off),
            selected = glassBlur,
            onClick = {
                onGlassBlur(!glassBlur)
                stats.reset()
                summary = stats.summary(SystemClock.elapsedRealtimeNanos())
            },
        )
        CheckButton(
            stringResource(R.string.check_frames_reset),
            selected = false,
            onClick = {
                stats.reset()
                summary = stats.summary(SystemClock.elapsedRealtimeNanos())
            },
        )
    }
    Info(stringResource(R.string.check_frames_total, summary.frames, summary.slow))
    Info(
        stringResource(
            R.string.check_frames_times,
            summary.median.format(1),
            summary.p95.format(1),
            summary.longest.format(1),
        ),
    )
    Info(stringResource(R.string.check_frames_rate, summary.perSecond.format(1), refreshRate.toDouble().format(0)))
}

private const val NANOS_PER_MILLI = 1e6
private const val DEFAULT_REFRESH_RATE = 60f

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = pogodaText(12f, 700, color = PanelTextDim), modifier = modifier.padding(top = 4.dp))
}

@Composable
private fun Info(text: String) {
    Text(text, style = pogodaText(12f, 500, color = PanelText, tabular = true))
}

/** Кнопки рядами по [columns] штук; в неполном ряду пустые места дополняются, чтобы кнопки были одной ширины. */
@Composable
private fun <T> ButtonGrid(columns: Int, items: List<T>, cell: @Composable RowScope.(T) -> Unit) {
    items.chunked(columns).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems.forEach { cell(it) }
            repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CheckButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ButtonSelected else ButtonIdle)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = pogodaText(13f, if (selected) 700 else 500, color = PanelText), maxLines = 1)
    }
}
