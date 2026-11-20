package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.gustBeaufort
import io.github.dumahuch.pogodaapp.weather.HourDetails
import kotlin.math.roundToInt

/**
 * Значения панели, которая раскрывается под лентой часов по нажатию на час. Числа округлены так, как показываются, чтобы
 * слово («умеренный ветер») и число («22 км/ч») не расходились. Пустое поле значит «модель не дала» или «прогноз сохранён
 * старой версией»: такая строка в панели не показывается.
 */
data class HourPanel(
    /** Давление как есть (гПа): экран показывает его в единицах языка (мм рт. ст. в русском) и сам округляет. */
    val pressureHpa: Double?,
    val feelsLikeC: Double?,
    val humidityPercent: Int?,
    val dewPointC: Double?,
    val windKmh: Int?,
    /** Ступень шкалы Бофорта по [windKmh], 0 — штиль. */
    val windStep: Int?,
    /** Откуда дует ветер; пусто при штиле и когда направления нет. */
    val windFrom: CompassPoint?,
    /** Порывы, км/ч; только если стоит о них сказать (как на вкладке «Подробно»). */
    val gustKmh: Int?,
) {
    /** Есть ли в панели хоть одна строка. */
    val hasRows: Boolean
        get() = pressureHpa != null || feelsLikeC != null || humidityPercent != null || dewPointC != null ||
            windKmh != null
}

/** Панель по значениям часа или null, если значений часа нет или в них не нашлось ни одной строки. */
fun hourPanelOf(details: HourDetails?): HourPanel? {
    details ?: return null
    val wind = details.windKmh?.roundToInt()
    val step = wind?.let { beaufortOf(it) }
    val gust = details.gustKmh?.roundToInt()
    val panel = HourPanel(
        pressureHpa = details.pressureHpa,
        feelsLikeC = details.feelsLikeC,
        humidityPercent = details.humidity,
        dewPointC = details.dewPointC,
        windKmh = wind,
        windStep = step,
        windFrom = if (step != null && step >= 1) details.windDirection?.let { CompassPoint.of(it) } else null,
        gustKmh = gust?.takeIf { gustBeaufort(wind, it) != null },
    )
    return panel.takeIf { it.hasRows }
}
