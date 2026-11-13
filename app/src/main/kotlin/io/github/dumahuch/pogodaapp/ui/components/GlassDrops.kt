package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import io.github.dumahuch.pogodaapp.sky.GlassRain
import kotlin.math.roundToInt

/**
 * Картинка неба для линз капель: маленький снимок того, что за стеклом, и где он лежит на экране ([top] в пикселях от
 * верха окна, [widthPx] ширина неба на экране).
 */
@Stable
class LensSky(val image: ImageBitmap, val top: Float, val widthPx: Int)

/**
 * Дождь на стекле карточек: общие часы осадков [clock] (секунды), сила дождя и картинка неба для линз (пока её нет, капли
 * без отражения). [animate] выключено системой: стекло показывается уже мокрым и неподвижным.
 */
@Stable
class RainOnGlass(val clock: State<Double>, val strength: Double, val lens: State<LensSky?>, val animate: Boolean)

/** Капли поверх заливки стекла и под содержимым карточки; без дождя ничего не делает. */
internal fun Modifier.glassDrops(rain: RainOnGlass?): Modifier = if (rain == null) this else this then GlassDropsElement(rain)

private class GlassDropsElement(val rain: RainOnGlass) : ModifierNodeElement<GlassDropsNode>() {
    override fun create() = GlassDropsNode(rain)

    override fun update(node: GlassDropsNode) = node.update(rain)

    override fun equals(other: Any?): Boolean = other is GlassDropsElement && other.rain === rain

    override fun hashCode(): Int = System.identityHashCode(rain)

    override fun InspectorInfo.inspectableProperties() {
        name = "glassDrops"
    }
}

/** Линза показывает перевёрнутое небо из круга во столько раз шире самой капли. */
private const val LENS_WIDTH = 2.6f

/** Капли мельче этого радиуса (dp) рисуются без линзы: тень и блик. */
private const val SMALL_RADIUS = 1.7

/** Мокрый след виден слабо, как в прототипе. */
private const val TRAIL_ALPHA = 0.13f

/** Мокрым стекло показывается, когда анимации выключены: столько секунд дождя прогоняется сразу. */
private const val STILL_RAIN_SECONDS = 20.0
private const val STILL_STEP = 0.1

private var nextSeed = 1L

private class GlassDropsNode(private var rain: RainOnGlass) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private val seed = nextSeed++
    private var glass: GlassRain? = null
    private var lastTime = Double.NaN
    private var rootX = 0f
    private var rootY = 0f

    private var lensImage: ImageBitmap? = null
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(argb(0.10f, 255), argb(0.02f, 255), argb(0.30f, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val rimDark = stroke(argb(0.28f, 0))
    private val rimLight = stroke(argb(0.35f, 255))
    private val highlight = fill(argb(0.7f, 255))
    private val smallShadow = fill(android.graphics.Color.argb((0.22f * 255).roundToInt(), 10, 20, 30))
    private val smallLight = fill(argb(0.5f, 255))
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val layerPaint = Paint().apply { alpha = (TRAIL_ALPHA * 255).roundToInt() }
    private val matrix = Matrix()
    private val oval = RectF()

    fun update(rain: RainOnGlass) {
        this.rain = rain
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        rootX = p.x
        rootY = p.y
    }

    override fun ContentDrawScope.draw() {
        val px = density
        val t = rain.clock.value
        val glass = glassFor(size.width / px, size.height / px)
        if (glass != null) {
            if (!lastTime.isNaN() && t > lastTime) glass.step(t - lastTime, rain.strength)
            lastTime = t
            drawIntoCanvas { canvas -> drawGlass(canvas.nativeCanvas, glass, px) }
        }
        drawContent()
    }

    /** Стекло этого размера; новое при заметной смене размера. Без анимации сразу мокрое. */
    private fun glassFor(widthDp: Float, heightDp: Float): GlassRain? {
        if (widthDp < 1f || heightDp < 1f) return null
        val current = glass
        if (current != null && kotlin.math.abs(current.widthDp - widthDp) < 1 && kotlin.math.abs(current.heightDp - heightDp) < 1) return current
        val fresh = GlassRain(widthDp.toDouble(), heightDp.toDouble(), seed)
        if (!rain.animate) {
            var s = 0.0
            while (s < STILL_RAIN_SECONDS) {
                fresh.step(STILL_STEP, rain.strength)
                s += STILL_STEP
            }
        }
        glass = fresh
        return fresh
    }

    private fun drawGlass(canvas: Canvas, glass: GlassRain, px: Float) {
        val sky = rain.lens.value
        if (sky != null && sky.image !== lensImage) {
            lensImage = sky.image
            lensPaint.shader = BitmapShader(sky.image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.scale(px, px)
        if (glass.trails.isNotEmpty()) {
            canvas.saveLayer(0f, 0f, glass.widthDp.toFloat(), glass.heightDp.toFloat(), layerPaint)
            for (trail in glass.trails) {
                trailPaint.strokeWidth = trail.width.toFloat()
                trailPaint.alpha = (glass.trailAlpha(trail.age) * 255).roundToInt()
                canvas.drawLine(trail.x1.toFloat(), trail.y1.toFloat(), trail.x2.toFloat(), trail.y2.toFloat(), trailPaint)
            }
            canvas.restore()
        }
        for (bead in glass.beads) {
            val x = bead.x.toFloat()
            val y = bead.y.toFloat()
            val r = bead.r.toFloat()
            if (bead.r < SMALL_RADIUS) {
                canvas.drawCircle(x + 0.3f, y + 0.5f, r * 0.9f, smallShadow)
                canvas.drawCircle(x - 0.3f, y - 0.4f, r * 0.38f, smallLight)
            } else {
                lensDrop(canvas, x, y, r * 0.95f, r * 1.08f, px, sky)
            }
        }
        for (runner in glass.runners) {
            val r = runner.r.toFloat()
            lensDrop(canvas, runner.x.toFloat(), runner.y.toFloat(), r * 0.95f, r * 1.28f, px, sky)
        }
        canvas.restore()
    }

    /**
     * Капля-линза с центром ([x], [y]) и полуосями [rx], [ry] (dp): в ней перевёрнутое небо из круга в [LENS_WIDTH] раз
     * шире, сверху светлее, снизу тёмная кромка, слева сверху блик.
     */
    private fun lensDrop(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, px: Float, sky: LensSky?) {
        oval.set(x - rx, y - ry, x + rx, y + ry)
        if (sky != null && sky.widthPx > 0) {
            // Точка капли p показывает небо в точке (центр − k·(p − центр)) экрана; матрица переводит пиксели снимка в dp карточки.
            val scale = sky.image.width.toFloat() / sky.widthPx
            val k = LENS_WIDTH
            val a = 1f / (k * px * scale)
            val cardX = rootX / px
            val cardY = rootY / px
            matrix.setScale(-a, -a)
            matrix.postTranslate(x - (0f - (cardX + x) * px) / (k * px), y - (sky.top - (cardY + y) * px) / (k * px))
            lensPaint.shader?.setLocalMatrix(matrix)
            canvas.drawOval(oval, lensPaint)
        }
        matrix.setScale(1f, 2 * ry)
        matrix.postTranslate(0f, y - ry)
        shadePaint.shader?.setLocalMatrix(matrix)
        canvas.drawOval(oval, shadePaint)
        canvas.drawArc(oval, BOTTOM_START, BOTTOM_SWEEP, false, rimDark)
        canvas.drawArc(oval, TOP_START, TOP_SWEEP, false, rimLight)
        if (rx > 2f) {
            canvas.save()
            canvas.translate(x - rx * 0.35f, y - ry * 0.45f)
            canvas.rotate(HIGHLIGHT_TURN)
            oval.set(-rx * 0.22f, -ry * 0.15f, rx * 0.22f, ry * 0.15f)
            canvas.drawOval(oval, highlight)
            canvas.restore()
        }
    }
}

/** Тёмная кромка по нижней половине капли и светлая сверху слева (градусы, по часовой от «трёх часов»). */
private const val BOTTOM_START = 5.7f
private const val BOTTOM_SWEEP = 168.6f
private const val TOP_START = 208.6f
private const val TOP_SWEEP = 106.4f
private const val HIGHLIGHT_TURN = -28.6f
private const val RIM_WIDTH_DP = 0.7f

private fun argb(alpha: Float, gray: Int): Int = android.graphics.Color.argb((alpha * 255).roundToInt(), gray, gray, gray)

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = RIM_WIDTH_DP
}
