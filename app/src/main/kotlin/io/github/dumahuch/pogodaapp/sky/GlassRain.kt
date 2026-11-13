package io.github.dumahuch.pogodaapp.sky

import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

/**
 * Капли дождя на стекле одной карточки размером [widthDp] × [heightDp] (всё в dp, время в секундах). Перенос прототипа
 * 13.11.2026: капли садятся на стекло и медленно растут; созревшие срываются и стекают рывками, оставляя мокрый след и
 * мелкие капли, по пути вбирают встречные. Состояние меняется только в [step]; рисует его
 * [io.github.dumahuch.pogodaapp.ui.components.glassDrops].
 */
internal class GlassRain(val widthDp: Double, val heightDp: Double, seed: Long) {

    /** Неподвижная капля: центр и радиус. */
    class Bead(var x: Double, var y: Double, var r: Double)

    /** Стекающая капля: идёт рывками ([moving] и [timer]), [v] её скорость, [wobble] фаза покачивания. */
    class Runner(var x: Double, var y: Double, var r: Double, internal val k: Double, internal val wobble: Double) {
        var moving = true
        var timer = 0.0
        var v = 0.0
        internal var sinceBead = 0.0
    }

    /** Отрезок мокрого следа: концы, толщина и возраст в секундах. */
    class Trail(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val width: Double) {
        var age = 0.0
    }

    private val random = Random(seed)
    private val area = min(widthDp * heightDp / REF_AREA, MAX_AREA)
    val beads = ArrayList<Bead>()
    val runners = ArrayList<Runner>()
    val trails = ArrayList<Trail>()
    private var spawnDebt = 0.0
    private var runnerClock = 0.0

    /** Сколько капель может держать стекло при силе [strength]: на площадь карточки и меньше при слабом дожде. */
    fun capacity(strength: Double): Int = (MAX_BEADS * area * (MIN_SHARE + (1 - MIN_SHARE) * strength.coerceIn(0.0, 1.0))).toInt()

    /** Продвигает стекло на [dt] секунд дождя силой [strength] (0…1). Большие перерывы режутся, чтобы капли не прыгали. */
    fun step(dt: Double, strength: Double) {
        val d = dt.coerceIn(0.0, MAX_DT)
        if (d == 0.0) return
        val s = strength.coerceIn(0.0, 1.0)
        spawn(d, s)
        for (b in beads) if (b.r < GROW_LIMIT) b.r += GROW_RATE * d
        release(d)
        for (runner in runners) move(runner, d)
        runners.removeAll { it.y > heightDp + it.r * 3 }
        for (t in trails) t.age += d
        trails.removeAll { it.age > TRAIL_LIFE }
    }

    /** Прозрачность следа возраста [age]: свежий ярче, к концу жизни исчезает. */
    fun trailAlpha(age: Double): Double = max(0.0, 1 - age / TRAIL_LIFE)

    private fun spawn(d: Double, s: Double) {
        spawnDebt += d * SPAWN_PER_SECOND * area * (MIN_SHARE + (1 - MIN_SHARE) * s)
        val cap = capacity(s)
        while (spawnDebt >= 1) {
            spawnDebt -= 1
            if (beads.size < cap) {
                beads += Bead(random.nextDouble() * widthDp, heightDp * random.nextDouble().pow(1.35), 1.0 + 2.6 * random.nextDouble().pow(2.4))
            }
        }
    }

    /** Раз в [RELEASE_SECONDS] созревшая капля может сорваться, иногда новая крупная капля прилетает сверху сразу стекающей. */
    private fun release(d: Double) {
        runnerClock += d
        if (runnerClock < RELEASE_SECONDS) return
        runnerClock = 0.0
        if (runners.size >= max(1, (MAX_RUNNERS * area).toInt())) return
        val ripe = beads.filter { it.r > RIPE_RADIUS && it.y < heightDp * 0.8 }
        if (ripe.isNotEmpty() && random.nextDouble() < 0.7) {
            val b = ripe[random.nextInt(ripe.size)]
            beads.remove(b)
            runners += runner(b.x, b.y, b.r + 0.9)
        } else if (random.nextDouble() < 0.3) {
            runners += runner(random.nextDouble() * widthDp, random.nextDouble() * heightDp * 0.35, 3.8 + random.nextDouble() * 1.6)
        }
    }

    private fun runner(x: Double, y: Double, r: Double) =
        Runner(x, y, r, 0.8 + 0.4 * random.nextDouble(), random.nextDouble() * 6.28).also { it.timer = 0.3 + random.nextDouble() * 0.5 }

    private fun move(runner: Runner, d: Double) {
        runner.timer -= d
        if (runner.timer <= 0) {
            runner.moving = !runner.moving
            runner.timer = if (runner.moving) 0.25 + random.nextDouble() * 0.7 else 0.15 + random.nextDouble() * 0.8
            runner.v = 0.0
        }
        if (runner.moving) {
            val top = RUN_SPEED + RUN_SPEED_PER_DP * runner.r * runner.k
            runner.v = min(top, runner.v + top * ACCELERATION * d)
            val dy = runner.v * d
            val px = runner.x
            val py = runner.y
            runner.y += dy
            runner.x += sin(runner.y * 0.018 + runner.wobble) * dy * 0.035
            trails += Trail(px, py, runner.x, runner.y, runner.r * 0.5)
            runner.sinceBead += dy
            if (runner.sinceBead > 10 + random.nextDouble() * 14) {
                runner.sinceBead = 0.0
                beads += Bead(runner.x + (random.nextDouble() - 0.5) * runner.r * 0.8, runner.y - runner.r, 0.8 + random.nextDouble() * 0.9)
            }
            runner.r = max(MIN_RUNNER_RADIUS, runner.r - SHED_PER_DP * dy)
        }
        // Капля вбирает встречные: объём складывается (с потерей части на стекле).
        val beadsLeft = beads.iterator()
        while (beadsLeft.hasNext()) {
            val b = beadsLeft.next()
            if (hypot(b.x - runner.x, b.y - runner.y) < runner.r * 0.9 + b.r) {
                runner.r = cbrt(runner.r.pow(3) + 0.7 * b.r.pow(3))
                beadsLeft.remove()
            }
        }
        if (trails.size > MAX_TRAILS) trails.subList(0, trails.size - MAX_TRAILS).clear()
    }

    companion object {
        /** Карточка образца 350 × 250 dp: на ней до 430 капель и до 9 стекающих, садится 34 капли в секунду. */
        private const val REF_AREA = 350.0 * 250.0
        const val MAX_BEADS = 430

        /** Карточка больше полутора образцов держит капель как полтора образца: число капель на кадр ограничено. */
        private const val MAX_AREA = 1.5
        private const val MAX_RUNNERS = 9
        private const val SPAWN_PER_SECOND = 34.0

        /** При самом слабом дожде капель и прилёта меньше в такой доле. */
        private const val MIN_SHARE = 0.35
        private const val GROW_RATE = 0.03
        private const val GROW_LIMIT = 4.2
        private const val RIPE_RADIUS = 3.2
        private const val RELEASE_SECONDS = 0.4
        private const val RUN_SPEED = 55.0
        private const val RUN_SPEED_PER_DP = 16.0
        private const val ACCELERATION = 6.0
        private const val SHED_PER_DP = 0.0035
        private const val MIN_RUNNER_RADIUS = 3.1
        const val TRAIL_LIFE = 2.5
        private const val MAX_TRAILS = 600

        /** Шаг больше этого (перерыв, возврат в приложение) режется: капля не перепрыгивает карточку. */
        private const val MAX_DT = 0.1
    }
}
