package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.ImageDecoder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import io.github.dumahuch.pogodaapp.sky.Lightning
import io.github.dumahuch.pogodaapp.sky.LightningFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.random.Random

/** Кадр грозы для рисования: свет и молния ([frame]) и картинка молнии ([image], пусто у вспышки без молнии или если картинка не прочиталась). */
internal class LightningShot(val frame: LightningFrame, val image: ImageBitmap?)

/**
 * Картинки молний (`assets/lightning`, белый свет с прозрачностью вместо чёрного фона). В памяти не больше двух: следующая
 * читается заранее, пока идёт пауза перед разрядом.
 */
private class LightningBitmaps(private val context: Context) {

    private val loaded = LinkedHashMap<Int, ImageBitmap>()

    suspend fun load(bolt: Int): ImageBitmap? {
        loaded[bolt]?.let { return it }
        val image = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.assets, Lightning.file(bolt))
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE }.asImageBitmap()
            } catch (e: IOException) {
                Log.w(TAG, "Не удалось прочитать молнию ${Lightning.file(bolt)}", e)
                null
            }
        } ?: return null
        while (loaded.size >= MAX_LOADED) loaded.remove(loaded.keys.first())
        loaded[bolt] = image
        return image
    }

    fun clear() = loaded.clear()
}

/**
 * Гроза, пока [active]: разряды по расписанию [Lightning]; между разрядами пусто, во время разряда кадр на каждый кадр экрана.
 * Читать значение только при рисовании: тогда во время пауз ничего не перерисовывается.
 */
@Composable
internal fun rememberLightning(active: Boolean): State<LightningShot?> {
    val context = LocalContext.current.applicationContext
    val bolts = remember(context) { LightningBitmaps(context) }
    val shot = remember { mutableStateOf<LightningShot?>(null) }
    LaunchedEffect(active) {
        shot.value = null
        if (!active) {
            bolts.clear()
            return@LaunchedEffect
        }
        val storm = Lightning(Random.nextLong())
        while (true) {
            val strike = storm.next()
            val image = if (strike.bolt >= 0) bolts.load(strike.bolt) else null
            delay((strike.gapSeconds * MILLIS_PER_SECOND).toLong())
            val begin = withFrameNanos { it }
            var frame = strike.frame(0.0)
            while (frame != null) {
                shot.value = LightningShot(frame, image)
                val now = withFrameNanos { it }
                frame = strike.frame((now - begin) / NANOS_PER_SECOND)
            }
            shot.value = null
        }
    }
    return shot
}

/**
 * Молния поверх облаков, под грядами: картинка по высоте [BOLT_HEIGHT] экрана (масштаб от исходных 1536 пикселей, у всех молний
 * верх у края экрана), центр по ширине в [io.github.dumahuch.pogodaapp.sky.Strike.boltX], иногда отражена.
 */
internal fun DrawScope.drawBolt(shot: LightningShot) {
    val image = shot.image ?: return
    val alpha = shot.frame.bolt.toFloat()
    if (alpha < MIN_LIGHT) return
    val strike = shot.frame.strike
    val scale = size.height * BOLT_HEIGHT / BOLT_SOURCE_HEIGHT
    val width = image.width * scale
    val left = (strike.boltX * size.width).toFloat() - width / 2
    withTransform({
        if (strike.mirrored) {
            translate(left + width, 0f)
            scale(-scale, scale, pivot = Offset.Zero)
        } else {
            translate(left, 0f)
            scale(scale, scale, pivot = Offset.Zero)
        }
    }) {
        drawImage(image, alpha = alpha)
    }
}

/**
 * Свет разряда поверх всего неба: небо и облака светлеют (смешивание «экран», светлое не пересвечивается), сильнее всего в
 * пятне вокруг молнии или там, где вспыхнуло в облаках.
 */
internal fun DrawScope.drawFlash(shot: LightningShot) {
    val light = shot.frame.flash.toFloat()
    if (light < MIN_LIGHT) return
    val strike = shot.frame.strike
    drawRect(FlashColor, alpha = light * FLASH_ALPHA, blendMode = BlendMode.Screen)
    drawRect(
        Brush.radialGradient(
            listOf(FlashColor.copy(alpha = light * GLOW_ALPHA), Color.Transparent),
            center = Offset((strike.glowX * size.width).toFloat(), (strike.glowY * size.height).toFloat()),
            radius = size.width * GLOW_RADIUS,
        ),
        blendMode = BlendMode.Screen,
    )
}

private const val TAG = "PogodaSky"
private const val MILLIS_PER_SECOND = 1000.0
private const val NANOS_PER_SECOND = 1e9
private const val MAX_LOADED = 2

/** Слабее этого свет и молния не рисуются. */
private const val MIN_LIGHT = 0.004f

/** Высота картинки молнии (исходные 1536 пикселей) в долях высоты экрана: молния кончается около середины экрана. */
private const val BOLT_HEIGHT = 0.62f
private const val BOLT_SOURCE_HEIGHT = 1536f

/** Голубоватый свет разряда; на всё небо ложится с [FLASH_ALPHA], пятно вокруг молнии с [GLOW_ALPHA] и радиусом [GLOW_RADIUS] ширины. */
private val FlashColor = Color(0.62f, 0.73f, 1f)
private const val FLASH_ALPHA = 0.48f
private const val GLOW_ALPHA = 0.5f
private const val GLOW_RADIUS = 0.75f
