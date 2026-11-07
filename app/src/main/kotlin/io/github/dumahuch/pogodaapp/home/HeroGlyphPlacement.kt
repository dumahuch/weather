package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle

/**
 * Где стоит значок погоды рядом с цифрами температуры (решение Димы 07.11.2026, step-30): значки выровнены по нижнему
 * краю облака, у всех облако на одной высоте; капли, молния, снег и туман свисают ниже. Весь значок приподнят.
 * Все числа в единицах значка (квадрат 24 × 24), сверху вниз.
 */

/**
 * Нижний край самого облака (без капель, молнии, снега и тумана): по нему значки выравниваются между собой.
 * У контурного набора значки Lucide, облако в них нарисовано по-разному, поэтому числа свои. У дождя, снега и тумана
 * контурное облако открыто снизу: его низом считается нижний край боковой дуги.
 */
internal fun glyphCloudBottom(icon: HeroIcon, style: WeatherIconStyle): Float = when (style) {
    WeatherIconStyle.Glass -> when (icon) {
        HeroIcon.Cloud -> 18.5f
        HeroIcon.CloudSun, HeroIcon.CloudMoon -> 19.3f
        HeroIcon.Rain, HeroIcon.Storm, HeroIcon.Snow -> 15.5f
        HeroIcon.Fog -> 16f
    }
    WeatherIconStyle.Outline -> when (icon) {
        HeroIcon.Cloud -> 19f
        HeroIcon.CloudSun, HeroIcon.CloudMoon -> 22f
        HeroIcon.Rain, HeroIcon.Snow, HeroIcon.Fog -> 16.3f
        HeroIcon.Storm -> 17f
    }
}

/**
 * Нижний край всего рисунка, вместе с каплями, молнией и прочим (для контура учтена половина толщины линии [stroke]).
 */
internal fun glyphBottom(icon: HeroIcon, style: WeatherIconStyle, stroke: Float): Float = when (style) {
    WeatherIconStyle.Glass -> when (icon) {
        HeroIcon.Cloud -> 18.5f
        HeroIcon.CloudSun, HeroIcon.CloudMoon -> 19.3f
        HeroIcon.Rain -> 22f
        HeroIcon.Storm -> 23.2f
        HeroIcon.Snow -> 23f
        HeroIcon.Fog -> 22f
    }
    WeatherIconStyle.Outline -> stroke / 2 + when (icon) {
        HeroIcon.Cloud -> 19f
        HeroIcon.CloudSun, HeroIcon.CloudMoon -> 22f
        HeroIcon.Rain, HeroIcon.Storm -> 22f
        HeroIcon.Snow, HeroIcon.Fog -> 21f
    }
}

/**
 * На сколько единиц значка нижний край облака стоит выше нижнего края цифр. Больше нельзя: у значка с солнцем наклонные
 * лучи доходят до знака градуса (он стоит над значком в той же колонке). У контурного набора облако с солнцем ниже и
 * выше целиком, поэтому подъём меньше.
 */
internal fun glyphLift(style: WeatherIconStyle): Float = when (style) {
    WeatherIconStyle.Glass -> 5f
    WeatherIconStyle.Outline -> 3.5f
}
