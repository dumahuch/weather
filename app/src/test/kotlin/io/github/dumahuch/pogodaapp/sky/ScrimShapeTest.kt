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
}
