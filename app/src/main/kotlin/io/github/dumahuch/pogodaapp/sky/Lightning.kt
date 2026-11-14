package io.github.dumahuch.pogodaapp.sky

import kotlin.math.exp
import kotlin.random.Random

/**
 * Один разряд грозы: пауза перед ним [gapSeconds], одна или две вспышки ([pulses]) и, если [bolt] не меньше нуля, молния
 * (номер картинки из [Lightning.BOLT_COUNT]). [boltX] центр молнии по ширине экрана (0…1), [mirrored] молния отражена,
 * [glowX] и [glowY] центр свечения в облаках (доли ширины и высоты экрана).
 */
internal class Strike(
    val gapSeconds: Double,
    val pulses: List<Pulse>,
    val bolt: Int,
    val boltX: Double,
    val mirrored: Boolean,
    val glowX: Double,
    val glowY: Double,
) {
    /** Вспышка: начало от начала разряда в секундах и сила (0…1). */
    class Pulse(val start: Double, val peak: Double)

    /** Сколько длится разряд: после последней вспышки свет гаснет почти до нуля. */
    val duration: Double get() = pulses.maxOf { it.start } + Lightning.RISE_SECONDS + Lightning.DECAY_SECONDS * Lightning.FADE_DECAYS

    /** Яркость в момент [t] секунд от начала разряда (0…1): быстрый подъём и плавное затухание каждой вспышки, берётся наибольшая. */
    fun light(t: Double): Double = pulses.maxOf { Lightning.pulse(t - it.start) * it.peak }

    /** Кадр разряда в момент [t] или пусто, когда разряд кончился. */
    fun frame(t: Double): LightningFrame? {
        if (t < 0 || t > duration) return null
        val light = light(t)
        val boltLight = if (bolt >= 0) (light * Lightning.BOLT_GAIN).coerceAtMost(1.0) else 0.0
        return LightningFrame(this, light, boltLight)
    }
}

/** Что видно в кадре: свет вспышки [flash] (0…1) и яркость молнии [bolt] (0…1, ноль у вспышки без молнии). */
internal data class LightningFrame(val strike: Strike, val flash: Double, val bolt: Double)

/**
 * Расписание грозы: разряды с паузами от [MIN_GAP_SECONDS] до [MAX_GAP_SECONDS], первый вскоре после начала. Чуть меньше половины
 * разрядов с молнией, остальные только свет в облаках. Вспышек в разряде не больше двух: так не больше двух вспышек в секунду
 * (WCAG 2.3.1 разрешает до трёх).
 */
internal class Lightning(seed: Long) {

    private val random = Random(seed)
    private var first = true
    private var lastBolt = -1

    /** Следующий разряд. */
    fun next(): Strike {
        val gap = if (first) FIRST_GAP_SECONDS else MIN_GAP_SECONDS + random.nextDouble() * (MAX_GAP_SECONDS - MIN_GAP_SECONDS)
        first = false
        val withBolt = random.nextDouble() < BOLT_SHARE
        val peak = if (withBolt) 0.8 + 0.2 * random.nextDouble() else 0.45 + 0.35 * random.nextDouble()
        val pulses = buildList {
            add(Strike.Pulse(0.0, peak))
            if (random.nextDouble() < DOUBLE_SHARE) {
                add(Strike.Pulse(MIN_PULSE_GAP + random.nextDouble() * (MAX_PULSE_GAP - MIN_PULSE_GAP), peak * (0.6 + 0.4 * random.nextDouble())))
            }
        }
        val bolt = if (withBolt) pickBolt() else -1
        val boltX = 0.2 + 0.6 * random.nextDouble()
        return Strike(
            gapSeconds = gap,
            pulses = pulses,
            bolt = bolt,
            boltX = boltX,
            mirrored = random.nextBoolean(),
            glowX = if (withBolt) boltX else 0.15 + 0.7 * random.nextDouble(),
            glowY = if (withBolt) BOLT_GLOW_Y else 0.1 + 0.35 * random.nextDouble(),
        )
    }

    /** Молния не повторяет предыдущую. */
    private fun pickBolt(): Int {
        var bolt = random.nextInt(BOLT_COUNT)
        if (bolt == lastBolt) bolt = (bolt + 1 + random.nextInt(BOLT_COUNT - 1)) % BOLT_COUNT
        lastBolt = bolt
        return bolt
    }

    companion object {
        /** Картинок молний в `assets/lightning` (bolt_01…bolt_06). */
        const val BOLT_COUNT = 6
        const val FIRST_GAP_SECONDS = 2.0
        const val MIN_GAP_SECONDS = 6.0
        const val MAX_GAP_SECONDS = 20.0
        const val BOLT_SHARE = 0.45
        const val DOUBLE_SHARE = 0.4
        const val MIN_PULSE_GAP = 0.09
        const val MAX_PULSE_GAP = 0.16
        const val RISE_SECONDS = 0.02
        const val DECAY_SECONDS = 0.06
        const val FADE_DECAYS = 5.0

        /** Молния ярче света в облаках: видна целиком и тогда, когда вспышка уже гаснет. */
        const val BOLT_GAIN = 1.4

        /** Свечение вокруг молнии по высоте экрана: верхняя треть, где молния выходит из облаков. */
        const val BOLT_GLOW_Y = 0.3

        /** Одна вспышка силы 1 в момент [t] от её начала: линейный подъём за [RISE_SECONDS], затем спад по экспоненте. */
        fun pulse(t: Double): Double = when {
            t < 0 -> 0.0
            t < RISE_SECONDS -> t / RISE_SECONDS
            else -> exp(-(t - RISE_SECONDS) / DECAY_SECONDS)
        }

        fun file(bolt: Int): String = "lightning/bolt_" + (bolt + 1).toString().padStart(2, '0') + ".webp"
    }
}
