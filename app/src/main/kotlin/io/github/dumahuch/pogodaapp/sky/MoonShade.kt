package io.github.dumahuch.pogodaapp.sky

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Фаза луны поверх картинки полной луны (решение Димы 22.11.2026): прежние четыре картинки фаз с резким эллипсом выглядели
 * вырезанной фигурой, а тусклый диск пятном. Здесь свет считается по шару: к границе света и тени яркость спадает широкой
 * плавной полосой, тёмная часть почти прозрачная и чуть синее (пепельный свет виден только у тонкого серпа), круглого свечения
 * вокруг неполной луны нет.
 *
 * Маска квадратная, как картинка луны (`moon_full`, диск занимает [Celestial.MOON_FRACTION] стороны). Каждый пиксель это
 * множитель цвета и прозрачности: рисуется поверх картинки режимом умножения (`Modulate`) на отдельном слое.
 */
internal object MoonShade {

    /** Сторона маски в пикселях: диск около 136 пикселей, на экране растягивается примерно вдвое (сглаживание билинейное). */
    const val SIDE = 256

    /** Ширина полосы перехода от света к тени (в долях освещённости по шару): шире значит мягче граница. */
    private const val SOFTNESS = 0.62

    /** Наклон начала полосы: свет нарастает быстрее, чем гаснет (как на снимках, где у терминатора серо, но не черно). */
    private const val RAMP_POWER = 0.85

    /** Прозрачность тёмной части у самой тонкой луны; к четверти почти ноль, у выпуклой ноль (пепельный свет). */
    private const val EARTHSHINE = 0.09
    private const val EARTHSHINE_POWER = 2.5

    /** Цвет тёмной части относительно освещённой: синее (свет Земли), множитель к цвету картинки. */
    private val DARK_TINT = doubleArrayOf(0.595, 0.706, 0.956)

    /** У выпуклой и полной луны край диска не темнеет: шар для расчёта чуть сплющен ([LIMB_FLATTEN]). */
    private const val LIMB_FLATTEN = 0.85

    /**
     * Множитель в точке (u, v) диска радиуса 1 (вне диска: свечение картинки): прозрачность и цвет 0…1.
     * [lit] освещённая доля диска 0…1, [lightOnRight] свет справа на экране.
     */
    fun at(u: Double, v: Double, lit: Double, lightOnRight: Boolean): Shade {
        val k = lit.coerceIn(0.0, 1.0)
        val cosA = 2 * k - 1
        val sinA = sqrt((1 - cosA * cosA).coerceAtLeast(0.0)) * if (lightOnRight) 1 else -1
        val r2 = u * u + v * v
        if (r2 > 1) {
            // Свечение картинки остаётся только там, куда падает свет, и слабеет с фазой; со стороны тени его нет совсем
            // (иначе тёмная часть выглядит обведённой).
            val r = sqrt(r2)
            val toward = (u / r * sinA + cosA).coerceIn(0.0, 1.0)
            return Shade(k * k * toward, 1.0, 1.0, 1.0)
        }
        val z = sqrt(if (cosA > 0) (1 - r2 * LIMB_FLATTEN) else (1 - r2))
        val facing = u * sinA + z * cosA
        val t = (facing / SOFTNESS).coerceIn(0.0, 1.0)
        val light = (t.pow(RAMP_POWER) * (3 - 2 * t)).coerceIn(0.0, 1.0)
        val earthshine = EARTHSHINE * (1 - k).pow(EARTHSHINE_POWER)
        val alpha = light + earthshine * (1 - light)
        return Shade(
            alpha,
            DARK_TINT[0] + (1 - DARK_TINT[0]) * light,
            DARK_TINT[1] + (1 - DARK_TINT[1]) * light,
            DARK_TINT[2] + (1 - DARK_TINT[2]) * light,
        )
    }

    /** Маска [SIDE]×[SIDE] в цветах ARGB без предумножения (для `Bitmap.createBitmap(colors, …)`). */
    fun mask(lit: Double, lightOnRight: Boolean): IntArray {
        val pixels = IntArray(SIDE * SIDE)
        for (j in 0 until SIDE) {
            val v = ((j + 0.5) / SIDE * 2 - 1) / Celestial.MOON_FRACTION
            for (i in 0 until SIDE) {
                val u = ((i + 0.5) / SIDE * 2 - 1) / Celestial.MOON_FRACTION
                val s = at(u, v, lit, lightOnRight)
                pixels[j * SIDE + i] = (byte(s.alpha) shl 24) or (byte(s.r) shl 16) or (byte(s.g) shl 8) or byte(s.b)
            }
        }
        return pixels
    }

    /** Ключ кэша маски: освещённость с шагом 0,002 (меняется за сутки примерно на 0,03) и сторона света. */
    fun key(lit: Double, lightOnRight: Boolean): Int = Math.round(lit.coerceIn(0.0, 1.0) * 500).toInt() * 2 + if (lightOnRight) 1 else 0

    /** Освещённость по ключу [key] (середина шага). */
    fun litOf(key: Int): Double = (key / 2) / 500.0

    private fun byte(x: Double): Int = Math.round(x.coerceIn(0.0, 1.0) * 255).toInt()

    class Shade(val alpha: Double, val r: Double, val g: Double, val b: Double)
}
