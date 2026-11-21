package io.github.dumahuch.pogodaapp.weather

import java.time.Instant

/**
 * Качество воздуха в данный час: европейский индекс EAQI (https://www.eea.europa.eu/themes/air/air-quality-index),
 * целое число от 0, выше 100 бывает. Данные Copernicus CAMS через Open-Meteo, только Европа.
 */
data class AirQuality(val eaqi: Int, val hours: List<AirHour> = emptyList()) {
    /** Ступень шкалы EEA: до 20 хорошо, до 40 удовлетворительно, до 60 умеренно, до 80 плохо, до 100 очень плохо, выше крайне плохо. */
    val level: AirLevel get() = AirLevel.of(eaqi)
}

/**
 * Один час ответа: индекс EAQI и вещества в мкг/м³ (PM2,5, PM10, диоксид азота, озон, диоксид серы). Любое поле пусто,
 * если модель его не дала. Нужно странице «Воздух» (0.8.80).
 */
data class AirHour(
    val time: Instant,
    val eaqi: Int?,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val no2: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
)

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
