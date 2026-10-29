package io.github.dumahuch.pogodaapp.weather

/**
 * Качество воздуха в данный час: европейский индекс EAQI (https://www.eea.europa.eu/themes/air/air-quality-index),
 * целое число от 0, выше 100 бывает. Данные Copernicus CAMS через Open-Meteo, только Европа.
 */
data class AirQuality(val eaqi: Int) {
    /** Ступень шкалы EEA: до 20 хорошо, до 40 удовлетворительно, до 60 умеренно, до 80 плохо, до 100 очень плохо, выше крайне плохо. */
    val level: AirLevel get() = AirLevel.of(eaqi)
}

enum class AirLevel {
    Good, Fair, Moderate, Poor, VeryPoor, ExtremelyPoor;

    companion object {
        fun of(eaqi: Int): AirLevel = when {
            eaqi < 20 -> Good
            eaqi < 40 -> Fair
            eaqi < 60 -> Moderate
            eaqi < 80 -> Poor
            eaqi < 100 -> VeryPoor
            else -> ExtremelyPoor
        }
    }
}
