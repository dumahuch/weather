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
 * Кадры держатся в памяти: картинки дождя сжатыми (PNG, до [RAIN_CACHE] штук), облака готовыми пикселями (до
 * [CLOUD_CACHE] часов). На телефон ничего не пишется.
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
    private val cloudCache = lru<Instant, Cached<IntArray?>>(CLOUD_CACHE)

    private val _state = MutableStateFlow(initialState(Instant.now()))
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val _images = MutableStateFlow(MapImages(null, null))
    val images: StateFlow<MapImages> = _images.asStateFlow()

    private var showJob: Job? = null
    private var playJob: Job? = null

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
    }

    /** Карту закрыли: анимация останавливается. */
    fun onHidden() = stopPlaying()

    fun selectDay(day: MapDay) {
        stopPlaying()
        val now = Instant.now()
        _state.update { it.copy(now = now, day = day, time = MapTimeline.sameTimeOn(day, it.time, now, zone)) }
        show()
    }

    /** Время со шкалы (пока палец ведёт её, и после отпускания): ставится ближайшее допустимое. */
    fun scrubTo(time: Instant) {
        stopPlaying()
        val s = _state.value
        val snapped = MapTimeline.snap(time, s.day, s.now, zone)
        if (snapped == s.time) return
        _state.update { it.copy(time = snapped) }
        show()
    }

    fun selectLayer(layer: MapLayer) {
        _state.update { it.copy(layer = layer) }
        show()
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
        playJob = viewModelScope.launch {
            var index = frames.indexOfFirst { !it.isBefore(s.time) }.coerceAtLeast(0)
            while (isActive) {
                val time = frames[index]
                _state.update { it.copy(time = time) }
                withTimeoutOrNull(FRAME_WAIT_MS) { render(time) }
                frames.getOrNull(index + 1)?.let { next -> launch { prefetch(next) } }
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

    private fun show() {
        showJob?.cancel()
        val time = _state.value.time
        showJob = viewModelScope.launch { render(time) }
    }

    /** Загружает и показывает кадр на [time]; прежние картинки остаются на экране, пока новые не готовы. */
    private suspend fun render(time: Instant) {
        val s = _state.value
        val withClouds = s.layer == MapLayer.CloudsAndRain
        _state.update { it.copy(rain = FrameStatus.Loading, clouds = if (withClouds) FrameStatus.Loading else it.clouds) }
        val (rainFrame, cloudFrame) = coroutineScope {
            val rain = async(Dispatchers.Default) { rainImage(time, s.now) }
            val clouds = if (withClouds) async(Dispatchers.Default) { cloudImage(time, s.now) } else null
            rain.await() to clouds?.await()
        }
        val (rainStatus, rainBitmap, styled) = rainFrame
        val (cloudStatus, cloudBitmap) = cloudFrame ?: (s.clouds to _images.value.clouds)
        if (_state.value.time != time) return
        _images.value = MapImages(rainBitmap, if (withClouds) cloudBitmap else null)
        _state.update {
            it.copy(rain = rainStatus, clouds = cloudStatus, legendValid = styled ?: it.legendValid)
        }
    }

    private suspend fun prefetch(time: Instant) {
        val now = _state.value.now
        withContext(Dispatchers.IO) {
            runCatching { rainResult(time, now) }
            if (_state.value.layer == MapLayer.CloudsAndRain) runCatching { cloudPixels(time, now) }
        }
    }

    private data class RainFrame(val status: FrameStatus, val bitmap: Bitmap?, val styled: Boolean?)

    private suspend fun rainImage(time: Instant, now: Instant): RainFrame = try {
        when (val result = withContext(Dispatchers.IO) { rainResult(time, now) }) {
            RainResult.Missing -> RainFrame(FrameStatus.Missing, null, null)
            is RainResult.Image -> decodePng(result.png)?.let { RainFrame(FrameStatus.Ready, it, result.styled) }
                ?: RainFrame(FrameStatus.Failed, null, null)
        }
    } catch (e: Exception) {
        if (!e.isLoadFailure()) throw e
        Log.w(TAG, "Дождь на карте: ${e.message}")
        RainFrame(FrameStatus.Failed, null, null)
    }

    private suspend fun cloudImage(time: Instant, now: Instant): Pair<FrameStatus, Bitmap?> = try {
        val pixels = withContext(Dispatchers.IO) { cloudPixels(time, now) }
        if (pixels == null) {
            FrameStatus.Missing to null
        } else {
            FrameStatus.Ready to Bitmap.createBitmap(pixels, MapArea.CLOUD_WIDTH, cloudHeight, Bitmap.Config.ARGB_8888)
        }
    } catch (e: Exception) {
        if (!e.isLoadFailure()) throw e
        Log.w(TAG, "Облака на карте: ${e.message}")
        FrameStatus.Failed to null
    }

    private fun rainResult(time: Instant, now: Instant): RainResult {
        val radar = MapTimeline.fromRadar(time, now)
        val frame = if (radar) MapTimeline.radarFrame(time) else MapTimeline.modelRainFrame(time)
        // Радар и модель могут дать кадр на одно и то же время: ключ различает источник.
        val key = (if (radar) "radar " else "model ") + frame
        val fetchedNow = Instant.now()
        synchronized(rainCache) { rainCache[key] }?.let { cached ->
            if (fresh(cached.fetched, fetchedNow, frame, now)) return cached.value
        }
        val result = rainLoader.load(time, now)
        synchronized(rainCache) { rainCache[key] = Cached(result, fetchedNow) }
        return result
    }

    private fun cloudPixels(time: Instant, now: Instant): IntArray? {
        val hour = MapTimeline.cloudHour(time)
        val fetchedNow = Instant.now()
        synchronized(cloudCache) { cloudCache[hour] }?.let { cached ->
            if (Duration.between(cached.fetched, fetchedNow) < CloudLife) return cached.value
        }
        val pixels = cloudLoader.load(hour, now)?.let { grid ->
            CloudRaster.render(grid, MapArea.Overlay, MapArea.CLOUD_WIDTH, cloudHeight)
        }
        synchronized(cloudCache) { cloudCache[hour] = Cached(pixels, fetchedNow) }
        return pixels
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
        private const val RAIN_CACHE = 40
        private const val CLOUD_CACHE = 6
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
