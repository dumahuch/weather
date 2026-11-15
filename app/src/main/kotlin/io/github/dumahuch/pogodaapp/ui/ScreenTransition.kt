package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Переход между вкладками: новая проявляется поверх старой за это время. */
private const val TAB_MS = 220

/** Переход вглубь (день, места, настройки) и обратно. */
private const val PUSH_MS = 320

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

    // Вкладки: новый экран проявляется поверх старого, а старый стоит целиком, пока новый не проявился. Так между экранами
    // не бывает пустого неба, и если тяжёлый экран собрался с опозданием, под ним всё равно виден прежний.
    TransitionKind.SlideForward, TransitionKind.SlideBack -> ContentTransform(
        targetContentEnter = fadeIn(tween(TAB_MS)),
        initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = 1f,
        sizeTransform = null,
    )

    TransitionKind.Fade -> ContentTransform(
        targetContentEnter = fadeIn(tween(FADE_IN_MS)),
        initialContentExit = fadeOut(tween(FADE_OUT_MS)),
        sizeTransform = null,
    )
}
