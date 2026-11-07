package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Форма подложек на небе: под цифрами как в макете, под строками под температурой, наибольшая из двух плотностей. */
class ScrimShapeTest {

    @Test
    fun heroShapeIsAsInTheMockupWhenThereIsNoInfoScrim() {
        // Макет: 0 до 96 dp, полная с 164,8 dp до 343,68 dp, снова 0 к 440 dp.
        assertEquals(0.0, ScrimShape.alphaAt(50.0, 0.3, 0.0), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(96.0, 0.3, 0.0), 1e-9)
        assertEquals(0.3, ScrimShape.alphaAt(164.8, 0.3, 0.0), 1e-9)
        assertEquals(0.3, ScrimShape.alphaAt(250.0, 0.3, 0.0), 1e-9)
        assertEquals(0.3, ScrimShape.alphaAt(343.68, 0.3, 0.0), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(440.0, 0.3, 0.0), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(600.0, 0.3, 0.0), 1e-9)
    }

    @Test
    fun infoShapeCoversTheLinesUnderTheTemperature() {
        // С версии 0.5.4 строки лежат на экране 412×892 dp между 375 и 495 dp (до 515 dp при плашке «нет сети»):
        // там подложка полная; без сдвига блока (крупный шрифт) они были между 320 и 460 dp, и верх этих строк тоже в полосе.
        for (y in listOf(340.0, 375.0, 400.0, 440.0, 495.0, 515.0, 530.0)) {
            assertEquals("y=$y", 0.3, ScrimShape.alphaAt(y, 0.0, 0.3), 1e-9)
        }
        assertEquals(0.0, ScrimShape.alphaAt(300.0, 0.0, 0.3), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(575.0, 0.0, 0.3), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(700.0, 0.0, 0.3), 1e-9)
    }

    @Test
    fun overlapTakesTheLargerDensityInsteadOfAdding() {
        for (y in 280..600 step 5) {
            val both = ScrimShape.alphaAt(y.toDouble(), 0.36, 0.30)
            val hero = ScrimShape.alphaAt(y.toDouble(), 0.36, 0.0)
            val info = ScrimShape.alphaAt(y.toDouble(), 0.0, 0.30)
            assertEquals("y=$y", maxOf(hero, info), both, 1e-9)
            assertTrue("y=$y: не больше наибольшей из двух плотностей", both <= 0.36 + 1e-9)
        }
    }

    @Test
    fun stopsAreIncreasingAndMatchTheShape() {
        val stops = ScrimShape.stops(0.30, 0.25)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 1e-6f)
        for (i in 1 until stops.size) assertTrue("остановка $i", stops[i].first > stops[i - 1].first)
        for ((position, alpha) in stops) {
            assertEquals(ScrimShape.alphaAt(position * ScrimShape.SCREEN_HEIGHT, 0.30, 0.25).toFloat(), alpha, 1e-6f)
            assertTrue(alpha in 0f..0.30f + 1e-6f)
        }
    }

    @Test
    fun noScrimGivesNothingVisible() {
        assertTrue(ScrimShape.stops(0.0, 0.0).all { it.second == 0f })
    }

    private val around = ScrimShape.around(
        heroTop = 300.0, heroBottom = 600.0, infoTop = 800.0, infoBottom = 1000.0, heroFeather = 60.0, infoFeather = 40.0,
    )

    @Test
    fun bandsFollowTheRealBlocks() {
        // Подложка под цифрами: полная от верха до низа блока, снаружи мягкий край 60.
        assertEquals(0.0, ScrimShape.alphaAt(240.0, 0.3, 0.0, around), 1e-9)
        assertEquals(0.15, ScrimShape.alphaAt(270.0, 0.3, 0.0, around), 1e-9)
        for (y in listOf(300.0, 450.0, 600.0)) assertEquals("y=$y", 0.3, ScrimShape.alphaAt(y, 0.3, 0.0, around), 1e-9)
        assertEquals(0.15, ScrimShape.alphaAt(630.0, 0.3, 0.0, around), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(660.0, 0.3, 0.0, around), 1e-9)
        // Подложка под строками: то же вокруг своих границ, мягкий край 40.
        assertEquals(0.0, ScrimShape.alphaAt(760.0, 0.0, 0.25, around), 1e-9)
        assertEquals(0.125, ScrimShape.alphaAt(780.0, 0.0, 0.25, around), 1e-9)
        for (y in listOf(800.0, 900.0, 1000.0)) assertEquals("y=$y", 0.25, ScrimShape.alphaAt(y, 0.0, 0.25, around), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(1040.0, 0.0, 0.25, around), 1e-9)
    }

    @Test
    fun bandsMoveTogetherWithTheBlocks() {
        val moved = ScrimShape.around(400.0, 700.0, 900.0, 1100.0, 60.0, 40.0)
        for (y in 200..1200 step 25) {
            assertEquals(
                "y=$y",
                ScrimShape.alphaAt(y.toDouble(), 0.3, 0.25, around),
                ScrimShape.alphaAt(y + 100.0, 0.3, 0.25, moved),
                1e-9,
            )
        }
    }

    @Test
    fun stopsOfMeasuredBandsMatchTheShapeAndAreIncreasing() {
        val height = 2400.0
        val stops = ScrimShape.stops(0.30, 0.25, around, height)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 1e-6f)
        for (i in 1 until stops.size) assertTrue("остановка $i", stops[i].first > stops[i - 1].first)
        for ((position, alpha) in stops) {
            assertEquals(ScrimShape.alphaAt(position * height, 0.30, 0.25, around).toFloat(), alpha, 1e-5f)
        }
        // Углы подложек попадают в остановки точно: иначе градиент срезал бы их линейной интерполяцией.
        for (corner in listOf(240.0, 300.0, 600.0, 660.0, 760.0, 800.0, 1000.0, 1040.0)) {
            assertTrue("угол $corner", stops.any { kotlin.math.abs(it.first * height - corner) < 0.01 })
        }
    }

    @Test
    fun aBlockNearTheTopEdgeKeepsItsDensityAtTheEdge() {
        // Мягкий край выходит за верх экрана: углы вне экрана отбрасываются, а плотность у верха считается по той же форме.
        val high = ScrimShape.around(10.0, 300.0, 400.0, 500.0, 60.0, 40.0)
        val stops = ScrimShape.stops(0.30, 0.0, high, 1000.0)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(ScrimShape.alphaAt(0.0, 0.30, 0.0, high).toFloat(), stops.first().second, 1e-6f)
        assertTrue("у верха уже плотная", stops.first().second > 0.2f)
        for (i in 1 until stops.size) assertTrue("остановка $i", stops[i].first > stops[i - 1].first)
    }

    @Test
    fun aBlockWithoutHeightStillGivesAValidBand() {
        val line = ScrimShape.around(500.0, 500.0, 500.0, 500.0, 60.0, 40.0)
        assertEquals(0.3, ScrimShape.alphaAt(500.0, 0.3, 0.0, line), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(440.0, 0.3, 0.0, line), 1e-9)
        assertEquals(0.0, ScrimShape.alphaAt(560.0, 0.3, 0.0, line), 1e-9)
    }
}
