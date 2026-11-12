package io.github.dumahuch.pogodaapp.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dumahuch.pogodaapp.data.Bzip2
import io.github.dumahuch.pogodaapp.data.HttpJson
import io.github.dumahuch.pogodaapp.map.CloudLoader
import io.github.dumahuch.pogodaapp.map.CloudRaster
import io.github.dumahuch.pogodaapp.map.FolderListings
import io.github.dumahuch.pogodaapp.map.FrameDiskCache
import io.github.dumahuch.pogodaapp.map.LevelPacking
import io.github.dumahuch.pogodaapp.map.ModelFrameDisk
import io.github.dumahuch.pogodaapp.map.MapArea
import io.github.dumahuch.pogodaapp.map.MapDataException
import io.github.dumahuch.pogodaapp.map.MapDay
import io.github.dumahuch.pogodaapp.map.MapTimeline
import io.github.dumahuch.pogodaapp.map.ModelRainLoader
import io.github.dumahuch.pogodaapp.map.RainLoader
import io.github.dumahuch.pogodaapp.map.RainPalette
import io.github.dumahuch.pogodaapp.map.RainResult
import io.github.dumahuch.pogodaapp.map.RainSource
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Слой карты: облака и дождь вместе или только дождь. */
enum class MapLayer { CloudsAndRain, RainOnly }

/** Состояние кадра: грузится, показан, на это время данных нет, не удалось (нет связи или ответ испорчен). */
enum class FrameStatus { Loading, Ready, Missing, Failed }

data class MapUiState(
    val now: Instant,
    val day: MapDay,
    val time: Instant,
    val layer: MapLayer,
    val playing: Boolean,
    val rain: FrameStatus,
    val clouds: FrameStatus,
    /** Сколько кадров выбранных суток уже обработано заранее и сколько их всего; поровну, когда всё готово. */
    val framesDone: Int = 0,
    val framesTotal: Int = 0,
)

/** Картинки текущего кадра для слоёв карты; пусто, пока не загружены. */
class MapImages(val rain: Bitmap?, val clouds: Bitmap?)

/**
 * Карта: выбранное время и слой, загрузка кадров дождя (радар DWD, после него осадки ICON-D2 по 15 минут, где их нет,
 * ICON-EU по часам) и облаков (ICON-D2), «играть».
 *
 * Кадры держатся в оперативной памяти, пока живо приложение: дождь сжатым (PNG радара или уровни модели, сжатые
 * [LevelPacking], до [RAIN_CACHE] штук), облака одной
 * непрозрачностью (байт на пиксель, до [CLOUD_CACHE] часов, около 16 МБ). Уход с вкладки запас не очищает
 * (устаревшие кадры перекачиваются сами по срокам [RainLife] и [CloudLife]). Кадры, которые уже не меняются
 * (прошедший радар и облака прошедших часов сегодняшних суток), ещё и лежат на диске в кэше приложения до 23:55
 * ([FrameDiskCache]). Кадры дождя ICON-D2 лежат на диске по прогонам, пока прогон не сменился ([ModelFrameDisk]).
 * Цвета дождя меняет [RainPalette].
 *
 * Загрузки идут одновременно, каждый кадр один раз: если он уже качается (для показа или заранее), второй спрос
 * ждёт ту же загрузку. Кадры суток, на которых стоит шкала, подгружаются в фоне ([warmUp], порядок
 * [MapTimeline.nextToWarm]): готовый кадр шкала показывает сразу, а ещё не загруженный заказывается, когда палец остановился. При выборе
 * «Только по Wi-Fi» ([setWifiOnly]) в сети с оплатой за трафик ([metered]) заранее не качается ничего, только кадр,
 * который показан; «играть» качает свой круг всегда (его включают нарочно).
 */
class MapViewModel(
    userAgent: String,
    private val cacheRoot: File,
    modelRoot: File,
    private val metered: () -> Boolean,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val rainLoader = RainLoader { url ->
        HttpJson.bytes(url, userAgent, SOURCE, "image/png", allowClientErrors = true)
    }
    private val listings = FolderListings { url -> HttpJson.get(url, userAgent, SOURCE) }
    private val cloudLoader = CloudLoader(
        listings = listings,
        bytes = { url -> HttpJson.bytes(url, userAgent, SOURCE, "application/octet-stream") },
        unpack = Bzip2::unpack,
    )
    private val modelRainLoader = ModelRainLoader(
        listings = listings,
        bytes = { url -> HttpJson.bytes(url, userAgent, SOURCE, "application/octet-stream") },
        unpack = Bzip2::unpack,
    )

    /** Кадры дождя ICON-D2 по прогонам: лежат, пока прогон не сменился, и через полночь тоже. */
    private val modelDisk = ModelFrameDisk(modelRoot)

    /** Подгружать кадры заранее только без оплаты за трафик (настройка «Подгрузка карты»). */
    @Volatile
    private var wifiOnly = false

    private class Cached<T>(val value: T, val fetched: Instant)

    private val rainCache = lru<String, Cached<RainResult>>(RAIN_CACHE)
    private val cloudCache = lru<Instant, Cached<ByteArray?>>(CLOUD_CACHE)

    /** Кадры, которые качаются сейчас; запись живёт, пока идёт загрузка. */
    private val rainLoads = HashMap<String, Deferred<RainResult>>()
    private val cloudLoads = HashMap<Instant, Deferred<ByteArray?>>()

    /** Заранее кадры качаются по несколько сразу, чтобы не завалить сервер DWD запросами. */
    private val prefetchSlots = Semaphore(PREFETCH_PARALLEL)

    private val _state = MutableStateFlow(initialState(Instant.now()))
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val _images = MutableStateFlow(MapImages(null, null))
    val images: StateFlow<MapImages> = _images.asStateFlow()

    private var showJob: Job? = null
    private var playJob: Job? = null
    private var warmJob: Job? = null

    private fun initialState(now: Instant) = MapUiState(
        now = now,
        day = MapDay.Today,
        time = MapTimeline.anchor(now),
        layer = MapLayer.CloudsAndRain,
        playing = false,
        rain = FrameStatus.Loading,
        clouds = FrameStatus.Loading,
    )

    /** Карту открыли: если время стояло на «сейчас», оно сдвигается на новое «сейчас». */
    fun onShown() {
        viewModelScope.launch(Dispatchers.IO) {
            FrameDiskCache.purgeOtherDays(cacheRoot, LocalDate.now(zone))
            modelDisk.tidy(Instant.now())
        }
        val now = Instant.now()
        val old = _state.value
        val time = if (old.time == MapTimeline.anchor(old.now)) MapTimeline.anchor(now) else old.time
        val snapped = MapTimeline.snap(time, now, zone)
        _state.update { it.copy(now = now, time = snapped, day = MapTimeline.dayOf(snapped, now, zone)) }
        show()
        warmUp()
    }

    /** Настройка «Подгрузка карты»: заранее только по Wi-Fi или всегда. Действует со следующей подгрузки. */
    fun setWifiOnly(value: Boolean) {
        wifiOnly = value
    }

    private fun mayPrefetch(): Boolean = !wifiOnly || !metered()

    /** Карту закрыли: анимация и подгрузка останавливаются, запас кадров в памяти остаётся. */
    fun onHidden() {
        stopPlaying()
        showJob?.cancel()
        stopWarming()
    }

    fun selectDay(day: MapDay) {
        stopPlaying()
        val now = Instant.now()
        val time = MapTimeline.sameTimeOn(day, _state.value.time, now, zone)
        _state.update { it.copy(now = now, day = MapTimeline.dayOf(time, now, zone), time = time) }
        show()
        warmUp()
    }

    /**
     * Время со шкалы, пока палец ведёт её: ставится ближайшее допустимое. Готовый в памяти кадр показывается сразу
     * (с малой выдержкой [CACHED_SETTLE_MS], чтобы не разбирать картинку на каждый пиксель сдвига), ещё не
     * загруженный заказывается, когда палец постоял [SCRUB_SETTLE_MS] (или сразу после [scrubEnd]).
     */
    fun scrubTo(time: Instant) {
        stopPlaying()
        val s = _state.value
        val snapped = MapTimeline.snap(time, s.now, zone)
        if (snapped == s.time) return
        val day = MapTimeline.dayOf(snapped, s.now, zone)
        _state.update { it.copy(time = snapped, day = day) }
        show(if (isCached(snapped, s)) CACHED_SETTLE_MS else SCRUB_SETTLE_MS)
        // Шкала ушла через полночь на другие сутки: подгрузка переходит на них.
        if (day != s.day) warmUp()
    }

    /** Палец оторвали от шкалы (или нажали на неё): кадр на выбранное время заказывается сразу. */
    fun scrubEnd() = show()

    fun selectLayer(layer: MapLayer) {
        _state.update { it.copy(layer = layer) }
        show()
        warmUp()
    }

    fun togglePlay() {
        if (playJob?.isActive == true) {
            stopPlaying()
            return
        }
        val s = _state.value
        val frames = MapTimeline.playFrames(s.day, s.now, zone)
        if (frames.isEmpty()) return
        _state.update { it.copy(playing = true) }
        showJob?.cancel()
        stopWarming()
        playJob = viewModelScope.launch {
            val first = frames.indexOfFirst { !it.isBefore(s.time) }.coerceAtLeast(0)
            // Остальные кадры круга качаются заранее, по несколько сразу и в порядке показа: на экран кадр идёт из памяти.
            for (step in 1 until frames.size) {
                val frame = frames[(first + step) % frames.size]
                launch { prefetch(frame) }
            }
            var index = first
            while (isActive) {
                val time = frames[index]
                _state.update { it.copy(time = time, day = MapTimeline.dayOf(time, s.now, zone)) }
                withTimeoutOrNull(FRAME_WAIT_MS) { render(time) }
                delay(FRAME_MS)
                index = (index + 1) % frames.size
            }
        }
    }

    private fun stopPlaying() {
        playJob?.cancel()
        playJob = null
        if (_state.value.playing) _state.update { it.copy(playing = false) }
    }

    /**
     * Подгружает в фоне все кадры суток, на которых стоит шкала, и полтора часа по их краям (только в память;
     * ушла шкала за полночь, подгрузка начинается заново на новые сутки). Кадры берутся по одному из общего списка
     * [MapTimeline.nextToWarm]: рядом со шкалой, потом будущее, потом прошлое; порядок пересчитывается при каждом
     * выборе, так что очередь идёт за пальцем. Параллельно работают [PREFETCH_PARALLEL] загрузчиков.
     */
    private fun warmUp() {
        warmJob?.cancel()
        if (!mayPrefetch()) {
            stopWarming()
            return
        }
        val s = _state.value
        val frames = MapTimeline.warmFrames(s.day, s.now, zone)
        val pending = frames.toMutableSet()
        val done = AtomicInteger(0)
        _state.update { it.copy(framesDone = 0, framesTotal = frames.size) }
        warmJob = viewModelScope.launch {
            repeat(PREFETCH_PARALLEL) {
                launch {
                    while (mayPrefetch()) {
                        val next = synchronized(pending) {
                            MapTimeline.nextToWarm(pending, _state.value.time, s.now)?.also { pending.remove(it) }
                        } ?: break
                        prefetch(next)
                        val count = done.incrementAndGet()
                        _state.update { it.copy(framesDone = maxOf(it.framesDone, count)) }
                    }
                }
            }
        }
    }

    /** Подгрузка остановлена (карту закрыли, пошла анимация): счётчик кадров на экране не нужен. */
    private fun stopWarming() {
        warmJob?.cancel()
        warmJob = null
        _state.update { it.copy(framesDone = 0, framesTotal = 0) }
    }

    /** Кадр на [time] целиком в памяти (дождь и, если слой с облаками, облака) и ещё свежий. */
    private fun isCached(time: Instant, s: MapUiState): Boolean =
        cachedRain(time, s.now) != null && (s.layer != MapLayer.CloudsAndRain || cachedClouds(time) != null)

    private fun show(settleMs: Long = 0) {
        showJob?.cancel()
        showJob = viewModelScope.launch {
            if (settleMs > 0) delay(settleMs)
            render(_state.value.time)
        }
    }

    /**
     * Загружает и показывает кадр на [time]. Дождь и облака показываются каждый, как только готов, прежние картинки
     * остаются на экране, пока новые не пришли; кадр, на который время уже ушло, не показывается.
     */
    private suspend fun render(time: Instant) {
        val s = _state.value
        val withClouds = s.layer == MapLayer.CloudsAndRain
        _state.update { it.copy(rain = FrameStatus.Loading, clouds = if (withClouds) FrameStatus.Loading else it.clouds) }
        if (!withClouds) _images.update { MapImages(it.rain, null) }
        coroutineScope {
            launch { showRain(time, s.now) }
            if (withClouds) launch { showClouds(time, s.now) }
        }
    }

    private suspend fun showRain(time: Instant, now: Instant) {
        val frame = rainImage(time, now)
        if (_state.value.time != time) return
        _images.update { MapImages(frame.bitmap, it.clouds) }
        _state.update { it.copy(rain = frame.status) }
    }

    private suspend fun showClouds(time: Instant, now: Instant) {
        val (status, bitmap) = cloudImage(time, now)
        if (_state.value.time != time) return
        _images.update { MapImages(it.rain, bitmap) }
        _state.update { it.copy(clouds = status) }
    }

    /** Заранее кладёт кадр в запас (для «играть»); ошибка загрузки здесь не важна, кадр спросят ещё раз при показе. */
    private suspend fun prefetch(time: Instant) = prefetchSlots.withPermit {
        val now = _state.value.now
        quietly { rainResult(time, now) }
        if (_state.value.layer == MapLayer.CloudsAndRain) quietly { cloudPixels(time, now) }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (!e.isLoadFailure()) throw e
        }
    }

    private class RainFrame(val status: FrameStatus, val bitmap: Bitmap?)

    private suspend fun rainImage(time: Instant, now: Instant): RainFrame = try {
        when (val result = rainResult(time, now)) {
            RainResult.Missing -> RainFrame(FrameStatus.Missing, null)
            is RainResult.Image -> {
                val source = if (MapTimeline.fromRadar(time, now)) RainSource.Radar else RainSource.Model
                withContext(Dispatchers.Default) { decodeRain(result.png, source) }
                    ?.let { RainFrame(FrameStatus.Ready, it) }
                    ?: RainFrame(FrameStatus.Failed, null)
            }
            is RainResult.Levels -> withContext(Dispatchers.Default) { levelsBitmap(result.packed) }
                ?.let { RainFrame(FrameStatus.Ready, it) }
                ?: RainFrame(FrameStatus.Failed, null)
        }
    } catch (e: Exception) {
        if (!e.isLoadFailure()) throw e
        Log.w(TAG, "Дождь на карте: ${e.message}")
        RainFrame(FrameStatus.Failed, null)
    }

    private suspend fun cloudImage(time: Instant, now: Instant): Pair<FrameStatus, Bitmap?> = try {
        val alpha = cloudPixels(time, now)
        if (alpha == null) {
            FrameStatus.Missing to null
        } else {
            val bitmap = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(CloudRaster.fromAlpha(alpha), MapArea.CLOUD_WIDTH, cloudHeight, Bitmap.Config.ARGB_8888)
            }
            FrameStatus.Ready to bitmap
        }
    } catch (e: Exception) {
        if (!e.isLoadFailure()) throw e
        Log.w(TAG, "Облака на карте: ${e.message}")
        FrameStatus.Failed to null
    }

    private fun cachedRain(time: Instant, now: Instant): Cached<RainResult>? {
        val (key, frame) = rainKey(time, now)
        val cached = synchronized(rainCache) { rainCache[key] } ?: return null
        val life = if (MapTimeline.fromRadar(time, now)) RainLife else ModelLife
        return cached.takeIf { fresh(it.fetched, Instant.now(), frame, now, life) }
    }

    private fun cachedClouds(time: Instant): Cached<ByteArray?>? {
        val cached = synchronized(cloudCache) { cloudCache[MapTimeline.cloudHour(time)] } ?: return null
        return cached.takeIf { Duration.between(it.fetched, Instant.now()) < CloudLife }
    }

    /**
     * Ключ кадра дождя и его время; радар и модель могут дать кадр на одно и то же время, ключ различает источник.
     * Кадр модели свой на каждые 15 минут (ICON-D2); если на это время есть только ICON-EU, у четырёх четвертей часа
     * одна картинка, но её запрашивает один раз сам [RainLoader].
     */
    private fun rainKey(time: Instant, now: Instant): Pair<String, Instant> {
        val radar = MapTimeline.fromRadar(time, now)
        val frame = if (radar) MapTimeline.radarFrame(time) else time
        return (if (radar) "radar " else "model ") + frame to frame
    }

    private suspend fun rainResult(time: Instant, now: Instant): RainResult {
        cachedRain(time, now)?.let { return it.value }
        val key = rainKey(time, now).first
        return sharedLoad(rainLoads, key) {
            val name = rainDiskName(time, now)
            val disk = todayDisk(now)
            val result = name?.let { disk.readRain(it) }?.let { RainResult.Image(it) }
                ?: modelRain(time, now)
                ?: rainLoader.load(time, now).also { loaded ->
                    if (name != null && loaded is RainResult.Image) disk.writeRain(name, loaded.png)
                }
            synchronized(rainCache) { rainCache[key] = Cached(result, Instant.now()) }
            result
        }.await()
    }

    /**
     * Дождь ICON-D2 на 15-минутный кадр после радара (сжатые уровни); null на радаре, без ICON-D2 и при испорченном файле.
     * Какой прогон нужен, показывает список папки; кадр этого прогона уже на диске берётся оттуда, иначе считается из
     * файлов DWD и записывается на диск.
     */
    private fun modelRain(time: Instant, now: Instant): RainResult? {
        if (MapTimeline.fromRadar(time, now)) return null
        return try {
            val run = modelRainLoader.runFor(time, now) ?: return null
            modelDisk.read(run, time, MapArea.RAIN_WIDTH, rainHeight)?.let { return RainResult.Levels(it) }
            modelRainLoader.load(time, now)?.let { frame ->
                val packed = LevelPacking.pack(frame.levels, MapArea.RAIN_WIDTH)
                modelDisk.write(frame.run, time, packed, now)
                RainResult.Levels(packed)
            }
        } catch (e: MapDataException) {
            // Файл не того вида: дождь берётся из ICON-EU, причина в журнал.
            Log.w(TAG, "Осадки ICON-D2: ${e.message}")
            null
        }
    }

    /** Папка кадров на диске для суток, в которые попадает [now]. */
    private fun todayDisk(now: Instant): FrameDiskCache =
        FrameDiskCache(FrameDiskCache.dayDir(cacheRoot, LocalDate.ofInstant(now, zone)))

    /**
     * Имя файла на диске для кадра дождя на [time], если он уже не меняется и относится к сегодняшним суткам
     * (прошедший кадр радара, старше [SettledAfter]); иначе null.
     */
    private fun rainDiskName(time: Instant, now: Instant): String? {
        if (!MapTimeline.fromRadar(time, now)) return null
        val frame = MapTimeline.radarFrame(time)
        val today = MapTimeline.dayStart(MapDay.Today, now, zone)
        return if (frame.isBefore(now.minus(SettledAfter)) && !frame.isBefore(today)) FrameDiskCache.rainName(frame) else null
    }

    /** Облачность часа как непрозрачность пикселей ([CloudRaster.alphaOnly]); null, если на этот час данных нет. */
    private suspend fun cloudPixels(time: Instant, now: Instant): ByteArray? {
        cachedClouds(time)?.let { return it.value }
        val hour = MapTimeline.cloudHour(time)
        return sharedLoad(cloudLoads, hour) {
            val disk = if (cloudSettled(hour, now)) todayDisk(now) else null
            val alpha = disk?.readClouds(hour, cloudPixelCount)
                ?: cloudLoader.load(hour, now)?.let { grid ->
                    CloudRaster.alphaOnly(CloudRaster.render(grid, MapArea.Overlay, MapArea.CLOUD_WIDTH, cloudHeight))
                }?.also { disk?.writeClouds(hour, it) }
            synchronized(cloudCache) { cloudCache[hour] = Cached(alpha, Instant.now()) }
            alpha
        }.await()
    }

    /** Час облаков уже прошёл (с запасом) и относится к сегодняшним суткам: его данные больше не меняются. */
    private fun cloudSettled(hour: Instant, now: Instant): Boolean =
        hour.isBefore(MapTimeline.anchor(now).minus(CloudSettledAfter)) &&
            !hour.isBefore(MapTimeline.dayStart(MapDay.Today, now, zone))

    /**
     * Загрузка кадра [key] идёт на своём потоке и не зависит от того, кто её ждёт: если ждущего отменили (палец ушёл
     * дальше, «играть» остановили), загрузка дойдёт до конца и кадр попадёт в запас. Второй спрос того же кадра
     * ждёт ту же загрузку.
     */
    private fun <K, V> sharedLoad(loads: MutableMap<K, Deferred<V>>, key: K, block: () -> V): Deferred<V> =
        synchronized(loads) {
            loads[key] ?: viewModelScope.async(Dispatchers.IO) {
                try {
                    block()
                } finally {
                    synchronized(loads) { loads.remove(key) }
                }
            }.also { loads[key] = it }
        }

    /** Прошедшие кадры радара не меняются; недавние и будущие (прогноз) обновляются, когда им исполнится [life]. */
    private fun fresh(fetched: Instant, at: Instant, frame: Instant, now: Instant, life: Duration): Boolean =
        frame.isBefore(now.minus(SettledAfter)) || Duration.between(fetched, at) < life

    private val cloudHeight = MapArea.heightFor(MapArea.CLOUD_WIDTH)
    private val cloudPixelCount = MapArea.CLOUD_WIDTH * cloudHeight

    private val rainHeight = MapArea.heightFor(MapArea.RAIN_WIDTH)

    /** Уровни дождя ICON-D2 картинкой в наших цветах; картинка того же размера, что у радара. */
    private fun levelsBitmap(packed: ByteArray): Bitmap? {
        val levels = LevelPacking.unpack(packed, MapArea.RAIN_WIDTH, rainHeight) ?: return null
        return Bitmap.createBitmap(RainPalette.colorize(levels), MapArea.RAIN_WIDTH, rainHeight, Bitmap.Config.ARGB_8888)
    }

    /** Картинка DWD как Bitmap в наших цветах ([RainPalette.recolor]); null, если размер не тот. */
    private fun decodeRain(png: ByteArray, source: RainSource): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        if (bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, options) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        RainPalette.recolor(source, pixels)
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return bitmap
    }

    private fun Exception.isLoadFailure(): Boolean =
        this is ForecastException || this is MapDataException || this is IOException || this is IllegalArgumentException

    companion object {
        private const val TAG = "PogodaMap"
        private const val SOURCE = "DWD"
        private const val RAIN_CACHE = 260
        private const val CLOUD_CACHE = 26
        private const val PREFETCH_PARALLEL = 6
        private const val SCRUB_SETTLE_MS = 250L
        private const val CACHED_SETTLE_MS = 40L
        private const val FRAME_MS = 650L
        private const val FRAME_WAIT_MS = 6_000L
        private const val MAX_SIDE = 2048
        private val RainLife: Duration = Duration.ofMinutes(5)

        /** Прогон модели меняется раз в три часа, список папки держится 10 минут. */
        private val ModelLife: Duration = Duration.ofMinutes(10)
        private val CloudLife: Duration = Duration.ofMinutes(30)
        private val SettledAfter: Duration = Duration.ofMinutes(15)
        private val CloudSettledAfter: Duration = Duration.ofHours(1)

        private fun <K, V> lru(size: Int): LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(size, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = this.size > size
        }
    }
}
