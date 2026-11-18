package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Выключатели частей неба и стекла для замера на экране проверки (step-123): по кадрам видно, сколько стоит каждая часть.
 * Пользователям не видны и ничего не меняют, пока экран проверки не открыт; [reset] возвращает всё как было.
 */
internal object SkyProbe {
    /** Облака на небе. */
    var clouds by mutableStateOf(true)

    /** Гряды на небе. */
    var ridges by mutableStateOf(true)

    /** Неподвижная часть неба готовой картинкой ([SkyStill]); выключено: всё рисуется заново каждый кадр. */
    var still by mutableStateOf(true)

    /** Размытие стекла на 79 % разрешения (по умолчанию Haze); выключено: на 50 % (замер 18.11.2026: разницы нет). */
    var fullBlur by mutableStateOf(true)

    /** Погода на стекле: сугроб, капли, запотевание, иней, отблеск грозы. */
    var glassWeather by mutableStateOf(true)

    /** Туман, дождь и снег на самом небе. */
    var skyWeather by mutableStateOf(true)

    fun reset() {
        clouds = true
        ridges = true
        still = true
        fullBlur = true
        glassWeather = true
        skyWeather = true
    }
}
