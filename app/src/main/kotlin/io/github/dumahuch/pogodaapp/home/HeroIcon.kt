package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Значок погоды справа от температуры (решение Димы 06.11.2026, step-27). Солнце и луна уже нарисованы на небе, поэтому
 * значок показывает только то, чего на небе нет: при ясной погоде значка нет, переменная облачность это облако без
 * солнца и луны (днём и ночью одинаково).
 */
enum class HeroIcon {
    Cloud,
    Clouds,
    Rain,
    Storm,
    Snow,
    Fog,
}

/** Значок для вида погоды; `null` при ясной погоде (значка нет). */
fun heroIconFor(condition: Condition): HeroIcon? = when (condition) {
    Condition.Clear -> null
    Condition.Partly -> HeroIcon.Cloud
    Condition.Cloudy -> HeroIcon.Clouds
    Condition.Rain -> HeroIcon.Rain
    Condition.Storm -> HeroIcon.Storm
    Condition.Snow -> HeroIcon.Snow
    Condition.Fog -> HeroIcon.Fog
}
