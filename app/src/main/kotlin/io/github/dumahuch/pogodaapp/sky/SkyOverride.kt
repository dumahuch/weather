package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Подстановка для экрана проверки: свой солнечный час и/или свой вид погоды вместо настоящих.
 * Пустое значение означает «как есть». Ни на данные, ни на сохранённое она не влияет.
 */
internal data class SkyOverride(
    val solarHour: Double? = null,
    val condition: Condition? = null,
) {
    val active: Boolean get() = solarHour != null || condition != null
}
