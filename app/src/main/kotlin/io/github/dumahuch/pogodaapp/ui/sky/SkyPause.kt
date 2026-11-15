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
