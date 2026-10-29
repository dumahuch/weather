package io.github.dumahuch.pogodaapp.ui.sky

/**
 * Счётчик кадров для экрана проверки: сколько кадров нарисовано, сколько из них дольше бюджета
 * ([budgetMillis], время одного кадра при частоте экрана), и как распределено время последних кадров.
 */
internal class FrameStats(private val budgetMillis: Double, private val capacity: Int = 600) {

    class Summary(
        val frames: Int,
        val slow: Int,
        val median: Double,
        val p95: Double,
        val longest: Double,
        val perSecond: Double,
    )

    private val durations = DoubleArray(capacity)
    private val stamps = LongArray(capacity)
    private var next = 0
    private var stored = 0
    private var frames = 0
    private var slow = 0
    private var firstStamp = 0L

    fun reset() {
        next = 0
        stored = 0
        frames = 0
        slow = 0
    }

    /** Кадр закончен в момент [atNanos] и занял [durationMillis]. */
    fun add(atNanos: Long, durationMillis: Double) {
        if (frames == 0) firstStamp = atNanos
        durations[next] = durationMillis
        stamps[next] = atNanos
        next = (next + 1) % capacity
        if (stored < capacity) stored++
        frames++
        if (durationMillis > budgetMillis) slow++
    }

    fun summary(nowNanos: Long): Summary {
        if (stored == 0) return Summary(frames, slow, 0.0, 0.0, 0.0, 0.0)
        val sorted = DoubleArray(stored) { durations[it] }.also { it.sort() }
        var recent = 0
        for (i in 0 until stored) {
            if (nowNanos - stamps[i] <= WINDOW_NANOS) recent++
        }
        // Пока после сброса или открытия прошло меньше пяти секунд, делим на прошедшее время (не меньше секунды),
        // иначе частота занижена: 63 кадра за 3 секунды показывались как 12,6 вместо 20 (29.10.2026).
        val observed = (nowNanos - firstStamp).coerceIn(MIN_WINDOW_NANOS, WINDOW_NANOS)
        return Summary(
            frames = frames,
            slow = slow,
            median = sorted[(stored - 1) / 2],
            p95 = sorted[((stored - 1) * 0.95).toInt()],
            longest = sorted[stored - 1],
            perSecond = recent * 1e9 / observed,
        )
    }

    private companion object {
        /** Частота кадров считается по последним пяти секундам. */
        const val WINDOW_NANOS = 5_000_000_000L
        const val MIN_WINDOW_NANOS = 1_000_000_000L
    }
}
