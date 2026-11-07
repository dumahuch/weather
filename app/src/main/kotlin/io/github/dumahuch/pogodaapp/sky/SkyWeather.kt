package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Как вид погоды меняет небо. Значения из таблицы макета.
 * [cloud] сколько неба закрыто облаками (0…1), [gray] насколько небо уходит в ровный серый,
 * [dark] насколько затемнено (гроза темнее всего), [fog] густота тумана (для цвета неба),
 * [wind] условная скорость ветра в км/ч: от неё зависит скорость дрейфа облаков,
 * [density] насколько густо лежат облака фона «Ди»: от почти чистого неба (0) до сплошной гряды (1), по ней [CloudLayout] выбирает число и размер облаков,
 * [darkShare] какая доля облаков «Ди» берётся с тёмного (грозового) листа: 0 все светлые, 1 все тёмные.
 * Настоящий процент облачности из прогноза подключён (см. [of]); ветер пока условный.
 */
internal data class SkyWeather(
    val cloud: Double,
    val gray: Double,
    val dark: Double,
    val fog: Double,
    val wind: Double,
    val density: Double = 0.5,
    val darkShare: Double = if (dark >= 0.1) 1.0 else 0.0,
) {
    /**
     * Множитель цвета светлых облаков: при плотной облачности без осадков они темнеют до светло-серых (0,72 при сплошной,
     * решение Димы 07.11.2026: плотные серые облака, но не тёмные тучи). У грозовых облаков (доля [darkShare] 1) цвет не
     * меняется, у светлой погоды (серость меньше 0,12) тоже.
     */
    val cloudShade: Double get() = 1.0 - 0.28 * ((gray - 0.12) / 0.5).coerceIn(0.0, 1.0) * (1.0 - darkShare.coerceIn(0.0, 1.0))

    companion object {
        /**
         * Небо для вида погоды [condition]. [precipitationMm] осадки текущего часа (мм): у дождя и снега от них зависит
         * густота облаков (морось против ливня); без них берётся средняя сила.
         * [cloudCoverPercent] облачность из прогноза: у ясного, переменного и пасмурного неба облака плавно следуют ей,
         * а не трём ступеням кода погоды (код 2 это 50–80 % неба, код 3 от 80 %). Остальные виды погоды определяет код.
         */
        fun of(condition: Condition, precipitationMm: Double? = null, cloudCoverPercent: Double? = null): SkyWeather {
            if (cloudCoverPercent != null && condition in COVER_CONDITIONS) return byCover(cloudCoverPercent / 100.0)
            val strength = precipitationMm?.let { smooth(MIN_PRECIPITATION_MM, HEAVY_PRECIPITATION_MM, it) } ?: 0.5
            return when (condition) {
                Condition.Clear -> SkyWeather(cloud = 0.14, gray = 0.0, dark = 0.0, fog = 0.0, wind = 8.0, density = 0.0)
                Condition.Partly -> SkyWeather(cloud = 0.45, gray = 0.18, dark = 0.04, fog = 0.0, wind = 14.0, density = 0.22)
                Condition.Cloudy -> SkyWeather(cloud = 0.95, gray = 0.62, dark = 0.12, fog = 0.0, wind = 16.0, density = 0.62, darkShare = 0.1)
                Condition.Rain -> SkyWeather(cloud = 1.0, gray = 0.72, dark = 0.22, fog = 0.08, wind = 22.0, density = 0.70 + 0.27 * strength)
                Condition.Storm -> SkyWeather(cloud = 1.0, gray = 0.80, dark = 0.46, fog = 0.0, wind = 55.0, density = 1.0)
                Condition.Snow -> SkyWeather(cloud = 0.95, gray = 0.70, dark = 0.06, fog = 0.15, wind = 14.0, density = 0.62 + 0.28 * strength)
                Condition.Fog -> SkyWeather(cloud = 0.30, gray = 0.55, dark = 0.0, fog = 0.9, wind = 4.0, density = 0.10)
            }
        }

        /** Виды погоды без осадков, у которых облака считаются по проценту облачности. */
        private val COVER_CONDITIONS = setOf(Condition.Clear, Condition.Partly, Condition.Cloudy)

        /**
         * Небо при облачности [cover] (доля неба 0…1) без осадков. Точки подобраны так, чтобы около 65 % давали
         * густоту выше прежнего «малооблачно» (0,22), а от 80 % небо было не реже прежнего «облачно» (0,62);
         * с step-29 облака плотные светло-серые (см. [cloudShade]): грозовой лист идёт только в одном из десяти облаков при
         * сплошной облачности, затемнение неба почти нет (решение Димы 07.11.2026).
         */
        private fun byCover(cover: Double): SkyWeather {
            val c = cover.coerceIn(0.0, 1.0)
            return SkyWeather(
                cloud = curve(c, 0.0 to 0.10, 0.2 to 0.20, 0.5 to 0.42, 0.8 to 0.88, 1.0 to 0.97),
                gray = curve(c, 0.0 to 0.0, 0.5 to 0.12, 0.8 to 0.55, 1.0 to 0.66),
                dark = curve(c, 0.0 to 0.0, 0.5 to 0.02, 0.8 to 0.04, 1.0 to 0.05),
                fog = 0.0,
                wind = 8.0 + 8.0 * c,
                density = curve(c, 0.0 to 0.0, 0.2 to 0.08, 0.5 to 0.30, 0.8 to 0.58, 1.0 to 0.72),
                darkShare = curve(c, 0.0 to 0.0, 0.9 to 0.0, 1.0 to 0.1),
            )
        }

        /** Значение ломаной по точкам [points] (x по возрастанию) в [x]. */
        private fun curve(x: Double, vararg points: Pair<Double, Double>): Double {
            if (x <= points.first().first) return points.first().second
            for (i in 1 until points.size) {
                val (x1, y1) = points[i]
                if (x <= x1) {
                    val (x0, y0) = points[i - 1]
                    return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
                }
            }
            return points.last().second
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
