package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Подстановка для экрана проверки: свой солнечный час, вид погоды и/или фаза луны вместо настоящих.
 * Пустое значение означает «как есть». Ни на данные, ни на сохранённое она не влияет.
 */
internal data class SkyOverride(
    val solarHour: Double? = null,
    val condition: Condition? = null,
    /** Фаза луны как доля лунного месяца (0 новолуние, 0,5 полнолуние): луна показывается всегда, в любое время суток. */
    val moonCycle: Double? = null,
) {
    val active: Boolean get() = solarHour != null || condition != null || moonCycle != null
}
