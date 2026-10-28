package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** На сколько нужно сдвинуть экран, чтобы отпускание запустило обновление. */
private val PullThreshold = 64.dp

/** На сколько экран остаётся сдвинутым, пока идёт обновление. */
private val PullHold = 56.dp

/** Дальше этого экран не тянется. */
private val PullMax = 100.dp

/** Сколько расстояния пальца приходится на сдвиг экрана: палец проходит вдвое больше, чем сдвигается экран. */
private const val PULL_RESISTANCE = 0.5f

private const val SETTLE_MS = 250

private val BadgeSize = 36.dp
private val BadgeArcSize = 20.dp

/**
 * Состояние обновления протягиванием. Работает на обычной вложенной прокрутке без пробных функций:
 * палец тянет экран вниз, пока содержимое уже прокручено до верха; отпускание дальше порога запускает обновление.
 */
@Stable
class PullRefreshState internal constructor(
    private val thresholdPx: Float,
    private val holdPx: Float,
    private val maxPx: Float,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val refreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
) {
    private val shift = Animatable(0f)

    /** Палец сейчас тянет экран. */
    internal var dragging by mutableStateOf(false)
        private set

    /** Расстояние, пройденное пальцем. Сдвиг экрана вдвое меньше. */
    private var finger = 0f

    /** Сдвиг содержимого вниз в пикселях. Читать только при отрисовке (graphicsLayer), не при компоновке. */
    val offset: Float get() = shift.value

    /** Насколько протянуто до порога, от 0 до 1 (для дуги). */
    val progress: Float get() = (shift.value / thresholdPx).coerceIn(0f, 1f)

    /** Насколько виден индикатор, от 0 до 1: он проявляется, когда под сдвинутым экраном освободилось место. */
    val visibility: Float get() = ((shift.value - 0.4f * holdPx) / (0.6f * holdPx)).coerceIn(0f, 1f)

    internal suspend fun settle(isRefreshing: Boolean) {
        shift.animateTo(if (isRefreshing) holdPx else 0f, tween(SETTLE_MS, easing = FastOutSlowInEasing))
    }

    internal fun cancelPull() {
        finger = 0f
        dragging = false
    }

    private fun moveTo(newFinger: Float) {
        finger = newFinger
        dragging = newFinger > 0f
        val target = minOf(newFinger * PULL_RESISTANCE, maxPx)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { shift.snapTo(target) }
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || finger <= 0f || available.y >= 0f || !enabled()) {
                return Offset.Zero
            }
            // Палец идёт вверх, пока экран оттянут: сначала возвращаем экран на место.
            val used = minOf(finger, -available.y)
            moveTo(finger - used)
            return Offset(0f, -used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || !enabled() || refreshing()) {
                return Offset.Zero
            }
            // Содержимое уже наверху, а палец продолжает идти вниз: тянем экран.
            moveTo(minOf(finger + available.y, maxPx / PULL_RESISTANCE))
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            val pulledEnough = finger * PULL_RESISTANCE >= thresholdPx
            cancelPull()
            if (pulledEnough && enabled() && !refreshing()) onRefresh()
            // Рывок после отпускания не передаём списку: экран просто возвращается.
            return available
        }
    }
}

/**
 * Запоминает состояние протягивания. [refreshing] держит экран сдвинутым и показывает индикатор;
 * [enabled] выключает жест (например, пока идёт первая загрузка).
 */
@Composable
fun rememberPullRefreshState(refreshing: Boolean, enabled: Boolean, onRefresh: () -> Unit): PullRefreshState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestRefreshing = rememberUpdatedState(refreshing)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestOnRefresh = rememberUpdatedState(onRefresh)
    val state = remember(density) {
        PullRefreshState(
            thresholdPx = with(density) { PullThreshold.toPx() },
            holdPx = with(density) { PullHold.toPx() },
            maxPx = with(density) { PullMax.toPx() },
            scope = scope,
            enabled = { latestEnabled.value },
            refreshing = { latestRefreshing.value },
            onRefresh = { latestOnRefresh.value() },
        )
    }
    // Когда палец отпущен, экран плавно ложится на место: на нулевое, а во время обновления на «держим».
    LaunchedEffect(state, refreshing, state.dragging) {
        if (!state.dragging) state.settle(refreshing)
    }
    LaunchedEffect(state, enabled) {
        if (!enabled) state.cancelPull()
    }
    return state
}

/**
 * Индикатор обновления: матовый кружок с дугой. Пока тянут, дуга растёт до полного круга (порог взят);
 * во время обновления дуга вращается. Появляется в освободившемся месте над сдвинутым экраном.
 */
@Composable
fun PullRefreshIndicator(state: PullRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.refreshing)
    val palette = LocalPogodaPalette.current
    Box(
        modifier
            .size(BadgeSize)
            .graphicsLayer {
                val visible = state.visibility
                alpha = visible
                scaleX = 0.6f + 0.4f * visible
                scaleY = 0.6f + 0.4f * visible
            }
            .glassButton(CircleShape, palette)
            .then(if (refreshing) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(BadgeArcSize),
                color = palette.ink,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        } else {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(BadgeArcSize),
                color = palette.ink,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        }
    }
}
