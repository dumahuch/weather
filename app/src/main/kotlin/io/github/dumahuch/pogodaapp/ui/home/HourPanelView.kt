package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.home.HourPanel
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.ZoneId
import java.util.Locale

/** Выбранный час с его панелью; [point] и [panel] есть всегда вместе. */
class HourSelection(val point: HourPoint, val panel: HourPanel)

/**
 * Куда «Главная» узнаёт высоту панели: [now] сколько занимает панель сейчас (растёт и сжимается вместе с анимацией),
 * [target] сколько займёт, когда анимация кончится (0 у свёрнутой). По ним главный блок сразу берёт конечный размер и
 * плавно приходит к нему за то же время, что панель раскрывается, и страница не вылезает за экран посреди анимации.
 */
internal class HourPanelReport(val now: (Int) -> Unit, val target: (Int) -> Unit)

/** Пусто там, где высоту панели сообщать некому (подробности дня: страница прокручивается). */
internal val LocalHourPanelReport = staticCompositionLocalOf<HourPanelReport?> { null }

/** Последний показанный выбор: при сворачивании панель плавно исчезает с прежним текстом, а не пустой. */
private class LastSelection {
    var value: HourSelection? = null
}

/**
 * Панель под лентой часов: раскрывается плавно, при смене часа меняет текст на месте, при сворачивании не прыгает.
 * Всё, что ниже панели, сдвигается вместе с её высотой.
 */
@Composable
fun HourPanelHost(selection: HourSelection?, zone: ZoneId, modifier: Modifier = Modifier) {
    val last = remember { LastSelection() }
    if (selection != null) last.value = selection
    val report = LocalHourPanelReport.current
    var natural by remember { mutableIntStateOf(0) }
    val open = selection != null
    LaunchedEffect(open, natural) { report?.target?.invoke(if (open) natural else 0) }
    DisposableEffect(report) {
        onDispose {
            report?.now?.invoke(0)
            report?.target?.invoke(0)
        }
    }
    AnimatedVisibility(
        visible = open,
        modifier = modifier.onSizeChanged { report?.now?.invoke(it.height) },
        enter = expandVertically(tween(PANEL_MS)) + fadeIn(tween(PANEL_MS)),
        exit = shrinkVertically(tween(PANEL_MS)) + fadeOut(tween(PANEL_MS * 3 / 4)),
    ) {
        Column(Modifier.onSizeChanged { natural = it.height }) {
            last.value?.let { HourPanelContent(it, zone) }
        }
    }
}

/** Столько же, сколько главный блок меняет размер ([GROW_MS] в HomeMiddle): оба идут по одной кривой. */
private const val PANEL_MS = 300

@Composable
private fun HourPanelContent(selection: HourSelection, zone: ZoneId) {
    val palette = LocalPogodaPalette.current
    val panel = selection.panel
    val hour = selection.point.time.atZone(zone).hour
    val kmh = stringResource(R.string.kmh_unit)
    val hpa = stringResource(R.string.hpa_unit)
    val directions = stringArrayResource(R.array.wind_from)
    val strengths = stringArrayResource(R.array.beaufort_names)

    val cells = buildList {
        panel.pressureHpa?.let { add(stringResource(R.string.tile_pressure) to "$it $hpa") }
        panel.feelsLikeC?.let { add(stringResource(R.string.feels_like) to temperatureText(it)) }
        panel.humidityPercent?.let { add(stringResource(R.string.tile_humidity) to "$it %") }
        panel.dewPointC?.let { add(stringResource(R.string.hour_dew_point) to temperatureText(it)) }
    }
    val windLine = panel.windKmh?.let { speed ->
        val word = strengths[panel.windStep ?: 0]
        val from = panel.windFrom?.let { directions[it.ordinal] }
        val text = if (from != null) "$word $from" else word
        if (panel.windStep == 0) text else "$text, $speed $kmh"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp)
            .animateContentSize(tween(PANEL_MS)),
    ) {
        Text(
            stringResource(
                R.string.hour_panel_header,
                "%02d:00".format(Locale.ROOT, hour),
                conditionName(selection.point.condition),
            ),
            style = pogodaText(13f, 700, tabular = true),
        )
        Spacer(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.ink.copy(alpha = 0.14f)),
        )
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                pair.forEach { (label, value) -> PanelCell(label, value, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (windLine != null) {
            PanelCell(
                stringResource(R.string.tile_wind_now),
                windLine,
                Modifier.fillMaxWidth().padding(top = 10.dp),
                sub = panel.gustKmh?.let { stringResource(R.string.gusts_up_to, it, kmh) },
            )
        }
    }
}

@Composable
private fun PanelCell(label: String, value: String, modifier: Modifier, sub: String? = null) {
    val palette = LocalPogodaPalette.current
    Column(modifier) {
        Text(label, style = pogodaText(11.5f, 400, color = palette.ink2))
        Text(value, style = pogodaText(14f, 500, tabular = true), modifier = Modifier.padding(top = 1.dp))
        if (sub != null) {
            Text(sub, style = pogodaText(12f, 400, color = palette.ink2, tabular = true))
        }
    }
}
