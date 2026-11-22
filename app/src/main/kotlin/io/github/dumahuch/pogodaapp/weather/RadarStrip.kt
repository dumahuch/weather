package io.github.dumahuch.pogodaapp.weather

import java.time.Duration
import java.time.Instant

/**
 * Карточка «Ближайшие 2 часа» на странице осадков (решение Димы 22.11.2026): кадры радара DWD с шагом пять минут от
 * текущего и первый дождь в них. Чистая логика; сеть в `ui/metric/RadarCard.kt`, порог и «сейчас» как у «Скоро дождь».
 */
object RadarStrip {

    /** Сколько кадров показывает карточка: два часа по пять минут. */
    const val FRAMES = 24

    /** Окно карточки. */
    val SPAN: Duration = Duration.ofHours(2)

    /** Дождь в окне: когда закончится (время последнего дождливого кадра) и сколько выпадет, мм. */
    data class Spell(val end: Instant, val totalMm: Double)

    /** Кадры от текущего (первый не раньше [now], он описывает пять минут до своего времени) по порядку, не больше [FRAMES]. */
    fun of(frames: List<RadarFrame>, now: Instant): List<RadarFrame> =
        frames.filter { !it.time.isBefore(now) }.sortedBy { it.time }.take(FRAMES)

    /** Первый дождь в [strip] (кадры подряд не слабее порога): конец и сумма; пусто, если в окне сухо. */
    fun spell(strip: List<RadarFrame>): Spell? {
        val first = strip.indexOfFirst { it.mmPerHour >= RainSoon.THRESHOLD_MM_PER_HOUR }
        if (first < 0) return null
        var last = first
        while (last + 1 < strip.size && strip[last + 1].mmPerHour >= RainSoon.THRESHOLD_MM_PER_HOUR) last++
        val hoursPerFrame = RainSoon.STEP.toMinutes() / 60.0
        val total = (first..last).sumOf { strip[it].mmPerHour * hoursPerFrame }
        return Spell(strip[last].time, total)
    }
}
