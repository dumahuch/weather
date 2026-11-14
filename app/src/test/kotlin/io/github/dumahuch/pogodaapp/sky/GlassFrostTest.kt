package io.github.dumahuch.pogodaapp.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/** Иней: с какой температуры появляется, как собирается из картинок и в каком порядке замерзает стекло. */
class GlassFrostTest {

    @Test
    fun frostStartsAtMinusThreeAtTheEdgesAndCoversTheGlassByMinusTen() {
        assertEquals(0.0, GlassFrost.coverage(null), 1e-9)
        assertEquals(0.0, GlassFrost.coverage(2.0), 1e-9)
        assertEquals(0.0, GlassFrost.coverage(-2.9), 1e-9)
        assertEquals(0.25, GlassFrost.coverage(-3.0), 1e-9)
        assertEquals(1.0, GlassFrost.coverage(-10.0), 1e-9)
        assertEquals(1.0, GlassFrost.coverage(-25.0), 1e-9)
        assertTrue(GlassFrost.coverage(-5.0) < GlassFrost.coverage(-7.0))
    }

    private fun noisy(size: Int, seed: Long): FrostTexture {
        val random = Random(seed)
        return FrostTexture(size, ByteArray(size * size) { random.nextInt(256).toByte() })
    }

    private fun flat(size: Int, value: Int, border: Int = -1): FrostTexture = FrostTexture(size, ByteArray(size * size) {
        val x = it % size
        val y = it / size
        val edge = x < 2 || y < 2 || x >= size - 2 || y >= size - 2
        (if (edge && border >= 0) border else value).toByte()
    })

    private val large = List(3) { noisy(96, it.toLong()) }
    private val dense = List(3) { noisy(64, 10L + it) }

    /** Карточка 200 × 150 dp по пикселю на dp. */
    private val card = FrostComposer.compose(200, 150, 1.0, large, dense, 5)

    private fun share(order: ByteArray, level: Double) = order.count { (it.toInt() and 0xFF) / 255.0 < level }.toDouble() / order.size

    @Test
    fun theShareOfFrozenGlassMatchesTheFrostLevel() {
        // «Доля инея 0,4» и правда закрывает около 40 % стекла: так −5 °C выглядит как на превью.
        for (level in listOf(0.25, 0.4, 0.7)) assertEquals(level, share(card.order, level), 0.03)
        assertEquals(1.0, share(card.order, 1.0 + 1e-6), 1e-9)
    }

    @Test
    fun theGlassFreezesFromTheEdgesNotTheMiddleFirst() {
        fun toEdge(i: Int): Int = minOf(i % 200, 199 - i % 200, i / 200, 149 - i / 200)
        val first = card.order.indices.sortedBy { card.order[it].toInt() and 0xFF }.take(card.order.size / 20)
        val early = first.map(::toEdge).average()
        val all = card.order.indices.map(::toEdge).average()
        assertTrue("$early $all", early < all * 0.6)
    }

    @Test
    fun brightFeathersFreezeAheadOfTheDarkGlassBetweenThem() {
        val bright = card.ice.indices.filter { (card.ice[it].toInt() and 0xFF) > 200 }.map { card.order[it].toInt() and 0xFF }.average()
        val dark = card.ice.indices.filter { (card.ice[it].toInt() and 0xFF) < 30 }.map { card.order[it].toInt() and 0xFF }.average()
        assertTrue("$bright $dark", bright < dark)
    }

    @Test
    fun aSmallGlassTakesOnlyTheFinePatterns() {
        val black = List(3) { flat(96, 0) }
        val white = List(3) { flat(64, 255) }
        val button = FrostComposer.compose(48, 48, 1.0, black, white, 1)
        assertTrue(button.ice.all { (it.toInt() and 0xFF) == 255 })
        assertTrue(FrostComposer.isSmall(300.0, 56.0))
        assertFalse(FrostComposer.isSmall(370.0, 215.0))
    }

    @Test
    fun theGlassNeverReachesTheEdgeOfAPicture() {
        // Края картинок не стыкуются: если бы окно стекла заходило за край картинки, там был бы чёрный ободок.
        val framed = List(3) { flat(96, 255, border = 0) }
        val sheet = FrostComposer.compose(200, 150, 1.0, framed, framed, 9)
        assertTrue(sheet.ice.all { (it.toInt() and 0xFF) == 255 })
    }

    @Test
    fun theHazeFollowsTheFrost() {
        assertTrue(card.haze.any { (it.toInt() and 0xFF) > 0 })
        val empty = FrostComposer.compose(120, 100, 1.0, List(2) { flat(96, 0) }, List(2) { flat(64, 0) }, 2)
        assertTrue(empty.haze.all { it.toInt() == 0 })
        assertTrue(empty.ice.all { it.toInt() == 0 })
    }

    @Test
    fun theSameSeedGivesTheSameFrostAndAnotherGivesAnother() {
        val again = FrostComposer.compose(200, 150, 1.0, large, dense, 5)
        assertTrue(again.ice.contentEquals(card.ice))
        assertTrue(again.order.contentEquals(card.order))
        assertFalse(FrostComposer.compose(200, 150, 1.0, large, dense, 6).ice.contentEquals(card.ice))
    }

    @Test
    fun aPictureIsSampledSmoothly() {
        val texture = FrostTexture(2, byteArrayOf(0, 255.toByte(), 0, 255.toByte()))
        assertEquals(0f, texture.at(0.5, 0.5), 1e-6f)
        assertEquals(1f, texture.at(1.5, 1.5), 1e-6f)
        assertEquals(0.5f, texture.at(1.0, 0.5), 1e-6f)
        assertTrue(abs(texture.at(-5.0, 9.0)) < 1e-6f)
    }
}
