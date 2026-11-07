package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.dumahuch.pogodaapp.sky.ScrimShape
import io.github.dumahuch.pogodaapp.sky.SkyLook
import io.github.dumahuch.pogodaapp.sky.SkySampler

/** Рисует картину целиком: сначала масштаб, потом сдвиг. Сдвиг дробный, поэтому слои плывут плавно. */
internal fun DrawScope.drawScaled(image: ImageBitmap, left: Float, top: Float, scale: Float, alpha: Float, filter: ColorFilter? = null) {
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha, colorFilter = filter)
    }
}

/**
 * Погода поверх картин: обесцвечивание (цветовой матрицей на слое), погодная вуаль,
 * затемнение грозы и приглушение света ночью. Хранит кисть и фильтр, на каждом кадре новых не создаёт.
 */
internal class SkyEffects {

    private val grayPaint = Paint()
    private val tonePaint = Paint()
    private var filterAmount = -1.0
    private var filter: ColorFilter? = null

    /**
     * Рисует [body] с обесцвечиванием непогоды (цветовой матрицей на слое). [force]: слой нужен и без обесцвечивания,
     * чтобы то, что рисуется в нём со смешиванием «только по нарисованному» ([BlendMode.SrcAtop]), не тронуло экран под ним.
     * [withTone]: затемнение грозы и приглушение ночью ложатся на этот же слой, после обесцвечивания, и красят только
     * нарисованное в нём (SrcAtop), поэтому всё, что рисуется вне слоя (солнце и луна), остаётся своего цвета.
     * [opaque]: слой закрашен целиком (небо); тогда накладки идут обычным смешиванием и лишний слой не нужен.
     */
    fun layer(
        scope: DrawScope,
        look: SkyLook,
        force: Boolean,
        withTone: Boolean = false,
        opaque: Boolean = false,
        body: DrawScope.() -> Unit,
    ) = with(scope) {
        val canvas = drawContext.canvas
        val bounds = Rect(0f, 0f, size.width, size.height)
        val gray = look.grayAmount
        val desaturate = gray > MIN_EFFECT
        val separate = desaturate || force
        val tone = withTone && (look.weather.dark > MIN_EFFECT || look.nightDim > MIN_EFFECT)
        val outer = tone && !opaque
        if (outer) canvas.saveLayer(bounds, tonePaint)
        if (separate) {
            grayPaint.colorFilter = if (desaturate) grayFilter(gray) else null
            canvas.saveLayer(bounds, grayPaint)
        }
        body()
        if (separate) canvas.restore()
        if (tone) {
            drawTone(this, look, if (opaque) BlendMode.SrcOver else BlendMode.SrcAtop)
            if (outer) canvas.restore()
        }
    }

    /** Затемнение грозы и приглушение света ночью поверх всего кадра. */
    fun finish(scope: DrawScope, look: SkyLook) = drawTone(scope, look, BlendMode.SrcOver)

    private fun drawTone(scope: DrawScope, look: SkyLook, blendMode: BlendMode) = with(scope) {
        if (look.weather.dark > MIN_EFFECT) drawRect(DARK_COLOR, alpha = look.darkAlpha.toFloat(), blendMode = blendMode)
        val dim = look.nightDim
        if (dim > MIN_EFFECT) drawRect(NIGHT_COLOR, alpha = dim.toFloat(), blendMode = blendMode)
    }

    /** Нужна ли погодная вуаль на этом кадре (она заметна). */
    fun hasVeil(look: SkyLook): Boolean = look.veilStrength > MIN_EFFECT

    /** Вуаль на весь кадр; [blendMode] [BlendMode.SrcAtop] красит только уже нарисованное (облака на прозрачном слое). */
    fun drawVeil(scope: DrawScope, look: SkyLook, blendMode: BlendMode = BlendMode.SrcOver) {
        val strength = look.veilStrength
        if (strength <= MIN_EFFECT) return
        val veil = look.veil
        val brush = Brush.verticalGradient(
            0f to veil.top.toColor(),
            SkySampler.VEIL_MID.toFloat() to veil.mid.toColor(),
            1f to veil.hor.toColor(),
            startY = 0f,
            endY = scope.size.height,
        )
        scope.drawRect(brush, alpha = strength.toFloat(), blendMode = blendMode)
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

    companion object {
        private const val MIN_EFFECT = 0.01
        private val DARK_COLOR = Color(10, 16, 32)
        private val NIGHT_COLOR = Color(8, 12, 26)

        /**
         * Подложки на небе: под большими цифрами ([scrim], координаты как в макете, 96…440 из 892) и под строками под
         * температурой ([infoScrim], около 300…575 dp). Форма и правило «берётся наибольшая плотность» в [ScrimShape].
         * Градиент идёт на весь экран, а не на отдельном прямоугольнике: у прямоугольника был край в середине экрана,
         * и на эмуляторе по этому краю проходила линия в 1 пиксель. Все цвета градиента одного оттенка (меняется только прозрачность),
         * как у CSS-градиента в макете, поэтому тёмной каймы «прозрачный чёрный» нет.
         */
        fun drawScrim(scope: DrawScope, scrim: Color, infoScrim: Color) {
            if (scrim.alpha <= 0f && infoScrim.alpha <= 0f) return
            val base = if (scrim.alpha > 0f) scrim else infoScrim
            val stops = ScrimShape.stops(scrim.alpha.toDouble(), infoScrim.alpha.toDouble())
            val brush = Brush.verticalGradient(
                *Array(stops.size) { stops[it].first to base.copy(alpha = stops[it].second) },
                startY = 0f,
                endY = scope.size.height,
            )
            scope.drawRect(brush)
        }
    }
}
