package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Насколько прокручен экран, который сейчас на виду: от этого гряды фона «Ди» поднимаются (ближние сильнее дальних).
 * Читается только при рисовании неба и при расчёте цветов, поэтому прокрутка не пересобирает экран.
 */
@Stable
internal class SkyParallax {
    /** Пиксели прокрутки вниз; протягивание вниз для обновления даёт отрицательное значение. */
    var scrollPx by mutableFloatStateOf(0f)
}

internal val LocalSkyParallax = staticCompositionLocalOf<SkyParallax?> { null }

/**
 * Сообщает небу прокрутку экрана [scroll] (и сдвиг протягивания [pull], пиксели вниз). Когда экран уходит, прокрутка обнуляется.
 */
@Composable
internal fun ReportSkyScroll(scroll: ScrollState, pull: () -> Float = { 0f }) {
    val parallax = LocalSkyParallax.current ?: return
    val latestPull by rememberUpdatedState(pull)
    LaunchedEffect(scroll, parallax) {
        snapshotFlow { scroll.value - latestPull() }.collect { parallax.scrollPx = it }
    }
    DisposableEffect(parallax) {
        onDispose { parallax.scrollPx = 0f }
    }
}
