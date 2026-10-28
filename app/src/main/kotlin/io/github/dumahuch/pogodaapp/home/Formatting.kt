package io.github.dumahuch.pogodaapp.home

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Настоящий минус (U+2212), как в макете, вместо дефиса. */
const val MINUS = "−"

/**
 * Округление до целого «от нуля» (2,5 → 3, −2,5 → −3), как в макете. Ноль не бывает отрицательным:
 * −0,4 даёт «0», а не «−0».
 */
fun roundTemperature(value: Double): Int {
    val magnitude = floor(abs(value) + 0.5).toInt()
    return if (value < 0) -magnitude else magnitude
}

/** Число градусов без значка градуса: «13», «−2». */
fun temperatureDigits(value: Double): String {
    val rounded = roundTemperature(value)
    return if (rounded < 0) "$MINUS${-rounded}" else rounded.toString()
}

/** Температура со значком градуса: «13°», «−2°». */
fun temperatureText(value: Double): String = temperatureDigits(value) + "°"

/**
 * Размер крупной цифры в sp: 124 для 0…99, 112 для «−2» (знак и одна цифра),
 * 94 для «−12» и «100». Так блок не упирается в край экрана (решено в макете).
 */
fun heroDigitsSize(value: Double): Int {
    val length = temperatureDigits(value).length
    return when {
        length <= 2 && roundTemperature(value) >= 0 -> 124
        length == 2 -> 112
        else -> 94
    }
}

/** Осадки за час: «–» при нуле, иначе одна цифра после запятой, разделитель по языку («0,3» / «0.3»). */
fun precipitationText(mm: Double, locale: Locale): String =
    if (mm <= 0.0) "–" else String.format(locale, "%.1f", mm)

/**
 * Подпись часа в ленте: двузначный час, а в полночь название дня недели.
 * [dayNames] семь названий начиная с понедельника.
 */
fun hourLabel(time: Instant, zone: ZoneId, dayNames: List<String>): String {
    val local = time.atZone(zone)
    return if (local.hour == 0) dayNames[local.dayOfWeek.value - 1] else "%02d".format(Locale.ROOT, local.hour)
}

/** Высота кривой температуры для часа: 82…110 в единицах колонки, при равных значениях 96 (как в макете). */
object HourlyGeometry {
    const val COLUMN_WIDTH = 58f
    const val HEIGHT = 172f
    private const val CURVE_TOP = 82f
    private const val CURVE_RANGE = 28f
    private const val CURVE_FLAT = 96f
    private const val BAR_MAX = 26f
    private const val BAR_MIN = 3f
    private const val MIN_SCALE_MM = 1.5

    fun curveY(rounded: List<Int>): List<Float> {
        val min = rounded.minOrNull() ?: return emptyList()
        val max = rounded.maxOrNull() ?: return emptyList()
        return rounded.map { t ->
            if (max == min) CURVE_FLAT else CURVE_TOP + (1f - (t - min).toFloat() / (max - min)) * CURVE_RANGE
        }
    }

    /** Высота столбика осадков; 0 для часов без осадков (там рисуется тонкая полоска). */
    fun barHeights(mm: List<Double>): List<Float> {
        val scale = maxOf(MIN_SCALE_MM, mm.maxOrNull() ?: 0.0)
        return mm.map { v -> if (v > 0.0) maxOf(BAR_MIN, (v / scale * BAR_MAX).toFloat()) else 0f }
    }
}
