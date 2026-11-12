package io.github.dumahuch.pogodaapp.map

import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Откуда берётся дождь на выбранное время. */
enum class RainSource(val layer: String) {
    /** Радар DWD, мм/ч, шаг 5 минут, прогноз на 2 часа. */
    Radar("Niederschlagsradar"),

    /** Модель ICON-EU, сумма осадков за час (мм), дальше 2 часов. */
    Model("Icon-eu_reg00625_fd_sl_TOTPREC01H"),
}

/**
 * Карта осадков с сервера DWD (WMS, https://maps.dwd.de/geoserver/dwd/wms): одна картинка PNG на всю область
 * [MapArea.Overlay] в проекции карты, в стиле самого DWD. Свои стили сервер запрещает («Dynamic style usage is
 * forbidden», проверено 12.11.2026), поэтому цвета ступеней меняет на телефоне [RainPalette].
 */
object DwdWms {
    private const val BASE = "https://maps.dwd.de/geoserver/dwd/wms"

    private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun mapUrl(source: RainSource, time: Instant, width: Int = MapArea.RAIN_WIDTH): String {
        val box = MapArea.Overlay
        val bbox = listOf(Mercator.x(box.west), Mercator.y(box.south), Mercator.x(box.east), Mercator.y(box.north))
            .joinToString(",") { String.format(Locale.ROOT, "%.1f", it) }
        val params = linkedMapOf(
            "SERVICE" to "WMS",
            "VERSION" to "1.3.0",
            "REQUEST" to "GetMap",
            "LAYERS" to source.layer,
            "STYLES" to "",
            "CRS" to "EPSG:3857",
            "BBOX" to bbox,
            "WIDTH" to width.toString(),
            "HEIGHT" to MapArea.heightFor(width).toString(),
            "FORMAT" to "image/png",
            "TRANSPARENT" to "TRUE",
            "EXCEPTIONS" to "XML",
            "TIME" to TimeFormat.format(time),
        )
        return BASE + "?" + params.entries.joinToString("&") { (k, v) -> k + "=" + encode(v) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /** Истина, если ответ это картинка PNG (а не сообщение об ошибке в XML). */
    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
}

/** Прогон модели ICON-D2: время начала (каждые 3 часа) и номер часа прогноза. */
data class IconD2Frame(val run: Instant, val lead: Int)

/**
 * Файлы облачности ICON-D2 на открытом сервере DWD:
 * `https://opendata.dwd.de/weather/nwp/icon-d2/grib/HH/clct_mod/`, в папке прогона только последний прогон этого часа
 * (каждые сутки перезаписывается). Какие часы прогноза уже выложены, видно по списку файлов в папке.
 */
object IconD2Files {
    private const val BASE = "https://opendata.dwd.de/weather/nwp/icon-d2/grib/"
    private val RunStep: Duration = Duration.ofHours(3)

    /** Обычный прогон считает на 27 часов вперёд, прогон 03 UTC на 45 (описание ICON-D2 в каталоге DWD). */
    private const val LEAD_SHORT = 27
    private const val LEAD_LONG = 45
    private const val LONG_RUN_HOUR = 3

    /** Дальше прогон [run] не считает. */
    fun maxLead(run: Instant): Int = if (run.atOffset(ZoneOffset.UTC).hour == LONG_RUN_HOUR) LEAD_LONG else LEAD_SHORT

    /** Сколько прогонов назад пробовать, если последний ещё не выложен. */
    private const val RUNS_BACK = 3

    private val Stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)

    fun runFolder(run: Instant): String = BASE + String.format(Locale.ROOT, "%02d", run.atOffset(ZoneOffset.UTC).hour) + "/clct_mod/"

    fun fileName(frame: IconD2Frame): String =
        "icon-d2_germany_regular-lat-lon_single-level_${Stamp.format(frame.run)}_" +
            String.format(Locale.ROOT, "%03d", frame.lead) + "_2d_clct_mod.grib2.bz2"

    /**
     * Прогоны, из которых можно взять час [hour], от лучшего к худшему: для прошедших часов ближайший прогон до этого
     * часа (короткий срок прогноза точнее), для будущих последний начавшийся прогон и до [RUNS_BACK] более ранних;
     * в конце последний прогон 03 UTC, он единственный считает дальше 27 часов (до 45). Кадр попадает в список,
     * только если час в пределах срока своего прогона.
     */
    fun candidates(hour: Instant, now: Instant): List<IconD2Frame> {
        val base = if (hour.isBefore(now)) hour else now
        val latest = MapTimeline.floorTo(base, RunStep)
        val runs = (0..RUNS_BACK).map { back -> latest.minus(RunStep.multipliedBy(back.toLong())) }.toMutableList()
        val dayStart = MapTimeline.floorTo(base, Duration.ofDays(1))
        var long = dayStart.plus(Duration.ofHours(LONG_RUN_HOUR.toLong()))
        if (long.isAfter(base)) long = long.minus(Duration.ofDays(1))
        if (long !in runs) runs += long
        return runs.mapNotNull { run ->
            val lead = Duration.between(run, hour).toHours()
            if (lead in 0..maxLead(run).toLong()) IconD2Frame(run, lead.toInt()) else null
        }
    }

    /** Есть ли в списке папки (HTML сервера) файл этого кадра. */
    fun listed(listing: String, frame: IconD2Frame): Boolean = listing.contains("\"" + fileName(frame) + "\"")
}
