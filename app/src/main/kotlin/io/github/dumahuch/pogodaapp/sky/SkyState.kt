package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

/**
 * Состояние неба в данный момент: солнце, доли картин (не больше двух времён суток, чтобы в памяти
 * держать две пары картин), погода и цвета погодной вуали.
 */
internal class SkyState(
    val sun: SunPosition.Sun,
    val weights: SceneWeights,
    val weather: SkyWeather,
    val veil: Veil,
) {
    /** Куда смотрит кадр на картине (0 слева, 1 справа). */
    val pan: Double get() = weights.pan

    fun look(cloudPosition: Double) = SkyLook(weights, weather, veil, cloudPosition)

    companion object {
        /** Сколько времён суток держим в памяти и рисуем одновременно. */
        const val MAX_SCENES = 2

        fun of(sun: SunPosition.Sun, condition: Condition): SkyState {
            val weather = SkyWeather.of(condition)
            val weights = SkyTimeOfDay.weights(sun.elevation, sun.solarHour).strongest(MAX_SCENES)
            val veil = SkyColors.veil(sun.elevation, SkyTimeOfDay.evening(sun.solarHour), weather)
            return SkyState(sun, weights, weather, veil)
        }

        fun at(latitude: Double, longitude: Double, time: Instant, condition: Condition): SkyState =
            of(SunPosition.at(latitude, longitude, time), condition)

        /**
         * Солнечный час (с шагом в минуту), когда картина [scene] видна сильнее всего в тот же день и на той же широте.
         * Экран проверки ставит по нему время кнопками «Рассвет», «Утро» и так далее.
         */
        fun dominantSolarHour(scene: SkyScene, latitude: Double, day: Instant): Double {
            var best = 0.0
            var bestWeight = -1.0
            for (minute in 0 until 24 * 60) {
                val hour = minute / 60.0
                val sun = SunPosition.atSolarHour(latitude, day, hour)
                val weight = SkyTimeOfDay.weights(sun.elevation, sun.solarHour)[scene]
                if (weight > bestWeight + 1e-9) {
                    best = hour
                    bestWeight = weight
                }
            }
            return best
        }
    }
}

/**
 * Положение слоя облаков: облака плывут туда-сюда вокруг положения, которое задаёт кадр,
 * на десятую долю доступного хода (как в макете), с периодом около двух минут.
 */
internal object SkyDrift {
    private const val AMPLITUDE = 0.1
    private const val PHASE_OFFSET = 7.7

    /** Фаза покачивания в начале работы; одинаковая при каждом запуске. */
    const val START_PHASE = 0.0

    fun position(pan: Double, phase: Double): Double =
        AMPLITUDE + (1 - 2 * AMPLITUDE) * pan + AMPLITUDE * sin(phase + PHASE_OFFSET)

    /** Радианы в секунду. */
    fun speed(weather: SkyWeather): Double = 2 * PI / weather.driftPeriodSeconds
}
