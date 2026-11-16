package io.github.dumahuch.pogodaapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import kotlinx.coroutines.delay

/**
 * Вкладки с небом («Главная», «Подробно», «Прогноз») собираются один раз и держатся собранными (0.8.56): по стенду сборка
 * при каждой смене стоила 75–420 мс, и всё это время на экране было пустое небо. Размещается и рисуется только [active];
 * остальные собраны и измерены, но не на экране: не рисуются, не считают размытие и не принимают нажатий. Вкладки,
 * где ещё не были, досоздаются по одной, когда данные уже есть ([ready]), чтобы и первая смена шла без сборки.
 * Карта сюда не входит: она тяжёлая и рисуется своей поверхностью.
 */
@Composable
internal fun TabDeck(
    active: Int,
    all: List<Int>,
    ready: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    var warm by remember { mutableStateOf(setOf(active)) }
    SideEffect { if (active !in warm) warm = warm + active }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        delay(PREWARM_START_MS)
        while (true) {
            val next = nextToWarm(warm, all) ?: break
            warm = warm + next
            delay(PREWARM_STEP_MS)
        }
    }
    Layout(
        content = {
            tabsToKeep(warm, active).forEach { tab ->
                key(tab) { Box(Modifier.layoutId(tab)) { content(tab) } }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.firstOrNull { it.first == active }?.second?.place(0, 0)
        }
    }
}

/** Какие вкладки держать собранными: те, где уже были, и текущая; порядок постоянный, чтобы сборка не терялась. */
internal fun tabsToKeep(warm: Set<Int>, active: Int): List<Int> = (warm + active).sorted()

/** Какую вкладку собрать заранее следующей; пусто, если собраны все. */
internal fun nextToWarm(warm: Set<Int>, all: List<Int>): Int? = all.firstOrNull { it !in warm }

/** Сколько ждать после появления данных, прежде чем собирать остальные вкладки: сначала пусть покажется первая. */
private const val PREWARM_START_MS = 1500L

/** Пауза между заранее собираемыми вкладками: по одной, чтобы не было одного длинного кадра. */
private const val PREWARM_STEP_MS = 700L
