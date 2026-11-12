package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant

/** Кадр дождя: картинка PNG в стиле DWD (цвета меняет [RainPalette]) либо данных на это время нет. */
sealed interface RainResult {
    class Image(val png: ByteArray) : RainResult

    data object Missing : RainResult
}

/**
 * Загрузка кадра дождя. До 2 часов вперёд радар, дальше модель ([MapTimeline.fromRadar]). Ответ без картинки
 * (сервер вернул XML с ошибкой: на это время нет прогона или кадра) значит, что данных нет. Ошибка сети уходит наверх
 * исключением из [fetch]. Загрузки разных кадров идут одновременно, каждая со своим запросом.
 */
class RainLoader(private val fetch: (url: String) -> ByteArray) {

    fun load(time: Instant, now: Instant): RainResult {
        val source = if (MapTimeline.fromRadar(time, now)) RainSource.Radar else RainSource.Model
        val frame = if (source == RainSource.Radar) MapTimeline.radarFrame(time) else MapTimeline.modelRainFrame(time)
        val png = fetch(DwdWms.mapUrl(source, frame))
        return if (DwdWms.isPng(png)) RainResult.Image(png) else RainResult.Missing
    }
}

/**
 * Загрузка облачности ICON-D2 на час: список файлов папки прогона показывает, выложен ли нужный час (список
 * держится 10 минут), файл скачивается, распаковывается ([unpack], bzip2) и разбирается. Нет ни в одном
 * подходящем прогоне: null. Разные часы грузятся одновременно; одновременно только список папки берётся один раз.
 */
class CloudLoader(
    private val text: (url: String) -> String,
    private val bytes: (url: String) -> ByteArray,
    private val unpack: (ByteArray) -> ByteArray,
) {
    private class Listing(val fetched: Instant, val body: String)

    private val listings = HashMap<String, Listing>()

    fun load(hour: Instant, now: Instant): LatLonGrid? {
        for (frame in IconD2Files.candidates(hour, now)) {
            val folder = IconD2Files.runFolder(frame.run)
            if (!IconD2Files.listed(listing(folder, now), frame)) continue
            val grib = unpack(bytes(folder + IconD2Files.fileName(frame)))
            return CloudRaster.normalize(Grib2.parse(grib))
        }
        return null
    }

    /** Список папки качается под замком: несколько часов, начатых вместе, спрашивают его один раз. */
    @Synchronized
    private fun listing(folder: String, now: Instant): String {
        val cached = listings[folder]
        if (cached != null && Duration.between(cached.fetched, now) < ListingLife && !cached.fetched.isAfter(now)) {
            return cached.body
        }
        val body = text(folder)
        listings[folder] = Listing(now, body)
        return body
    }

    private companion object {
        val ListingLife: Duration = Duration.ofMinutes(10)
    }
}
