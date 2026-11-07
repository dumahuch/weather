package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Значок погоды справа от температуры (решение Димы 06.11.2026, step-27; уточнено 07.11.2026, step-29). При ясной погоде
 * значка нет: солнце и луна уже нарисованы на небе. Переменная облачность это облако с выглядывающим солнцем днём и
 * с луной ночью, пасмурно это одно облако.
 */
enum class HeroIcon {
    Cloud,
    CloudSun,
    CloudMoon,
    Rain,
    Storm,
    Snow,
    Fog,
}

/** Значок для вида погоды; `null` при ясной погоде (значка нет). [isDay] выбирает солнце или луну у переменной облачности. */
fun heroIconFor(condition: Condition, isDay: Boolean): HeroIcon? = when (condition) {
    Condition.Clear -> null
    Condition.Partly -> if (isDay) HeroIcon.CloudSun else HeroIcon.CloudMoon
    Condition.Cloudy -> HeroIcon.Cloud
    Condition.Rain -> HeroIcon.Rain
    Condition.Storm -> HeroIcon.Storm
    Condition.Snow -> HeroIcon.Snow
    Condition.Fog -> HeroIcon.Fog
}
