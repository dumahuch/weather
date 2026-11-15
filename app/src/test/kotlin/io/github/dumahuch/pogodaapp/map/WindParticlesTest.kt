package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Линии по ветру: экран в градусах, движение частиц, следы, корзины отрезков. */
class WindParticlesTest {

    /** Поле с одинаковым ветром [east], [north] км/ч во всех точках. */
    private fun flow(east: Float, north: Float) =
        WindFlow(FloatArray(PointGrid.COUNT) { east }, FloatArray(PointGrid.COUNT) { north })

    /** Экран 1000 × 2000 пикселей вокруг Ганновера. */
    private val view = ScreenView(1000f, 2000f, west = 7.0, east = 12.0, north = 54.0, south = 50.0)

    @Test
    fun screenEdgesMatchTheVisibleRegion() {
        assertEquals(7.0, view.lon(0f), 1e-9)
        assertEquals(12.0, view.lon(1000f), 1e-9)
        assertEquals(9.5, view.lon(500f), 1e-9)
        assertEquals(54.0, view.lat(0f), 1e-6)
        assertEquals(50.0, view.lat(2000f), 1e-6)
        // по Меркатору середина экрана севернее средней широты
        assertTrue(view.lat(1000f) > 52.0)
    }

    @Test
    fun particlesMoveDownwindAtScreenSpeed() {
        val particles = WindParticles(50, dp = 2f, random = Random(1))
        val east = flow(40f, 0f)
        particles.advance(0f, east, view)
        val before = List(50) { particles.position(it) }
        particles.advance(WindParticles.TICK_S * 3 + 0.001f, east, view)
        var moved = 0
        for (i in 0 until 50) {
            val (x0, y0) = before[i]
            val (x1, y1) = particles.position(i)
            if (particles.trailLength(i) != 4) continue // за это время частица родилась заново
            moved++
            assertEquals(40f * WindParticles.SPEED_DP_PER_KMH * 2f * WindParticles.TICK_S * 3, x1 - x0, 0.01f)
            assertEquals(y0, y1, 0.001f)
        }
        assertTrue(moved > 30)
    }

    @Test
    fun northWindGoesUpTheScreen() {
        val particles = WindParticles(20, dp = 1f, random = Random(2))
        particles.advance(0f, flow(0f, 30f), view)
        val (_, y0) = particles.position(0)
        particles.advance(WindParticles.TICK_S + 0.001f, flow(0f, 30f), view)
        if (particles.trailLength(0) == 2) assertTrue(particles.position(0).second < y0)
    }

    @Test
    fun trailIsLimitedAndFades() {
        val particles = WindParticles(30, dp = 1f, random = Random(3))
        val wind = flow(20f, 10f)
        particles.advance(0f, wind, view)
        repeat(120) { particles.advance(WindParticles.TICK_S, wind, view) }
        for (i in 0 until 30) assertTrue(particles.trailLength(i) <= WindParticles.TRAIL)
        val segments = WindSegments()
        particles.collect(segments)
        assertTrue(segments.total() > 0)
        assertTrue(segments.total() <= 30 * (WindParticles.TRAIL - 1))
        // все отрезки внутри экрана
        for (bucket in 0 until segments.bucketCount) {
            val data = segments.data(bucket)
            for (k in 0 until segments.size(bucket)) {
                val limit = if (k % 2 == 0) view.width else view.height
                assertTrue(data[k] in 0f..limit)
            }
        }
    }

    @Test
    fun calmAndMissingWindDrawNothing() {
        val segments = WindSegments()
        val calm = WindParticles(40, dp = 1f, random = Random(4))
        repeat(30) { calm.advance(WindParticles.TICK_S, flow(0.5f, 0.5f), view) }
        calm.collect(segments)
        assertEquals(0, segments.total())
        val missing = WindParticles(40, dp = 1f, random = Random(5))
        repeat(30) { missing.advance(WindParticles.TICK_S, flow(Float.NaN, Float.NaN), view) }
        missing.collect(segments)
        assertEquals(0, segments.total())
    }

    @Test
    fun strongWindUsesThickerLines() {
        fun widths(kmh: Float): Set<Int> {
            val particles = WindParticles(40, dp = 1f, random = Random(6))
            repeat(20) { particles.advance(WindParticles.TICK_S, flow(kmh, 0f), view) }
            val segments = WindSegments()
            particles.collect(segments)
            return (0 until segments.bucketCount).filter { segments.size(it) > 0 }.map { segments.width(it) }.toSet()
        }
        assertEquals(setOf(0), widths(10f))
        assertEquals(setOf(2), widths(60f))
    }

    @Test
    fun longPausesDoNotJumpParticles() {
        val particles = WindParticles(10, dp = 1f, random = Random(7))
        val wind = flow(50f, 0f)
        particles.advance(0f, wind, view)
        particles.advance(5f, wind, view)
        // не больше 0,2 с движения (6 шагов) за раз, сколько бы ни прошло
        for (i in 0 until 10) assertTrue(particles.trailLength(i) <= 7)
    }

    @Test
    fun countFollowsScreenArea() {
        assertEquals(897, WindParticles.countFor(412f, 915f))
        assertEquals(1000, WindParticles.countFor(800f, 600f))
        assertEquals(200, WindParticles.countFor(100f, 100f))
        assertEquals(0.0625f, WindParticles.alphaOf(0), 1e-6f)
    }

    @Test
    fun lightWindStillDrawsALongStreak() {
        // 15 км/ч: за полный след (31 отрезок) линия длиннее 20 dp; 0.8.41 давала около 6 dp
        val particles = WindParticles(1, dp = 1f, random = Random(8))
        val wind = flow(15f, 0f)
        particles.advance(0f, wind, view)
        repeat(WindParticles.TRAIL) { particles.advance(WindParticles.TICK_S, wind, view) }
        val segments = WindSegments()
        particles.collect(segments)
        var longest = 0f
        for (bucket in 0 until segments.bucketCount) {
            val data = segments.data(bucket)
            var k = 0
            while (k < segments.size(bucket)) {
                longest += kotlin.math.abs(data[k + 2] - data[k])
                k += 4
            }
        }
        assertTrue("след короткий: $longest dp", longest > 20f || particles.trailLength(0) < WindParticles.TRAIL)
    }
}
