package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Насколько прокручен экран, который сейчас на виду: от этого гряды фона «Ди» поднимаются (ближние сильнее дальних).
 * Читается только при рисовании неба и при расчёте цветов, поэтому прокрутка не пересобирает экран.
 *
 * Экранов, которые сообщают прокрутку, несколько: три вкладки колоды собраны одновременно, а над вкладкой может лежать
 * страница подробностей. Небо следует за тем, кто сообщил последним. Когда он уходит, небо следует за тем, кто сообщал
 * перед ним (раньше прокрутка обнулялась, и после закрытия страницы небо стояло в нуле, пока вкладку не прокрутят заново).
 */
@Stable
internal class SkyParallax {
    /** Один сообщающий экран: пиксели прокрутки вниз, протягивание вниз для обновления даёт отрицательное значение. */
    @Stable
    class Source {
        var px by mutableFloatStateOf(0f)
    }

    /** Сообщавшие экраны по порядку последнего сообщения: последний в списке задаёт небо. */
    private val order = mutableStateListOf<Source>()

    /** Прокрутка экрана, сообщавшего последним; 0, пока никто не сообщал. */
    val scrollPx: Float get() = order.lastOrNull()?.px ?: 0f

    fun report(source: Source, px: Float) {
        source.px = px
        if (order.lastOrNull() !== source) {
            order.remove(source)
            order.add(source)
        }
    }

    fun detach(source: Source) {
        order.remove(source)
    }
}

internal val LocalSkyParallax = staticCompositionLocalOf<SkyParallax?> { null }

/**
 * Сообщает небу прокрутку экрана [scroll] (и сдвиг протягивания [pull], пиксели вниз). Когда экран уходит, небо снова
 * следует за тем, кто сообщал перед ним.
 */
@Composable
internal fun ReportSkyScroll(scroll: ScrollState, pull: () -> Float = { 0f }) {
    val parallax = LocalSkyParallax.current ?: return
    val latestPull by rememberUpdatedState(pull)
    val source = remember(parallax) { SkyParallax.Source() }
    LaunchedEffect(scroll, source) {
        snapshotFlow { scroll.value - latestPull() }.collect { parallax.report(source, it) }
    }
    DisposableEffect(parallax, source) {
        onDispose { parallax.detach(source) }
    }
}
