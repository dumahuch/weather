package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp

/** Переход между вкладками. Кривая по умолчанию (быстрый старт, мягкая остановка) та же, что у панели часов. */
private const val TAB_MS = 280

/** Переход вглубь (день, места, настройки) и обратно. */
private const val PUSH_MS = 320

/** Короткое проявление (карта). */
private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 160

/** На сколько вкладка выезжает сбоку и уходит в другую сторону. */
internal val TAB_SLIDE = 48.dp

/** Доля ширины, на которую прежнее окно уходит под новое при углублении (и приходит обратно при возврате). */
private const val UNDER_SHIFT = 5

/**
 * Анимация перехода вида [kind]. Двигается только содержимое: небо стоит, панель вкладок не едет. [tabSlidePx] сдвиг
 * вкладки в пикселях ([TAB_SLIDE]).
 */
internal fun screenTransition(kind: TransitionKind, tabSlidePx: Int): ContentTransform = when (kind) {
    TransitionKind.None -> ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    TransitionKind.Push -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { it } + fadeIn(tween(PUSH_MS / 2)),
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT } + fadeOut(tween(PUSH_MS)),
        sizeTransform = null,
    )

    // Уходящее окно остаётся сверху и уезжает вправо, под ним возвращается прежнее.
    TransitionKind.Pop -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT } + fadeIn(tween(PUSH_MS)),
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { it } +
            fadeOut(tween(PUSH_MS / 2, delayMillis = PUSH_MS / 2)),
        targetContentZIndex = -1f,
        sizeTransform = null,
    )

    TransitionKind.SlideForward -> tabSlide(tabSlidePx)
    TransitionKind.SlideBack -> tabSlide(-tabSlidePx)

    TransitionKind.Fade -> ContentTransform(
        targetContentEnter = fadeIn(tween(FADE_IN_MS)),
        initialContentExit = fadeOut(tween(FADE_OUT_MS)),
        sizeTransform = null,
    )
}

/** Новая вкладка приходит со стороны [shiftPx], прежняя уходит в противоположную. */
private fun tabSlide(shiftPx: Int) = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(TAB_MS)) { shiftPx } + fadeIn(tween(TAB_MS)),
    initialContentExit = slideOutHorizontally(tween(TAB_MS)) { -shiftPx } + fadeOut(tween(TAB_MS * 2 / 3)),
    sizeTransform = null,
)
