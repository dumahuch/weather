package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.dumahuch.pogodaapp.sky.SkyLook
import io.github.dumahuch.pogodaapp.sky.SkySampler
import io.github.dumahuch.pogodaapp.sky.SkyScene

/**
 * Рисует небо в том же порядке слоёв, что и макет (и расчёт цветов в пакете sky):
 * картины со своими долями → погодная вуаль → облака → обесцвечивание → затемнение → приглушение света облаков.
 * Доли картин «каскадом»: каждая следующая ложится с прозрачностью вес / сумма весов до неё, это взвешенное среднее.
 * Объект хранит кисти и не создаёт новых на каждом кадре.
 */
internal class SkyPainter {

    private val grayPaint = Paint()
    private val cloudPaint = Paint()
    private var filterAmount = -1.0
    private var filter: ColorFilter? = null

    fun draw(scope: DrawScope, look: SkyLook, store: SkyBitmapStore) {
        val loaded = look.weights.visible().filter { store.get(it) != null }
        if (loaded.isEmpty()) return
        scope.drawLayers(look, loaded, store)
    }

    private fun DrawScope.drawLayers(look: SkyLook, loaded: List<SkyScene>, store: SkyBitmapStore) {
        val canvas = drawContext.canvas
        val bounds = Rect(0f, 0f, size.width, size.height)
        val gray = look.grayAmount
        val desaturate = gray > MIN_EFFECT
        if (desaturate) {
            grayPaint.colorFilter = grayFilter(gray)
            canvas.saveLayer(bounds, grayPaint)
        }
        drawPlates(look, loaded, store)
        drawVeil(look)
        drawClouds(look, loaded, store)
        if (desaturate) canvas.restore()
        if (look.weather.dark > MIN_EFFECT) drawRect(DARK_COLOR, alpha = look.darkAlpha.toFloat())
        val dim = look.nightDim
        if (dim > MIN_EFFECT) drawRect(NIGHT_COLOR, alpha = dim.toFloat())
    }

    private fun DrawScope.drawPlates(look: SkyLook, loaded: List<SkyScene>, store: SkyBitmapStore) {
        var sum = 0.0
        for (scene in loaded) {
            val image = store.get(scene)?.plate ?: continue
            val weight = look.weights[scene]
            sum += weight
            val scale = size.height / image.height
            val shift = -look.weights.pan.toFloat() * (image.width * scale - size.width)
            drawScaled(image, shift, 0f, scale, (weight / sum).toFloat())
        }
    }

    private fun DrawScope.drawVeil(look: SkyLook) {
        val strength = look.veilStrength
        if (strength <= MIN_EFFECT) return
        val veil = look.veil
        val brush = Brush.verticalGradient(
            0f to veil.top.toColor(),
            VEIL_MIDDLE to veil.mid.toColor(),
            1f to veil.hor.toColor(),
            startY = 0f,
            endY = size.height,
        )
        drawRect(brush, alpha = strength.toFloat())
    }

    private fun DrawScope.drawClouds(look: SkyLook, loaded: List<SkyScene>, store: SkyBitmapStore) {
        val alpha = look.weather.cloudLayerAlpha.toFloat()
        if (loaded.size == 1) {
            val image = store.get(loaded[0])?.clouds ?: return
            drawCloudLayer(image, look.cloudPosition, alpha)
            return
        }
        // Слои разных времён суток сначала складываются друг с другом, потом целиком ложатся на небо.
        cloudPaint.alpha = alpha
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), cloudPaint)
        var sum = 0.0
        for (scene in loaded) {
            val image = store.get(scene)?.clouds ?: continue
            val weight = look.weights[scene]
            sum += weight
            drawCloudLayer(image, look.cloudPosition, (weight / sum).toFloat())
        }
        canvas.restore()
    }

    private fun DrawScope.drawCloudLayer(image: ImageBitmap, position: Double, alpha: Float) {
        val scale = SkySampler.CLOUD_SCALE.toFloat() * size.height / image.height
        val shift = -position.toFloat() * (image.width * scale - size.width)
        val top = size.height - image.height * scale
        drawScaled(image, shift, top, scale, alpha)
    }

    /** Рисует картину целиком: сначала масштаб, потом сдвиг. Сдвиг дробный, поэтому облака плывут плавно. */
    private fun DrawScope.drawScaled(image: ImageBitmap, left: Float, top: Float, scale: Float, alpha: Float) {
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawImage(image, alpha = alpha)
        }
    }

    /** Фильтр, который тянет цвет к серому с яркостью 0,30 R + 0,59 G + 0,11 B (как в расчёте цветов интерфейса). */
    private fun grayFilter(amount: Double): ColorFilter {
        filter?.let { cached -> if (amount == filterAmount) return cached }
        val keep = (1 - amount).toFloat()
        val a = amount.toFloat()
        val r = 0.30f * a
        val g = 0.59f * a
        val b = 0.11f * a
        val matrix = ColorMatrix(
            floatArrayOf(
                keep + r, g, b, 0f, 0f,
                r, keep + g, b, 0f, 0f,
                r, g, keep + b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val created = ColorFilter.colorMatrix(matrix)
        filterAmount = amount
        filter = created
        return created
    }

    /** Полоса под большими цифрами: плотность 0 → [color] на 20 %, до 72 %, затем снова 0. Координаты как в макете (96…440 из 892). */
    fun drawScrim(scope: DrawScope, color: Color) {
        if (color.alpha <= 0f) return
        with(scope) {
            val top = size.height * SCRIM_TOP
            val bottom = size.height * SCRIM_BOTTOM
            val brush = Brush.verticalGradient(
                0f to Color.Transparent,
                SCRIM_FADE_IN to color,
                SCRIM_FADE_OUT to color,
                1f to Color.Transparent,
                startY = top,
                endY = bottom,
            )
            drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
        }
    }

    private companion object {
        const val MIN_EFFECT = 0.01
        const val VEIL_MIDDLE = 0.55f
        const val SCRIM_TOP = 96f / 892f
        const val SCRIM_BOTTOM = 440f / 892f
        const val SCRIM_FADE_IN = 0.2f
        const val SCRIM_FADE_OUT = 0.72f
        val DARK_COLOR = Color(10, 16, 32)
        val NIGHT_COLOR = Color(8, 12, 26)
    }
}
