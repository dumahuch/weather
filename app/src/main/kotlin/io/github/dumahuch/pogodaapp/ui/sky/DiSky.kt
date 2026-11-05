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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.dumahuch.pogodaapp.sky.DiArt
import io.github.dumahuch.pogodaapp.sky.Placement
import io.github.dumahuch.pogodaapp.sky.SkyLook
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
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
internal class DiBitmapStore(private val context: Context) : SkyArtStore {

    private val loaded = mutableStateMapOf<SkyScene, DiSceneBitmaps>()

    var clouds by mutableStateOf<DiClouds?>(null)
        private set

    final override var lastLoad by mutableStateOf<SkyBitmapStore.LoadTiming?>(null)
        private set

    fun get(scene: SkyScene): DiSceneBitmaps? = loaded[scene]

    override fun has(scene: SkyScene): Boolean = loaded[scene] != null && clouds != null

    override fun clear() {
        loaded.clear()
        clouds = null
    }

    override suspend fun ensure(scenes: Set<SkyScene>) {
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
            val timing = SkyBitmapStore.LoadTiming(decoded, firstMillis, SystemClock.elapsedRealtime() - begin)
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
 * небо → погодная вуаль → облака (подкрашенные, с прозрачностью слоя) → пять гряд сзади вперёд, всё под обесцвечиванием непогоды;
 * затем затемнение и приглушение. Каждый слой двух времён суток ложится каскадом (вес / сумма весов до него).
 */
internal class DiPainter(private val store: DiBitmapStore) : SkyArtPainter {

    private val effects = SkyEffects()
    private var tintKey = -1
    private var tintFilter: ColorFilter? = null

    override fun draw(scope: DrawScope, look: SkyLook) {
        val loaded = look.weights.visible().filter { store.has(it) }
        if (loaded.isEmpty()) return
        val clouds = store.clouds ?: return
        effects.withWeather(scope, look) {
            val pan = DiArt.pan(look.weights)
            val w = size.width.toDouble()
            val h = size.height.toDouble()
            for (layer in DiArt.Layer.entries) {
                val place = DiArt.place(layer, w, h, pan, look.lift)
                var sum = 0.0
                for (scene in loaded) {
                    val image = store.get(scene)?.layers?.get(layer.ordinal) ?: continue
                    val weight = look.weights[scene]
                    sum += weight
                    drawPlaced(image, place, (weight / sum).toFloat())
                }
                if (layer == DiArt.Layer.Sky) {
                    effects.drawVeil(this, look)
                    val sheet = if (DiArt.storm(look.weather)) clouds.storm else clouds.light
                    drawPlaced(sheet, DiArt.placeClouds(w, h, look.cloudPosition), look.weather.cloudLayerAlpha.toFloat(), tint(look))
                }
            }
        }
    }

    private fun DrawScope.drawPlaced(image: ImageBitmap, place: Placement, alpha: Float, filter: ColorFilter? = null) {
        drawScaled(image, place.left.toFloat(), place.top.toFloat(), place.scale.toFloat(), alpha, filter)
    }

    /** Подкраска облаков умножением; фильтр пересоздаётся только при заметной смене цвета (шаг 1 из 255). */
    private fun tint(look: SkyLook): ColorFilter {
        val c = DiArt.tint(look.weights)
        val r = c.r.roundToInt().coerceIn(0, 255)
        val g = c.g.roundToInt().coerceIn(0, 255)
        val b = c.b.roundToInt().coerceIn(0, 255)
        val key = (r shl 16) or (g shl 8) or b
        tintFilter?.let { if (key == tintKey) return it }
        val created = ColorFilter.tint(Color(r, g, b), BlendMode.Modulate)
        tintKey = key
        tintFilter = created
        return created
    }
}
