package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant

/**
 * Кадр дождя: картинка PNG в стиле DWD (радар и ICON-EU, цвета меняет [RainPalette]), уровни дождя ICON-D2
 * ([RainPalette.levelOf], сжатые [LevelPacking]) либо данных на это время нет.
 */
sealed interface RainResult {
    class Image(val png: ByteArray) : RainResult

    class Levels(val packed: ByteArray) : RainResult

    data object Missing : RainResult
}

/**
 * Загрузка кадра дождя картинкой DWD. До 2 часов вперёд радар, дальше модель ICON-EU по часам ([MapTimeline.fromRadar]);
 * она нужна только там, где нет ICON-D2 ([ModelRainLoader]). Ответ без картинки
 * (сервер вернул XML с ошибкой: на это время нет прогона или кадра) значит, что данных нет. Ошибка сети уходит наверх
 * исключением из [fetch]. Загрузки разных кадров идут одновременно, каждая со своим запросом.
 */
class RainLoader(private val fetch: (url: String) -> ByteArray) {

    private class Recent(val fetched: Instant, val result: RainResult)

    /**
     * Недавние кадры ICON-EU: у четырёх 15-минутных кадров часа он один, запрос на него уходит один раз в [RecentLife].
     */
    private val recent = object : LinkedHashMap<String, Recent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Recent>?): Boolean = size > RECENT
    }
    private val locks = HashMap<String, Any>()

    fun load(time: Instant, now: Instant): RainResult {
        val source = if (MapTimeline.fromRadar(time, now)) RainSource.Radar else RainSource.Model
        val frame = if (source == RainSource.Radar) MapTimeline.radarFrame(time) else MapTimeline.modelRainFrame(time)
        val url = DwdWms.mapUrl(source, frame)
        if (source == RainSource.Radar) return fetchFrame(url)
        val lock = synchronized(locks) { locks.getOrPut(url) { Any() } }
        return synchronized(lock) {
            val cached = synchronized(recent) { recent[url] }
            if (cached != null && Duration.between(cached.fetched, now).abs() < RecentLife) {
                cached.result
            } else {
                fetchFrame(url).also { result -> synchronized(recent) { recent[url] = Recent(now, result) } }
            }
        }
    }

    private fun fetchFrame(url: String): RainResult {
        val png = fetch(url)
        return if (DwdWms.isPng(png)) RainResult.Image(png) else RainResult.Missing
    }

    private companion object {
        const val RECENT = 8
        val RecentLife: Duration = Duration.ofMinutes(10)
    }
}

/**
 * Загрузка облачности на час: сначала ICON-D2, где его срок кончился, ICON-EU ([IconEuFiles], сетка грубее).
 * Список файлов папки прогона показывает, выложен ли нужный час (список держится 10 минут), файл скачивается,
 * распаковывается ([unpack], bzip2) и разбирается. Нет ни в одном подходящем прогоне: null. Разные часы грузятся
 * одновременно; список папки общий ([FolderListings]).
 */
class CloudLoader(
    private val listings: FolderListings,
    private val bytes: (url: String) -> ByteArray,
    private val unpack: (ByteArray) -> ByteArray,
) {
    fun load(hour: Instant, now: Instant): LatLonGrid? {
        for (frame in IconD2Files.candidates(hour, now)) {
            val folder = IconD2Files.runFolder(frame.run)
            if (!IconD2Files.listed(listings.get(folder, now), frame)) continue
            val grib = unpack(bytes(folder + IconD2Files.fileName(frame)))
            return CloudRaster.normalize(Grib2.parse(grib))
        }
        for (run in IconEuFiles.runs(hour, now)) {
            val folder = IconEuFiles.folder(run, IconEuFiles.CLOUDS)
            val name = IconEuFiles.fileName(run, IconEuFiles.lead(run, hour), IconEuFiles.CLOUDS)
            if (!IconEuFiles.listed(listings.get(folder, now), name)) continue
            return CloudRaster.normalize(Grib2.parse(unpack(bytes(folder + name))))
        }
        return null
    }
}
