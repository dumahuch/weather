package io.github.dumahuch.pogodaapp.weather

import java.time.Duration
import java.time.Instant

/** Один кадр радара над местом: [time] это конец пятиминутки, [mmPerHour] средняя интенсивность в пересчёте на час. */
data class RadarFrame(val time: Instant, val mmPerHour: Double)

/** Что радар обещает на ближайший час. */
sealed interface RainOutlook {
    /** Данных на сейчас нет (ответ устарел или пуст): прежнее состояние не меняется. */
    data object Unknown : RainOutlook

    /** В ближайший час осадков выше порога нет. */
    data object Dry : RainOutlook

    /** Дождь уже идёт. */
    data object Raining : RainOutlook

    /** Дождь начнётся через [inMinutes] минут (0 значит уже в ближайшие минуты). */
    data class Starts(val inMinutes: Int) : RainOutlook
}

/** Что сделать с уведомлением: показать (если [notifyInMinutes] не `null`), убрать ([cancel]); [notified] записывается как новое «уже сообщали». */
data class RainDecision(val notifyInMinutes: Int?, val cancel: Boolean, val notified: Boolean)

/**
 * «Скоро дождь» (0.7.0, шаг 3; решения Димы 08.11.2026: порог 0,5 мм/ч, окно один час). Чистая логика над кадрами радара
 * DWD с шагом пять минут; сеть и уведомление отдельно (`RainNotifier`).
 */
object RainSoon {
    /** Дождём считается интенсивность не ниже порога, мм/ч. */
    const val THRESHOLD_MM_PER_HOUR = 0.5

    /** Сообщаем о дожде, который начнётся не позже чем через час. */
    val WINDOW: Duration = Duration.ofHours(1)

    /** Шаг радара. */
    val STEP: Duration = Duration.ofMinutes(5)

    /** Одиночный кадр над порогом считается дождём, только если он вдвое сильнее порога; слабее нужны два кадра подряд (шум). */
    private const val SINGLE_FRAME_FACTOR = 2

    fun outlook(frames: List<RadarFrame>, now: Instant): RainOutlook {
        val sorted = frames.sortedBy { it.time }
        // Кадр с временем T описывает пять минут до T, поэтому «сейчас» это первый кадр, который не раньше текущего момента.
        val currentIndex = sorted.indexOfFirst { !it.time.isBefore(now) }
        if (currentIndex < 0) return RainOutlook.Unknown
        if (sorted[currentIndex].mmPerHour >= THRESHOLD_MM_PER_HOUR) return RainOutlook.Raining
        val horizon = now.plus(WINDOW)
        for (index in currentIndex + 1 until sorted.size) {
            val frame = sorted[index]
            val start = frame.time.minus(STEP)
            if (!start.isBefore(horizon)) break
            if (isRain(frame, sorted.getOrNull(index + 1))) {
                return RainOutlook.Starts(minutesUntil(now, start))
            }
        }
        return RainOutlook.Dry
    }

    /** Решение по уведомлению; [notified]: о текущем дожде уже сообщали (сбрасывается, когда в окне сухо). */
    fun decide(outlook: RainOutlook, notified: Boolean): RainDecision = when (outlook) {
        RainOutlook.Unknown -> RainDecision(null, cancel = false, notified = notified)
        RainOutlook.Dry -> RainDecision(null, cancel = true, notified = false)
        RainOutlook.Raining -> RainDecision(null, cancel = true, notified = notified)
        is RainOutlook.Starts ->
            RainDecision(if (notified) null else outlook.inMinutes, cancel = false, notified = true)
    }

    /** Минуты для текста: округление вверх до пяти, чтобы не обещать точность, которой у радара и у получасового шага нет. */
    fun roundedMinutes(minutes: Int): Int = (minutes + 4) / 5 * 5

    private fun isRain(frame: RadarFrame, next: RadarFrame?): Boolean = when {
        frame.mmPerHour < THRESHOLD_MM_PER_HOUR -> false
        frame.mmPerHour >= THRESHOLD_MM_PER_HOUR * SINGLE_FRAME_FACTOR -> true
        else -> next != null && next.mmPerHour >= THRESHOLD_MM_PER_HOUR
    }

    private fun minutesUntil(now: Instant, start: Instant): Int {
        val seconds = Duration.between(now, start).seconds.coerceAtLeast(0)
        return ((seconds + 59) / 60).toInt()
    }
}
