package io.github.dumahuch.pogodaapp.map

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Кадры карты за сегодня на диске, в кэше приложения (система может стереть его сама, нам это не мешает: недостающий кадр
 * просто скачивается заново). Лежат только кадры, которые уже не меняются: прошедшие кадры радара и облака прошедших
 * часов. Каждые сутки в своей папке `<корень>/<дата>`; папки прошлых суток стираются при запуске и при открытии карты
 * ([purgeOtherDays]), сегодняшняя около 23:55 ([purgeAll] из задачи системы). Любой сбой чтения или записи
 * это просто «кадра нет».
 */
class FrameDiskCache(private val dir: File) {

    /** Кадр дождя (PNG DWD) по имени [name]; null, если его нет или файл испорчен (тогда он удаляется). */
    fun readRain(name: String): ByteArray? = read(File(dir, name)) { DwdWms.isPng(it) }

    fun writeRain(name: String, png: ByteArray) = write(File(dir, name), png)

    /** Облачность часа [hour] (непрозрачность пикселей, [length] байт); null, если нет или испорчена. */
    fun readClouds(hour: Instant, length: Int): ByteArray? {
        val file = File(dir, cloudName(hour))
        val packed = read(file) { true } ?: return null
        val alpha = inflate(packed, length)
        if (alpha == null) file.delete()
        return alpha
    }

    fun writeClouds(hour: Instant, alpha: ByteArray) = write(File(dir, cloudName(hour)), deflate(alpha))

    private fun read(file: File, valid: (ByteArray) -> Boolean): ByteArray? = try {
        if (!file.isFile) {
            null
        } else {
            file.readBytes().takeIf(valid) ?: run { file.delete(); null }
        }
    } catch (_: IOException) {
        null
    }

    /** Запись через временный файл, чтобы оборванная запись не оставила половину кадра. */
    private fun write(file: File, bytes: ByteArray) {
        try {
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (_: IOException) {
            // Диск полон или недоступен: кадр просто не сохранится.
        }
    }

    companion object {
        /** Когда задача очистки удаляет сегодняшние кадры. */
        val CleanupTime: LocalTime = LocalTime.of(23, 55)

        /** Имя файла кадра радара на время [frame]. */
        fun rainName(frame: Instant): String = "radar-" + frame.epochSecond + ".png"

        private fun cloudName(hour: Instant): String = "clouds-" + hour.epochSecond + ".z"

        /** Папка суток [day] внутри [root]. */
        fun dayDir(root: File, day: LocalDate): File = File(root, day.toString())

        /** Стирает всё, что лежит в [root], кроме папки суток [today]. */
        fun purgeOtherDays(root: File, today: LocalDate) {
            val keep = today.toString()
            root.listFiles()?.filter { it.name != keep }?.forEach { it.deleteRecursively() }
        }

        /** Стирает всё, что лежит в [root]. */
        fun purgeAll(root: File) {
            root.listFiles()?.forEach { it.deleteRecursively() }
        }

        /** Сколько осталось до ближайших 23:55 по местному времени. */
        fun untilCleanup(now: ZonedDateTime): Duration {
            var at = now.toLocalDate().atTime(CleanupTime).atZone(now.zone)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return Duration.between(now, at)
        }

        /** Сжатие кадра (deflate, по умолчанию быстро): дождь и облака почти везде нули и жмутся в десятки раз. */
        fun deflate(bytes: ByteArray, level: Int = Deflater.BEST_SPEED): ByteArray {
            val deflater = Deflater(level)
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 8 + 64)
            val buffer = ByteArray(BUFFER)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            deflater.end()
            return out.toByteArray()
        }

        /** Распаковка ровно в [length] байт; null, если данные испорчены или короче. */
        fun inflate(bytes: ByteArray, length: Int): ByteArray? {
            val inflater = Inflater()
            return try {
                inflater.setInput(bytes)
                val result = ByteArray(length)
                var done = 0
                while (done < length && !inflater.finished()) {
                    val n = inflater.inflate(result, done, length - done)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    done += n
                }
                if (done == length) result else null
            } catch (_: DataFormatException) {
                null
            } finally {
                inflater.end()
            }
        }

        private const val BUFFER = 16 * 1024
    }
}
