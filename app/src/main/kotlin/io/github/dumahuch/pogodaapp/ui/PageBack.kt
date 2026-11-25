package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Жест «назад» с предпросмотром (0.9). Пока палец ведёт от края, окно-страница ([Layer.depth] больше нуля) едет вправо, а
 * колода под ним ([TabUnderlay]) проявляется и приходит на место, как при обычном возврате. Отпустили: окно доезжает за
 * край, страница закрывается без переходной анимации (её роль уже сыграл жест). Вернули палец: всё возвращается.
 */
@Stable
internal class PageBack {
    /** Насколько окно уехало: доля ширины экрана, 0 на месте, 1 за краем. */
    val shift = Animatable(0f)

    /** Идёт закрытие по жесту: обычный переход окон и колоды в это время не играет. */
    var silent by mutableStateOf(false)
        private set

    /** Палец ведёт: [progress] жеста от 0 до 1. */
    suspend fun drag(progress: Float) {
        shift.snapTo(progress.coerceIn(0f, 1f) * DRAG_SHARE)
    }

    /** Жест отменён: окно возвращается на место. */
    suspend fun cancel() {
        silent = false
        shift.animateTo(0f, tween(CANCEL_MS))
    }

    /** Жест завершён: окно уезжает за край и вызывается [close]; сброс отдельно ([finish]). */
    suspend fun commit(close: () -> Unit) {
        silent = true
        shift.animateTo(1f, tween(COMMIT_MS, easing = LinearOutSlowInEasing))
        close()
    }

    /** Страница закрыта, колода уже на месте: окно снова считается неподвинутым. */
    suspend fun finish() {
        shift.snapTo(0f)
        silent = false
    }

    private companion object {
        /** Насколько окно уезжает (доля ширины), пока палец ведёт до конца. */
        const val DRAG_SHARE = 0.5f
        const val COMMIT_MS = 140
        const val CANCEL_MS = 180
    }
}

/** Сдвиг окна жестом «назад» ([PageBack]); [active] нет у окон-вкладок. */
internal fun Modifier.pageBack(back: PageBack, active: Boolean): Modifier =
    if (!active) this else graphicsLayer { translationX = back.shift.value * size.width }

/** Сдвиг колоды под страницей с учётом жеста «назад»: окно уехало на [back], колода приходит на место. */
internal fun deckShiftWith(back: Float, shift: Float): Float = shift * (1f - deckArrival(back))

/** Непрозрачность колоды с учётом жеста «назад» ([deckShiftWith]). */
internal fun deckAlphaWith(back: Float, alpha: Float): Float = alpha + (1f - alpha) * deckArrival(back)

/** Колода приходит быстрее окна: на месте и видна уже к [DECK_DONE] пути окна, чтобы предпросмотр был заметен. */
internal fun deckArrival(back: Float): Float = (back / DECK_DONE).coerceIn(0f, 1f)

private const val DECK_DONE = 0.3f
