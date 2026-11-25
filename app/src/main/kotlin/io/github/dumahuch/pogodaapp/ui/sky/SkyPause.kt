package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Идёт ли переход между окнами. Пока идёт, небо не двигает облака и не пересчитывает цвета: на слабом кадре перерисовка
 * неба отнимает у перехода кадры, и он выходит рваным. Облака стоят меньше полсекунды, этого не видно.
 */
internal object SkyPause {
    var active by mutableStateOf(false)
}

/**
 * Закрыто ли небо картой. На вкладке «Карта» карта лежит на всё окно, неба не видно, а оно продолжало бы двигать облака,
 * считать цвета и снимать кадры (в дождь каждый кадр). Пока закрыто, эти циклы стоят; последний кадр неба остаётся в дереве,
 * картины из памяти не выгружаются (аудит 02, П1). Ставит `PogodaApp`: вкладка «Карта», на экране вкладка, не переход.
 */
internal object SkyCover {
    var covered by mutableStateOf(false)
}
