package io.github.dumahuch.pogodaapp.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.map.MapDay
import io.github.dumahuch.pogodaapp.map.MapTimeline
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Ширина одного часа на шкале. */
private val HourWidth = 44.dp

/**
 * Часовая шкала-линейка: большие деления каждый час с подписью, около «сейчас» малые через 15 минут, метка выбранного
 * времени посередине. Шкалу листают пальцем (время меняется вместе с ней) или нажимают на деление. Прошедшее белым,
 * прогноз голубым. [onTime] вызывается на каждый сдвиг, [onSettled] когда палец отпущен или нажатие закончено
 * (по нему заказывается кадр, не на каждое промежуточное время).
 */
@Composable
fun TimeRuler(
    day: MapDay,
    time: Instant,
    now: Instant,
    zone: ZoneId,
    description: String,
    onTime: (Instant) -> Unit,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = pogodaText(10f, 500, tabular = true)
    val ticks = remember(day, now, zone) { MapTimeline.ticks(day, now, zone) }
    val anchor = remember(now) { MapTimeline.anchor(now) }
    val range = remember(day, now, zone) { MapTimeline.range(day, now, zone) }
    // Пока палец ведёт шкалу, она идёт за пальцем плавно; выбранное время (с шагом) приходит снаружи.
    var dragTime by remember { mutableStateOf<Instant?>(null) }
    val selected by rememberUpdatedState(time)
    val report by rememberUpdatedState(onTime)
    val settled by rememberUpdatedState(onSettled)

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(range) {
                val hourPx = HourWidth.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { dragTime = selected },
                    onDragEnd = { dragTime = null; settled() },
                    onDragCancel = { dragTime = null; settled() },
                ) { change, dx ->
                    change.consume()
                    val moved = (dragTime ?: selected).minus(Duration.ofMillis((dx / hourPx * HOUR_MS).toLong()))
                        .let { if (it.isBefore(range.first)) range.first else if (it.isAfter(range.second)) range.second else it }
                    dragTime = moved
                    report(moved)
                }
            }
            .pointerInput(day, now) {
                val hourPx = HourWidth.toPx()
                detectTapGestures { offset ->
                    val shift = (offset.x - size.width / 2f) / hourPx * HOUR_MS
                    report(selected.plus(Duration.ofMillis(shift.toLong())))
                    settled()
                }
            },
    ) {
        val hourPx = HourWidth.toPx()
        val center = size.width / 2f
        val shown = dragTime ?: time
        val baseline = size.height * 0.52f
        clipRect {
            for (tick in ticks) {
                val x = center + Duration.between(shown, tick.time).toMillis() / HOUR_MS.toFloat() * hourPx
                if (x < -hourPx || x > size.width + hourPx) continue
                val color = if (tick.time.isAfter(anchor)) palette.accent else palette.ink
                val height = if (tick.major) 14.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = color.copy(alpha = if (tick.major) 0.9f else 0.55f),
                    start = Offset(x, baseline - height),
                    end = Offset(x, baseline),
                    strokeWidth = 1.dp.toPx(),
                )
                if (tick.major) {
                    val label = tick.time.atZone(zone).hour.let { if (it == 0 && tick.time != ticks.first().time) 24 else it }
                    val layout = measurer.measure(label.toString(), labelStyle.copy(color = color.copy(alpha = 0.8f)))
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, baseline + 3.dp.toPx()))
                }
            }
        }
        drawLine(
            color = palette.ink,
            start = Offset(center, baseline - 20.dp.toPx()),
            end = Offset(center, baseline + 2.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private const val HOUR_MS = 3_600_000L
