package io.github.dumahuch.pogodaapp.ui

/** Слой окна: страница вкладки, подробности дня поверх «Прогноза», список мест, настройки. */
internal enum class Layer(val depth: Int) {
    Tab(0),
    Day(1),
    Places(1),
    Settings(1),
}

/**
 * Какое окно сейчас на экране, для анимации перехода. [tabIndex] порядковый номер вкладки в панели (слева направо),
 * [isMap] вкладка «Карта» (у неё свои цвета и стекло).
 */
internal data class ScreenKey(
    val layer: Layer,
    val tabIndex: Int = 0,
    val isMap: Boolean = false,
    /** Открытый день (дни от 1970 года) у слоя [Layer.Day]: уходящее окно помнит свой день, пока анимируется. */
    val day: Long = 0L,
)

/** Как переходить из одного окна в другое. */
internal enum class TransitionKind {
    None,

    /** Углубление: новое окно выезжает справа. */
    Push,

    /** Возврат: окно уходит вправо, под ним прежнее. */
    Pop,

    /** Только проявление (редкие сочетания окон одного слоя). */
    Fade,
}

internal fun transitionKind(from: ScreenKey, to: ScreenKey): TransitionKind = when {
    from == to -> TransitionKind.None
    to.layer.depth > from.layer.depth -> TransitionKind.Push
    to.layer.depth < from.layer.depth -> TransitionKind.Pop
    from.layer != Layer.Tab || to.layer != Layer.Tab -> TransitionKind.Fade
    // Вкладки само окно переключает мгновенно; их движение ведёт [TabMotion] (прежняя вкладка гаснет картинкой, новая въезжает).
    else -> TransitionKind.None
}

/** На какую долю ширины новая вкладка въезжает сбоку: вдесятеро меньше, чем окно при переходе вглубь. */
internal const val TAB_SHIFT_PART = 10

/**
 * С какого сдвига (в пикселях) въезжает новая вкладка: вкладка правее прежней приходит справа (сдвиг положительный),
 * левее приходит слева; та же вкладка не двигается. [widthPx] ширина окна.
 */
internal fun tabShift(fromIndex: Int, toIndex: Int, widthPx: Int): Int {
    val step = widthPx / TAB_SHIFT_PART
    return when {
        toIndex > fromIndex -> step
        toIndex < fromIndex -> -step
        else -> 0
    }
}
