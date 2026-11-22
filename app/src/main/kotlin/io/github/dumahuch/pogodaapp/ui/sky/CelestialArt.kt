package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.Celestial
import io.github.dumahuch.pogodaapp.sky.MoonShade
import io.github.dumahuch.pogodaapp.sky.SkyLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Картинки солнца и луны в памяти видеокарты: только те, что нужны сейчас (солнце и одна фаза луны, около 6 МБ каждая).
 * Общие для обоих фонов; файлы лежат в `assets/sky/celestial/`.
 */
@Stable
internal class CelestialBitmaps(private val context: Context) {

    private val loaded = mutableStateMapOf<String, ImageBitmap>()

    fun get(file: String): ImageBitmap? = loaded[file]

    fun clear() {
        loaded.clear()
        shade = null
    }

    private var shade: Pair<Int, ImageBitmap>? = null

    /** Маска фазы ([MoonShade]) для освещённости [lit]; одна в памяти, пересчитывается, когда фаза заметно сдвинулась. */
    fun shade(lit: Double, lightOnRight: Boolean): ImageBitmap {
        val key = MoonShade.key(lit, lightOnRight)
        shade?.let { (k, image) -> if (k == key) return image }
        val side = MoonShade.SIDE
        val image = Bitmap.createBitmap(MoonShade.mask(MoonShade.litOf(key), lightOnRight), side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
        shade = key to image
        return image
    }

    /** Оставляет в памяти ровно [files] и догружает недостающие. */
    suspend fun ensure(files: Set<String>) {
        for (file in loaded.keys.toList()) {
            if (file !in files) loaded.remove(file)
        }
        for (file in files) {
            if (loaded.containsKey(file)) continue
            val image = withContext(Dispatchers.IO) { read(file) } ?: continue
            loaded[file] = image
        }
    }

    private fun read(file: String): ImageBitmap? = try {
        val source = ImageDecoder.createSource(context.assets, "sky/celestial/$file.webp")
        ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE }.asImageBitmap()
    } catch (e: IOException) {
        Log.w("PogodaSky", "Не удалось прочитать картинку $file", e)
        null
    }
}

/**
 * Рисует солнце и луну тем же положением и размером, что учитывает расчёт цветов ([Celestial.discs]).
 * У картинок солнца и полной луны свечение уже есть; у остальных фаз луны его рисует код.
 */
internal fun DrawScope.drawCelestial(look: SkyLook, bitmaps: CelestialBitmaps) {
    if (look.bodies.isEmpty()) return
    for (disc in Celestial.discs(look.bodies, size.width.toDouble(), size.height.toDouble())) {
        val body = disc.body
        val image = bitmaps.get(body.file) ?: continue
        val side = disc.imageSize.roundToInt().coerceAtLeast(1)
        val center = Offset(disc.cx.toFloat(), disc.cy.toFloat())
        if (body.shaded) {
            drawShadedMoon(image, bitmaps.shade(body.lit, body.lightOnRight), center, side, body.mirrored, body.alpha.toFloat())
            continue
        }
        if (body.moon && body.lit < 1.0) {
            val glowRadius = (disc.radius * MOON_GLOW_RADIUS).toFloat()
            drawCircle(
                brush = Brush.radialGradient(0f to MOON_GLOW.copy(alpha = (MOON_GLOW_ALPHA * body.alpha).toFloat()), 1f to Color.Transparent, center = center, radius = glowRadius),
                radius = glowRadius,
                center = center,
            )
        }
        withTransform({ if (body.mirrored) scale(-1f, 1f, pivot = center) }) {
            drawImage(
                image,
                dstOffset = IntOffset((disc.cx - side / 2.0).roundToInt(), (disc.cy - side / 2.0).roundToInt()),
                dstSize = IntSize(side, side),
                alpha = body.alpha.toFloat(),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/**
 * Луна с фазой: картинка полной луны и поверх неё маска [MoonShade] умножением, на отдельном слое (иначе умножение задело бы
 * небо под луной). Маска лежит в координатах экрана: [mirrored] отражает только картинку, сторона света уже учтена в маске.
 */
private fun DrawScope.drawShadedMoon(image: ImageBitmap, mask: ImageBitmap, center: Offset, side: Int, mirrored: Boolean, alpha: Float) {
    val topLeft = IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt())
    val bounds = Rect(topLeft.x.toFloat(), topLeft.y.toFloat(), (topLeft.x + side).toFloat(), (topLeft.y + side).toFloat())
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
        withTransform({ if (mirrored) scale(-1f, 1f, pivot = center) }) {
            drawImage(image, dstOffset = topLeft, dstSize = IntSize(side, side), filterQuality = FilterQuality.High)
        }
        drawImage(mask, dstOffset = topLeft, dstSize = IntSize(side, side), blendMode = BlendMode.Modulate, filterQuality = FilterQuality.Low)
        canvas.restore()
    }
}

private val MOON_GLOW = Color(205, 222, 255)
private const val MOON_GLOW_RADIUS = 1.5
private const val MOON_GLOW_ALPHA = 0.28
