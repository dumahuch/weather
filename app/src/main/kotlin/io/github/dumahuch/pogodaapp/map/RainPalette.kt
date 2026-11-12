package io.github.dumahuch.pogodaapp.map

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Цвета дождя на карте. Сервер DWD отдаёт картинку в своём стиле и чужой стиль не принимает, поэтому каждая ступень
 * DWD (у радара 15, у модели 14) на телефоне заменяется нашим цветом. Ступеней не меньше, чем у DWD: цвет плавно идёт
 * от светло-голубого через синий и фиолетовый к розово-фиолетовому и зависит только от силы дождя, так что радар и
 * модель выглядят одинаково. Остальное в картинке (серая зона «нет данных», розовые контуры радара, всё
 * полупрозрачное) становится прозрачным.
 */
object RainPalette {

    /** Ступень стиля DWD: цвет её пикселей в картинке сервера и сила дождя (мм/ч), по которой берётся наш цвет. */
    class Step(val rgb: Int, val mmPerHour: Double)

    private fun step(r: Int, g: Int, b: Int, from: Double, to: Double) = Step(rgb(r, g, b), sqrt(from * to))

    /** Последняя ступень без верхней границы. */
    private fun top(r: Int, g: Int, b: Int, from: Double) = Step(rgb(r, g, b), from * OPEN_END)

    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

    /** Радар (мм/ч), стиль `niederschlagsradar`: интервалы по подписям легенды DWD. */
    private val RadarSteps = listOf(
        step(51, 255, 255, 0.1, 0.2),
        step(26, 204, 154, 0.2, 0.4),
        step(1, 153, 52, 0.4, 1.0),
        step(77, 179, 27, 1.0, 2.0),
        step(153, 204, 1, 2.0, 3.0),
        step(204, 230, 1, 3.0, 5.0),
        step(255, 255, 1, 5.0, 7.5),
        step(255, 196, 1, 7.5, 10.0),
        step(255, 137, 1, 10.0, 15.0),
        step(255, 69, 1, 15.0, 30.0),
        step(254, 0, 0, 30.0, 45.0),
        step(229, 0, 76, 45.0, 75.0),
        step(204, 0, 152, 75.0, 100.0),
        step(102, 0, 203, 100.0, 150.0),
        top(0, 0, 254, 150.0),
    )

    /** Модель ICON-EU (мм за час), стиль `icon-eu_reg00625_fd_sl_totprec01h_lawa`. */
    private val ModelSteps = listOf(
        step(192, 192, 192, 0.1, 2.0),
        step(254, 254, 192, 2.0, 5.0),
        step(254, 254, 91, 5.0, 10.0),
        step(207, 242, 0, 10.0, 15.0),
        step(160, 214, 37, 15.0, 20.0),
        step(54, 201, 105, 20.0, 25.0),
        step(0, 215, 215, 25.0, 30.0),
        step(0, 166, 221, 30.0, 40.0),
        step(0, 0, 254, 40.0, 60.0),
        step(151, 48, 194, 60.0, 80.0),
        step(217, 38, 199, 80.0, 100.0),
        step(254, 0, 0, 100.0, 130.0),
        step(160, 0, 0, 130.0, 160.0),
        top(96, 96, 96, 160.0),
    )

    /**
     * Опорные цвета нашей гаммы: сила дождя (мм/ч) и цвет. Выше 100 мм/ч ряд уходит в светлый розовый, чтобы и самые
     * сильные ступени DWD (в легенде их нет) различались; выше последней точки цвет не меняется.
     */
    private val Anchors = listOf(
        0.1 to 0x8FD3FF,
        1.0 to 0x4AA3FF,
        5.0 to 0x3D56FF,
        20.0 to 0x9A4DFF,
        100.0 to 0xE04DFF,
        200.0 to 0xFFB3F0,
    )

    /** Подписи легенды, мм/ч, от слабого к сильному. */
    val LegendValues = doubleArrayOf(0.1, 1.0, 5.0, 20.0, 100.0)

    /** Положение силы дождя на шкале легенды: 0 у самой слабой отметки, 1 у самой сильной (шкала логарифмическая). */
    fun legendPosition(mmPerHour: Double): Double {
        val low = log10(LegendValues.first())
        val high = log10(LegendValues.last())
        return (log10(mmPerHour.coerceIn(LegendValues.first(), LegendValues.last())) - low) / (high - low)
    }

    /** Наш цвет для дождя силой [mmPerHour] без прозрачности (для легенды), 0xRRGGBB. */
    fun legendColor(mmPerHour: Double): Int {
        val x = log10(mmPerHour.coerceIn(Anchors.first().first, Anchors.last().first))
        for (k in 1 until Anchors.size) {
            val (toMm, toColor) = Anchors[k]
            if (x <= log10(toMm) || k == Anchors.lastIndex) {
                val (fromMm, fromColor) = Anchors[k - 1]
                val t = ((x - log10(fromMm)) / (log10(toMm) - log10(fromMm))).coerceIn(0.0, 1.0)
                return mix(fromColor, toColor, t)
            }
        }
        return Anchors.last().second
    }

    /** Цвет на карте (ARGB): слабый дождь прозрачнее, чтобы под ним были видны облака и подложка. */
    fun mapColor(mmPerHour: Double): Int {
        val t = (legendPosition(mmPerHour) / legendPosition(FULL_ALPHA_AT)).coerceIn(0.0, 1.0)
        val alpha = Math.round((WEAK_ALPHA + (STRONG_ALPHA - WEAK_ALPHA) * t) * 255).toInt()
        return (alpha shl 24) or legendColor(mmPerHour)
    }

    private fun mix(from: Int, to: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * t).toInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private class Lookup(steps: List<Step>) {
        val keys = IntArray(steps.size) { 0xFF000000.toInt() or steps[it].rgb }
        val values = IntArray(steps.size) { mapColor(steps[it].mmPerHour) }
    }

    private val Radar = Lookup(RadarSteps)
    private val Model = Lookup(ModelSteps)

    /**
     * Заменяет цвета DWD нашими в списке пикселей ARGB (не предумноженных). Непрозрачный пиксель со цветом ступени
     * получает наш цвет, всё остальное (прозрачное, «нет данных», контуры, сглаженные края) становится прозрачным.
     */
    fun recolor(source: RainSource, pixels: IntArray) {
        val lookup = if (source == RainSource.Radar) Radar else Model
        var lastIn = 0
        var lastOut = 0
        for (i in pixels.indices) {
            val px = pixels[i]
            if (px == lastIn) {
                pixels[i] = lastOut
                continue
            }
            var out = 0
            if ((px ushr 24) == 0xFF) {
                for (k in lookup.keys.indices) {
                    if (lookup.keys[k] == px) {
                        out = lookup.values[k]
                        break
                    }
                }
            }
            lastIn = px
            lastOut = out
            pixels[i] = out
        }
    }

    /**
     * Дождь модели ICON-D2 приходит числом (мм/ч), а не картинкой: кадр держится байтом на пиксель, уровнем 0…255.
     * 0 значит «дождя нет» (слабее [LEVEL_LOW]), 1…255 логарифмически от [LEVEL_LOW] до [LEVEL_HIGH] мм/ч.
     */
    fun levelOf(mmPerHour: Double): Int {
        if (mmPerHour.isNaN() || mmPerHour < LEVEL_LOW) return 0
        val t = ln(mmPerHour.coerceAtMost(LEVEL_HIGH) / LEVEL_LOW) / ln(LEVEL_HIGH / LEVEL_LOW)
        return 1 + (t * (LEVELS - 1)).roundToInt()
    }

    /** Сила дождя (мм/ч) для уровня [levelOf]; у уровня 0 это 0. */
    fun mmOfLevel(level: Int): Double {
        if (level <= 0) return 0.0
        val t = (level.coerceAtMost(LEVELS) - 1).toDouble() / (LEVELS - 1)
        return LEVEL_LOW * Math.pow(LEVEL_HIGH / LEVEL_LOW, t)
    }

    /** Цвет каждого уровня (ARGB); у уровня 0 прозрачный. */
    private val LevelColors = IntArray(LEVELS + 1) { if (it == 0) 0 else mapColor(mmOfLevel(it)) }

    /** Кадр уровней в цвета ARGB для картинки на карте. */
    fun colorize(levels: ByteArray): IntArray = IntArray(levels.size) { LevelColors[levels[it].toInt() and 0xFF] }

    private const val LEVELS = 255
    private const val LEVEL_LOW = 0.1
    private const val LEVEL_HIGH = 200.0

    /** Начиная с этой силы дождь рисуется самым плотным. */
    private const val FULL_ALPHA_AT = 5.0
    private const val WEAK_ALPHA = 0.55
    private const val STRONG_ALPHA = 0.85

    /** Во сколько раз сила последней, открытой сверху ступени больше её нижней границы. */
    private const val OPEN_END = 1.2
}
