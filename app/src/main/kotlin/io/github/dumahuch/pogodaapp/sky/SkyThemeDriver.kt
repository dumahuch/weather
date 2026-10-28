package io.github.dumahuch.pogodaapp.sky

import kotlin.math.abs

/**
 * Следит за цветами интерфейса при меняющемся небе: снимает цвета фона, сглаживает их с прошлым снимком
 * (чтобы дрейф облаков не дёргал тему), выбирает цвет текста с задержкой и плавно меняет его.
 * Не потокобезопасна: вызывать по очереди.
 */
internal class SkyThemeDriver(private val sampler: SkySampler) {

    private val ink = InkSwitch()
    private var previous: Backdrops? = null
    private var previousWeather: SkyWeather? = null
    private var previousHour = 0.0

    /** Цвета фона и оценка текста для неба [state] с облаками в положении [cloudPosition]. */
    fun measure(state: SkyState, cloudPosition: Double): InkScores {
        val raw = sampler.backdrops(state.look(cloudPosition))
        val old = previous
        // Небольшое сглаживание, только пока небо то же самое (та же погода, время почти не сдвинулось).
        val smooth = if (old != null && previousWeather == state.weather && abs(previousHour - state.sun.solarHour) < SMOOTH_HOURS) {
            old.blend(raw, SMOOTH_WEIGHT)
        } else {
            raw
        }
        previous = smooth
        previousWeather = state.weather
        previousHour = state.sun.solarHour
        return SkyTheme.scores(smooth)
    }

    /** Палитра с учётом того, что с прошлого вызова прошло [seconds] секунд (от этого зависит плавность смены текста). */
    fun palette(scores: InkScores, seconds: Double): SkyPalette = SkyTheme.palette(scores, ink.update(scores, seconds))

    /** Истина, когда цвет текста дошёл до выбранного и больше не меняется. */
    val settled: Boolean get() = ink.position == ink.targetPosition

    private companion object {
        const val SMOOTH_HOURS = 0.15
        const val SMOOTH_WEIGHT = 0.4
    }
}
