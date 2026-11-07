package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.ImageDecoder
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.CloudLayout
import io.github.dumahuch.pogodaapp.sky.DiArt
import io.github.dumahuch.pogodaapp.sky.PlacedCloud
import io.github.dumahuch.pogodaapp.sky.Placement
import io.github.dumahuch.pogodaapp.sky.SkyLook
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import io.github.dumahuch.pogodaapp.sky.SkyWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.math.roundToInt

/** Шесть слоёв одного времени суток фона «Ди», в порядке [DiArt.Layer]. */
internal class DiSceneBitmaps(val layers: List<ImageBitmap>)

/** Два листа облаков фона «Ди»: светлые и грозовые. */
internal class DiClouds(val light: ImageBitmap, val storm: ImageBitmap)

/**
 * Картины фона «Ди» в памяти видеокарты: не больше [SkyState.MAX_SCENES] времён суток по шесть слоёв (около 36 МБ на время суток)
 * и два листа облаков. Слои одного времени суток декодируются параллельно.
 */
@Stable
internal class DiBitmapStore(private val context: Context) {

    /**
     * Сколько заняла последняя загрузка картин: сколько времён суток декодировано, через сколько миллисекунд готово
     * первое (с него небо уже можно показывать) и через сколько все. Нужно экрану проверки.
     */
    class LoadTiming(val scenes: Int, val firstMillis: Long, val totalMillis: Long)

    private val loaded = mutableStateMapOf<SkyScene, DiSceneBitmaps>()

    var clouds by mutableStateOf<DiClouds?>(null)
        private set

    var lastLoad by mutableStateOf<LoadTiming?>(null)
        private set

    fun get(scene: SkyScene): DiSceneBitmaps? = loaded[scene]

    fun has(scene: SkyScene): Boolean = loaded[scene] != null && clouds != null

    fun clear() {
        loaded.clear()
        clouds = null
    }

    suspend fun ensure(scenes: Set<SkyScene>) {
        if (scenes.isEmpty()) return
        require(scenes.size <= SkyState.MAX_SCENES) { "Нельзя держать больше ${SkyState.MAX_SCENES} времён суток" }
        for (scene in loaded.keys.toList()) {
            if (scene !in scenes) loaded.remove(scene)
        }
        val begin = SystemClock.elapsedRealtime()
        if (clouds == null) {
            clouds = try {
                coroutineScope {
                    val light = async(Dispatchers.IO) { read("clouds_light") }
                    val storm = async(Dispatchers.IO) { read("clouds_storm") }
                    DiClouds(light.await(), storm.await())
                }
            } catch (e: IOException) {
                Log.w(TAG, "Не удалось прочитать облака фона «Ди»", e)
                null
            }
        }
        var decoded = 0
        var firstMillis = 0L
        for (scene in scenes) {
            if (loaded.containsKey(scene)) continue
            val bitmaps = decode(scene) ?: continue
            loaded[scene] = bitmaps
            if (decoded == 0) firstMillis = SystemClock.elapsedRealtime() - begin
            decoded++
        }
        if (decoded > 0) {
            val timing = LoadTiming(decoded, firstMillis, SystemClock.elapsedRealtime() - begin)
            lastLoad = timing
            Log.i(TAG, "Слои «Ди» загружены: ${timing.scenes} шт., первое время суток за ${timing.firstMillis} мс, все за ${timing.totalMillis} мс")
        }
    }

    private suspend fun decode(scene: SkyScene): DiSceneBitmaps? = try {
        coroutineScope {
            DiSceneBitmaps(DiArt.Layer.entries.map { layer -> async(Dispatchers.IO) { read(DiArt.file(scene, layer)) } }.awaitAll())
        }
    } catch (e: IOException) {
        Log.w(TAG, "Не удалось прочитать слои фона «Ди» ${DiArt.prefix(scene)}", e)
        null
    }

    private fun read(name: String): ImageBitmap {
        val source = ImageDecoder.createSource(context.assets, "sky/di/$name.webp")
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val TAG = "PogodaSky"
    }
}

/**
 * Рисует фон «Ди» тем же порядком, что и расчёт цветов ([io.github.dumahuch.pogodaapp.sky.DiRaster]):
 * небо → погодная вуаль на небе (под обесцвечиванием непогоды) → солнце и луна своим цветом → облака (отдельные, число и размер по
 * погоде: [CloudLayout]; подкрашенные; вуаль красит и их) → пять гряд сзади вперёд (под обесцвечиванием); затемнение и приглушение лежат на небе и на облаках с грядами, но не на солнце и луне. Каждый слой двух времён суток ложится каскадом (вес / сумма весов до него).
 */
internal class DiPainter(private val store: DiBitmapStore, private val celestial: CelestialBitmaps) {

    private val effects = SkyEffects()
    private var tintKey = -1
    private var tintFilter: ColorFilter? = null
    private var cloudWeather: SkyWeather? = null
    private var cloudList: List<PlacedCloud> = emptyList()

    fun draw(scope: DrawScope, look: SkyLook) {
        val loaded = look.weights.visible().filter { store.has(it) }
        if (loaded.isEmpty()) return
        val clouds = store.clouds ?: return
        val pan = DiArt.pan(look.weights)
        val w = scope.size.width.toDouble()
        val h = scope.size.height.toDouble()
        // Небо с вуалью под обесцвечиванием, затемнением грозы и ночным приглушением; солнце и луна лежат на небе своим
        // цветом (step-29, step-30), поэтому они вне слоя и ничем из этого не затемняются.
        effects.layer(scope, look, force = false, withTone = true, opaque = true) {
            drawLayer(DiArt.Layer.Sky, loaded, look, pan, w, h)
            effects.drawVeil(this, look)
        }
        scope.drawCelestial(look, celestial)
        // Облака и гряды в своём слое. Вуаль поверх облаков красит только их (SrcAtop на прозрачном слое), солнце под ними не задевает.
        effects.layer(scope, look, force = effects.hasVeil(look), withTone = true) {
            drawClouds(look, clouds, w, h)
            effects.drawVeil(this, look, BlendMode.SrcAtop)
            for (layer in DiArt.Layer.entries) {
                if (layer != DiArt.Layer.Sky) drawLayer(layer, loaded, look, pan, w, h)
            }
        }
    }

    /** Один слой картины, смешанный между временами суток каскадом (вес / сумма весов до него). */
    private fun DrawScope.drawLayer(layer: DiArt.Layer, loaded: List<SkyScene>, look: SkyLook, pan: Double, w: Double, h: Double) {
        val place = DiArt.place(layer, w, h, pan, look.lift)
        var sum = 0.0
        for (scene in loaded) {
            val image = store.get(scene)?.layers?.get(layer.ordinal) ?: continue
            val weight = look.weights[scene]
            sum += weight
            drawPlaced(image, place, (weight / sum).toFloat())
        }
    }

    /**
     * Облака погоды: каждое рисуется своим куском листа (отражённое по горизонтали при [PlacedCloud.mirrored]);
     * те, что целиком за краем экрана, пропускаются. Раскладка пересчитывается только при смене погоды.
     */
    private fun DrawScope.drawClouds(look: SkyLook, clouds: DiClouds, w: Double, h: Double) {
        if (look.weather != cloudWeather) {
            cloudList = CloudLayout.of(look.weather)
            cloudWeather = look.weather
        }
        val place = DiArt.placeClouds(w, h, look.cloudPosition)
        val filter = tint(look)
        for (cloud in cloudList) {
            val left = place.left + cloud.left * place.scale
            val top = place.top + cloud.top * place.scale
            val width = cloud.width * place.scale
            val height = cloud.height * place.scale
            if (left + width < 0 || left > w || top + height < 0 || top > h) continue
            val scale = (cloud.scale * place.scale).toFloat()
            withTransform({
                if (cloud.mirrored) {
                    translate((left + width).toFloat(), top.toFloat())
                    scale(-scale, scale, pivot = Offset.Zero)
                } else {
                    translate(left.toFloat(), top.toFloat())
                    scale(scale, scale, pivot = Offset.Zero)
                }
            }) {
                drawImage(
                    if (cloud.storm) clouds.storm else clouds.light,
                    srcOffset = IntOffset(cloud.rect.x, cloud.rect.y),
                    srcSize = IntSize(cloud.rect.w, cloud.rect.h),
                    alpha = cloud.alpha.toFloat(),
                    colorFilter = filter,
                )
            }
        }
    }

    private fun DrawScope.drawPlaced(image: ImageBitmap, place: Placement, alpha: Float, filter: ColorFilter? = null) {
        drawScaled(image, place.left.toFloat(), place.top.toFloat(), place.scale.toFloat(), alpha, filter)
    }

    /**
     * Подкраска облаков умножением на цвет времени суток, серость плотной облачности ([SkyWeather.cloudShade]) и подъём яркости
     * при малой облачности ([SkyWeather.cloudLift], результат обрезается белым); фильтр пересоздаётся только при заметной смене цвета.
     */
    private fun tint(look: SkyLook): ColorFilter {
        val t = DiArt.tint(look.weights)
        val k = look.weather.cloudShade * look.weather.cloudLift
        val r = (t.r * k).roundToInt().coerceIn(0, MAX_TINT)
        val g = (t.g * k).roundToInt().coerceIn(0, MAX_TINT)
        val b = (t.b * k).roundToInt().coerceIn(0, MAX_TINT)
        val key = (r shl 20) or (g shl 10) or b
        tintFilter?.let { if (key == tintKey) return it }
        val scale = 1f / 255f
        val created = ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    r * scale, 0f, 0f, 0f, 0f,
                    0f, g * scale, 0f, 0f, 0f,
                    0f, 0f, b * scale, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        tintKey = key
        tintFilter = created
        return created
    }

    private companion object {
        /** Множитель цвета в единицах 0…255 с запасом на подъём яркости (до 1,14 · 255 ≈ 291). */
        const val MAX_TINT = 400
    }
}
