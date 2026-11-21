package io.github.dumahuch.pogodaapp.ui

/**
 * Слой окна: страница вкладки, подробности дня поверх «Прогноза», страница показателя поверх «Главной», страница плитки
 * поверх «Подробно» ([io.github.dumahuch.pogodaapp.ui.pages.DetailPage], номер в [ScreenKey.metric]), места, настройки.
 */
internal enum class Layer(val depth: Int) {
    Tab(0),
    Day(1),
    Metric(1),
    Page(1),
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
    /** Номер показателя ([io.github.dumahuch.pogodaapp.forecast.Metric]) у слоя [Layer.Metric] или страницы плитки у [Layer.Page]. */
    val metric: Int = 0,
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
    // Вкладки само окно переключает мгновенно; их движение ведёт [TabMotion] (прежняя вкладка гаснет картинкой поверх новой).
    else -> TransitionKind.None
}
