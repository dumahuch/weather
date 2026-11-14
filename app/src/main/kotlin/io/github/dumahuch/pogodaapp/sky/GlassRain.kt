package io.github.dumahuch.pogodaapp.sky

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Random

/**
 * Капли дождя на стекле одной карточки размером [widthDp] × [heightDp] (всё в dp, время в секундах). Перенос прототипа
 * 13.11.2026: капли садятся на стекло и медленно растут; созревшие срываются и стекают рывками, оставляя мокрый след и
 * мелкие капли, по пути вбирают встречные. Стекло вертикальное, поэтому вода собирается внизу: у кромки капли не
 * обрезаны, дошедшая до низа вода уходит на карточку ниже ([spill], см. [GlassStack]) или копится у кромки каплей,
 * которая, потяжелев, отрывается. Состояние меняется только в [step]; рисует его
 * [io.github.dumahuch.pogodaapp.ui.components.glassDrops].
 */
internal class GlassRain(val widthDp: Double, val heightDp: Double, seed: Long, cornerDp: Double = CORNER_DP) {

    /** Неподвижная капля: центр и радиус. [pooled] капля собралась у нижней кромки из стёкшей воды. */
    class Bead(var x: Double, var y: Double, var r: Double) {
        var pooled = false
    }

    /**
     * Стекающая капля: идёт рывками ([moving] и [timer]), [v] её скорость, [wobble] фаза покачивания. [dripping] капля
     * оторвалась от нижней кромки: падает всё быстрее и ничего не вбирает.
     */
    class Runner(var x: Double, var y: Double, var r: Double, internal val k: Double, internal val wobble: Double) {
        var moving = true
        var timer = 0.0
        var v = 0.0
        var dripping = false
        internal var sinceBead = 0.0
    }

    /** Отрезок мокрого следа: концы, толщина и возраст в секундах. */
    class Trail(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val width: Double) {
        var age = 0.0
    }

    /** Вода, летящая с карточки выше: упадёт на это стекло в точке [x] через [delay] секунд. */
    private class Incoming(val x: Double, val r: Double, var delay: Double)

    private val random = Random(seed)
    private val area = min(widthDp * heightDp / REF_AREA, MAX_AREA)

    /** Радиус скругления углов: у плашек и пилюль не больше половины меньшей стороны. */
    private val corner = min(cornerDp, min(widthDp, heightDp) / 2)
    val beads = ArrayList<Bead>()
    val runners = ArrayList<Runner>()
    val trails = ArrayList<Trail>()
    private val incoming = ArrayList<Incoming>()
    private val dripped = ArrayList<Runner>()
    private var spawnDebt = 0.0
    private var feedDebt = 0.0
    private var runnerClock = 0.0

    /**
     * Куда уходит вода, дошедшая до нижней кромки: точка [x] кромки и радиус капли; true, если воду принимает карточка
     * ниже. Пусто или false: вода собирается у кромки.
     */
    var spill: ((x: Double, r: Double) -> Boolean)? = null

    /** Сколько капель может держать стекло при силе [strength]: на площадь карточки и меньше при слабом дожде. */
    fun capacity(strength: Double): Int = (MAX_BEADS * area * (MIN_SHARE + (1 - MIN_SHARE) * strength.coerceIn(0.0, 1.0))).toInt()

    /** Вода с карточки выше: капля радиуса [r] упадёт на верхнюю кромку в точке [x] через [delay] секунд. */
    fun receive(x: Double, r: Double, delay: Double) {
        if (incoming.size < MAX_INCOMING) incoming += Incoming(x, r, max(0.0, delay))
    }

    /** Как вытянута стекающая капля сейчас (0…1): на разгоне хвост длиннее, стоящая капля тяжёлая и круглая. */
    fun stretch(runner: Runner): Double {
        if (!runner.moving) return 0.0
        val top = if (runner.dripping) DRIP_SPEED else RUN_SPEED + RUN_SPEED_PER_DP * runner.r * runner.k
        return (runner.v / top).coerceIn(0.0, 1.0)
    }

    /** Помещается ли круг радиуса [r] с центром ([x], [y]) целиком в стекло со скруглёнными углами. */
    fun fits(x: Double, y: Double, r: Double): Boolean {
        if (y < r || y > heightDp - r) return false
        val side = sideLimit(y, r)
        return x >= side && x <= widthDp - side
    }

    /** Продвигает стекло на [dt] секунд дождя силой [strength] (0…1). Большие перерывы режутся, чтобы капли не прыгали. */
    fun step(dt: Double, strength: Double) {
        val d = dt.coerceIn(0.0, MAX_DT)
        if (d == 0.0) return
        val s = strength.coerceIn(0.0, 1.0)
        spawn(d, s)
        feedFromEdge(d, s)
        land(d)
        for (b in beads) if (b.r < GROW_LIMIT && !b.pooled && fits(b.x, b.y, b.r + GROW_RATE * d)) b.r += GROW_RATE * d
        release(d)
        val flowing = runners.iterator()
        while (flowing.hasNext()) if (move(flowing.next(), d)) flowing.remove()
        if (dripped.isNotEmpty()) {
            runners += dripped
            dripped.clear()
        }
        runners.removeAll { it.y > heightDp + it.r * 3 }
        for (t in trails) t.age += d
        trails.removeAll { it.age > TRAIL_LIFE }
    }

    /** Прозрачность следа возраста [age]: свежий ярче, к концу жизни исчезает. */
    fun trailAlpha(age: Double): Double = max(0.0, 1 - age / TRAIL_LIFE)

    /** Наименьший отступ центра круга радиуса [r] на высоте [y] от боковой кромки; в углах он больше из-за скругления. */
    private fun sideLimit(y: Double, r: Double): Double {
        val c = corner
        val dy = when {
            y < c -> c - y
            y > heightDp - c -> y - (heightDp - c)
            else -> return r
        }
        val inner = c - r
        val limit = if (inner <= 0 || dy >= inner) c else c - sqrt(inner * inner - dy * dy)
        return max(r, limit)
    }

    /** Центр круга радиуса [r] на высоте [y] сдвигается от боковых кромок и углов, так что круг помещается; у узкого стекла на середину. */
    private fun keepInsideSides(x: Double, y: Double, r: Double): Double {
        val side = sideLimit(y, r)
        return if (side * 2 >= widthDp) widthDp / 2 else x.coerceIn(side, widthDp - side)
    }

    private fun spawn(d: Double, s: Double) {
        spawnDebt += d * SPAWN_PER_SECOND * area * (MIN_SHARE + (1 - MIN_SHARE) * s)
        val cap = capacity(s)
        while (spawnDebt >= 1) {
            spawnDebt -= 1
            if (beads.size < cap) {
                val r = 1.0 + 2.6 * random.nextDouble().pow(2.4)
                val x = random.nextDouble() * widthDp
                val y = heightDp * random.nextDouble().pow(1.35)
                // Капля у самой кромки висела бы обрезанной пополам: такая не садится.
                if (fits(x, y, r)) beads += Bead(x, y, r)
            }
        }
    }

    /**
     * Вода, попавшая на верхнюю кромку (в неё бьют брызги [EdgeSplash]), стекает на стекло: у самой кромки появляются
     * стекающие капли, чаще при сильном дожде и у широкого стекла. Им оставлен запас сверх обычного числа стекающих.
     */
    private fun feedFromEdge(d: Double, s: Double) {
        feedDebt += d * EDGE_FEED_PER_100DP * widthDp / 100.0 * (MIN_SHARE + (1 - MIN_SHARE) * s)
        val limit = max(1, (MAX_RUNNERS * area).toInt()) + EDGE_RUNNERS_EXTRA
        while (feedDebt >= 1) {
            feedDebt -= 1
            if (runners.size < limit) {
                val margin = min(max(EDGE_MARGIN, corner), widthDp / 4)
                runners += runner(margin + random.nextDouble() * (widthDp - 2 * margin), 1.0, 3.8 + random.nextDouble() * 1.4)
            }
        }
    }

    /** Вода с карточки выше долетает до верхней кромки и стекает дальше уже по этому стеклу. */
    private fun land(d: Double) {
        val waiting = incoming.iterator()
        while (waiting.hasNext()) {
            val drop = waiting.next()
            drop.delay -= d
            if (drop.delay > 0) continue
            waiting.remove()
            runners += runner(drop.x, 1.0, drop.r).also {
                it.v = RUN_SPEED
                it.timer = 0.5 + random.nextDouble() * 0.5
            }
        }
    }

    /** Раз в [RELEASE_SECONDS] созревшая капля может сорваться, иногда новая крупная капля прилетает сверху сразу стекающей. */
    private fun release(d: Double) {
        runnerClock += d
        if (runnerClock < RELEASE_SECONDS) return
        runnerClock = 0.0
        if (runners.size >= max(1, (MAX_RUNNERS * area).toInt())) return
        val ripe = beads.filter { it.r > RIPE_RADIUS && it.y < heightDp * 0.8 && !it.pooled }
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

    /** Двигает каплю на [d] секунд; true, если она дошла до нижней кромки и ушла со стекла. */
    private fun move(runner: Runner, d: Double): Boolean {
        if (runner.dripping) {
            runner.v = min(DRIP_SPEED, runner.v + DRIP_ACCELERATION * d)
            val py = runner.y
            runner.y += runner.v * d
            trails += Trail(runner.x, py, runner.x, runner.y, runner.r * 0.5)
            return false
        }
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
                val x = runner.x + (random.nextDouble() - 0.5) * runner.r * 0.8
                val y = runner.y - runner.r
                val r = 0.8 + random.nextDouble() * 0.9
                if (fits(x, y, r)) beads += Bead(x, y, r)
            }
            runner.r = max(MIN_RUNNER_RADIUS, runner.r - SHED_PER_DP * dy)
        }
        if (runner.y + runner.r * EXIT_REACH >= heightDp) {
            reachBottom(runner)
            return true
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
        // Боком капля тоже не заходит за кромку: проверка после того, как она вобрала соседей и выросла.
        runner.x = keepInsideSides(runner.x, runner.y, runner.r)
        if (trails.size > MAX_TRAILS) trails.subList(0, trails.size - MAX_TRAILS).clear()
        return false
    }

    /** Капля дошла до нижней кромки: вода уходит на карточку ниже, а если её нет, собирается у кромки. */
    private fun reachBottom(runner: Runner) {
        if (spill?.invoke(runner.x, runner.r) == true) return
        val pool = beads.firstOrNull { it.pooled && abs(it.x - runner.x) < it.r + runner.r }
        if (pool == null) {
            val r = (runner.r * POOL_START_SHARE).coerceIn(MIN_POOL_RADIUS, POOL_LIMIT * 0.9)
            beads += Bead(keepInsideSides(runner.x, heightDp - r, r), heightDp - r, r).also { it.pooled = true }
            return
        }
        pool.r = cbrt(pool.r.pow(3) + 0.7 * runner.r.pow(3))
        pool.y = heightDp - pool.r
        if (pool.r >= POOL_LIMIT) {
            // Капля у кромки стала слишком тяжёлой и отрывается.
            beads.remove(pool)
            dripped += Runner(pool.x, pool.y, pool.r, 1.0, 0.0).also {
                it.dripping = true
                it.v = DRIP_START_SPEED
            }
        }
    }

    companion object {
        /** Карточка образца 350 × 250 dp: на ней до 430 капель и до 9 стекающих, садится 34 капли в секунду. */
        private const val REF_AREA = 350.0 * 250.0
        const val MAX_BEADS = 430

        /** Карточка больше полутора образцов держит капель как полтора образца: число капель на кадр ограничено. */
        private const val MAX_AREA = 1.5
        private const val MAX_RUNNERS = 9

        /** Радиус скругления углов карточек (у них 20–26 dp), по которому капли держатся подальше от угла. */
        private const val CORNER_DP = 22.0

        /** Стекающих капель с кромки в секунду на 100 dp ширины при сильном дожде, запас сверх обычного числа и отступ от углов (dp). */
        private const val EDGE_FEED_PER_100DP = 0.28
        private const val EDGE_RUNNERS_EXTRA = 2
        private const val EDGE_MARGIN = 10.0
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

        /** Капля уходит с нижней кромки, когда её нос (радиус на это число) дошёл до неё. */
        private const val EXIT_REACH = 1.5

        /** Вода у нижней кромки: радиус первой капли в доле стёкшей, наименьший радиус и радиус, при котором капля отрывается. */
        private const val POOL_START_SHARE = 0.85
        private const val MIN_POOL_RADIUS = 1.6
        const val POOL_LIMIT = 4.6

        /** Оторвавшаяся капля падает с этой скоростью, разгоняясь до [DRIP_SPEED] (dp/с и dp/с²). */
        private const val DRIP_START_SPEED = 10.0
        private const val DRIP_SPEED = 220.0
        private const val DRIP_ACCELERATION = 420.0

        /** Сколько капель с карточки выше может ждать падения на это стекло. */
        private const val MAX_INCOMING = 6

        /** Шаг больше этого (перерыв, возврат в приложение) режется: капля не перепрыгивает карточку. */
        private const val MAX_DT = 0.1
    }
}
