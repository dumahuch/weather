package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant

/** Кадр дождя: картинка PNG и признак, что она в наших цветах (тогда легенда верна), либо данных на это время нет. */
sealed interface RainResult {
    class Image(val png: ByteArray, val styled: Boolean) : RainResult

    data object Missing : RainResult
}

/**
 * Загрузка кадра дождя. До 2 часов вперёд радар, дальше модель ([MapTimeline.fromRadar]). Сначала просится кадр
 * в наших цветах; если сервер стиль не принял (ответ не картинка), кадр берётся в цветах DWD, и для этого слоя
 * больше стиль не просится. Ошибка сети уходит наверх исключением из [fetch].
 */
class RainLoader(private val fetch: (url: String) -> ByteArray) {

    /** Принимает ли сервер наш стиль для слоя: пока не знаем, null. */
    private val styleAccepted = HashMap<RainSource, Boolean>()

    @Synchronized
    fun load(time: Instant, now: Instant): RainResult {
        val source = if (MapTimeline.fromRadar(time, now)) RainSource.Radar else RainSource.Model
        val frame = if (source == RainSource.Radar) MapTimeline.radarFrame(time) else MapTimeline.modelRainFrame(time)
        val accepted = styleAccepted[source]
        if (accepted != false) {
            val styled = fetch(DwdWms.mapUrl(source, frame, styled = true))
            if (DwdWms.isPng(styled)) {
                styleAccepted[source] = true
                return RainResult.Image(styled, styled = true)
            }
            // Стиль уже работал: значит, на это время у сервера просто нет кадра.
            if (accepted == true) return RainResult.Missing
        }
        val plain = fetch(DwdWms.mapUrl(source, frame, styled = false))
        if (!DwdWms.isPng(plain)) return RainResult.Missing
        if (accepted == null) styleAccepted[source] = false
        return RainResult.Image(plain, styled = false)
    }
}

/**
 * Загрузка облачности ICON-D2 на час: список файлов папки прогона показывает, выложен ли нужный час (список
 * держится 10 минут), файл скачивается, распаковывается ([unpack], bzip2) и разбирается. Нет ни в одном
 * подходящем прогоне: null.
 */
class CloudLoader(
    private val text: (url: String) -> String,
    private val bytes: (url: String) -> ByteArray,
    private val unpack: (ByteArray) -> ByteArray,
) {
    private class Listing(val fetched: Instant, val body: String)

    private val listings = HashMap<String, Listing>()

    @Synchronized
    fun load(hour: Instant, now: Instant): LatLonGrid? {
        for (frame in IconD2Files.candidates(hour, now)) {
            val folder = IconD2Files.runFolder(frame.run)
            if (!IconD2Files.listed(listing(folder, now), frame)) continue
            val grib = unpack(bytes(folder + IconD2Files.fileName(frame)))
            return CloudRaster.normalize(Grib2.parse(grib))
        }
        return null
    }

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
