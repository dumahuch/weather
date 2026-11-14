package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Списки файлов в папках прогонов на opendata.dwd.de. Список держится [LIFE]; несколько загрузок, начатых вместе,
 * спрашивают его один раз. Общий для облаков и осадков.
 */
class FolderListings(private val text: (url: String) -> String) {
    private class Listing(val fetched: Instant, val body: String)

    private val listings = HashMap<String, Listing>()

    @Synchronized
    fun get(folder: String, now: Instant): String {
        val cached = listings[folder]
        if (cached != null && Duration.between(cached.fetched, now) < LIFE && !cached.fetched.isAfter(now)) return cached.body
        val body = text(folder)
        listings[folder] = Listing(now, body)
        return body
    }

    private companion object {
        val LIFE: Duration = Duration.ofMinutes(10)
    }
}

/**
 * Осадки ICON-D2 на открытом сервере DWD: `https://opendata.dwd.de/weather/nwp/icon-d2/grib/HH/tot_prec/`.
 * В файле часа прогноза четыре поля: осадки, накопленные с начала прогона, на :00, :15, :30 и :45 этого часа
 * (проверено 12.11.2026 через среду «dwd»: файл 007 прогона 15 UTC несёт 22:00, 22:15, 22:30, 22:45).
 */
object IconD2Rain {
    private const val BASE = "https://opendata.dwd.de/weather/nwp/icon-d2/grib/"
    private val Stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)

    /** Шаг полей в файле и шаг кадров дождя модели на шкале. */
    val Step: Duration = Duration.ofMinutes(15)

    private val Hour: Duration = Duration.ofHours(1)

    /** Во сколько раз сумма за 15 минут меньше силы дождя в мм/ч. */
    private const val PER_HOUR = 4.0

    fun folder(run: Instant): String = BASE + String.format(Locale.ROOT, "%02d", run.atOffset(ZoneOffset.UTC).hour) + "/tot_prec/"

    fun fileName(run: Instant, lead: Int): String =
        "icon-d2_germany_regular-lat-lon_single-level_${Stamp.format(run)}_" +
            String.format(Locale.ROOT, "%03d", lead) + "_2d_tot_prec.grib2.bz2"

    /** Номер файла (час прогноза), в котором лежит накопление на момент [time]. */
    fun leadOf(run: Instant, time: Instant): Int = (Duration.between(run, time).seconds / Hour.seconds).toInt()

    /** Сила дождя (мм/ч) за 15 минут между накоплениями [before] и [after] (мм). */
    fun rate(before: Double, after: Double): Double = ((after - before) * PER_HOUR).coerceAtLeast(0.0)
}

/**
 * Дождь модели ICON-D2 на 15-минутный кадр: сила дождя за 15 минут, которые кончаются в [load]`(time)`, как разность
 * двух накоплений, в уровнях [RainPalette.levelOf] на картинку [width] × [height] по области [MapArea.Overlay].
 * Между узлами сетки (около 2 км) сила дождя сглаживается кубически (Катмулл — Ром): значения в узлах те же, что у
 * модели, а пятна мягкие, без квадратов. Картинка шириной как у радара ([MapArea.RAIN_WIDTH]), полтора пикселя на клетку.
 * Пятна дождя в один-два узла не показываются ([RainSpots]): на картинке они выходят квадратиками в 3–4 пикселя.
 * Нужный прогон выбирается так же, как для облаков ([IconD2Files.candidates]); есть ли файл, показывает список папки.
 * Нет ни в одном прогоне: null (тогда дождь берётся из ICON-EU).
 *
 * Скачанные файлы держатся разобранными в небольшом запасе ([FILE_CACHE]), только вырезка сетки под область карты:
 * у четырёх кадров часа один файл, а кадр на :00 берёт ещё последнее поле предыдущего файла. Одновременно
 * скачивается и разбирается не больше [PARALLEL] файлов, чтобы не занять слишком много памяти (файл около 2,4 МБ,
 * распакованный около 6,5 МБ).
 */
/**
 * Мелкие пятна дождя модели. Пятно в один или два узла сетки (около 2 км) модель по-настоящему не разрешает, а на
 * картинке оно выходит квадратиком в 3–4 пикселя (одиночный узел 3 × 4, два узла 4 × 4 при ширине 1200; измерено
 * 12.11.2026), который при приближении карты виден жёстким пятнышком. Такие пятна убираются целиком.
 */
internal object RainSpots {
    /** Пятна меньше этого числа узлов не показываются. */
    const val MIN_NODES = 3

    /**
     * Обнуляет в [rate] (мм/ч, [columns] × [rows] узлов) пятна дождя, видимые на карте (от [RainPalette.LEVEL_LOW]),
     * меньше [MIN_NODES] узлов; узлы пятна соседи по стороне или по углу.
     */
    fun dropSmall(rate: FloatArray, columns: Int, rows: Int) {
        val wet = RainPalette.LEVEL_LOW.toFloat()
        val seen = BooleanArray(rate.size)
        val stack = IntArray(rate.size)
        val spot = IntArray(MIN_NODES)
        for (startNode in rate.indices) {
            if (seen[startNode] || rate[startNode] < wet) continue
            seen[startNode] = true
            var top = 0
            stack[top++] = startNode
            var size = 0
            while (top > 0) {
                val node = stack[--top]
                if (size < MIN_NODES) spot[size] = node
                size++
                val x = node % columns
                val y = node / columns
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= rows) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= columns) continue
                        val next = ny * columns + nx
                        if (!seen[next] && rate[next] >= wet) {
                            seen[next] = true
                            stack[top++] = next
                        }
                    }
                }
            }
            if (size < MIN_NODES) for (k in 0 until size) rate[spot[k]] = 0f
        }
    }
}

class ModelRainLoader(
    private val listings: FolderListings,
    private val bytes: (url: String) -> ByteArray,
    private val unpack: (ByteArray) -> ByteArray,
    private val width: Int = MapArea.RAIN_WIDTH,
    private val height: Int = MapArea.heightFor(MapArea.RAIN_WIDTH),
) {
    /** Вырезка сетки: столбцы и строки узлов, которые нужны картинке, и где пиксели картинки лежат между ними. */
    private class Window(val key: String, val columns: Int, val rows: Int, val firstColumn: Int, val firstRow: Int, val gx: DoubleArray, val gy: DoubleArray)

    /** Накопления файла по моментам: на каждый узел вырезки в 1/[UNITS] мм, [GAP] где у модели пропуск. */
    private class FileData(val window: Window, val fields: Map<Instant, CharArray>)

    private val files = object : LinkedHashMap<String, FutureTask<FileData>>(FILE_CACHE * 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FutureTask<FileData>>?): Boolean = size > FILE_CACHE
    }

    private val slots = Semaphore(PARALLEL)

    @Volatile
    private var window: Window? = null

    /** Кадр дождя модели: прогон, из которого он посчитан, и уровни [RainPalette.levelOf] на пиксели картинки. */
    class Frame(val run: Instant, val levels: ByteArray)

    /** Откуда берётся кадр на [time]: прогон, его папка и номера двух файлов с накоплениями. */
    private class Source(val run: Instant, val folder: String, val leadBefore: Int, val leadAfter: Int)

    /** Подходящие прогоны по порядку, у которых в списке папки есть оба нужных файла. */
    private fun sources(time: Instant, now: Instant): Sequence<Source> {
        val start = time.minus(IconD2Rain.Step)
        return IconD2Files.candidates(MapTimeline.floorTo(start, Duration.ofHours(1)), now).asSequence().mapNotNull { frame ->
            val run = frame.run
            if (start.isBefore(run)) return@mapNotNull null
            val source = Source(run, IconD2Rain.folder(run), IconD2Rain.leadOf(run, start), IconD2Rain.leadOf(run, time))
            if (source.leadAfter > IconD2Files.MAX_LEAD) return@mapNotNull null
            val listing = listings.get(source.folder, now)
            source.takeIf { listed(listing, run, it.leadBefore) && listed(listing, run, it.leadAfter) }
        }
    }

    /**
     * Прогон, из которого сейчас берётся кадр на [time] (по спискам папок, без скачивания файлов); null, если ICON-D2
     * на это время нет. Кадр одного прогона не меняется, поэтому по прогону его можно хранить на диске.
     */
    fun runFor(time: Instant, now: Instant): Instant? = sources(time, now).firstOrNull()?.run

    fun load(time: Instant, now: Instant): Frame? {
        val start = time.minus(IconD2Rain.Step)
        for (source in sources(time, now)) {
            val first = file(source.folder, source.run, source.leadBefore)
            val second = file(source.folder, source.run, source.leadAfter)
            if (first.window !== second.window) continue
            val before = first.fields[start] ?: continue
            val after = second.fields[time] ?: continue
            return Frame(source.run, levels(first.window, before, after))
        }
        return null
    }

    private fun listed(listing: String, run: Instant, lead: Int) = listing.contains("\"" + IconD2Rain.fileName(run, lead) + "\"")

    /** Сила дождя в узлах вырезки, сглаженная на пиксели картинки, в уровнях палитры. */
    private fun levels(window: Window, before: CharArray, after: CharArray): ByteArray {
        val rate = FloatArray(before.size) { i ->
            val a = before[i].code
            val b = after[i].code
            if (a == GAP || b == GAP || b <= a) 0f else IconD2Rain.rate(a / UNITS, b / UNITS).toFloat()
        }
        RainSpots.dropSmall(rate, window.columns, window.rows)
        // Сначала вдоль строк сетки на столбцы картинки, потом вдоль столбцов на строки картинки.
        val across = FloatArray(window.rows * width)
        for (row in 0 until window.rows) {
            val base = row * window.columns
            for (x in 0 until width) {
                val gx = window.gx[x]
                across[row * width + x] = if (gx < 0) 0f else cubic(gx, window.columns) { rate[base + it] }
            }
        }
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            val gy = window.gy[y]
            if (gy < 0) continue
            for (x in 0 until width) {
                if (window.gx[x] < 0) continue
                val mm = cubic(gy, window.rows) { across[it * width + x] }
                out[y * width + x] = RainPalette.levelOf(mm.toDouble()).toByte()
            }
        }
        return out
    }

    /** Значение в дробной точке [at] ряда из [size] узлов [value] по Катмуллу — Рому; не меньше 0 (без отрицательного дождя). */
    private inline fun cubic(at: Double, size: Int, value: (Int) -> Float): Float {
        val i = floor(at).toInt()
        val t = (at - i).toFloat()
        val last = size - 1
        val p0 = value((i - 1).coerceIn(0, last))
        val p1 = value(i.coerceIn(0, last))
        val p2 = value((i + 1).coerceIn(0, last))
        val p3 = value((i + 2).coerceIn(0, last))
        val result = p1 + 0.5f * t * (p2 - p0 + t * (2f * p0 - 5f * p1 + 4f * p2 - p3 + t * (3f * (p1 - p2) + p3 - p0)))
        return if (result > 0f) result else 0f
    }

    /** Файл из запаса или скачанный; второй спрос того же файла ждёт ту же загрузку. Неудача из запаса убирается. */
    private fun file(folder: String, run: Instant, lead: Int): FileData {
        val name = IconD2Rain.fileName(run, lead)
        var mine = false
        val task = synchronized(files) {
            files[name] ?: FutureTask { download(folder + name) }.also {
                files[name] = it
                mine = true
            }
        }
        if (mine) task.run()
        return try {
            task.get()
        } catch (e: ExecutionException) {
            synchronized(files) { if (files[name] === task) files.remove(name) }
            throw (e.cause as? Exception) ?: e
        }
    }

    private fun download(url: String): FileData {
        slots.acquire()
        try {
            val grib = unpack(bytes(url))
            val fields = HashMap<Instant, CharArray>()
            var window: Window? = null
            Grib2.parseAll(grib) { grid ->
                val end = grid.validEnd ?: throw MapDataException("GRIB2: у осадков нет времени накопления")
                val own = windowFor(grid)
                if (window != null && window !== own) throw MapDataException("GRIB2: у полей осадков разные сетки")
                window = own
                fields[end] = cut(grid, own)
            }
            return FileData(window ?: throw MapDataException("GRIB2: в файле осадков нет полей"), fields)
        } finally {
            slots.release()
        }
    }

    /** Узлы вырезки в 1/[UNITS] мм. */
    private fun cut(grid: LatLonGrid, window: Window): CharArray = CharArray(window.columns * window.rows) { n ->
        val value = grid.at(window.firstColumn + n % window.columns, window.firstRow + n / window.columns)
        if (value.isNaN()) GAP.toChar() else (value * UNITS).roundToInt().coerceIn(0, GAP - 1).toChar()
    }

    /** Вырезка под картинку (с запасом в два узла для сглаживания); строится один раз на вид сетки. */
    private fun windowFor(grid: LatLonGrid): Window {
        val key = "${grid.columns} ${grid.rows} ${grid.west} ${grid.south} ${grid.stepLon} ${grid.stepLat}"
        window?.let { if (it.key == key) return it }
        val box = MapArea.Overlay
        val top = Mercator.y(box.north)
        val bottom = Mercator.y(box.south)
        val columnAt = DoubleArray(width) { x -> (box.west + (box.east - box.west) * (x + 0.5) / width - grid.west) / grid.stepLon }
        val rowAt = DoubleArray(height) { y -> (Mercator.lat(top + (bottom - top) * (y + 0.5) / height) - grid.south) / grid.stepLat }
        fun inside(at: Double, size: Int) = at >= 0 && at <= size - 1
        val firstColumn = (floor(columnAt.filter { inside(it, grid.columns) }.minOrNull() ?: 0.0).toInt() - MARGIN).coerceAtLeast(0)
        val lastColumn = (ceil(columnAt.filter { inside(it, grid.columns) }.maxOrNull() ?: 0.0).toInt() + MARGIN).coerceAtMost(grid.columns - 1)
        val firstRow = (floor(rowAt.filter { inside(it, grid.rows) }.minOrNull() ?: 0.0).toInt() - MARGIN).coerceAtLeast(0)
        val lastRow = (ceil(rowAt.filter { inside(it, grid.rows) }.maxOrNull() ?: 0.0).toInt() + MARGIN).coerceAtMost(grid.rows - 1)
        val built = Window(
            key = key,
            columns = lastColumn - firstColumn + 1,
            rows = lastRow - firstRow + 1,
            firstColumn = firstColumn,
            firstRow = firstRow,
            gx = DoubleArray(width) { x -> if (inside(columnAt[x], grid.columns)) columnAt[x] - firstColumn else -1.0 },
            gy = DoubleArray(height) { y -> if (inside(rowAt[y], grid.rows)) rowAt[y] - firstRow else -1.0 },
        )
        window = built
        return built
    }

    private companion object {
        /** Накопление хранится в 1/128 мм (до 512 мм): шаг 0,03 мм/ч у силы дождя, ниже порога 0,1. */
        const val UNITS = 128.0
        const val GAP = 0xFFFF
        const val FILE_CACHE = 4
        const val PARALLEL = 2

        /** Запас узлов вокруг картинки: кубическому сглаживанию нужны соседи с обеих сторон. */
        const val MARGIN = 2
    }
}
