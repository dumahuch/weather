package io.github.dumahuch.pogodaapp.map

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Точки, где на карте стоят числа температуры и берутся скорости ветра: сетка по области [MapArea.Overlay], около 28 км между
 * соседними точками (шаг по долготе больше, потому что в Германии градус долготы короче градуса широты). Какие из
 * точек видны, решает сама карта: при малом масштабе стоящие рядом значки скрываются.
 */
object PointGrid {
    internal const val SOUTH = 45.75
    internal const val LAT_STEP = 0.25
    const val ROWS = 42

    internal const val WEST = 2.7
    internal const val LON_STEP = 0.4
    const val COLUMNS = 40

    const val COUNT = ROWS * COLUMNS

    fun lat(row: Int): Double = SOUTH + row * LAT_STEP

    fun lon(column: Int): Double = WEST + column * LON_STEP

    /** Значения поля [grid] в точках построчно с юга на север; вне сетки модели и в пропусках NaN. */
    fun sample(grid: LatLonGrid): FloatArray {
        val out = FloatArray(COUNT)
        for (row in 0 until ROWS) {
            val gy = (lat(row) - grid.south) / grid.stepLat
            for (column in 0 until COLUMNS) {
                val gx = (lon(column) - grid.west) / grid.stepLon
                out[row * COLUMNS + column] =
                    if (gx < 0 || gx > grid.columns - 1 || gy < 0 || gy > grid.rows - 1) Float.NaN else CloudRaster.sample(grid, gx, gy)
            }
        }
        return out
    }

    /** Точка в записи GeoJSON с набором свойств [properties] (уже готовый текст `"имя":значение,…`). */
    internal fun feature(out: StringBuilder, row: Int, column: Int, properties: String) {
        if (out.isNotEmpty()) out.append(',')
        out.append("""{"type":"Feature","properties":{""").append(properties)
            .append("""},"geometry":{"type":"Point","coordinates":[""")
            .append(String.format(Locale.ROOT, "%.2f,%.2f", lon(column), lat(row)))
            .append("]}}")
    }

    internal fun collection(body: StringBuilder): String = """{"type":"FeatureCollection","features":[$body]}"""
}

/**
 * Температура воздуха на 2 м (ICON-D2 `t_2m`) для карты: цветная заливка и числа в точках [PointGrid].
 *
 * Кадр часа хранится одним массивом байт ([encode]): сначала значения в точках, потом картинка [PICTURE_WIDTH] ×
 * [PICTURE_HEIGHT]. Байт это температура с шагом [STEP] от [MIN] (0 значит «данных нет»). Цвет по байту берётся из
 * таблицы, поэтому час занимает около 170 КБ, а не 700.
 *
 * Цвета: красный начинается с 32 °C (решение Димы 14.11.2026: 27 °C летом не красная жара), от 0 °C и ниже голубые и
 * синие.
 */
object TemperatureField {
    const val MIN = -30.0
    const val STEP = 0.3
    const val PICTURE_WIDTH = 400
    val PICTURE_HEIGHT: Int = MapArea.heightFor(PICTURE_WIDTH)
    val LENGTH: Int = PointGrid.COUNT + PICTURE_WIDTH * PICTURE_HEIGHT

    /** Непрозрачность заливки: подложка и подписи городов остаются видны. */
    private const val FILL_ALPHA = 0.6

    /** Числа температуры в точках рисуются значками для этого диапазона (°C). */
    val IconRange: IntRange = -35..50

    /** Опорные точки шкалы цвета: градусы и цвет RGB. Между ними цвет плавный. */
    val Stops: List<Pair<Double, Int>> = listOf(
        -30.0 to 0x5B3FA8,
        -20.0 to 0x4B5FD6,
        -10.0 to 0x4A8FE8,
        -2.0 to 0x6EC3F5,
        0.0 to 0x9BDCF2,
        4.0 to 0x7BD8C8,
        9.0 to 0x8FD97A,
        14.0 to 0xC9E060,
        19.0 to 0xF3DD4F,
        24.0 to 0xF5B53D,
        28.0 to 0xF58F3A,
        30.0 to 0xF2713A,
        32.0 to 0xE5443A,
        36.0 to 0xB81F2E,
        42.0 to 0x7C1030,
    )

    /** Отметки легенды (°C). */
    val LegendValues: List<Int> = listOf(-20, -10, 0, 10, 20, 30, 40)

    val LegendMin: Double = Stops.first().first
    val LegendMax: Double = Stops.last().first

    /** Цвет RGB для температуры [celsius]; за краями шкалы цвет крайней точки. */
    fun colorOf(celsius: Double): Int = colorOnScale(Stops, celsius)

    /** Таблица «байт → цвет заливки ARGB»; 0 (нет данных) прозрачный. */
    private val Lut: IntArray = IntArray(256) { code ->
        if (code == 0) 0 else ((FILL_ALPHA * 255).roundToInt() shl 24) or colorOf(celsiusOf(code))
    }

    /**
     * Градусы Цельсия из поля модели. ICON-D2 отдаёт температуру в кельвинах; если значения уже в градусах (самое
     * большое меньше 150), пересчёта нет.
     */
    fun celsius(grid: LatLonGrid): LatLonGrid {
        var max = -Float.MAX_VALUE
        for (v in grid.values) if (!v.isNaN() && v > max) max = v
        if (max < KELVIN_BORDER) return grid
        val shifted = FloatArray(grid.values.size) { grid.values[it] - KELVIN }
        return LatLonGrid(grid.columns, grid.rows, grid.west, grid.south, grid.stepLon, grid.stepLat, shifted)
    }

    /** Байт для температуры [celsius]: 0 для NaN, иначе 1…255 с шагом [STEP]. */
    fun code(celsius: Float): Int =
        if (celsius.isNaN()) 0 else (((celsius - MIN) / STEP).roundToInt() + 1).coerceIn(1, 255)

    fun celsiusOf(code: Int): Double = MIN + (code - 1) * STEP

    /** Кадр часа из поля [grid] (кельвины или градусы): значения в точках и картинка на [MapArea.Overlay]. */
    fun encode(grid: LatLonGrid): ByteArray {
        val degrees = celsius(grid)
        val out = ByteArray(LENGTH)
        val points = PointGrid.sample(degrees)
        for (i in points.indices) out[i] = code(points[i]).toByte()
        val box = MapArea.Overlay
        val top = Mercator.y(box.north)
        val bottom = Mercator.y(box.south)
        val lons = DoubleArray(PICTURE_WIDTH) { x -> box.west + (box.east - box.west) * (x + 0.5) / PICTURE_WIDTH }
        for (y in 0 until PICTURE_HEIGHT) {
            val lat = Mercator.lat(top + (bottom - top) * (y + 0.5) / PICTURE_HEIGHT)
            val gy = (lat - degrees.south) / degrees.stepLat
            if (gy < 0 || gy > degrees.rows - 1) continue
            for (x in 0 until PICTURE_WIDTH) {
                val gx = (lons[x] - degrees.west) / degrees.stepLon
                if (gx < 0 || gx > degrees.columns - 1) continue
                out[PointGrid.COUNT + y * PICTURE_WIDTH + x] = code(CloudRaster.sample(degrees, gx, gy)).toByte()
            }
        }
        return out
    }

    /** Цвета заливки ARGB для картинки кадра [frame] ([PICTURE_WIDTH] × [PICTURE_HEIGHT]). */
    fun pixels(frame: ByteArray): IntArray {
        require(frame.size == LENGTH) { "кадр температуры не того размера" }
        return IntArray(PICTURE_WIDTH * PICTURE_HEIGHT) { Lut[frame[PointGrid.COUNT + it].toInt() and 0xFF] }
    }

    /** Имя значка с числом [degrees] (целые градусы, вне [IconRange] ближайший край). */
    fun iconName(degrees: Int): String = "t" + degrees.coerceIn(IconRange.first, IconRange.last)

    /** Числа температуры в точках кадра [frame] как GeoJSON (свойство `i`, имя значка); точки без данных пропущены. */
    fun numbersJson(frame: ByteArray): String {
        require(frame.size == LENGTH) { "кадр температуры не того размера" }
        val out = StringBuilder()
        for (row in 0 until PointGrid.ROWS) {
            for (column in 0 until PointGrid.COLUMNS) {
                val code = frame[row * PointGrid.COLUMNS + column].toInt() and 0xFF
                if (code == 0) continue
                val degrees = celsiusOf(code).roundToInt()
                PointGrid.feature(out, row, column, "\"i\":\"" + iconName(degrees) + "\"")
            }
        }
        return PointGrid.collection(out)
    }

    private const val KELVIN = 273.15f
    private const val KELVIN_BORDER = 150f
}

/**
 * Ветер на 10 м (ICON-D2 `u_10m` и `v_10m`, м/с, на восток и на север) для карты: цветная заливка по силе ветра
 * ([pixels]) и поле скоростей для линий, бегущих по ветру ([flow], [WindParticles]). Вид выбран Димой 15.11.2026
 * (вариант «А» превью: тонкие линии по ветру поверх приглушённой заливки, без стрелок).
 *
 * Кадр часа это два байта на точку [PointGrid]: u и v с шагом [STEP] м/с, 0 значит «данных нет».
 */
object WindField {
    const val STEP = 0.5
    private const val ZERO = 128

    const val LENGTH = PointGrid.COUNT * 2

    const val PICTURE_WIDTH = 400
    val PICTURE_HEIGHT: Int = MapArea.heightFor(PICTURE_WIDTH)

    /** Опорные точки шкалы цвета: км/ч и цвет RGB, приглушённые, чтобы линии поверх читались. Между ними цвет плавный. */
    val Stops: List<Pair<Double, Int>> = listOf(
        0.0 to 0x4A627C,
        10.0 to 0x3C788E,
        20.0 to 0x469278,
        30.0 to 0x8EA25A,
        40.0 to 0xC89E54,
        55.0 to 0xCE6E4E,
        70.0 to 0xB64A70,
        90.0 to 0x8052B0,
    )

    /** Непрозрачность заливки; у слабого ветра (до [CALM_FILL_KMH]) она ещё меньше, штиль почти не закрашен. */
    private const val FILL_ALPHA = 0.42
    private const val CALM_ALPHA = 0.2
    private const val CALM_FILL_KMH = 5.0

    /** Шкала легенды: от нуля до этой силы (км/ч). */
    const val LEGEND_MAX = 90.0
    val LegendValues: List<Int> = listOf(20, 40, 60, 80)

    const val KMH_PER_MS = 3.6

    /** Цвет RGB для силы ветра [kmh]; за краями шкалы цвет крайней точки. */
    fun colorOf(kmh: Double): Int = colorOnScale(Stops, kmh)

    /** Цвет заливки ARGB для силы [kmh] с непрозрачностью. */
    fun fillColor(kmh: Double): Int {
        val alpha = if (kmh >= CALM_FILL_KMH) FILL_ALPHA else CALM_ALPHA + (FILL_ALPHA - CALM_ALPHA) * kmh / CALM_FILL_KMH
        return ((alpha * 255).roundToInt() shl 24) or colorOf(kmh)
    }

    /** Таблица «сила с шагом [LUT_STEP] км/ч → цвет заливки». */
    private const val LUT_STEP = 0.5
    private val Lut: IntArray = IntArray(((LEGEND_MAX + 30) / LUT_STEP).toInt() + 1) { fillColor(it * LUT_STEP) }

    private fun code(value: Float): Int =
        if (value.isNaN()) 0 else ((value / STEP).roundToInt() + ZERO).coerceIn(1, 255)

    private fun valueOf(code: Int): Double = (code - ZERO) * STEP

    /** Кадр часа из составляющих [u] и [v]: сетки должны совпадать. */
    fun encode(u: LatLonGrid, v: LatLonGrid): ByteArray {
        if (u.columns != v.columns || u.rows != v.rows || u.west != v.west || u.south != v.south ||
            u.stepLon != v.stepLon || u.stepLat != v.stepLat
        ) {
            throw MapDataException("ветер: сетки u и v не совпадают")
        }
        val east = PointGrid.sample(u)
        val north = PointGrid.sample(v)
        val out = ByteArray(LENGTH)
        for (i in 0 until PointGrid.COUNT) {
            val cu = code(east[i])
            val cv = code(north[i])
            if (cu == 0 || cv == 0) continue
            out[2 * i] = cu.toByte()
            out[2 * i + 1] = cv.toByte()
        }
        return out
    }

    /** Сила ветра в точке (км/ч) и направление, куда он дует (градусы по часовой от севера); null без данных. */
    fun at(frame: ByteArray, index: Int): Pair<Double, Int>? {
        val cu = frame[2 * index].toInt() and 0xFF
        val cv = frame[2 * index + 1].toInt() and 0xFF
        if (cu == 0 || cv == 0) return null
        val east = valueOf(cu)
        val north = valueOf(cv)
        val kmh = sqrt(east * east + north * north) * KMH_PER_MS
        val degrees = Math.toDegrees(atan2(east, north))
        return kmh to ((degrees.roundToInt() % 360) + 360) % 360
    }

    /** Скорости кадра [frame] в км/ч (на восток и на север) для линий по ветру. */
    fun flow(frame: ByteArray): WindFlow {
        require(frame.size == LENGTH) { "кадр ветра не того размера" }
        val east = FloatArray(PointGrid.COUNT)
        val north = FloatArray(PointGrid.COUNT)
        for (i in 0 until PointGrid.COUNT) {
            val cu = frame[2 * i].toInt() and 0xFF
            val cv = frame[2 * i + 1].toInt() and 0xFF
            if (cu == 0 || cv == 0) {
                east[i] = Float.NaN
                north[i] = Float.NaN
            } else {
                east[i] = (valueOf(cu) * KMH_PER_MS).toFloat()
                north[i] = (valueOf(cv) * KMH_PER_MS).toFloat()
            }
        }
        return WindFlow(east, north)
    }

    /** Заливка по силе ветра, ARGB, [PICTURE_WIDTH] × [PICTURE_HEIGHT] на [MapArea.Overlay]; где данных нет, прозрачно. */
    fun pixels(frame: ByteArray): IntArray = pixels(flow(frame))

    fun pixels(flow: WindFlow): IntArray {
        val out = IntArray(PICTURE_WIDTH * PICTURE_HEIGHT)
        val box = MapArea.Overlay
        val top = Mercator.y(box.north)
        val bottom = Mercator.y(box.south)
        val wind = FloatArray(2)
        for (y in 0 until PICTURE_HEIGHT) {
            val lat = Mercator.lat(top + (bottom - top) * (y + 0.5) / PICTURE_HEIGHT)
            for (x in 0 until PICTURE_WIDTH) {
                val lon = box.west + (box.east - box.west) * (x + 0.5) / PICTURE_WIDTH
                if (!flow.sample(lon, lat, wind)) continue
                val kmh = sqrt(wind[0] * wind[0] + wind[1] * wind[1]).toDouble()
                out[y * PICTURE_WIDTH + x] = Lut[(kmh / LUT_STEP).roundToInt().coerceIn(0, Lut.size - 1)]
            }
        }
        return out
    }
}

/** Цвет RGB на шкале [stops] (значение и цвет, по возрастанию) для [value]; за краями цвет крайней точки. */
internal fun colorOnScale(stops: List<Pair<Double, Int>>, value: Double): Int {
    if (value <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (upperValue, upper) = stops[i]
        if (value <= upperValue) {
            val (lowerValue, lower) = stops[i - 1]
            return mixRgb(lower, upper, (value - lowerValue) / (upperValue - lowerValue))
        }
    }
    return stops.last().second
}

private fun mixRgb(a: Int, b: Int, share: Double): Int {
    fun channel(shift: Int): Int {
        val from = (a shr shift) and 0xFF
        val to = (b shr shift) and 0xFF
        return (from + (to - from) * share).roundToInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Часовые поля карты, которые лежат в памяти и на диске готовыми кадрами: имя файла на диске и размер кадра. */
enum class HourlyField(val diskPrefix: String, val length: Int) {
    Temperature("temp", TemperatureField.LENGTH),
    Wind("wind", WindField.LENGTH),
}
