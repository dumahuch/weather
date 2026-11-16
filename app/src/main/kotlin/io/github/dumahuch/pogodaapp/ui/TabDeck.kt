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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Вкладки с небом («Главная», «Подробно», «Прогноз») собираются один раз и держатся собранными (0.8.56): по стенду сборка
 * при каждой смене стоила 75–420 мс, и всё это время на экране было пустое небо. Размещается и рисуется только [active];
 * остальные собраны и измерены, но не на экране: не рисуются, не считают размытие и не принимают нажатий. Вкладки,
 * где ещё не были, досоздаются по одной, когда данные уже есть ([ready]), чтобы и первая смена шла без сборки.
 * С 0.8.58 досозданная вкладка один раз рисуется за краем экрана в скрытый слой и снимается в картинку: первая
 * отрисовка (по стенду 130–190 мс) проходит заранее, а не на первой смене. Карта в колоду не входит (она рисуется своей
 * поверхностью), но пока открыта карта, [active] не из [all]: на экране ничего из колоды, а собранное держится.
 */
@Composable
internal fun TabDeck(
    active: Int,
    all: List<Int>,
    ready: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    var warm by remember { mutableStateOf(if (active in all) setOf(active) else emptySet()) }
    SideEffect { if (active in all && active !in warm) warm = warm + active }
    // Вкладка, которую сейчас рисуем заранее, и знак, что её кадр записан в слой.
    var painting by remember { mutableStateOf<Pair<Int, CompletableDeferred<Unit>>?>(null) }
    val paintLayer = rememberGraphicsLayer()
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        delay(PREWARM_START_MS)
        while (true) {
            val next = nextToWarm(warm, all) ?: break
            val drawn = CompletableDeferred<Unit>()
            painting = next to drawn
            warm = warm + next
            try {
                withTimeoutOrNull(PAINT_TIMEOUT_MS) {
                    drawn.await()
                    // Снимок прогоняет записанный кадр через видеокарту: так готовится и размытие, и всё, что рисуется впервые.
                    paintLayer.toImageBitmap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Не вышло нарисовать заранее: вкладка просто нарисуется при первой смене, как раньше.
            } finally {
                painting = null
            }
            delay(PREWARM_STEP_MS)
        }
    }
    val offscreen = offscreenTab(active, painting?.first)
    Layout(
        content = {
            tabsToKeep(warm, active, all).forEach { tab ->
                key(tab) {
                    Box(
                        Modifier
                            .layoutId(tab)
                            .drawWithContent {
                                val job = painting
                                if (job != null && job.first == tab && tab != active) {
                                    paintLayer.record { this@drawWithContent.drawContent() }
                                    job.second.complete(Unit)
                                } else {
                                    drawContent()
                                }
                            },
                    ) { content(tab) }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.firstOrNull { it.first == active }?.second?.place(0, 0)
            // За правым краем: не видна и не принимает нажатий, но рисуется.
            if (offscreen != null) placeables.firstOrNull { it.first == offscreen }?.second?.place(constraints.maxWidth, 0)
        }
    }
}

/**
 * Какие вкладки держать собранными: те, где уже были, и текущая, если она из колоды ([all]; карта нет); порядок
 * постоянный, чтобы сборка не терялась.
 */
internal fun tabsToKeep(warm: Set<Int>, active: Int, all: List<Int>): List<Int> =
    (if (active in all) warm + active else warm).sorted()

/** Какую вкладку разместить за краем экрана, чтобы нарисовать заранее: ту, что рисуем, если она не на экране. */
internal fun offscreenTab(active: Int, painting: Int?): Int? = painting?.takeIf { it != active }

/** Какую вкладку собрать заранее следующей; пусто, если собраны все. */
internal fun nextToWarm(warm: Set<Int>, all: List<Int>): Int? = all.firstOrNull { it !in warm }

/** Сколько ждать после появления данных, прежде чем собирать остальные вкладки: сначала пусть покажется первая. */
private const val PREWARM_START_MS = 1500L

/** Дольше не ждём кадра заранее рисуемой вкладки (например, приложение ушло в фон). */
private const val PAINT_TIMEOUT_MS = 1000L

/** Пауза между заранее собираемыми вкладками: по одной, чтобы не было одного длинного кадра. */
private const val PREWARM_STEP_MS = 700L
