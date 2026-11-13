package io.github.dumahuch.pogodaapp.sky

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Форма сугроба на верхней кромке карточки (всё в dp). Высота берётся по силе снега, верх неровный и к краям
 * тоньше, низ повторяет скруглённые углы карточки. Рисует [io.github.dumahuch.pogodaapp.ui.components.snowDrift].
 */
internal object SnowDriftShape {
    const val MIN_HEIGHT_DP = 1.5
    const val MAX_HEIGHT_DP = 10.0

    /** За сколько секунд сугроб вырастает до своей высоты (в макете около 12 с) и сколько сходит, когда снег прекратился (dp в секунду). */
    const val GROW_SECONDS = 12.0
    const val MELT_DP_PER_SECOND = 0.5

    /** Ширина, на которой сугроб набирает полную высоту от края карточки, и доля высоты у самого края. */
    private const val TAPER_DP = 44.0
    private const val EDGE_SHARE = 0.12
    private const val MIN_PROFILE = 0.25

    /** Высота сугроба по силе снега (0…1). */
    fun heightDp(strength: Double): Double = MIN_HEIGHT_DP + (MAX_HEIGHT_DP - MIN_HEIGHT_DP) * strength.coerceIn(0.0, 1.0)

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
