package io.github.dumahuch.pogodaapp.ui.sky

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.SkyFog
import io.github.dumahuch.pogodaapp.sky.SkyState
import kotlin.math.roundToInt

/** За сколько туман проступает или сходит при смене погоды (мс); ниже [MIN_AMOUNT] слоя нет совсем. */
private const val FADE_MILLIS = 4_000
private const val MIN_AMOUNT = 0.01f

/** Полосы плывут медленно: обновления раз в 50 мс хватает, а размытие стекла пересчитывается реже, чем у дождя. */
private const val STEP_NANOS = 50_000_000L

private const val TEXTURE_WIDTH = 256
private const val TEXTURE_HEIGHT = 64
private const val TEXTURE_SEED = 13L

/**
 * Туман на небе: дымка и плывущие полосы ([SkyFog]). Лежит внутри источника размытия, как дождь и снег, поэтому стекло
 * карточек размывает и его. [moving] выключено (анимации выключены или приложение свёрнуто): полосы стоят.
 */
@Composable
internal fun FogLayer(sky: SkyState, moving: Boolean, modifier: Modifier = Modifier) {
    val amount by animateFloatAsState(SkyFog.amount(sky.weather.fog).toFloat(), tween(FADE_MILLIS), label = "sky-fog")
    if (amount < MIN_AMOUNT) return
    val time = rememberFogTime(moving)
    val painter = remember { FogPainter() }
    val color = argb(1.0, SkyFog.color(sky.veil.hor))
    Box(modifier.drawBehind { painter.draw(this, amount, color, time.value) })
}

@Composable
private fun rememberFogTime(moving: Boolean): State<Double> {
    val time = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last < 0) {
                    last = now
                } else if (now - last >= STEP_NANOS) {
                    time.doubleValue += (now - last) / 1e9
                    last = now
                }
            }
        }
    }
    return time
}

private class FogPainter {
    private val texture: Bitmap = SkyFog.texture(TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_SEED).let { values ->
        val pixels = IntArray(values.size) { i -> ((values[i] * 255).roundToInt() shl 24) or 0x00FFFFFF }
        Bitmap.createBitmap(pixels, TEXTURE_WIDTH, TEXTURE_HEIGHT, Bitmap.Config.ARGB_8888)
    }
    private val bandShader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = bandShader }
    private val veilPaint = Paint()
    private val matrix = Matrix()
    private var veilColor = 0
    private var veilHeight = -1f
    private var bandColor = 0

    fun draw(scope: DrawScope, amount: Float, color: Int, seconds: Double) = with(scope) {
        val w = size.width
        val h = size.height
        if (color != veilColor || h != veilHeight) {
            veilColor = color
            veilHeight = h
            veilPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(withAlpha(color, SkyFog.VEIL_TOP), withAlpha(color, SkyFog.VEIL_MID), withAlpha(color, SkyFog.VEIL_BOTTOM)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        if (color != bandColor) {
            bandColor = color
            bandPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            veilPaint.alpha = (amount * 255).roundToInt()
            canvas.drawRect(0f, 0f, w, h, veilPaint)
            for (band in SkyFog.Bands) {
                val top = ((band.centre - band.height / 2) * h).toFloat()
                val height = (band.height * h).toFloat()
                val tile = (band.width * w).toFloat()
                val shift = ((seconds * band.speed * density) % tile).toFloat()
                matrix.setScale(tile / TEXTURE_WIDTH, height / TEXTURE_HEIGHT)
                matrix.postTranslate(-shift, top)
                bandShader.setLocalMatrix(matrix)
                bandPaint.alpha = (amount * band.alpha * 255).roundToInt()
                canvas.drawRect(0f, top, w, top + height, bandPaint)
            }
        }
    }
}

private fun argb(alpha: Double, c: Rgb): Int =
    android.graphics.Color.argb((alpha * 255).roundToInt(), c.r.roundToInt(), c.g.roundToInt(), c.b.roundToInt())

private fun withAlpha(color: Int, alpha: Double): Int = (color and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)
