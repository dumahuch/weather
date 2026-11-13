package io.github.dumahuch.pogodaapp.sky

import java.util.Random
import kotlin.math.min

/**
 * Брызги дождя над верхней кромкой стекла шириной [widthDp] (dp, секунды): капля бьёт о кромку, несколько мелких капель
 * разлетаются вверх и в стороны и падают. Положение считается по возрасту, [step] только двигает время, заводит новые
 * брызги и убирает старые. Рисует [io.github.dumahuch.pogodaapp.ui.components.rainSplash]; стекающую с кромки воду
 * заводит [GlassRain].
 */
internal class EdgeSplash(val widthDp: Double, seed: Long) {

    /** Один всплеск в точке [x] кромки: у каждой частицы своя начальная скорость вбок ([vx]) и вверх ([vy]) и размер ([radius]). */
    class Splash(val x: Double, val vx: DoubleArray, val vy: DoubleArray, val radius: DoubleArray, val life: Double) {
        var age = 0.0

        /** Сдвиг частицы [i] по горизонтали от точки удара. */
        fun dx(i: Int): Double = vx[i] * age

        /** Высота частицы [i] над кромкой (положительная вверх, у упавшей ниже нуля). */
        fun height(i: Int): Double = vy[i] * age - 0.5 * GRAVITY * age * age

        /** Прозрачность: сначала почти полная, к концу жизни гаснет. */
        fun alpha(): Double = (1 - age / life).coerceIn(0.0, 1.0)
    }

    private val random = Random(seed)
    val splashes = ArrayList<Splash>()
    private var debt = 0.0

    /** Сколько всплесков одновременно: брызги живут меньше секунды, больше нужного не держим. */
    private val capacity = (MAX_SPLASHES_PER_100DP * widthDp / 100.0).toInt().coerceAtLeast(2)

    fun step(dt: Double, strength: Double) {
        val d = dt.coerceIn(0.0, MAX_DT)
        if (d == 0.0) return
        val s = strength.coerceIn(0.0, 1.0)
        debt += d * SPLASH_PER_100DP * widthDp / 100.0 * (MIN_SHARE + (1 - MIN_SHARE) * s)
        while (debt >= 1) {
            debt -= 1
            if (splashes.size < capacity) splashes += spawn()
        }
        for (splash in splashes) splash.age += d
        splashes.removeAll { it.age >= it.life }
    }

    private fun spawn(): Splash {
        val margin = min(MARGIN, widthDp / 4)
        val count = MIN_PARTICLES + random.nextInt(MAX_PARTICLES - MIN_PARTICLES + 1)
        val vx = DoubleArray(count) { (random.nextDouble() - 0.5) * 2 * SIDE_SPEED }
        val vy = DoubleArray(count) { UP_MIN + random.nextDouble() * (UP_MAX - UP_MIN) }
        val radius = DoubleArray(count) { 0.5 + random.nextDouble() * 0.6 }
        return Splash(margin + random.nextDouble() * (widthDp - 2 * margin), vx, vy, radius, LIFE_MIN + random.nextDouble() * (LIFE_MAX - LIFE_MIN))
    }

    companion object {
        /** Всплесков в секунду на 100 dp кромки при сильном дожде; при самом слабом их меньше в [MIN_SHARE] раз. */
        private const val SPLASH_PER_100DP = 5.0
        private const val MAX_SPLASHES_PER_100DP = 10.0
        private const val MIN_SHARE = 0.35
        private const val MAX_DT = 0.1
        private const val MARGIN = 10.0

        /** Гравитация брызг (dp/с²), начальная скорость вверх и вбок (dp/с), число частиц и время жизни (с). */
        const val GRAVITY = 260.0
        private const val UP_MIN = 35.0
        private const val UP_MAX = 85.0
        private const val SIDE_SPEED = 26.0
        private const val MIN_PARTICLES = 3
        private const val MAX_PARTICLES = 5
        private const val LIFE_MIN = 0.28
        private const val LIFE_MAX = 0.45
    }
}
