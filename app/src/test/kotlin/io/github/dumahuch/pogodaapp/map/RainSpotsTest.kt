package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Мелкие пятна дождя модели: пятна в один-два узла убираются, крупнее и сухие узлы не трогаются. */
class RainSpotsTest {

    private fun grid(vararg rows: String): FloatArray =
        rows.joinToString("").map { if (it == '#') 2f else if (it == '.') 0f else 0.05f }.toFloatArray()

    private fun drop(vararg rows: String): FloatArray = grid(*rows).also { RainSpots.dropSmall(it, rows[0].length, rows.size) }

    @Test
    fun singleAndPairAreDropped() {
        assertArrayEquals(grid("......", "......", "......"), drop("#.....", "....#.", "...#.."), 0f)
    }

    @Test
    fun threeTouchingByCornerStay() {
        val spot = arrayOf("#.....", ".#....", "..#...")
        assertArrayEquals(grid(*spot), drop(*spot), 0f)
    }

    @Test
    fun weakRainBelowMapThresholdDoesNotJoinSpots() {
        // Узлы «~» слабее 0,1 мм/ч: на карте их нет, и пятно из двух узлов рядом с ними всё равно мелкое.
        assertArrayEquals(grid("~~~~", "~..~", "~~~~"), drop("~~~~", "~##~", "~~~~"), 0f)
    }
}
