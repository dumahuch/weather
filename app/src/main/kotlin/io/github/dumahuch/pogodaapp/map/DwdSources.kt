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

/** Пороги легенды дождя, мм/ч, и цвета ступеней (от слабого к сильному). */
object RainScale {
    val Thresholds = doubleArrayOf(0.1, 1.0, 5.0, 20.0)

    /** Цвета ступеней «от 0,1», «от 1», «от 5», «от 20», как в легенде. */
    val Colors = listOf("#8FD3FF", "#4AA3FF", "#3D56FF", "#9A4DFF")
}

/**
 * Карта осадков с сервера DWD (WMS, https://maps.dwd.de/geoserver/dwd/wms): одна картинка PNG на всю область
 * [MapArea.Overlay] в проекции карты. Цвета задаёт наш стиль (SLD), чтобы они совпадали с легендой; если сервер стиль
 * не примет, кадр просится в стиле DWD, а легенда прячется ([styled] = false).
 */
object DwdWms {
    private const val BASE = "https://maps.dwd.de/geoserver/dwd/wms"

    private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun mapUrl(source: RainSource, time: Instant, styled: Boolean, width: Int = MapArea.RAIN_WIDTH): String {
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
        if (styled) params["SLD_BODY"] = sld(source)
        return BASE + "?" + params.entries.joinToString("&") { (k, v) -> k + "=" + encode(v) }
    }

    /**
     * Стиль слоя: значения меньше 0,1 мм прозрачны, дальше четыре ступени легенды. У ColorMap вида «intervals» запись
     * красит значения ниже своего порога (и не ниже предыдущего).
     */
    fun sld(source: RainSource): String {
        val t = RainScale.Thresholds
        val c = RainScale.Colors
        val entries = buildString {
            append(entry("#000000", t[0], 0.0))
            for (k in 1 until t.size) append(entry(c[k - 1], t[k], OPACITY))
            append(entry(c.last(), TOP, OPACITY))
        }
        return "<StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\">" +
            "<NamedLayer><Name>${source.layer}</Name><UserStyle><FeatureTypeStyle><Rule><RasterSymbolizer>" +
            "<ColorMap type=\"intervals\">$entries</ColorMap>" +
            "</RasterSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>"
    }

    private const val OPACITY = 0.85
    private const val TOP = 100_000.0

    private fun entry(color: String, quantity: Double, opacity: Double): String =
        "<ColorMapEntry color=\"$color\" quantity=\"$quantity\" opacity=\"$opacity\"/>"

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

    /** Дальше прогон не считает. */
    const val MAX_LEAD = 48

    /** Сколько прогонов назад пробовать, если последний ещё не выложен. */
    private const val RUNS_BACK = 3

    private val Stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)

    fun runFolder(run: Instant): String = BASE + String.format(Locale.ROOT, "%02d", run.atOffset(ZoneOffset.UTC).hour) + "/clct_mod/"

    fun fileName(frame: IconD2Frame): String =
        "icon-d2_germany_regular-lat-lon_single-level_${Stamp.format(frame.run)}_" +
            String.format(Locale.ROOT, "%03d", frame.lead) + "_2d_clct_mod.grib2.bz2"

    /**
     * Прогоны, из которых можно взять час [hour], от лучшего к худшему: для прошедших часов ближайший прогон до этого
     * часа (короткий срок прогноза точнее), для будущих последний начавшийся прогон и до [RUNS_BACK] более ранних.
     */
    fun candidates(hour: Instant, now: Instant): List<IconD2Frame> {
        val latest = MapTimeline.floorTo(if (hour.isBefore(now)) hour else now, RunStep)
        return (0..RUNS_BACK).mapNotNull { back ->
            val run = latest.minus(RunStep.multipliedBy(back.toLong()))
            val lead = Duration.between(run, hour).toHours()
            if (lead in 0..MAX_LEAD.toLong()) IconD2Frame(run, lead.toInt()) else null
        }
    }

    /** Есть ли в списке папки (HTML сервера) файл этого кадра. */
    fun listed(listing: String, frame: IconD2Frame): Boolean = listing.contains("\"" + fileName(frame) + "\"")
}
