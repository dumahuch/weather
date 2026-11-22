package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.floor

/** Зона у круга осадков, где жест ловится, dp от края круга (Дима 22.11: водить пальцем вокруг круга, не по нему). */
private val CATCH_DP = 60.dp

/**
 * Выбор часа на круге осадков жестом вокруг него (Дима 22.11: палец по самому кругу закрывал бы столбики). Час берётся
 * по углу от центра круга, а не от места касания: палец может быть в зоне [CATCH_DP] вокруг круга или на нём, любое
 * касание или проведение там выбирает час, и страница не прокручивается. Вне зоны прокрутка работает как обычно.
 *
 * [content] получает выбранный час и модификатор для самого круга: по нему находится центр круга. Жест висит на всей
 * карточке, потому что зона шире круга и выходит за его границы.
 */
@Composable
internal fun RainRingPicker(
    values: List<Double?>,
    nowIndex: Int?,
    content: @Composable (selected: Int, ring: Modifier) -> Unit,
) {
    val peak = values.indices.filter { (values[it] ?: 0.0) > 0 }.maxByOrNull { values[it]!! }
    var selected by remember(values, nowIndex) { mutableIntStateOf(nowIndex ?: peak ?: HOURS / 2) }
    val frames = remember { Frames() }
    Box(
        Modifier
            .onGloballyPositioned { frames.box = it }
            .pointerInput(Unit) {
                val reach = CATCH_DP.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val ring = frames.ringInBox() ?: return@awaitEachGesture
                    if (!ring.inflate(reach).contains(down.position)) return@awaitEachGesture
                    selected = hourAt(down.position, ring.center)
                    down.consume()
                    drag(down.id) { change ->
                        selected = hourAt(change.position, ring.center)
                        change.consume()
                    }
                }
            },
    ) {
        content(selected, Modifier.onGloballyPositioned { frames.ring = it })
    }
}

/** Координаты карточки и круга; читаются только в момент касания, поэтому не состояние. */
private class Frames {
    var box: LayoutCoordinates? = null
    var ring: LayoutCoordinates? = null

    /** Границы круга в координатах карточки, пусто, пока что-то не размещено. */
    fun ringInBox(): Rect? {
        val b = box?.takeIf { it.isAttached } ?: return null
        val r = ring?.takeIf { it.isAttached } ?: return null
        return b.localBoundingBoxOf(r)
    }
}

/** Час суток (0..23) по углу точки [at] от центра [center], от верха по часовой. */
internal fun hourAt(at: Offset, center: Offset): Int {
    var angle = Math.toDegrees(atan2((at.x - center.x).toDouble(), -(at.y - center.y).toDouble()))
    if (angle < 0) angle += 360.0
    return floor(angle / DEGREES_PER_HOUR).toInt().coerceIn(0, HOURS - 1)
}
