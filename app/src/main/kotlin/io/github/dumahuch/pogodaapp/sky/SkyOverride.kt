package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Подстановка для экрана проверки: свой солнечный час, вид погоды, фаза луны и/или сильный ветер вместо настоящих.
 * Пустое значение означает «как есть». Ни на данные, ни на сохранённое она не влияет.
 */
internal data class SkyOverride(
    val solarHour: Double? = null,
    val condition: Condition? = null,
    /** Фаза луны как доля лунного месяца (0 новолуние, 0,5 полнолуние): луна показывается всегда, в любое время суток. */
    val moonCycle: Double? = null,
    /** Сильный ветер для значка у температуры (step-42): `true` показывает значок с ветром. */
    val windy: Boolean? = null,
    /** Гроза с дождём для значка у температуры (0.7.13): `true` показывает мокрую грозу, настоящих осадков не нужно. */
    val wet: Boolean? = null,
) {
    val active: Boolean get() = solarHour != null || condition != null || moonCycle != null || windy != null || wet != null
}
