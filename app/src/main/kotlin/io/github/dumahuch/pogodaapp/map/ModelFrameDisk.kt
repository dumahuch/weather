package io.github.dumahuch.pogodaapp.map

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.zip.Deflater

/**
 * Сжатие кадра уровней дождя модели. Пятна модели плавные, поэтому сначала каждый пиксель заменяется разностью с
 * соседями слева и сверху (как фильтр PNG), потом deflate. Кадр выходит в 1,8–2,5 раза меньше, чем просто deflate
 * (на поле, где дождь занимает 30 % области, около 170 КБ вместо 360; оценка 12.11.2026 на придуманном поле).
 */
object LevelPacking {
    fun pack(levels: ByteArray, width: Int): ByteArray {
        val out = ByteArray(levels.size)
        for (i in levels.indices) {
            val x = i % width
            val left = if (x > 0) levels[i - 1].toInt() else 0
            val up = if (i >= width) levels[i - width].toInt() else 0
            val corner = if (x > 0 && i >= width) levels[i - width - 1].toInt() else 0
            out[i] = (levels[i] - left - up + corner).toByte()
        }
        return FrameDiskCache.deflate(out, Deflater.DEFAULT_COMPRESSION)
    }

    /** Уровни кадра [width] × [height]; null, если данные испорчены или другого размера. */
    fun unpack(packed: ByteArray, width: Int, height: Int): ByteArray? {
        val levels = FrameDiskCache.inflate(packed, width * height) ?: return null
        for (i in levels.indices) {
            val x = i % width
            val left = if (x > 0) levels[i - 1].toInt() else 0
            val up = if (i >= width) levels[i - width].toInt() else 0
            val corner = if (x > 0 && i >= width) levels[i - width - 1].toInt() else 0
            levels[i] = (levels[i] + left + up - corner).toByte()
        }
        return levels
    }
}

/**
 * Кадры дождя модели ICON-D2 на диске, в кэше приложения, по прогонам: `<корень>/<вид>-<прогон>/rain-<время>.z`
 * (уровни [RainPalette.levelOf], сжатые [LevelPacking]). Кадр одного прогона не меняется, поэтому лежит,
 * пока прогон нужен, и через полночь тоже. Держатся только [RUNS] последних прогонов (пока выкладывается новый, дальние
 * часы ещё берутся из прежнего); кадры на прошедшее время и папки другого [FORMAT] стираются ([tidy]). Любой сбой
 * чтения или записи это просто «кадра нет». Система может стереть кэш сама, тогда кадр скачивается заново.
 */
class ModelFrameDisk(private val root: File) {

    /** Сжатые уровни кадра [width] × [height] на [time] из прогона [run]; null, если нет или испорчен (тогда файл удаляется). */
    fun read(run: Instant, time: Instant, width: Int, height: Int): ByteArray? {
        val file = file(run, time)
        return try {
            if (!file.isFile) return null
            val packed = file.readBytes()
            if (LevelPacking.unpack(packed, width, height) != null) packed else null.also { file.delete() }
        } catch (_: IOException) {
            null
        }
    }

    /** Записывает кадр; первая запись нового прогона заодно убирает старое ([tidy]). */
    fun write(run: Instant, time: Instant, packed: ByteArray, now: Instant) {
        val folder = folder(run)
        val fresh = !folder.isDirectory
        try {
            folder.mkdirs()
            val target = file(run, time)
            val tmp = File(folder, target.name + ".tmp")
            tmp.writeBytes(packed)
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (_: IOException) {
            // Диск полон или недоступен: кадр просто не сохранится.
        }
        if (fresh) tidy(now)
    }

    /**
     * Убирает папки чужого вида и всех прогонов, кроме [RUNS] последних, а в оставшихся кадры на время раньше [now]
     * (прошедшее время показывает радар, кадры модели там больше не нужны).
     */
    @Synchronized
    fun tidy(now: Instant) {
        val folders = root.listFiles()?.toList() ?: return
        val ours = folders.filter { it.isDirectory && runOf(it.name) != null }.sortedByDescending { runOf(it.name) }
        (folders - ours.take(RUNS).toSet()).forEach { it.deleteRecursively() }
        for (folder in ours.take(RUNS)) {
            folder.listFiles()?.forEach { file ->
                val time = timeOf(file.name)
                if (time == null || time.isBefore(now)) file.delete()
            }
        }
    }

    private fun folder(run: Instant) = File(root, FORMAT + "-" + Stamp.format(run))

    private fun file(run: Instant, time: Instant) = File(folder(run), "rain-" + time.epochSecond + ".z")

    companion object {
        /**
         * Вид кадров. Меняется, когда меняется расчёт кадра или размер картинки: тогда старые кадры не читаются
         * и стираются.
         */
        const val FORMAT = "v1"

        /** Сколько последних прогонов держать на диске. */
        const val RUNS = 2

        private val Pattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
        private val Stamp: DateTimeFormatter = Pattern.withZone(ZoneOffset.UTC)

        private fun runOf(name: String): Instant? {
            if (!name.startsWith("$FORMAT-")) return null
            return try {
                LocalDateTime.parse(name.removePrefix("$FORMAT-"), Pattern).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        private fun timeOf(name: String): Instant? =
            name.removePrefix("rain-").removeSuffix(".z").takeIf { name.startsWith("rain-") && name.endsWith(".z") }
                ?.toLongOrNull()?.let { Instant.ofEpochSecond(it) }
    }
}
