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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.PollenStatus
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Ступень пыльцы так, как её пишет DWD: «0», «0-1», «1», «1-2», «2», «2-3», «3». */
internal fun pollenLevelText(level: PollenLevel): String =
    if (level.isBetween) "${level.whole}-${level.whole + 1}" else level.whole.toString()

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

/** Луна на экране проверки: пусто это настоящая луна, число это доля лунного месяца (луна показывается всегда). */
private val Moons = listOf<Pair<Double?, Int>>(
    null to R.string.check_moon_real,
    0.125 to R.string.check_moon_waxing_crescent,
    0.25 to R.string.check_moon_first_quarter,
    0.375 to R.string.check_moon_waxing_gibbous,
    0.5 to R.string.check_moon_full,
    0.625 to R.string.check_moon_waning_gibbous,
    0.75 to R.string.check_moon_last_quarter,
    0.875 to R.string.check_moon_waning_crescent,
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
    pollen: PollenCheck?,
    pollenSample: Boolean,
    alertSample: Int,
    onOverride: (SkyOverride) -> Unit,
    onGlassBlur: (Boolean) -> Unit,
    onPollenSample: (Boolean) -> Unit,
    onAlertSample: (Int) -> Unit,
    onSampleAlertNotice: () -> Unit,
    onSampleRainNotice: () -> Unit,
    onSamplePollenNotice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    // Счётчик кадров один на свёрнутую и развёрнутую панель: он считает и пока панель свёрнута.
    val meter = rememberFrameMeter()
    if (collapsed) {
        CollapsedBar(meter = meter, onExpand = { collapsed = false }, onClose = onClose, modifier = modifier)
    } else {
        ExpandedPanel(
            input = input,
            override = override,
            diagnostics = diagnostics,
            meter = meter,
            glassBlur = glassBlur,
            pollen = pollen,
            pollenSample = pollenSample,
            alertSample = alertSample,
            onOverride = onOverride,
            onGlassBlur = onGlassBlur,
            onPollenSample = onPollenSample,
            onAlertSample = onAlertSample,
            onSampleAlertNotice = onSampleAlertNotice,
            onSampleRainNotice = onSampleRainNotice,
            onSamplePollenNotice = onSamplePollenNotice,
            onCollapse = { collapsed = true },
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/**
 * Свёрнутая панель оставляет на виду весь экран: видно, как текст и стекло лежат на небе. В полосе одна строка со
 * счётчиком кадров (обычное время кадра и кадры в секунду), чтобы снимать цифры на любом экране приложения.
 */
@Composable
private fun CollapsedBar(meter: FrameMeter, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.check_title), style = pogodaText(14f, 700, color = PanelText))
            Text(
                stringResource(
                    R.string.check_frames_short,
                    meter.summary.median.format(1),
                    meter.summary.perSecond.format(1),
                ),
                style = pogodaText(12f, 500, color = PanelTextDim, tabular = true),
                maxLines = 1,
            )
        }
        CheckButton(stringResource(R.string.check_expand), selected = false, onClick = onExpand)
        CheckButton(stringResource(R.string.check_close), selected = false, onClick = onClose)
    }
}

@Composable
private fun ExpandedPanel(
    input: SkyInput?,
    override: SkyOverride,
    diagnostics: SkyDiagnostics,
    meter: FrameMeter,
    glassBlur: Boolean,
    pollen: PollenCheck?,
    pollenSample: Boolean,
    alertSample: Int,
    onOverride: (SkyOverride) -> Unit,
    onGlassBlur: (Boolean) -> Unit,
    onPollenSample: (Boolean) -> Unit,
    onAlertSample: (Int) -> Unit,
    onSampleAlertNotice: () -> Unit,
    onSampleRainNotice: () -> Unit,
    onSamplePollenNotice: () -> Unit,
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
        // Сразу под шапкой: так ряд виден без прокрутки панели (на медленном эмуляторе прокрутка ненадёжна).
        AlertSection(
            count = alertSample,
            onCount = onAlertSample,
            onNotice = onSampleAlertNotice,
            onRainNotice = onSampleRainNotice,
            onPollenNotice = onSamplePollenNotice,
        )

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
                onClick = { onOverride(override.copy(condition = condition, wet = null)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckButton(
                text = stringResource(R.string.check_wx_wind),
                selected = override.windy == true,
                onClick = { onOverride(override.copy(windy = if (override.windy == true) null else true)) },
                modifier = Modifier.weight(1f),
            )
            // Мокрая гроза (значок с каплями под облаком): на настоящих данных её не вызвать без осадков в этот час.
            CheckButton(
                text = stringResource(R.string.check_wx_storm_wet),
                selected = override.wet == true,
                onClick = {
                    onOverride(
                        if (override.wet == true) override.copy(wet = null) else override.copy(condition = Condition.Storm, wet = true),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        Caption(stringResource(R.string.check_moon_title))
        ButtonGrid(columns = 2, items = Moons) { (cycle, label) ->
            CheckButton(
                text = stringResource(label),
                selected = override.moonCycle == cycle,
                onClick = { onOverride(override.copy(moonCycle = cycle)) },
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
        FrameSection(meter = meter, glassBlur = glassBlur, onGlassBlur = onGlassBlur)
        PollenSection(pollen = pollen, sample = pollenSample, onSample = onPollenSample)
    }
}

/**
 * Учебные предупреждения: сколько подставить на «Главную» вместо настоящих (0: настоящие). Кнопка ниже присылает учебное
 * уведомление о предупреждении: видно, как оно приходит, звучит и открывает плашку. Ещё две кнопки присылают учебные
 * «Скоро дождь» и уведомление о пыльце (0.7.13).
 */
@Composable
private fun AlertSection(
    count: Int,
    onCount: (Int) -> Unit,
    onNotice: () -> Unit,
    onRainNotice: () -> Unit,
    onPollenNotice: () -> Unit,
) {
    Caption(stringResource(R.string.check_alerts_title))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AlertSampleCounts.forEach { option ->
            CheckButton(
                if (option == 0) stringResource(R.string.check_alerts_none) else stringResource(R.string.check_alerts_count, option),
                selected = option == count,
                onClick = { onCount(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    CheckButton(stringResource(R.string.check_alerts_notice), selected = false, onClick = onNotice, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckButton(stringResource(R.string.check_notice_rain), selected = false, onClick = onRainNotice, modifier = Modifier.weight(1f))
        CheckButton(stringResource(R.string.check_notice_pollen), selected = false, onClick = onPollenNotice, modifier = Modifier.weight(1f))
    }
}

/** Варианты числа учебных предупреждений: ни одного, одно, три (все видны) и все (видны три и строка «Ещё N»). */
private val AlertSampleCounts = listOf(0, 1, 3, SAMPLE_ALERTS)

/**
 * Пыльца DWD: чем кончилась загрузка (вне сезона все значения нулевые, и по пустой плитке не понять, работает ли загрузка),
 * район, день выпуска и значения на сегодня. Кнопка подставляет пример, чтобы увидеть плитку с данными.
 */
@Composable
private fun PollenSection(pollen: PollenCheck?, sample: Boolean, onSample: (Boolean) -> Unit) {
    Caption(stringResource(R.string.check_pollen_title))
    if (pollen == null) {
        Info(stringResource(R.string.check_pollen_no_place))
    } else {
        Info(
            stringResource(
                R.string.check_pollen_status,
                stringResource(
                    when (pollen.status) {
                        PollenStatus.Pending -> R.string.check_pollen_pending
                        PollenStatus.OutsideGermany -> R.string.check_pollen_outside
                        PollenStatus.Unavailable -> R.string.check_pollen_unavailable
                        PollenStatus.Loaded -> R.string.check_pollen_loaded
                    },
                ),
            ),
        )
        val forecast = pollen.forecast
        if (forecast != null) {
            Info(stringResource(R.string.check_pollen_region, forecast.regionKey, forecast.issued.toString()))
            val kinds = stringArrayResource(R.array.pollen_kinds)
            val today = forecast.on(pollen.today)
            val text = if (today == null) {
                stringResource(R.string.check_pollen_no_day)
            } else {
                today.entries.joinToString(", ") { (kind, level) -> "${kinds[kind.ordinal]} ${pollenLevelText(level)}" }
            }
            Info(stringResource(R.string.check_pollen_values, text))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        CheckButton(
            stringResource(if (sample) R.string.check_pollen_sample_off else R.string.check_pollen_sample_on),
            selected = sample,
            onClick = { onSample(!sample) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Счётчик кадров окна: считает кадры по `FrameMetrics`, пока он в композиции, и раз в секунду обновляет сводку
 * ([summary]). Один на свёрнутую и развёрнутую панель.
 */
internal class FrameMeter(val stats: FrameStats, val refreshRate: Float) {
    var summary by mutableStateOf(stats.summary(SystemClock.elapsedRealtimeNanos()))
        private set

    fun refresh() {
        summary = stats.summary(SystemClock.elapsedRealtimeNanos())
    }

    fun reset() {
        stats.reset()
        refresh()
    }
}

@Composable
private fun rememberFrameMeter(): FrameMeter {
    val view = LocalView.current
    val refreshRate = view.display?.refreshRate ?: DEFAULT_REFRESH_RATE
    val meter = remember(refreshRate) { FrameMeter(FrameStats(budgetMillis = 1000.0 / refreshRate), refreshRate) }
    DisposableEffect(view, meter) {
        val window = view.context.findActivity()?.window
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            meter.stats.add(
                SystemClock.elapsedRealtimeNanos(),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION) / NANOS_PER_MILLI,
            )
        }
        window?.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        onDispose { window?.removeOnFrameMetricsAvailableListener(listener) }
    }
    LaunchedEffect(meter) {
        while (true) {
            delay(1000)
            meter.refresh()
        }
    }
    return meter
}

@Composable
private fun FrameSection(meter: FrameMeter, glassBlur: Boolean, onGlassBlur: (Boolean) -> Unit) {
    val summary = meter.summary
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
                meter.reset()
            },
        )
        CheckButton(
            stringResource(R.string.check_frames_reset),
            selected = false,
            onClick = { meter.reset() },
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
    Info(stringResource(R.string.check_frames_rate, summary.perSecond.format(1), meter.refreshRate.toDouble().format(0)))
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
