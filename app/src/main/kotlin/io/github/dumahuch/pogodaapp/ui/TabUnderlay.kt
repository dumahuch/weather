package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Колода вкладок под страницами (0.8.76). Раньше колода жила внутри перехода окон и выгружалась, пока открыта страница
 * показателя, день, места или настройки, а при возврате собиралась заново: по стенду закрытие страницы стоило разрыва
 * 145–175 мс против 58–80 мс при смене вкладки. Теперь колода собрана всегда, а её движение под страницей ([shift],
 * [alpha]) повторяет прежний переход: при углублении она уходит влево под страницу и гаснет, при возврате приходит и
 * проявляется (см. [screenTransition]).
 */
@Stable
internal class TabUnderlay(visible: Boolean) {
    /** Сдвиг по горизонтали в долях ширины экрана: 0 на месте, [HIDDEN_SHIFT] спрятана под страницей. */
    val shift = Animatable(if (visible) 0f else HIDDEN_SHIFT)

    /** Непрозрачность колоды: 0, когда её закрывает страница (рисоваться ей тогда незачем). */
    val alpha = Animatable(if (visible) 1f else 0f)

    /** Колода уходит под страницу ([visible] нет) или возвращается ([visible] да) с движением прежнего перехода. */
    suspend fun animateTo(visible: Boolean) = coroutineScope {
        if (visible) {
            launch { shift.animateTo(0f, tween(PUSH_MS)) }
            alpha.animateTo(1f, tween(POP_IN_MS, delayMillis = POP_DELAY_MS, easing = LinearOutSlowInEasing))
        } else {
            launch { shift.animateTo(HIDDEN_SHIFT, tween(PUSH_MS)) }
            alpha.animateTo(0f, tween(PUSH_COVER_MS, easing = LinearEasing))
        }
    }

    /** То же без движения (анимации выключены). */
    suspend fun snapTo(visible: Boolean) {
        shift.snapTo(if (visible) 0f else HIDDEN_SHIFT)
        alpha.snapTo(if (visible) 1f else 0f)
    }

    companion object {
        /** Куда колода уходит под страницу: на пятую часть ширины влево, как прежнее окно при углублении. */
        const val HIDDEN_SHIFT = -1f / UNDER_SHIFT
    }
}

/** Колода вкладок лежит сверху, когда на экране вкладка; иначе она под страницей и недоступна. */
internal fun tabsAreOnTop(screen: ScreenKey): Boolean = screen.layer == Layer.Tab
