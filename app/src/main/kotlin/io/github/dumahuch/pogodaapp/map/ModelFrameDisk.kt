package io.github.dumahuch.pogodaapp.map

import java.io.File
import java.io.IOException
import java.time.Duration
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
 * Кадры модели ICON-D2 на диске, в кэше приложения, по прогонам: `<корень>/<вид>-<прогон>/<префикс>-<время>.z`.
 * Префикс у дождя [RAIN] (уровни [RainPalette.levelOf], сжатые [LevelPacking]), у облаков [CLOUDS], у температуры и ветра
 * [HourlyField.diskPrefix] (готовый кадр часа, сжатый deflate). Кадр одного прогона не меняется, поэтому лежит, пока
 * прогон нужен, и через полночь тоже. Держатся только [RUNS] последних прогонов (пока выкладывается новый, дальние
 * часы ещё берутся из прежнего); кадры на время раньше, чем за [KEEP_PAST] до «сейчас», и папки другого [FORMAT]
 * стираются ([tidy]). Любой сбой чтения или записи это просто «кадра нет». Система может стереть кэш сама, тогда кадр
 * скачивается заново.
 */
class ModelFrameDisk(private val root: File) {

    /** Сжатые уровни дождя кадра [width] × [height] на [time] из прогона [run]; null, если нет или испорчен (тогда файл удаляется). */
    fun read(run: Instant, time: Instant, width: Int, height: Int): ByteArray? =
        readFile(RAIN, run, time) { packed -> packed.takeIf { LevelPacking.unpack(it, width, height) != null } }

    /** Записывает кадр дождя; первая запись нового прогона заодно убирает старое ([tidy]). */
    fun write(run: Instant, time: Instant, packed: ByteArray, now: Instant) = writeFile(RAIN, run, time, packed, now)

    /** Кадр [length] байт вида [prefix] на [time] из прогона [run]; null, если нет или испорчен (тогда файл удаляется). */
    fun readFrame(prefix: String, run: Instant, time: Instant, length: Int): ByteArray? =
        readFile(prefix, run, time) { FrameDiskCache.inflate(it, length) }

    /** Записывает кадр вида [prefix] (сжимает сам). */
    fun writeFrame(prefix: String, run: Instant, time: Instant, frame: ByteArray, now: Instant) =
        writeFile(prefix, run, time, FrameDiskCache.deflate(frame), now)

    /** Самый поздний прогон, у которого на диске есть кадр вида [prefix] на [time]; null, если нет ни у одного. */
    fun newestRun(prefix: String, time: Instant): Instant? {
        val folders = root.listFiles() ?: return null
        return folders
            .filter { it.isDirectory }
            .mapNotNull { folder -> runOf(folder.name)?.takeIf { File(folder, fileName(prefix, time)).isFile } }
            .maxOrNull()
    }

    private fun <T : Any> readFile(prefix: String, run: Instant, time: Instant, decode: (ByteArray) -> T?): T? {
        val file = file(prefix, run, time)
        return try {
            if (!file.isFile) return null
            decode(file.readBytes()) ?: null.also { file.delete() }
        } catch (_: IOException) {
            null
        }
    }

    private fun writeFile(prefix: String, run: Instant, time: Instant, bytes: ByteArray, now: Instant) {
        val folder = folder(run)
        val fresh = !folder.isDirectory
        try {
            folder.mkdirs()
            val target = file(prefix, run, time)
            val tmp = File(folder, target.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (_: IOException) {
            // Диск полон или недоступен: кадр просто не сохранится.
        }
        if (fresh) tidy(now)
    }

    /**
     * Убирает папки чужого вида и всех прогонов, кроме [RUNS] последних, а в оставшихся кадры на время раньше, чем за
     * [KEEP_PAST] до [now] (прошедшее время показывает радар и сегодняшние кадры, кадры модели там больше не нужны;
     * час облаков, температуры и ветра округляется до ближайшего, поэтому текущий час ещё нужен).
     */
    @Synchronized
    fun tidy(now: Instant) {
        val folders = root.listFiles()?.toList() ?: return
        val ours = folders.filter { it.isDirectory && runOf(it.name) != null }.sortedByDescending { runOf(it.name) }
        (folders - ours.take(RUNS).toSet()).forEach { it.deleteRecursively() }
        val from = now.minus(KEEP_PAST)
        for (folder in ours.take(RUNS)) {
            folder.listFiles()?.forEach { file ->
                val time = timeOf(file.name)
                if (time == null || time.isBefore(from)) file.delete()
            }
        }
    }

    private fun folder(run: Instant) = File(root, FORMAT + "-" + Stamp.format(run))

    private fun file(prefix: String, run: Instant, time: Instant) = File(folder(run), fileName(prefix, time))

    companion object {
        /**
         * Вид кадров. Меняется, когда меняется расчёт кадра или размер картинки: тогда старые кадры не читаются
         * и стираются.
         */
        const val FORMAT = "v1"

        /** Сколько последних прогонов держать на диске. */
        const val RUNS = 2

        /** Сколько времени после своего часа кадр ещё лежит на диске. */
        val KEEP_PAST: Duration = Duration.ofHours(1)

        /** Префиксы файлов кадров дождя и облаков (у температуры и ветра [HourlyField.diskPrefix]). */
        const val RAIN = "rain"
        const val CLOUDS = "clouds"

        private val Pattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
        private val Stamp: DateTimeFormatter = Pattern.withZone(ZoneOffset.UTC)

        private fun fileName(prefix: String, time: Instant) = prefix + "-" + time.epochSecond + ".z"

        private fun runOf(name: String): Instant? {
            if (!name.startsWith("$FORMAT-")) return null
            return try {
                LocalDateTime.parse(name.removePrefix("$FORMAT-"), Pattern).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        private fun timeOf(name: String): Instant? {
            if (!name.endsWith(".z") || '-' !in name) return null
            return name.substringAfterLast('-').removeSuffix(".z").toLongOrNull()?.let { Instant.ofEpochSecond(it) }
        }
    }
}
