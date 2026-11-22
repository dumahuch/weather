package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/** Переход вглубь (день, места, настройки) и обратно. */
internal const val PUSH_MS = 320

/** Через сколько уходящее окно уже не просвечивает сквозь выезжающее. */
internal const val PUSH_COVER_MS = 120

/** Возврат: пока уходящее окно гаснет, прежнее проявляется с небольшим сдвигом по времени. */
internal const val POP_DELAY_MS = 60
private const val POP_OUT_MS = 140
internal const val POP_IN_MS = 200

/** Короткое проявление (редкие сочетания окон одного слоя). */
private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 160

/** Сколько всего длится самый долгий переход (небо стоит, пока он идёт). */
internal const val TRANSITION_MAX_MS = 360L

/** Доля ширины, на которую прежнее окно уходит под новое при углублении (и приходит обратно при возврате). */
internal const val UNDER_SHIFT = 5

/**
 * Движение окон при переходе вида [kind]. Двигается только содержимое: небо стоит, панель вкладок не едет. Проявление и
 * угасание окон не здесь, а в [screenFade]: встроенные fadeIn/fadeOut рисуют окно целиком в отдельную картинку.
 */
internal fun screenTransition(kind: TransitionKind): ContentTransform = when (kind) {
    TransitionKind.None, TransitionKind.Fade -> ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    TransitionKind.Push -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { it },
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT },
        sizeTransform = null,
    )

    // Уходящее окно остаётся сверху и уезжает вправо, под ним возвращается прежнее.
    TransitionKind.Pop -> ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PUSH_MS)) { -it / UNDER_SHIFT },
        initialContentExit = slideOutHorizontally(tween(PUSH_MS)) { it },
        targetContentZIndex = -1f,
        sizeTransform = null,
    )
}

/** Время проявления ([entering]) или угасания окна: длительность, задержка, плавность. */
internal data class FadeTiming(val millis: Int, val delayMillis: Int = 0, val easing: Easing = FastOutSlowInEasing)

/** Как окно проявляется или гаснет при переходе вида [kind]; null, если сразу. Сроки прежних fadeIn/fadeOut. */
internal fun fadeTiming(kind: TransitionKind, entering: Boolean): FadeTiming? = when (kind) {
    TransitionKind.None -> null
    TransitionKind.Push -> if (entering) FadeTiming(PUSH_COVER_MS) else FadeTiming(PUSH_COVER_MS, easing = LinearEasing)
    TransitionKind.Pop ->
        if (entering) FadeTiming(POP_IN_MS, POP_DELAY_MS, LinearOutSlowInEasing) else FadeTiming(POP_OUT_MS, POP_DELAY_MS, LinearEasing)
    TransitionKind.Fade -> if (entering) FadeTiming(FADE_IN_MS) else FadeTiming(FADE_OUT_MS)
}

/** Помнит окно на экране, чтобы вид последнего перехода не менялся, пока он идёт ([screenFade]). */
internal class ScreenMoves(private var last: ScreenKey) {
    /** Вид перехода к [to]; дальше прежним окном считается [to]. */
    fun move(to: ScreenKey): TransitionKind = transitionKind(last, to).also { last = to }
}

/**
 * Непрозрачность окна при переходе вида [kind]. Раньше её давали fadeIn/fadeOut: пока прозрачность меньше 1, окно целиком
 * рисовалось в отдельную картинку во весь экран. По трассе стенда 22.11.2026 (сборка как выпуск) кадр с двумя такими
 * картинками (окно и колода под ним) стоил потоку отрисовки 15–40 мс, без них 6–12 мс. Окна ждут конца этой анимации,
 * прежде чем уйти. Накладывать через [fadeLayer].
 */
@Composable
internal fun AnimatedVisibilityScope.screenFade(kind: TransitionKind, animate: Boolean): State<Float> =
    transition.animateFloat(
        transitionSpec = {
            val timing = if (animate) fadeTiming(kind, entering = targetState == EnterExitState.Visible) else null
            if (timing == null) snap() else tween(timing.millis, timing.delayMillis, timing.easing)
        },
        label = "screenFade",
    ) { if (it == EnterExitState.Visible) 1f else 0f }

/** Прозрачность [alpha] на каждую операцию рисования, без отдельной картинки во весь экран (см. [screenFade]). */
internal fun Modifier.fadeLayer(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha()
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
