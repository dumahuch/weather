package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Разбор GRIB2 в том виде, в каком DWD выкладывает ICON-D2 (сетка 3.0, простая упаковка 5.0), и отказ от прочего. */
class Grib2Test {

    @Test
    fun readsGridFromSouthToNorth() {
        // Сетка 3 × 2, точки с юга на север (режим 64), долгота в записи 0…360.
        val grid = Grib2.parse(message(columns = 3, rows = 2, raw = intArrayOf(0, 10, 20, 30, 40, 100), scan = 64))
        assertEquals(3, grid.columns)
        assertEquals(2, grid.rows)
        assertEquals(-4.0, grid.west, 1e-9)
        assertEquals(47.0, grid.south, 1e-9)
        assertEquals(-2.0, grid.west + (grid.columns - 1) * grid.stepLon, 1e-9)
        assertEquals(48.0, grid.south + (grid.rows - 1) * grid.stepLat, 1e-9)
        assertEquals(0f, grid.at(0, 0), 1e-6f)
        assertEquals(0.2f, grid.at(2, 0), 1e-6f)
        assertEquals(1.0f, grid.at(2, 1), 1e-6f)
    }

    @Test
    fun northToSouthScanIsTurnedOver() {
        val grid = Grib2.parse(message(columns = 3, rows = 2, raw = intArrayOf(0, 10, 20, 30, 40, 100), scan = 0))
        // Первая строка в файле северная.
        assertEquals(0f, grid.at(0, 1), 1e-6f)
        assertEquals(1.0f, grid.at(2, 0), 1e-6f)
    }

    @Test
    fun bitmapGivesGaps() {
        val grid = Grib2.parse(
            message(columns = 2, rows = 2, raw = intArrayOf(50, 100), scan = 64, bitmap = booleanArrayOf(true, false, false, true)),
        )
        assertEquals(0.5f, grid.at(0, 0), 1e-6f)
        assertTrue(grid.at(1, 0).isNaN())
        assertTrue(grid.at(0, 1).isNaN())
        assertEquals(1.0f, grid.at(1, 1), 1e-6f)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(MapDataException::class.java) { Grib2.parse(ByteArray(0)) }
        assertThrows(MapDataException::class.java) { Grib2.parse("<html>Not found</html>".toByteArray()) }
        assertThrows(MapDataException::class.java) { Grib2.parse(ByteArray(4096) { 7 }) }
    }

    @Test
    fun rejectsTruncatedMessage() {
        val whole = message(columns = 3, rows = 2, raw = intArrayOf(0, 10, 20, 30, 40, 100), scan = 64)
        for (cut in listOf(10, 40, 120, whole.size - 6)) {
            assertThrows(MapDataException::class.java) { Grib2.parse(whole.copyOf(cut)) }
        }
    }

    @Test
    fun rejectsOtherGridsAndPackings() {
        assertThrows(MapDataException::class.java) {
            Grib2.parse(message(columns = 2, rows = 2, raw = intArrayOf(1, 2, 3, 4), scan = 64, gridTemplate = 1))
        }
        assertThrows(MapDataException::class.java) {
            Grib2.parse(message(columns = 2, rows = 2, raw = intArrayOf(1, 2, 3, 4), scan = 64, packingTemplate = 42))
        }
    }

    @Test
    fun rejectsHugeGrid() {
        assertThrows(MapDataException::class.java) {
            Grib2.parse(message(columns = 3, rows = 2, raw = intArrayOf(0, 1, 2, 3, 4, 5), scan = 64, declaredColumns = 3_000_000))
        }
    }

    companion object {
        /** Сообщение GRIB2: сетка с юго-западным углом 47° с.ш., 4° з.д. (356°), шаг 1°, значения raw / 100. */
        fun message(
            columns: Int,
            rows: Int,
            raw: IntArray,
            scan: Int,
            bitmap: BooleanArray? = null,
            gridTemplate: Int = 0,
            packingTemplate: Int = 0,
            declaredColumns: Int = columns,
        ): ByteArray {
            val south = 47_000_000
            val north = 47_000_000 + (rows - 1) * 1_000_000
            val west = 356_000_000
            val east = west + (columns - 1) * 1_000_000
            val (lat1, lat2) = if (scan and 0x40 != 0) south to north else north to south
            val sections = ByteArrayOutputStream()
            sections.write(section(1, ByteArray(16)))
            sections.write(
                section(
                    3,
                    bytes(0) + int(columns * rows, 4) + bytes(0, 0) + int(gridTemplate, 2) +
                        bytes(6) + ByteArray(15) + int(declaredColumns, 4) + int(rows, 4) + int(0, 4) + int(-1, 4) +
                        int(lat1, 4) + int(west, 4) + bytes(48) + int(lat2, 4) + int(east, 4) +
                        int(1_000_000, 4) + int(1_000_000, 4) + bytes(scan),
                ),
            )
            sections.write(section(4, ByteArray(30)))
            sections.write(
                section(
                    5,
                    int(raw.size, 4) + int(packingTemplate, 2) + int(0f.toRawBits(), 4) + int(0, 2) +
                        signed(2, 2) + bytes(16, 0),
                ),
            )
            if (bitmap == null) {
                sections.write(section(6, bytes(255)))
            } else {
                val packed = ByteArray((bitmap.size + 7) / 8)
                bitmap.forEachIndexed { n, on -> if (on) packed[n / 8] = (packed[n / 8].toInt() or (0x80 shr (n % 8))).toByte() }
                sections.write(section(6, bytes(0) + packed))
            }
            val data = ByteArray(raw.size * 2)
            raw.forEachIndexed { n, v ->
                data[2 * n] = (v shr 8).toByte()
                data[2 * n + 1] = v.toByte()
            }
            sections.write(section(7, data))
            val body = sections.toByteArray()
            val total = 16 + body.size + 4
            return "GRIB".toByteArray() + bytes(0, 0, 0, 2) + long8(total) + body + "7777".toByteArray()
        }

        private fun section(number: Int, content: ByteArray): ByteArray = int(content.size + 5, 4) + bytes(number) + content

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        private fun int(value: Int, count: Int) = ByteArray(count) { k -> (value shr (8 * (count - 1 - k))).toByte() }

        /** Число со знаком в записи GRIB (старший бит это знак). */
        private fun signed(value: Int, count: Int): ByteArray {
            val magnitude = int(kotlin.math.abs(value), count)
            if (value < 0) magnitude[0] = (magnitude[0].toInt() or 0x80).toByte()
            return magnitude
        }

        private fun long8(value: Int) = int(0, 4) + int(value, 4)
    }
}
