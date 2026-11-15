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
    // Вкладки переключаются мгновенно, без анимации (решение Димы 15.11.2026: сдвиг и гашение карточек выглядели как сбой).
    else -> TransitionKind.None
}
