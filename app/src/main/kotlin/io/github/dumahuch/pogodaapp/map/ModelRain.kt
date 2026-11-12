package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
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
 * Нужный прогон выбирается так же, как для облаков ([IconD2Files.candidates]); есть ли файл, показывает список папки.
 * Нет ни в одном прогоне: null (тогда дождь берётся из ICON-EU).
 *
 * Скачанные файлы держатся разобранными в небольшом запасе ([FILE_CACHE]): у четырёх кадров часа один файл, а кадр на
 * :00 берёт ещё последнее поле предыдущего файла. Одновременно скачивается и разбирается не больше [PARALLEL] файлов,
 * чтобы не занять слишком много памяти (файл около 2,4 МБ, распакованный около 6,5 МБ).
 */
class ModelRainLoader(
    private val listings: FolderListings,
    private val bytes: (url: String) -> ByteArray,
    private val unpack: (ByteArray) -> ByteArray,
    private val width: Int = MapArea.CLOUD_WIDTH,
    private val height: Int = MapArea.heightFor(MapArea.CLOUD_WIDTH),
) {
    /** Накопления файла по моментам: на каждый пиксель картинки в 1/[UNITS] мм, [GAP] вне сетки. */
    private class FileData(val fields: Map<Instant, CharArray>)

    private val files = object : LinkedHashMap<String, FutureTask<FileData>>(FILE_CACHE * 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FutureTask<FileData>>?): Boolean = size > FILE_CACHE
    }

    private val slots = Semaphore(PARALLEL)

    /** Какой узел сетки берёт каждый пиксель картинки; строится один раз на вид сетки. */
    @Volatile
    private var projection: Pair<String, IntArray>? = null

    fun load(time: Instant, now: Instant): ByteArray? {
        val start = time.minus(IconD2Rain.Step)
        for (frame in IconD2Files.candidates(MapTimeline.floorTo(start, Duration.ofHours(1)), now)) {
            val run = frame.run
            if (start.isBefore(run)) continue
            val leadBefore = IconD2Rain.leadOf(run, start)
            val leadAfter = IconD2Rain.leadOf(run, time)
            if (leadAfter > IconD2Files.MAX_LEAD) continue
            val folder = IconD2Rain.folder(run)
            val listing = listings.get(folder, now)
            if (!listed(listing, run, leadBefore) || !listed(listing, run, leadAfter)) continue
            val before = file(folder, run, leadBefore).fields[start] ?: continue
            val after = file(folder, run, leadAfter).fields[time] ?: continue
            return levels(before, after)
        }
        return null
    }

    private fun listed(listing: String, run: Instant, lead: Int) = listing.contains("\"" + IconD2Rain.fileName(run, lead) + "\"")

    private fun levels(before: CharArray, after: CharArray): ByteArray = ByteArray(before.size) { i ->
        val a = before[i].code
        val b = after[i].code
        if (a == GAP || b == GAP || b <= a) {
            0
        } else {
            RainPalette.levelOf(IconD2Rain.rate(a / UNITS, b / UNITS)).toByte()
        }
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
            Grib2.parseAll(grib) { grid ->
                val end = grid.validEnd ?: throw MapDataException("GRIB2: у осадков нет времени накопления")
                fields[end] = project(grid)
            }
            return FileData(fields)
        } finally {
            slots.release()
        }
    }

    /** Поле в пиксели картинки (ближайший узел сетки, широта по проекции карты), 1/[UNITS] мм. */
    private fun project(grid: LatLonGrid): CharArray {
        val map = indexFor(grid)
        return CharArray(map.size) { p ->
            val n = map[p]
            val value = if (n < 0) Float.NaN else grid.values[n]
            if (value.isNaN()) GAP.toChar() else (value * UNITS).roundToInt().coerceIn(0, GAP - 1).toChar()
        }
    }

    private fun indexFor(grid: LatLonGrid): IntArray {
        val key = "${grid.columns} ${grid.rows} ${grid.west} ${grid.south} ${grid.stepLon} ${grid.stepLat}"
        projection?.let { if (it.first == key) return it.second }
        val box = MapArea.Overlay
        val top = Mercator.y(box.north)
        val bottom = Mercator.y(box.south)
        val columns = IntArray(width) { x ->
            val lon = box.west + (box.east - box.west) * (x + 0.5) / width
            ((lon - grid.west) / grid.stepLon).roundToInt().let { if (it in 0 until grid.columns) it else -1 }
        }
        val map = IntArray(width * height)
        for (y in 0 until height) {
            val lat = Mercator.lat(top + (bottom - top) * (y + 0.5) / height)
            val row = ((lat - grid.south) / grid.stepLat).roundToInt()
            for (x in 0 until width) {
                val column = columns[x]
                map[y * width + x] = if (row in 0 until grid.rows && column >= 0) row * grid.columns + column else -1
            }
        }
        projection = key to map
        return map
    }

    private companion object {
        /** Накопление хранится в 1/128 мм (до 512 мм): шаг 0,03 мм/ч у силы дождя, ниже порога 0,1. */
        const val UNITS = 128.0
        const val GAP = 0xFFFF
        const val FILE_CACHE = 4
        const val PARALLEL = 2
    }
}
