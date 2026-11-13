package io.github.dumahuch.pogodaapp.sky

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Форма сугроба на верхней кромке карточки (всё в dp). Растёт, пока идёт снег (быстрее при сильном), верх неровный и к краям
 * тоньше, низ повторяет скруглённые углы карточки. Рисует [io.github.dumahuch.pogodaapp.ui.components.snowDrift].
 */
internal object SnowDriftShape {
    /** Сугроб растёт, пока идёт снег, до этой высоты (dp); когда снег прекратился, сходит с такой скоростью (dp в секунду). */
    const val MAX_HEIGHT_DP = 12.0
    const val MELT_DP_PER_SECOND = 0.5

    /** За сколько секунд сугроб вырастает от нуля до предела: при слабом снеге и при сильном (решение Димы 13.11: «хочу, чтобы сугроб рос»). */
    private const val LIGHT_GROW_SECONDS = 90.0
    private const val HEAVY_GROW_SECONDS = 25.0

    /** Ширина, на которой сугроб набирает полную высоту от края карточки, и доля высоты у самого края. */
    private const val TAPER_DP = 44.0
    private const val EDGE_SHARE = 0.12
    private const val MIN_PROFILE = 0.25

    /** Пока сугроб ниже этой высоты (dp), он проявляется из прозрачного: иначе первая полоска появлялась бы внезапно. */
    const val FADE_IN_DP = 3.0

    /** Непрозрачность сугроба высотой [heightDp]: от 0 у пустой кромки плавно до 1 к [FADE_IN_DP]. */
    fun visibility(heightDp: Double): Double = smooth(0.0, FADE_IN_DP, heightDp)

    /** Скорость роста сугроба (dp в секунду) по силе снега (0…1): чем сильнее снег, тем быстрее. */
    fun growDpPerSecond(strength: Double): Double {
        val s = strength.coerceIn(0.0, 1.0)
        return MAX_HEIGHT_DP / (LIGHT_GROW_SECONDS + (HEAVY_GROW_SECONDS - LIGHT_GROW_SECONDS) * s)
    }

    /** Доля высоты на расстоянии [x] от левого края карточки шириной [width]: у краёв тоньше. */
    fun taper(x: Double, width: Double): Double =
        EDGE_SHARE + (1 - EDGE_SHARE) * smooth(0.0, 1.0, min(x, width - x) / TAPER_DP)

    /** Неровность верха в точке [x] (0,25…1,0), у каждой карточки своя по [seed]. */
    fun profile(x: Double, seed: Int): Double {
        val p0 = phase(seed, 0)
        val p1 = phase(seed, 1)
        val p2 = phase(seed, 2)
        return (0.62 + 0.2 * sin(x * 0.045 + p0) + 0.12 * sin(x * 0.11 + p1) + 0.06 * sin(x * 0.27 + p2)).coerceAtLeast(MIN_PROFILE)
    }

    /** Насколько ниже верхней грани карточки лежит её скруглённая кромка в точке [x] (0 на прямом участке, [radius] у самого края). */
    fun edgeDrop(x: Double, width: Double, radius: Double): Double {
        val d = min(x, width - x).coerceAtLeast(0.0)
        if (radius <= 0.0 || d >= radius) return 0.0
        val k = radius - d
        return radius - sqrt(radius * radius - k * k)
    }

    /** Фаза номер [i] для [seed]: тот же [seed] даёт тот же сугроб, другой другой. */
    private fun phase(seed: Int, i: Int): Double {
        val h = (seed * 73856093) xor ((i + 1) * 19349663)
        return ((h and 0xFFFF) / 65536.0) * 2 * PI
    }
}
