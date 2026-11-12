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
import io.github.dumahuch.pogodaapp.map.MapArea
import io.github.dumahuch.pogodaapp.map.MapDataException
import io.github.dumahuch.pogodaapp.map.MapDay
import io.github.dumahuch.pogodaapp.map.MapTimeline
import io.github.dumahuch.pogodaapp.map.RainLoader
import io.github.dumahuch.pogodaapp.map.RainResult
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
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
    /** Дождь нарисован в наших цветах: легенда ему соответствует. */
    val legendValid: Boolean,
)

/** Картинки текущего кадра для слоёв карты; пусто, пока не загружены. */
class MapImages(val rain: Bitmap?, val clouds: Bitmap?)

/**
 * Карта: выбранное время и слой, загрузка кадров дождя (радар и модель DWD) и облаков (ICON-D2), «играть».
 *
 * Кадры держатся только в оперативной памяти и только пока открыта вкладка: дождь сжатым (PNG, до [RAIN_CACHE]
 * штук), облака одной непрозрачностью (байт на пиксель, до [CLOUD_CACHE] часов, около 16 МБ). Уход с вкладки
 * очищает запас; на диск телефона ничего не пишется.
 *
 * Загрузки идут одновременно, каждый кадр один раз: если он уже качается (для показа или заранее), второй спрос
 * ждёт ту же загрузку. Кадры выбранных суток подгружаются в фоне, ближайшие к выбранному времени первыми
 * ([warmUp]): готовый кадр шкала показывает сразу, а ещё не загруженный заказывается, когда палец остановился.
 */
class MapViewModel(userAgent: String) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val rainLoader = RainLoader { url ->
        HttpJson.bytes(url, userAgent, SOURCE, "image/png", allowClientErrors = true)
    }
    private val cloudLoader = CloudLoader(
        text = { url -> HttpJson.get(url, userAgent, SOURCE) },
        bytes = { url -> HttpJson.bytes(url, userAgent, SOURCE, "application/octet-stream") },
        unpack = Bzip2::unpack,
    )

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
        legendValid = true,
    )

    /** Карту открыли: если время стояло на «сейчас», оно сдвигается на новое «сейчас». */
    fun onShown() {
        val now = Instant.now()
        val old = _state.value
        val time = if (old.day == MapDay.Today && old.time == MapTimeline.anchor(old.now)) MapTimeline.anchor(now) else old.time
        _state.update { it.copy(now = now, time = MapTimeline.snap(time, it.day, now, zone)) }
        show()
        warmUp()
    }

    /** Карту закрыли: анимация останавливается, запас кадров из памяти уходит. */
    fun onHidden() {
        stopPlaying()
        showJob?.cancel()
        warmJob?.cancel()
        synchronized(rainCache) { rainCache.clear() }
        synchronized(cloudCache) { cloudCache.clear() }
    }

    fun selectDay(day: MapDay) {
        stopPlaying()
        val now = Instant.now()
        _state.update { it.copy(now = now, day = day, time = MapTimeline.sameTimeOn(day, it.time, now, zone)) }
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
        val snapped = MapTimeline.snap(time, s.day, s.now, zone)
        if (snapped == s.time) return
        _state.update { it.copy(time = snapped) }
        show(if (isCached(snapped, s)) CACHED_SETTLE_MS else SCRUB_SETTLE_MS)
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
        warmJob?.cancel()
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
                _state.update { it.copy(time = time) }
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

    /** Подгружает в фоне все кадры выбранных суток, ближайшие к выбранному времени первыми (только в память). */
    private fun warmUp() {
        warmJob?.cancel()
        val s = _state.value
        warmJob = viewModelScope.launch {
            val frames = MapTimeline.dayFrames(s.day, s.now, zone).sortedBy { Duration.between(it, s.time).abs() }
            for (frame in frames) launch { prefetch(frame) }
        }
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
        _state.update { it.copy(rain = frame.status, legendValid = frame.styled ?: it.legendValid) }
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

    private class RainFrame(val status: FrameStatus, val bitmap: Bitmap?, val styled: Boolean?)

    private suspend fun rainImage(time: Instant, now: Instant): RainFrame = try {
        when (val result = rainResult(time, now)) {
            RainResult.Missing -> RainFrame(FrameStatus.Missing, null, null)
            is RainResult.Image -> withContext(Dispatchers.Default) { decodePng(result.png) }
                ?.let { RainFrame(FrameStatus.Ready, it, result.styled) }
                ?: RainFrame(FrameStatus.Failed, null, null)
        }
    } catch (e: Exception) {
        if (!e.isLoadFailure()) throw e
        Log.w(TAG, "Дождь на карте: ${e.message}")
        RainFrame(FrameStatus.Failed, null, null)
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
        return cached.takeIf { fresh(it.fetched, Instant.now(), frame, now) }
    }

    private fun cachedClouds(time: Instant): Cached<ByteArray?>? {
        val cached = synchronized(cloudCache) { cloudCache[MapTimeline.cloudHour(time)] } ?: return null
        return cached.takeIf { Duration.between(it.fetched, Instant.now()) < CloudLife }
    }

    /** Ключ кадра дождя и его время; радар и модель могут дать кадр на одно и то же время, ключ различает источник. */
    private fun rainKey(time: Instant, now: Instant): Pair<String, Instant> {
        val radar = MapTimeline.fromRadar(time, now)
        val frame = if (radar) MapTimeline.radarFrame(time) else MapTimeline.modelRainFrame(time)
        return (if (radar) "radar " else "model ") + frame to frame
    }

    private suspend fun rainResult(time: Instant, now: Instant): RainResult {
        cachedRain(time, now)?.let { return it.value }
        val key = rainKey(time, now).first
        return sharedLoad(rainLoads, key) {
            val result = rainLoader.load(time, now)
            synchronized(rainCache) { rainCache[key] = Cached(result, Instant.now()) }
            result
        }.await()
    }

    /** Облачность часа как непрозрачность пикселей ([CloudRaster.alphaOnly]); null, если на этот час данных нет. */
    private suspend fun cloudPixels(time: Instant, now: Instant): ByteArray? {
        cachedClouds(time)?.let { return it.value }
        val hour = MapTimeline.cloudHour(time)
        return sharedLoad(cloudLoads, hour) {
            val alpha = cloudLoader.load(hour, now)?.let { grid ->
                CloudRaster.alphaOnly(CloudRaster.render(grid, MapArea.Overlay, MapArea.CLOUD_WIDTH, cloudHeight))
            }
            synchronized(cloudCache) { cloudCache[hour] = Cached(alpha, Instant.now()) }
            alpha
        }.await()
    }

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

    /** Прошедшие кадры радара не меняются; недавние и будущие (прогноз) обновляются каждые 5 минут. */
    private fun fresh(fetched: Instant, at: Instant, frame: Instant, now: Instant): Boolean =
        frame.isBefore(now.minus(SettledAfter)) || Duration.between(fetched, at) < RainLife

    private val cloudHeight = MapArea.heightFor(MapArea.CLOUD_WIDTH)

    private fun decodePng(png: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        if (bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(png, 0, png.size, options)
    }

    private fun Exception.isLoadFailure(): Boolean =
        this is ForecastException || this is MapDataException || this is IOException || this is IllegalArgumentException

    companion object {
        private const val TAG = "PogodaMap"
        private const val SOURCE = "DWD"
        private const val RAIN_CACHE = 100
        private const val CLOUD_CACHE = 26
        private const val PREFETCH_PARALLEL = 4
        private const val SCRUB_SETTLE_MS = 250L
        private const val CACHED_SETTLE_MS = 40L
        private const val FRAME_MS = 650L
        private const val FRAME_WAIT_MS = 6_000L
        private const val MAX_SIDE = 2048
        private val RainLife: Duration = Duration.ofMinutes(5)
        private val CloudLife: Duration = Duration.ofMinutes(30)
        private val SettledAfter: Duration = Duration.ofMinutes(15)

        private fun <K, V> lru(size: Int): LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(size, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = this.size > size
        }
    }
}
