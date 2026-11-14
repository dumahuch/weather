package io.github.dumahuch.pogodaapp.map

import io.github.dumahuch.pogodaapp.weather.ForecastException
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Кадры модели ICON-D2 для карты (дождь по 15 минут после радара, облака, температура и ветер по часам): откуда берётся
 * кадр и как он лежит на диске ([ModelFrameDisk], по прогонам). Одно и то же делают карта на экране и утренняя
 * подгрузка ([MapPreload]), поэтому всё здесь, без Android.
 *
 * Порядок для кадра: 1) на диске есть кадр прогона, который ещё наверняка последний ([SURE_AGE] после его начала): берётся
 * сразу, без сети; 2) иначе список папок DWD показывает, из какого прогона брать сейчас (список держится 10 минут),
 * кадр этого прогона на диске берётся оттуда, нет его: скачивается и записывается; 3) сети нет или она оборвалась:
 * самый поздний кадр на диске, какой есть (лучше прежний прогноз, чем пустая карта), а если и его нет, ошибка идёт наверх.
 * Кадры, которые уже не меняются (прошедшие часы сегодняшних суток), лежат не здесь, а в [FrameDiskCache]: для них
 * вызывающий передаёт `keep = false`.
 */
class ModelFrames(
    private val fields: FieldLoader,
    private val rain: ModelRainLoader,
    private val disk: ModelFrameDisk,
    private val log: (String) -> Unit = {},
) {
    private val cloudHeight = MapArea.heightFor(MapArea.CLOUD_WIDTH)
    private val rainHeight = MapArea.heightFor(MapArea.RAIN_WIDTH)

    /** Байт в кадре облаков (непрозрачность пикселей). */
    val cloudLength: Int = MapArea.CLOUD_WIDTH * cloudHeight

    /** Убирает с диска старые прогоны и прошедшее время ([ModelFrameDisk.tidy]). */
    fun tidy(now: Instant) = disk.tidy(now)

    /** Облачность часа как непрозрачность пикселей ([CloudRaster.alphaOnly]); null, если на этот час данных нет. */
    fun clouds(hour: Instant, now: Instant, keep: Boolean = true): ByteArray? =
        hourly(ModelFrameDisk.CLOUDS, cloudLength, hour, now, listOf(IconD2Files.CLOUDS), keep) { grids ->
            val grid = CloudRaster.normalize(grids.first())
            CloudRaster.alphaOnly(CloudRaster.render(grid, MapArea.Overlay, MapArea.CLOUD_WIDTH, cloudHeight))
        }

    /** Готовый кадр часа температуры или ветра ([TemperatureField.encode], [WindField.encode]); null, если данных нет. */
    fun field(field: HourlyField, hour: Instant, now: Instant, keep: Boolean = true): ByteArray? = when (field) {
        HourlyField.Temperature ->
            hourly(field.diskPrefix, field.length, hour, now, listOf(IconD2Files.TEMPERATURE), keep) { TemperatureField.encode(it.first()) }
        HourlyField.Wind ->
            hourly(field.diskPrefix, field.length, hour, now, listOf(IconD2Files.WIND_U, IconD2Files.WIND_V), keep) {
                WindField.encode(it[0], it[1])
            }
    }

    /**
     * Дождь ICON-D2 на 15-минутный кадр [time] (сжатые уровни [LevelPacking]); null, если ICON-D2 на это время нет или файл
     * испорчен (тогда дождь берётся из ICON-EU). Звать только для времени после радара.
     */
    fun rain(time: Instant, now: Instant, keep: Boolean = true): ByteArray? {
        newestSure(ModelFrameDisk.RAIN, time, now) { run -> disk.read(run, time, MapArea.RAIN_WIDTH, rainHeight) }?.let { return it }
        try {
            val run = rain.runFor(time, now) ?: return null
            disk.read(run, time, MapArea.RAIN_WIDTH, rainHeight)?.let { return it }
            val frame = rain.load(time, now) ?: return null
            val packed = LevelPacking.pack(frame.levels, MapArea.RAIN_WIDTH)
            if (keep) disk.write(frame.run, time, packed, now)
            return packed
        } catch (e: MapDataException) {
            // Файл не того вида: дождь берётся из ICON-EU, причина в журнал.
            log("Осадки ICON-D2: ${e.message}")
            return null
        } catch (e: Exception) {
            if (!e.isNetwork()) throw e
            return newest(ModelFrameDisk.RAIN, time) { run -> disk.read(run, time, MapArea.RAIN_WIDTH, rainHeight) } ?: throw e
        }
    }

    /** Загружает на диск один кадр из списка подгрузки; сбой (нет файла, нет сети) уходит исключением. */
    fun preload(task: PreloadTask, now: Instant) {
        when (task.kind) {
            PreloadKind.Rain -> rain(task.time, now)
            PreloadKind.Clouds -> clouds(task.time, now)
            PreloadKind.Temperature -> field(HourlyField.Temperature, task.time, now)
            PreloadKind.Wind -> field(HourlyField.Wind, task.time, now)
        }
    }

    private fun hourly(
        prefix: String,
        length: Int,
        hour: Instant,
        now: Instant,
        names: List<String>,
        keep: Boolean,
        build: (List<LatLonGrid>) -> ByteArray,
    ): ByteArray? {
        val read = { run: Instant -> disk.readFrame(prefix, run, hour, length) }
        newestSure(prefix, hour, now, read)?.let { return it }
        try {
            val frame = fields.resolve(hour, now, names) ?: return newest(prefix, hour, read)
            read(frame.run)?.let { return it }
            val built = build(fields.fetch(frame, names))
            if (keep) disk.writeFrame(prefix, frame.run, hour, built, now)
            return built
        } catch (e: Exception) {
            if (!e.isNetwork()) throw e
            return newest(prefix, hour, read) ?: throw e
        }
    }

    /** Кадр самого позднего прогона на диске, если этот прогон начался не раньше [SURE_AGE] назад: новее на сервере ещё нет. */
    private fun newestSure(prefix: String, time: Instant, now: Instant, read: (Instant) -> ByteArray?): ByteArray? {
        val run = disk.newestRun(prefix, time) ?: return null
        if (Duration.between(run, now) >= SURE_AGE) return null
        return read(run)
    }

    /** Кадр самого позднего прогона на диске, какой бы он ни был. */
    private fun newest(prefix: String, time: Instant, read: (Instant) -> ByteArray?): ByteArray? =
        disk.newestRun(prefix, time)?.let(read)

    private fun Exception.isNetwork(): Boolean = this is IOException || this is ForecastException

    private companion object {
        /**
         * Сколько после начала прогона он наверняка ещё последний: DWD выкладывает первые часы нового прогона не раньше чем
         * через полтора часа после его начала, а прогоны идут раз в три часа.
         */
        val SURE_AGE: Duration = Duration.ofHours(4)
    }
}
