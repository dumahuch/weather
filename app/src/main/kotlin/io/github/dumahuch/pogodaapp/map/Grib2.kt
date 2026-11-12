package io.github.dumahuch.pogodaapp.map

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.pow

/** Ошибка данных карты: файл повреждён, не того вида или сервер ответил не тем. Текст для журнала. */
class MapDataException(message: String) : Exception(message)

/**
 * Поле на правильной сетке широта-долгота. Значения лежат строками с юга на север, в строке с запада на восток:
 * точка (i, j) имеет долготу [west] + i·[stepLon] и широту [south] + j·[stepLat]. Пропуски (битовая маска GRIB) это NaN.
 */
class LatLonGrid(
    val columns: Int,
    val rows: Int,
    val west: Double,
    val south: Double,
    val stepLon: Double,
    val stepLat: Double,
    val values: FloatArray,
    /** Конец интервала накопления (шаблон 4.8, например осадки с начала прогона); у мгновенных полей null. */
    val validEnd: Instant? = null,
) {
    fun at(column: Int, row: Int): Float = values[row * columns + column]
}

/**
 * Разбор одного сообщения GRIB2, ровно того вида, в каком DWD выкладывает ICON-D2 на правильной сетке:
 * сетка по шаблону 3.0 (широта-долгота), упаковка по шаблону 5.0 (простая), необязательная битовая маска.
 * Всё прочее (другие сетки, сжатие JPEG или CCSDS, несколько полей в одном сообщении) отклоняется [MapDataException]:
 * файл с чужого сервера не должен уронить приложение или занять всю память.
 */
object Grib2 {

    /** Больше точек не бывает: у ICON-D2 их 906 390 (1215 × 746). */
    private const val MAX_POINTS = 4_000_000

    private const val MICRO = 1e-6

    /** Больше сообщений в одном файле не бывает: у осадков ICON-D2 их четыре (каждые 15 минут часа). */
    private const val MAX_MESSAGES = 8

    /**
     * Все сообщения файла по очереди (у осадков ICON-D2 в файле часа четыре поля: :00, :15, :30, :45). Каждое
     * разбирается [parse] и отдаётся [consume] сразу, чтобы в памяти не лежали все поля вместе.
     */
    fun parseAll(bytes: ByteArray, consume: (LatLonGrid) -> Unit): Int {
        val reader = Reader(bytes)
        var start = 0
        var count = 0
        while (start + 16 <= bytes.size) {
            reader.require(String(bytes, start, 4, Charsets.US_ASCII) == "GRIB", "нет заголовка GRIB")
            val total = reader.long(start + 8, 8)
            reader.require(total in 16..(bytes.size - start).toLong(), "длина сообщения не сходится")
            count++
            reader.require(count <= MAX_MESSAGES, "слишком много сообщений")
            consume(parse(bytes.copyOfRange(start, start + total.toInt())))
            start += total.toInt()
        }
        reader.require(count > 0, "нет сообщений")
        return count
    }

    fun parse(bytes: ByteArray): LatLonGrid {
        val reader = Reader(bytes)
        reader.require(bytes.size >= 16 && String(bytes, 0, 4, Charsets.US_ASCII) == "GRIB", "нет заголовка GRIB")
        reader.require(bytes[7].toInt() == 2, "не GRIB2")
        val total = reader.long(8, 8)
        reader.require(total in 16..bytes.size.toLong(), "длина сообщения не сходится")
        val end = total.toInt()

        var grid: GridDefinition? = null
        var packing: Packing? = null
        var bitmap: Bitmap? = null
        var data: IntRange? = null
        var validEnd: Instant? = null
        var offset = 16
        while (offset + 4 <= end) {
            if (String(bytes, offset, 4, Charsets.US_ASCII) == "7777") break
            reader.require(offset + 5 <= end, "обрыв раздела")
            val length = reader.long(offset, 4)
            reader.require(length >= 5 && offset + length <= end, "длина раздела не сходится")
            val section = bytes[offset + 4].toInt() and 0xFF
            val sectionEnd = offset + length.toInt()
            when (section) {
                3 -> grid = readGrid(reader, offset, sectionEnd)
                4 -> validEnd = readValidEnd(reader, offset, sectionEnd)
                5 -> packing = readPacking(reader, offset, sectionEnd)
                6 -> bitmap = readBitmap(reader, offset, sectionEnd)
                7 -> {
                    reader.require(data == null, "в сообщении больше одного поля")
                    data = (offset + 5) until sectionEnd
                }
            }
            offset = sectionEnd
        }
        val g = grid ?: throw MapDataException("GRIB2: нет описания сетки")
        val p = packing ?: throw MapDataException("GRIB2: нет описания упаковки")
        val d = data ?: throw MapDataException("GRIB2: нет данных")
        return assemble(reader, g, p, bitmap, d, validEnd)
    }

    private class GridDefinition(
        val columns: Int,
        val rows: Int,
        val lat1: Double,
        val lon1: Double,
        val lat2: Double,
        val lon2: Double,
        val stepLon: Double,
        val stepLat: Double,
        val scan: Int,
    )

    private class Packing(val count: Int, val reference: Float, val binaryScale: Int, val decimalScale: Int, val bits: Int)

    private class Bitmap(val start: Int, val end: Int)

    private fun readGrid(r: Reader, at: Int, end: Int): GridDefinition {
        r.require(end - at >= 72, "раздел сетки короткий")
        val template = r.long(at + 12, 2).toInt()
        r.require(template == 0, "сетка не широта-долгота (шаблон $template)")
        val columns = r.long(at + 30, 4)
        val rows = r.long(at + 34, 4)
        r.require(columns in 2..MAX_POINTS && rows in 2..MAX_POINTS && columns * rows <= MAX_POINTS, "размер сетки")
        val basicAngle = r.long(at + 38, 4)
        r.require(basicAngle == 0L || basicAngle == 0xFFFFFFFFL, "непривычная единица углов")
        return GridDefinition(
            columns = columns.toInt(),
            rows = rows.toInt(),
            lat1 = r.signed(at + 46, 4) * MICRO,
            lon1 = longitude(r.signed(at + 50, 4) * MICRO),
            lat2 = r.signed(at + 55, 4) * MICRO,
            lon2 = longitude(r.signed(at + 59, 4) * MICRO),
            stepLon = r.long(at + 63, 4) * MICRO,
            stepLat = r.long(at + 67, 4) * MICRO,
            scan = r.byte(at + 71),
        )
    }

    /** Конец интервала накопления из раздела 4 по шаблону 4.8 (октеты 35–41); у других шаблонов null. */
    private fun readValidEnd(r: Reader, at: Int, end: Int): Instant? {
        if (end - at < 9 || r.long(at + 7, 2).toInt() != 8) return null
        r.require(end - at >= 41, "раздел продукта короткий")
        return try {
            LocalDateTime.of(
                r.long(at + 34, 2).toInt(), r.byte(at + 36), r.byte(at + 37), r.byte(at + 38), r.byte(at + 39), r.byte(at + 40),
            ).toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeException) {
            throw MapDataException("GRIB2: время накопления (${e.message})")
        }
    }

    /** Долгота в −180…180 (в GRIB она бывает в 0…360). */
    private fun longitude(value: Double): Double = if (value > 180.0) value - 360.0 else value

    private fun readPacking(r: Reader, at: Int, end: Int): Packing {
        r.require(end - at >= 21, "раздел упаковки короткий")
        val template = r.long(at + 9, 2).toInt()
        r.require(template == 0, "упаковка не простая (шаблон $template)")
        val count = r.long(at + 5, 4)
        r.require(count in 1..MAX_POINTS, "число значений")
        val bits = r.byte(at + 19)
        r.require(bits in 0..32, "ширина значения $bits бит")
        return Packing(
            count = count.toInt(),
            reference = Float.fromBits(r.long(at + 11, 4).toInt()),
            binaryScale = r.signed(at + 15, 2).toInt(),
            decimalScale = r.signed(at + 17, 2).toInt(),
            bits = bits,
        )
    }

    private fun readBitmap(r: Reader, at: Int, end: Int): Bitmap? {
        r.require(end - at >= 6, "раздел маски короткий")
        return when (val indicator = r.byte(at + 5)) {
            255 -> null
            0 -> Bitmap(at + 6, end)
            else -> throw MapDataException("GRIB2: маска $indicator не поддерживается")
        }
    }

    private fun assemble(r: Reader, g: GridDefinition, p: Packing, bitmap: Bitmap?, data: IntRange, validEnd: Instant?): LatLonGrid {
        val total = g.columns * g.rows
        r.require(g.scan and 0x20 == 0, "точки идут столбцами")
        val expected = if (bitmap == null) total else countBits(r.bytes, bitmap, total)
        r.require(p.count == expected, "число значений не совпадает с сеткой")
        val dataBits = (data.last - data.first + 1).toLong() * 8
        r.require(dataBits >= p.count.toLong() * p.bits, "данные обрываются")

        val scale = 2.0.pow(p.binaryScale) / 10.0.pow(p.decimalScale)
        val base = p.reference / 10.0.pow(p.decimalScale)
        val westToEast = g.scan and 0x80 == 0
        val southToNorth = g.scan and 0x40 != 0
        val values = FloatArray(total)
        var packed = 0L
        for (n in 0 until total) {
            val present = bitmap == null || bit(r.bytes, bitmap.start, n)
            val value = if (present) {
                val raw = if (p.bits == 0) 0L else readBits(r.bytes, data.first, packed, p.bits)
                packed++
                (base + raw * scale).toFloat()
            } else {
                Float.NaN
            }
            val i = n % g.columns
            val j = n / g.columns
            val column = if (westToEast) i else g.columns - 1 - i
            val row = if (southToNorth) j else g.rows - 1 - j
            values[row * g.columns + column] = value
        }
        return LatLonGrid(
            columns = g.columns,
            rows = g.rows,
            west = minOf(g.lon1, g.lon2),
            south = minOf(g.lat1, g.lat2),
            stepLon = g.stepLon,
            stepLat = g.stepLat,
            values = values,
            validEnd = validEnd,
        )
    }

    private fun countBits(bytes: ByteArray, bitmap: Bitmap, total: Int): Int {
        if ((bitmap.end - bitmap.start).toLong() * 8 < total) throw MapDataException("GRIB2: маска короче сетки")
        var count = 0
        for (n in 0 until total) if (bit(bytes, bitmap.start, n)) count++
        return count
    }

    private fun bit(bytes: ByteArray, start: Int, index: Int): Boolean =
        (bytes[start + index / 8].toInt() shr (7 - index % 8)) and 1 == 1

    /** [width] бит со сдвига [index]·[width] от начала [start], старший бит первым. */
    private fun readBits(bytes: ByteArray, start: Int, index: Long, width: Int): Long {
        var position = index * width
        var result = 0L
        var left = width
        while (left > 0) {
            val byte = bytes[start + (position / 8).toInt()].toInt() and 0xFF
            val offset = (position % 8).toInt()
            val take = minOf(8 - offset, left)
            val chunk = (byte shr (8 - offset - take)) and ((1 shl take) - 1)
            result = (result shl take) or chunk.toLong()
            left -= take
            position += take
        }
        return result
    }

    private class Reader(val bytes: ByteArray) {
        fun require(condition: Boolean, what: String) {
            if (!condition) throw MapDataException("GRIB2: $what")
        }

        fun byte(at: Int): Int {
            require(at in bytes.indices, "чтение за концом")
            return bytes[at].toInt() and 0xFF
        }

        /** Беззнаковое число из [count] байт, старший первым. */
        fun long(at: Int, count: Int): Long {
            require(at >= 0 && at + count <= bytes.size, "чтение за концом")
            var value = 0L
            for (k in 0 until count) value = (value shl 8) or (bytes[at + k].toLong() and 0xFF)
            return value
        }

        /** Число со знаком в записи GRIB: старший бит это знак, остальное величина. */
        fun signed(at: Int, count: Int): Long {
            val raw = long(at, count)
            val signBit = 1L shl (count * 8 - 1)
            return if (raw and signBit != 0L) -(raw and (signBit - 1)) else raw
        }
    }
}
