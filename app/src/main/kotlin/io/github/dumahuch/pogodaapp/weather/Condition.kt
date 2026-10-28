package io.github.dumahuch.pogodaapp.weather

/** Семь видов погоды из макета: по ним выбираются значок и подпись. */
enum class Condition { Clear, Partly, Cloudy, Rain, Storm, Snow, Fog }

/**
 * Перевод кодов погоды WMO (так их отдаёт Open-Meteo) в вид из макета.
 * Таблица кодов: https://open-meteo.com/en/docs (раздел «WMO Weather interpretation codes»).
 * Морось и ледяной дождь отнесены к дождю, снежные зёрна к снегу, все грозы к грозе.
 * Неизвестный код даёт «пасмурно»: лучше нейтральный значок, чем сбой.
 */
object WeatherCodes {
    fun toCondition(code: Int): Condition = when (code) {
        0, 1 -> Condition.Clear
        2 -> Condition.Partly
        3 -> Condition.Cloudy
        45, 48 -> Condition.Fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Condition.Rain
        71, 73, 75, 77, 85, 86 -> Condition.Snow
        95, 96, 97, 99 -> Condition.Storm
        else -> Condition.Cloudy
    }
}
