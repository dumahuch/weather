package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Переход между вкладками: на какую долю ширины сдвигаются экраны (на Pixel около 40 dp), как быстро гаснет старый и когда
 * начинает проявляться новый.
 */
private const val TAB_SHIFT_DIV = 10
private const val TAB_OUT_MS = 140
private const val TAB_IN_MS = 200
private const val TAB_IN_DELAY_MS = 60

/** Переход вглубь (день, места, настройки) и обратно. */
private const val PUSH_MS = 320

/** Через сколько уходящее окно уже не просвечивает сквозь выезжающее. */
private const val PUSH_COVER_MS = 120

/** Возврат: пока уходящее окно гаснет, прежнее проявляется с небольшим сдвигом по времени. */
private const val POP_DELAY_MS = 60
private const val POP_OUT_MS = 140
private const val POP_IN_MS = 200

/** Короткое проявление (карта). */
private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 160

/** Сколько всего длится самый долгий переход (небо стоит, пока он идёт). */
internal const val TRANSITION_MAX_MS = 360L

/** Доля ширины, на которую прежнее окно уходит под новое при углублении (и приходит обратно при возврате). */
private const val UNDER_SHIFT = 5

/**
 * Анимация перехода вида [kind]. Двигается только содержимое: небо стоит, панель вкладок не едет.
 */
internal fun screenTransition(kind: TransitionKind): ContentTransform = when (kind) {
    TransitionKind.None -> ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    TransitionKind.Push -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { it } + fadeIn(tween(PUSH_COVER_MS)),
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT } +
            fadeOut(tween(PUSH_COVER_MS, easing = LinearEasing)),
        sizeTransform = null,
    )

    // Уходящее окно остаётся сверху и уезжает вправо, под ним возвращается прежнее.
    TransitionKind.Pop -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT } +
            fadeIn(tween(POP_IN_MS, delayMillis = POP_DELAY_MS, easing = LinearOutSlowInEasing)),
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { it } +
            fadeOut(tween(POP_OUT_MS, delayMillis = POP_DELAY_MS, easing = LinearEasing)),
        targetContentZIndex = -1f,
        sizeTransform = null,
    )

    // Вкладки: боковой сдвиг на малую долю ширины с тем же временем и кривой, что вглубь. Сдвиг скрывает просвечивание старого
    // экрана сквозь стекло нового. Новый экран сверху, чтобы поздно собравшийся тяжёлый экран не оказывался под старым.
    TransitionKind.SlideForward -> tabShift(from = 1)
    TransitionKind.SlideBack -> tabShift(from = -1)

    TransitionKind.Fade -> ContentTransform(
        targetContentEnter = fadeIn(tween(FADE_IN_MS)),
        initialContentExit = fadeOut(tween(FADE_OUT_MS)),
        sizeTransform = null,
    )
}

/** Новая вкладка приходит со стороны [from] (1 справа, -1 слева), старая уходит в противоположную. */
private fun tabShift(from: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { it * from / TAB_SHIFT_DIV } +
        fadeIn(tween(TAB_IN_MS, delayMillis = TAB_IN_DELAY_MS, easing = LinearOutSlowInEasing)),
    initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { -it * from / TAB_SHIFT_DIV } +
        fadeOut(tween(TAB_OUT_MS, easing = LinearEasing)),
    targetContentZIndex = 1f,
    sizeTransform = null,
)
