package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.sky.SkyDrift
import io.github.dumahuch.pogodaapp.sky.SkyMaps
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import io.github.dumahuch.pogodaapp.sky.SkyPalette
import io.github.dumahuch.pogodaapp.sky.SkySampler
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import io.github.dumahuch.pogodaapp.sky.SkyThemeDriver
import io.github.dumahuch.pogodaapp.sky.SunPosition
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.rememberGlass
import io.github.dumahuch.pogodaapp.ui.mixWith
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt
import java.time.Instant
import kotlin.math.PI

/** Что нужно небу от экрана: где мы (для положения солнца) и какая сейчас погода. */
internal data class SkyInput(val latitude: Double, val longitude: Double, val condition: Condition)

/** Небо показывается только когда есть прогноз; вид погоды берётся из первого часа ленты. */
internal fun HomeUiState.skyInput(): SkyInput? {
    val content = this as? HomeUiState.Content ?: return null
    return SkyInput(
        latitude = content.place.latitude,
        longitude = content.place.longitude,
        condition = content.strip.firstOrNull()?.condition ?: content.forecast.current.condition,
    )
}

/** Что видно на экране проверки: положение солнца, доли картин, облака и читаемость текста. */
@Stable
internal class SkyDiagnostics {

    class Snapshot(
        val elevation: Double,
        val solarHour: Double,
        val weights: List<Pair<SkyScene, Double>>,
        val cloudPosition: Double,
        val darkInk: Boolean,
        val hero: Double,
        val cards: Double,
        val buttons: Double,
        val load: SkyBitmapStore.LoadTiming?,
    )

    var snapshot by mutableStateOf<Snapshot?>(null)
        private set

    fun publish(sky: SkyState, palette: SkyPalette, cloudPosition: Double, load: SkyBitmapStore.LoadTiming?) {
        snapshot = Snapshot(
            elevation = sky.sun.elevation,
            solarHour = sky.sun.solarHour,
            weights = sky.weights.visible().map { it to sky.weights[it] },
            cloudPosition = cloudPosition,
            darkInk = palette.darkInk,
            hero = palette.contrast.hero,
            cards = palette.contrast.cards,
            buttons = palette.contrast.buttons,
            load = load,
        )
    }
}

private const val DRIFT_STEP_MILLIS = 50L
private const val THEME_STEP_MILLIS = 250L
private const val THEME_FAST_STEP_MILLIS = 33L
private const val REVEAL_MILLIS = 600

/** Через сколько после ухода с экрана картины выгружаются из памяти (две минуты). */
private const val UNLOAD_DELAY_MILLIS = 120_000L
private const val TWO_PI = 2 * PI

/** Секунд «прошло» для смены цвета текста, когда анимации выключены: переключение происходит сразу. */
private const val INSTANT_SECONDS = 10.0

private const val TAG = "PogodaSky"

/**
 * Хозяин неба: считает небо по месту, времени и погоде, рисует его под экраном и раздаёт экрану цвета
 * ([LocalPogodaPalette], [LocalContentColor]). Пока прогноза нет или картины не загрузились, фон спокойный
 * дневной градиент шага 1; небо проявляется за 0,6 с, и цвета интерфейса переходят вместе с ним.
 *
 * Цвета интерфейса пересчитываются по карте яркости 4 раза в секунду (быстрее, пока меняется цвет текста),
 * облака сдвигаются 20 раз в секунду. Если в системе выключены анимации или включена экономия энергии,
 * облака стоят на месте и цвета считаются один раз на каждое состояние неба.
 */
@Composable
internal fun SkyHost(
    input: SkyInput?,
    override: SkyOverride,
    diagnostics: SkyDiagnostics?,
    onDarkIcons: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    glassBlur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val maps by produceState<SkyMaps?>(null, appContext) {
        value = withContext(Dispatchers.IO) { loadMaps(appContext) }
    }
    val store = remember(appContext) { SkyBitmapStore(appContext) }
    val animationsOn by rememberAnimationsEnabled()
    var started by remember { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    val now by rememberNow(started)
    val sky = remember(input, override, now) { input?.let { buildSky(it, override, now) } }

    // Картины в памяти: ровно те, что нужны сейчас; когда приложение ушло с экрана надолго, память освобождается.
    val scenes = remember(sky) { sky?.weights?.visible()?.toSet().orEmpty() }
    LaunchedEffect(scenes, started) {
        if (started) {
            store.ensure(scenes)
        } else {
            // Короткий уход (свайп между приложениями) картины не трогает: иначе при возвращении небо мигало бы нейтральным светлым фоном.
            delay(UNLOAD_DELAY_MILLIS)
            store.clear()
        }
    }

    var phase by remember { mutableDoubleStateOf(SkyDrift.START_PHASE) }
    LaunchedEffect(animationsOn, started, sky?.weather) {
        if (!animationsOn || !started) return@LaunchedEffect
        val speed = sky?.weather?.let(SkyDrift::speed) ?: return@LaunchedEffect
        var last = SystemClock.elapsedRealtimeNanos()
        while (true) {
            delay(DRIFT_STEP_MILLIS)
            val at = SystemClock.elapsedRealtimeNanos()
            phase = (phase + speed * (at - last) / NANOS_PER_SECOND) % TWO_PI
            last = at
        }
    }

    val driver = remember(maps) { maps?.let { SkyThemeDriver(SkySampler(it)) } }
    var skyPalette by remember { mutableStateOf<PogodaPalette?>(null) }
    LaunchedEffect(driver, sky, animationsOn, started, diagnostics != null) {
        val themeDriver = driver ?: return@LaunchedEffect
        val state = sky ?: return@LaunchedEffect
        if (!started) return@LaunchedEffect
        var last = SystemClock.elapsedRealtimeNanos()
        while (true) {
            val at = SystemClock.elapsedRealtimeNanos()
            val seconds = if (animationsOn) (at - last) / NANOS_PER_SECOND else INSTANT_SECONDS
            last = at
            val cloudPosition = SkyDrift.position(state.pan, phase)
            val scores = themeDriver.measure(state, cloudPosition)
            val palette = themeDriver.palette(scores, seconds)
            skyPalette = palette.toUi()
            diagnostics?.publish(state, palette, cloudPosition, store.lastLoad)
            if (!animationsOn) break
            delay(if (themeDriver.settled) THEME_STEP_MILLIS else THEME_FAST_STEP_MILLIS)
        }
    }

    val visibleLoaded = sky?.weights?.visible()?.count { store.get(it) != null } ?: 0
    val ready = visibleLoaded > 0 && skyPalette != null
    val reveal by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(REVEAL_MILLIS),
        label = "skyReveal",
    )

    // Снимок последнего неба: показывается с первого кадра, пока настоящие картины декодируются, и плавно уступает им место.
    val restored = remember(appContext) { SkySnapshotStorage.load(appContext) }
    var snapshotGivenUp by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready) {
            snapshotGivenUp = false
        } else {
            // Если неба так и нет (нет разрешения, нет данных), старый снимок не должен висеть вечно.
            delay(SNAPSHOT_HOLD_MILLIS)
            snapshotGivenUp = true
        }
    }
    val snapshotAlpha by animateFloatAsState(
        targetValue = if (restored != null && !snapshotGivenUp) 1f else 0f,
        animationSpec = tween(REVEAL_MILLIS),
        label = "skySnapshot",
    )
    val base = PogodaPalette.Neutral.mixWith(restored?.palette ?: PogodaPalette.Neutral, snapshotAlpha)
    val palette = base.mixWith(skyPalette ?: base, reveal)
    LaunchedEffect(palette.darkInk) { onDarkIcons(palette.darkInk) }

    // Раз в две минуты, пока приложение на виду и небо настоящее, снимок неба уходит на диск для следующего запуска.
    val snapshotLayer = rememberGraphicsLayer()
    val snapshotPainter = remember { SkyPainter() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSky by rememberUpdatedState(sky)
    val latestPalette by rememberUpdatedState(skyPalette)
    LaunchedEffect(ready, started, override.active) {
        if (!ready || !started || override.active) return@LaunchedEffect
        delay(SNAPSHOT_FIRST_DELAY_MILLIS)
        while (true) {
            val state = latestSky
            val colors = latestPalette
            if (state != null && colors != null && hostSize.width > 0) {
                captureSnapshot(
                    appContext, snapshotLayer, snapshotPainter, store, state, colors,
                    SkyDrift.position(state.pan, phase), density, layoutDirection, hostSize,
                )
            }
            delay(SNAPSHOT_REPEAT_MILLIS)
        }
    }

    // Стекло размывает то, что нарисовано на небе (вместе с нейтральным фоном, пока неба ещё нет).
    val hazeState = rememberHazeState()
    // Без размытия (выключатель на экране проверки) нет ни источника размытия, ни самого размытия: кадры сравниваются честно.
    val glass = rememberGlass(palette, if (glassBlur) hazeState else null)

    CompositionLocalProvider(
        LocalPogodaPalette provides palette,
        LocalGlass provides glass,
        LocalContentColor provides palette.ink,
    ) {
        Box(modifier.onSizeChanged { hostSize = it }) {
            SkyBackground(
                sky = sky,
                store = store,
                snapshot = restored?.image,
                snapshotAlpha = { snapshotAlpha },
                phase = { phase },
                reveal = { reveal },
                scrim = palette.scrim,
                modifier = Modifier.matchParentSize().then(if (glassBlur) Modifier.hazeSource(hazeState) else Modifier),
            )
            content()
        }
    }
}

/** Фон: спокойный градиент, поверх него небо (когда готово) и подложка под большими цифрами. */
@Composable
private fun SkyBackground(
    sky: SkyState?,
    store: SkyBitmapStore,
    snapshot: ImageBitmap?,
    snapshotAlpha: () -> Float,
    phase: () -> Double,
    reveal: () -> Float,
    scrim: Color,
    modifier: Modifier = Modifier,
) {
    val painter = remember { SkyPainter() }
    Box(modifier.background(PogodaColors.SkyBrush)) {
        if (snapshot != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = snapshotAlpha() }
                    .drawBehind {
                        drawImage(
                            snapshot,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    },
            )
        }
        if (sky != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = reveal() }
                    .drawBehind {
                        painter.draw(this, sky.look(SkyDrift.position(sky.pan, phase())), store)
                        painter.drawScrim(this, scrim)
                    },
            )
        }
    }
}

private const val NANOS_PER_SECOND = 1e9

/** Сколько ждать неба при запуске, прежде чем отпустить старый снимок (если неба так и нет). */
private const val SNAPSHOT_HOLD_MILLIS = 5_000L

/** Снимок делается спустя это время после того, как небо проявилось (цвета успевают устояться), затем регулярно. */
private const val SNAPSHOT_FIRST_DELAY_MILLIS = 3_000L
private const val SNAPSHOT_REPEAT_MILLIS = 120_000L

/** Ширина снимка в пикселях: маленькая картинка вместо неба, сглаживание при растяжении даёт нужную размытость. */
private const val SNAPSHOT_WIDTH = 96

/** Рисует небо в маленький слой и сохраняет его вместе с цветами интерфейса. Ошибки только в журнал. */
private suspend fun captureSnapshot(
    context: Context,
    layer: GraphicsLayer,
    painter: SkyPainter,
    store: SkyBitmapStore,
    sky: SkyState,
    colors: PogodaPalette,
    cloudPosition: Double,
    density: Density,
    layoutDirection: LayoutDirection,
    host: IntSize,
) {
    try {
        val height = (SNAPSHOT_WIDTH * host.height.toFloat() / host.width).roundToInt().coerceAtLeast(1)
        layer.record(density, layoutDirection, IntSize(SNAPSHOT_WIDTH, height)) {
            painter.draw(this, sky.look(cloudPosition), store)
            painter.drawScrim(this, colors.scrim)
        }
        val image = layer.toImageBitmap().asAndroidBitmap()
        withContext(Dispatchers.IO) {
            val soft = image.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            SkySnapshotStorage.save(context, soft, colors)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Не удалось снять снимок неба", e)
    }
}

private fun buildSky(input: SkyInput, override: SkyOverride, now: Instant): SkyState {
    val sun = override.solarHour?.let { SunPosition.atSolarHour(input.latitude, now, it) }
        ?: SunPosition.at(input.latitude, input.longitude, now)
    return SkyState.of(sun, override.condition ?: input.condition)
}

private fun loadMaps(context: Context): SkyMaps? = try {
    context.assets.open(SkyMaps.ASSET_PATH).use { SkyMaps.read(it) }
} catch (e: IOException) {
    Log.w(TAG, "Не удалось прочитать карты яркости неба", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "Файл карт яркости неба испорчен", e)
    null
}

/** Текущий момент, обновляется раз в минуту и при возвращении в приложение. */
@Composable
private fun rememberNow(started: Boolean): State<Instant> = produceState(Instant.now(), started) {
    while (true) {
        val moment = Instant.now()
        value = moment
        delay(MILLIS_PER_MINUTE - moment.toEpochMilli() % MILLIS_PER_MINUTE + 50)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
