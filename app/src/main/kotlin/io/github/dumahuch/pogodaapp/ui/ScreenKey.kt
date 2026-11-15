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
 * [isMap] вкладка «Карта» (её не сдвигаем, только проявляем).
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

    /** Вкладка правее: новое выезжает справа на небольшое расстояние. */
    SlideForward,

    /** Вкладка левее. */
    SlideBack,

    /** Только проявление (переходы с участием карты и редкие сочетания). */
    Fade,
}

internal fun transitionKind(from: ScreenKey, to: ScreenKey): TransitionKind = when {
    from == to -> TransitionKind.None
    to.layer.depth > from.layer.depth -> TransitionKind.Push
    to.layer.depth < from.layer.depth -> TransitionKind.Pop
    from.layer != Layer.Tab || to.layer != Layer.Tab -> TransitionKind.Fade
    from.isMap || to.isMap -> TransitionKind.Fade
    to.tabIndex > from.tabIndex -> TransitionKind.SlideForward
    to.tabIndex < from.tabIndex -> TransitionKind.SlideBack
    else -> TransitionKind.None
}
