package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Температура и ветер для карты: точки, байты кадра, цвета, GeoJSON. */
class WeatherFieldsTest {

    /** Сетка вокруг всей области карты, значение в узле даёт [value] от (долгота, широта). */
    private fun grid(value: (Double, Double) -> Float): LatLonGrid {
        val west = 1.0
        val south = 44.0
        val step = 0.1
        val columns = 190
        val rows = 130
        val values = FloatArray(columns * rows) { n -> value(west + (n % columns) * step, south + (n / columns) * step) }
        return LatLonGrid(columns, rows, west, south, step, step, values)
    }

    private fun count(text: String, part: String): Int = text.split(part).size - 1

    @Test
    fun pointGridLiesInsideTheOverlay() {
        val box = MapArea.Overlay
        assertEquals(1680, PointGrid.COUNT)
        for (row in listOf(0, PointGrid.ROWS - 1)) assertTrue(PointGrid.lat(row) in box.south..box.north)
        for (column in listOf(0, PointGrid.COLUMNS - 1)) assertTrue(PointGrid.lon(column) in box.west..box.east)
        assertEquals(56.0, PointGrid.lat(PointGrid.ROWS - 1), 1e-9)
        assertEquals(18.3, PointGrid.lon(PointGrid.COLUMNS - 1), 1e-9)
    }

    @Test
    fun sampleReadsTheFieldAtPointsAndLeavesNaNOutsideTheGrid() {
        val everywhere = PointGrid.sample(grid { _, lat -> lat.toFloat() })
        assertEquals(PointGrid.lat(0), everywhere[0].toDouble(), 1e-3)
        assertEquals(PointGrid.lat(20), everywhere[20 * PointGrid.COLUMNS + 5].toDouble(), 1e-3)
        val small = LatLonGrid(2, 2, 10.0, 50.0, 1.0, 1.0, floatArrayOf(1f, 1f, 1f, 1f))
        val partial = PointGrid.sample(small)
        assertTrue(partial[0].isNaN())
        assertEquals(10, partial.count { !it.isNaN() })
    }

    @Test
    fun kelvinBecomeCelsiusAndDegreesStay() {
        val kelvin = LatLonGrid(2, 2, 0.0, 0.0, 1.0, 1.0, floatArrayOf(273.15f, 283.15f, 300.15f, Float.NaN))
        assertEquals(0f, TemperatureField.celsius(kelvin).values[0], 1e-4f)
        assertEquals(27f, TemperatureField.celsius(kelvin).values[2], 1e-3f)
        assertTrue(TemperatureField.celsius(kelvin).values[3].isNaN())
        val degrees = LatLonGrid(2, 2, 0.0, 0.0, 1.0, 1.0, floatArrayOf(-5f, 10f, 20f, 31f))
        assertEquals(degrees.values.toList(), TemperatureField.celsius(degrees).values.toList())
    }

    @Test
    fun temperatureByteKeepsDegreesWithinHalfAStep() {
        assertEquals(0, TemperatureField.code(Float.NaN))
        for (t in listOf(-29.9f, -12.3f, 0f, 7.7f, 27.4f, 45.9f)) {
            assertEquals(t.toDouble(), TemperatureField.celsiusOf(TemperatureField.code(t)), TemperatureField.STEP / 2 + 1e-6)
        }
        assertEquals(1, TemperatureField.code(-80f))
        assertEquals(255, TemperatureField.code(90f))
    }

    @Test
    fun redStartsAt32DegreesAnd27IsStillOrange() {
        assertEquals(0xE5443A, TemperatureField.colorOf(32.0))
        val warm = TemperatureField.colorOf(27.0)
        assertTrue("27 °C ещё оранжевая, зелёного много", ((warm shr 8) and 0xFF) > 0x90)
        val hot = TemperatureField.colorOf(32.0)
        assertTrue("32 °C красная, зелёного мало", ((hot shr 8) and 0xFF) < 0x50)
        assertEquals(TemperatureField.colorOf(-30.0), TemperatureField.colorOf(-60.0))
        assertEquals(TemperatureField.colorOf(42.0), TemperatureField.colorOf(60.0))
    }

    @Test
    fun temperatureFrameHoldsPointsAndPicture() {
        val frame = TemperatureField.encode(grid { _, _ -> 300.15f })
        assertEquals(TemperatureField.LENGTH, frame.size)
        for (i in 0 until PointGrid.COUNT) assertEquals(27.0, TemperatureField.celsiusOf(frame[i].toInt() and 0xFF), 0.2)
        val pixels = TemperatureField.pixels(frame)
        assertEquals(TemperatureField.PICTURE_WIDTH * TemperatureField.PICTURE_HEIGHT, pixels.size)
        assertTrue(pixels.all { (it ushr 24) == 153 })
        assertEquals(1680, count(TemperatureField.numbersJson(frame), "\"Feature\""))
        assertTrue(TemperatureField.numbersJson(frame).contains("\"i\":\"t27\""))
    }

    @Test
    fun pointsAndPixelsOutsideTheModelAreEmpty() {
        val small = LatLonGrid(3, 3, 9.0, 49.0, 1.0, 1.0, FloatArray(9) { 283.15f })
        val frame = TemperatureField.encode(small)
        val json = TemperatureField.numbersJson(frame)
        // Сетка 2° × 2°: пять долгот и девять широт точек внутри.
        assertEquals(45, count(json, "\"Feature\""))
        val pixels = TemperatureField.pixels(frame)
        assertEquals(0, pixels[0])
        assertTrue(pixels.any { it != 0 })
    }

    @Test
    fun negativeDegreesHaveOwnIconNames() {
        assertEquals("t-12", TemperatureField.iconName(-12))
        assertEquals("t-35", TemperatureField.iconName(-90))
        assertEquals("t50", TemperatureField.iconName(99))
    }

    @Test
    fun windPointsAlongTheFlow() {
        val east = WindField.encode(grid { _, _ -> 10f }, grid { _, _ -> 0f })
        val (eastKmh, eastDirection) = WindField.at(east, 0)!!
        assertEquals(36.0, eastKmh, 0.5)
        assertEquals(90, eastDirection)
        val south = WindField.encode(grid { _, _ -> 0f }, grid { _, _ -> -5f })
        val (southKmh, southDirection) = WindField.at(south, 100)!!
        assertEquals(18.0, southKmh, 0.5)
        assertEquals(180, southDirection)
        val northWest = WindField.encode(grid { _, _ -> -3f }, grid { _, _ -> 3f })
        assertEquals(315, WindField.at(northWest, 7)!!.second)
    }

    @Test
    fun windClassesFollowTheBounds() {
        assertEquals(0, WindField.speedClass(4.9))
        assertEquals(1, WindField.speedClass(5.0))
        assertEquals(3, WindField.speedClass(36.0))
        assertEquals(6, WindField.speedClass(120.0))
        assertEquals(7, WindField.ClassColors.size)
        assertNotEquals(WindField.colorOf(10.0), WindField.colorOf(30.0))
    }

    @Test
    fun windJsonHasIconAndDirection() {
        val frame = WindField.encode(grid { _, _ -> 10f }, grid { _, _ -> 0f })
        val json = WindField.arrowsJson(frame)
        assertEquals(1680, count(json, "\"Feature\""))
        assertTrue(json.contains("\"i\":\"w3\",\"d\":90"))
        val calm = WindField.arrowsJson(WindField.encode(grid { _, _ -> 0.4f }, grid { _, _ -> 0.4f }))
        assertTrue(calm.contains("\"i\":\"wc\",\"d\":0"))
    }

    @Test
    fun windWithoutDataIsSkippedAndSizeIsChecked() {
        val small = LatLonGrid(3, 3, 9.0, 49.0, 1.0, 1.0, FloatArray(9) { 5f })
        val frame = WindField.encode(small, small)
        assertNull(WindField.at(frame, 0))
        assertEquals(45, count(WindField.arrowsJson(frame), "\"Feature\""))
        assertThrows(IllegalArgumentException::class.java) { WindField.arrowsJson(ByteArray(10)) }
    }

    @Test
    fun differentWindGridsAreRejected() {
        val a = LatLonGrid(2, 2, 0.0, 0.0, 1.0, 1.0, FloatArray(4))
        val b = LatLonGrid(2, 2, 0.5, 0.0, 1.0, 1.0, FloatArray(4))
        assertThrows(MapDataException::class.java) { WindField.encode(a, b) }
    }

    @Test
    fun frameSizesMatchTheDiskCache() {
        assertEquals(TemperatureField.LENGTH, HourlyField.Temperature.length)
        assertEquals(WindField.LENGTH, HourlyField.Wind.length)
        assertArrayEquals(intArrayOf(-20, -10, 0, 10, 20, 30, 40), TemperatureField.LegendValues.toIntArray())
    }
}
