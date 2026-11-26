package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.collectLatest

/**
 * Насколько прокручен экран, который сейчас на виду: от этого гряды фона «Ди» поднимаются (ближние сильнее дальних).
 * Читается только при рисовании неба и при расчёте цветов, поэтому прокрутка не пересобирает экран.
 *
 * Экранов, которые сообщают прокрутку, несколько: три вкладки колоды собраны одновременно, а над вкладкой может лежать
 * страница подробностей. Небо следует за тем, кто сообщил последним. Когда он уходит (или сообщает новый), небо не
 * прыгает на прокрутку другого экрана, а плавно доезжает до неё ([settle]): Дима 26.11 «слои резко материализуются на
 * начальное положение».
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

    /** Поправка к прокрутке верхнего экрана: при смене экрана равна разнице со старым положением, затем плавно гаснет. */
    private var offset by mutableFloatStateOf(0f)

    /** Счётчик смен верхнего экрана: по нему [settle] понимает, что надо снова гасить поправку. */
    private var changes by mutableIntStateOf(0)

    private val target: Float get() = order.lastOrNull()?.px ?: 0f

    /** Прокрутка для неба: у верхнего экрана плюс гаснущая поправка; 0, пока никто не сообщал. */
    val scrollPx: Float get() = target + offset

    fun report(source: Source, px: Float) {
        val before = scrollPx
        source.px = px
        if (order.lastOrNull() !== source) {
            order.remove(source)
            order.add(source)
            keepContinuous(before)
        }
    }

    fun detach(source: Source) {
        val before = scrollPx
        val wasTop = order.lastOrNull() === source
        order.remove(source)
        if (wasTop) keepContinuous(before)
    }

    /** Небо в этот момент стоит там же, где стояло ([before]); к новому положению оно поедет в [settle]. */
    private fun keepContinuous(before: Float) {
        offset = before - target
        changes++
    }

    /** Мгновенно гасит поправку: для тестов, у которых нет часов анимации. */
    internal fun finishSettling() {
        offset = 0f
    }

    /** Гасит поправку плавно. Запускается один раз у хозяина неба и работает, пока он на экране. */
    suspend fun settle() {
        snapshotFlow { changes }.collectLatest {
            if (offset != 0f) {
                animate(offset, 0f, animationSpec = tween(SETTLE_MILLIS, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
            }
        }
    }
}

/** Сколько небо доезжает до прокрутки нового экрана после смены. */
internal const val SETTLE_MILLIS = 450

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
