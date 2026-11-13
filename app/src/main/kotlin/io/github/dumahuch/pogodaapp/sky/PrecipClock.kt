package io.github.dumahuch.pogodaapp.sky

/**
 * Часы слоя осадков: из кадров экрана (наносекунды) выдаёт время в секундах, но не чаще раза в [stepNanos].
 * Первый кадр всегда обновляет время; считает только разности, поэтому большие значения времени кадра ничего не переполняют.
 */
internal class PrecipClock(private val stepNanos: Long, private val startSeconds: Double) {
    private var first = 0L
    private var last = 0L
    private var started = false

    /** Новое время в секундах, если с прошлого обновления прошло не меньше шага, иначе `null` (кадр пропускается). */
    fun seconds(frameNanos: Long): Double? {
        if (!started) {
            started = true
            first = frameNanos
            last = frameNanos
            return startSeconds
        }
        if (frameNanos - last < stepNanos) return null
        last = frameNanos
        return startSeconds + (frameNanos - first) / NANOS_PER_SECOND
    }

    private companion object {
        const val NANOS_PER_SECOND = 1e9
    }
}
