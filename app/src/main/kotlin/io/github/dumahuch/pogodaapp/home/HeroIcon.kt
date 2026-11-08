package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Значок погоды справа от температуры (решение Димы 06.11.2026, step-27; набор переделан по его образцам 07.11.2026,
 * step-42). При ясной погоде значка нет: солнце и луна уже нарисованы на небе; при ясной погоде с сильным ветром только
 * ветер. Переменная облачность это облако с выглядывающим солнцем днём и с луной ночью, пасмурно это два облака.
 * При сильном ветре облако, дождь и снег рисуются вместе с ветром. Рисунок: [GlyphScenes].
 */
enum class HeroIcon {
    CloudSun,
    CloudMoon,
    Clouds,
    CloudWind,
    Rain,
    RainWind,
    Snow,
    SnowWind,
    Storm,
    StormRain,
    Wind,
    Fog,
}

/**
 * Сильный ветер для значка: средний ветер от 39 км/ч (6 баллов по Бофорту, «сильный ветер»; выбрано Claude 07.11.2026,
 * Дима может сменить порог). Порывы не считаются: при порывах без сильного ветра значок с ветром был бы слишком частым.
 */
const val STRONG_WIND_KMH = 39.0

fun isStrongWind(windKmh: Double?): Boolean = windKmh != null && windKmh >= STRONG_WIND_KMH

/**
 * Осадки за час (мм) от которых у грозы рисуются штрихи дождя (решение Димы 08.11.2026: сухая гроза и гроза с дождём;
 * порог 0,1 мм предложен Claude: это нижняя граница, с которой Open-Meteo считает час с осадками, меньше шум).
 */
const val STORM_RAIN_MM = 0.1

fun isStormWet(precipitationMm: Double?): Boolean = precipitationMm != null && precipitationMm >= STORM_RAIN_MM

/**
 * Значок для вида погоды; `null` при ясной погоде без сильного ветра (значка нет). [isDay] выбирает солнце или луну у
 * переменной облачности, [windy] добавляет ветер ([isStrongWind]). У грозы и тумана варианта с ветром нет. [wet]: у грозы
 * идут осадки ([isStormWet]), тогда молния со штрихами дождя.
 */
fun heroIconFor(condition: Condition, isDay: Boolean, windy: Boolean = false, wet: Boolean = false): HeroIcon? = when (condition) {
    Condition.Clear -> if (windy) HeroIcon.Wind else null
    Condition.Partly -> when {
        windy -> HeroIcon.CloudWind
        isDay -> HeroIcon.CloudSun
        else -> HeroIcon.CloudMoon
    }
    Condition.Cloudy -> if (windy) HeroIcon.CloudWind else HeroIcon.Clouds
    Condition.Rain -> if (windy) HeroIcon.RainWind else HeroIcon.Rain
    Condition.Storm -> if (wet) HeroIcon.StormRain else HeroIcon.Storm
    Condition.Snow -> if (windy) HeroIcon.SnowWind else HeroIcon.Snow
    Condition.Fog -> HeroIcon.Fog
}
