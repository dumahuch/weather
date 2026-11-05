package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Как вид погоды меняет небо. Значения из таблицы макета.
 * [cloud] сколько неба закрыто облаками (0…1), [gray] насколько небо уходит в ровный серый,
 * [dark] насколько затемнено (гроза темнее всего), [fog] густота тумана (для цвета неба),
 * [wind] условная скорость ветра в км/ч: от неё зависит скорость дрейфа облаков,
 * [density] насколько густо лежат облака фона «Ди»: от почти чистого неба (0) до сплошной гряды (1), по ней [CloudLayout] выбирает число и размер облаков.
 * Настоящую облачность и ветер из прогноза подключим вместе с погодой на фоне.
 */
internal data class SkyWeather(
    val cloud: Double,
    val gray: Double,
    val dark: Double,
    val fog: Double,
    val wind: Double,
    val density: Double = 0.5,
) {
    companion object {
        /**
         * Небо для вида погоды [condition]. [precipitationMm] осадки текущего часа (мм): у дождя и снега от них зависит
         * густота облаков (морось против ливня); без них берётся средняя сила.
         */
        fun of(condition: Condition, precipitationMm: Double? = null): SkyWeather {
            val strength = precipitationMm?.let { smooth(MIN_PRECIPITATION_MM, HEAVY_PRECIPITATION_MM, it) } ?: 0.5
            return when (condition) {
                Condition.Clear -> SkyWeather(cloud = 0.14, gray = 0.0, dark = 0.0, fog = 0.0, wind = 8.0, density = 0.0)
                Condition.Partly -> SkyWeather(cloud = 0.45, gray = 0.18, dark = 0.04, fog = 0.0, wind = 14.0, density = 0.22)
                Condition.Cloudy -> SkyWeather(cloud = 0.95, gray = 0.62, dark = 0.12, fog = 0.0, wind = 16.0, density = 0.62)
                Condition.Rain -> SkyWeather(cloud = 1.0, gray = 0.72, dark = 0.22, fog = 0.08, wind = 22.0, density = 0.70 + 0.27 * strength)
                Condition.Storm -> SkyWeather(cloud = 1.0, gray = 0.80, dark = 0.46, fog = 0.0, wind = 55.0, density = 1.0)
                Condition.Snow -> SkyWeather(cloud = 0.95, gray = 0.70, dark = 0.06, fog = 0.15, wind = 14.0, density = 0.62 + 0.28 * strength)
                Condition.Fog -> SkyWeather(cloud = 0.30, gray = 0.55, dark = 0.0, fog = 0.9, wind = 4.0, density = 0.10)
            }
        }

        /** Осадки часа (мм), с которых считается морось, и при которых дождь или снег уже сильные (плотность облаков больше не растёт). */
        const val MIN_PRECIPITATION_MM = 0.1
        const val HEAVY_PRECIPITATION_MM = 4.0
    }

    /** Прозрачность слоя облаков: ясное небо почти без них (12 %), сплошная облачность полностью. */
    val cloudLayerAlpha: Double get() = 0.12 + 0.88 * smooth(0.1, 0.95, cloud)

    /** Сколько секунд длится один период покачивания облаков: ветер ускоряет дрейф. */
    val driftPeriodSeconds: Double get() = 150.0 - wind * 1.5
}
