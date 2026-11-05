package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/** Облака фона «Ди» по погоде: число, размер и густота растут от ясного неба к грозе; облака на листе не задевают друг друга. */
class CloudLayoutTest {

    private val maps = FileInputStream(File("src/main/assets/${DiMaps.ASSET_PATH}")).use { DiMaps.read(it) }

    private fun clouds(condition: Condition, mm: Double? = null): List<PlacedCloud> {
        val weather = SkyWeather.of(condition, mm)
        return CloudLayout.of(weather, DiArt.storm(weather))
    }

    /** Сумма площадей облаков (пиксели листа): грубая мера того, сколько неба закрыто. */
    private fun area(list: List<PlacedCloud>) = list.sumOf { it.width * it.height }

    @Test
    fun cloudsGrowFromClearSkyToStorm() {
        val order = listOf(Condition.Clear, Condition.Partly, Condition.Cloudy, Condition.Rain, Condition.Storm)
        val counts = order.map { clouds(it).size }
        val areas = order.map { area(clouds(it)) }
        for (i in 1 until order.size) {
            assertTrue("${order[i]}: облаков ${counts[i]} не больше, чем у ${order[i - 1]} (${counts[i - 1]})", counts[i] > counts[i - 1])
            assertTrue("${order[i]}: площадь облаков не больше, чем у ${order[i - 1]}", areas[i] > areas[i - 1])
        }
    }

    @Test
    fun partlyCloudyHasFewSeparateCloudsAndCloudyHasManyTimesMore() {
        val partly = clouds(Condition.Partly).size
        val cloudy = clouds(Condition.Cloudy).size
        assertTrue("малооблачно: $partly", partly in 5..10)
        assertTrue("облачно: $cloudy, малооблачно: $partly", cloudy >= 2 * partly)
    }

    @Test
    fun heavyRainHasMoreCloudsThanDrizzle() {
        val drizzle = clouds(Condition.Rain, 0.1)
        val heavy = clouds(Condition.Rain, 6.0)
        assertTrue("${drizzle.size} против ${heavy.size}", heavy.size > drizzle.size)
        assertTrue(area(heavy) > area(drizzle))
        // Даже морось гуще, чем облачно без осадков.
        assertTrue(drizzle.size >= clouds(Condition.Cloudy).size)
        assertTrue(area(drizzle) > area(clouds(Condition.Cloudy)))
    }

    @Test
    fun snowGetsDenserWithHeavierSnowfall() {
        assertTrue(clouds(Condition.Snow, 3.0).size > clouds(Condition.Snow, 0.1).size)
    }

    @Test
    fun rainWithoutKnownPrecipitationIsInBetween() {
        val light = clouds(Condition.Rain, 0.1).size
        val unknown = clouds(Condition.Rain).size
        val heavy = clouds(Condition.Rain, 6.0).size
        assertTrue("$light ≤ $unknown ≤ $heavy", unknown in light..heavy)
    }

    @Test
    fun layoutIsStableAndSortedFarToNear() {
        val a = clouds(Condition.Cloudy)
        val b = clouds(Condition.Cloudy)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].cx, b[i].cx, 0.0)
            assertEquals(a[i].cy, b[i].cy, 0.0)
            assertEquals(a[i].mirrored, b[i].mirrored)
        }
        for (i in 1 until a.size) assertTrue("дальние первыми", a[i].cy >= a[i - 1].cy)
    }

    @Test
    fun everyCloudIsOnTheSheetWithSensibleSizeAndAlpha() {
        for (condition in Condition.entries) for (cloud in clouds(condition)) {
            assertTrue("$condition: прозрачность ${cloud.alpha}", cloud.alpha > 0.5 && cloud.alpha <= 1.0)
            assertTrue("$condition: размер ${cloud.scale}", cloud.scale in 0.4..1.6)
            assertTrue("$condition: центр по горизонтали ${cloud.cx}", cloud.cx in 0.0..DiArt.SHEET_W)
            assertTrue("$condition: центр по вертикали ${cloud.cy}", cloud.cy in 0.0..DiArt.SHEET_H)
        }
    }

    @Test
    fun neighbouringCloudsUseDifferentPictures() {
        for (condition in Condition.entries) {
            val weather = SkyWeather.of(condition)
            val storm = DiArt.storm(weather)
            val sprites = if (storm) CloudSprites.storm else CloudSprites.light
            val byX = CloudLayout.of(weather, storm).sortedBy { it.cx }
            for (i in 1 until byX.size) {
                assertTrue("$condition: два соседних облака одинаковы", sprites.indexOf(byX[i].rect) != sprites.indexOf(byX[i - 1].rect))
            }
        }
    }

    @Test
    fun spritesLieOnTheSheetApartFromEachOther() {
        for ((name, sprites) in listOf("light" to CloudSprites.light, "storm" to CloudSprites.storm)) {
            for ((i, a) in sprites.withIndex()) {
                assertTrue("$name $i: за краем листа", a.x >= 0 && a.y >= 0 && a.x + a.w <= DiArt.SHEET_W && a.y + a.h <= DiArt.SHEET_H)
                for (j in i + 1 until sprites.size) {
                    val b = sprites[j]
                    val apart = a.x + a.w + GAP <= b.x || b.x + b.w + GAP <= a.x || a.y + a.h + GAP <= b.y || b.y + b.h + GAP <= a.y
                    assertTrue("$name $i и $j слишком близко", apart)
                }
            }
        }
    }

    /** Список прямоугольников должен совпадать с листом: в каждом есть облако (иначе скрипт и список разошлись). */
    @Test
    fun spriteRectsMatchTheCloudMaps() {
        val k = maps.sheetW / DiArt.SHEET_W
        for ((storm, sprites) in listOf(false to CloudSprites.light, true to CloudSprites.storm)) {
            val map = maps.clouds(storm)
            fun alphaAt(x: Double, y: Double): Int {
                val ix = (x * k).toInt().coerceIn(0, maps.sheetW - 1)
                val iy = (y * k).toInt().coerceIn(0, maps.sheetH - 1)
                return map[(iy * maps.sheetW + ix) * 4 + 3].toInt() and 0xFF
            }
            for ((i, r) in sprites.withIndex()) {
                var solid = 0
                var y = r.y.toDouble()
                while (y < r.y + r.h) {
                    var x = r.x.toDouble()
                    while (x < r.x + r.w) {
                        if (alphaAt(x, y) > 128) solid++
                        x += 16.0
                    }
                    y += 16.0
                }
                assertTrue("storm=$storm, облако $i: в прямоугольнике нет облака", solid > 10)
            }
        }
    }

    private companion object {
        /** Зазор между облаками на листе: два блока карты (16 пикселей), чтобы они не смешивались при сжатии. */
        const val GAP = 16
    }
}
