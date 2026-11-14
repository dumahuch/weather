package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
import io.github.dumahuch.pogodaapp.sky.DropOutline
import io.github.dumahuch.pogodaapp.sky.GlassRain
import io.github.dumahuch.pogodaapp.sky.GlassStack
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Картинка неба для линз капель: маленький снимок того, что за стеклом, и где он лежит на экране ([top] в пикселях от
 * верха окна, [widthPx] ширина неба на экране).
 */
@Stable
class LensSky(val image: ImageBitmap, val top: Float, val widthPx: Int)

/**
 * Дождь на стекле карточек: общие часы осадков [clock] (секунды), сила дождя и картинка неба для линз (пока её нет, капли
 * без отражения). [animate] выключено системой: стекло показывается уже мокрым и неподвижным. Карточки экрана видят друг
 * друга через [stack]: вода, стёкшая с одной, падает на следующую.
 */
@Stable
class RainOnGlass(val clock: State<Double>, val strength: Double, val lens: State<LensSky?>, val animate: Boolean) {
    internal val stack = GlassStack()
}

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
    private val pane = GlassStack.Pane()

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
    private val shapeMatrix = Matrix()
    private val oval = RectF()
    private val shapes = arrayOfNulls<DropShape>(MAX_TAIL_LEVEL + 1)
    private val body = Path()
    private val rimBottom = Path()
    private val rimTop = Path()

    fun update(rain: RainOnGlass) {
        if (rain.stack !== this.rain.stack) {
            this.rain.stack.remove(pane)
            rain.stack.add(pane)
        }
        this.rain = rain
        invalidateDraw()
    }

    override fun onAttach() {
        rain.stack.add(pane)
    }

    override fun onDetach() {
        rain.stack.remove(pane)
        pane.glass = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        rootX = p.x
        rootY = p.y
        pane.left = p.x.toDouble()
        pane.top = p.y.toDouble()
    }

    override fun ContentDrawScope.draw() {
        val px = density
        pane.density = px.toDouble()
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
        if (current != null && kotlin.math.abs(current.widthDp - widthDp) < 1 && kotlin.math.abs(current.heightDp - heightDp) < 1) {
            pane.glass = current
            return current
        }
        val fresh = GlassRain(widthDp.toDouble(), heightDp.toDouble(), seed)
        if (!rain.animate) {
            var s = 0.0
            while (s < STILL_RAIN_SECONDS) {
                fresh.step(STILL_STEP, rain.strength)
                s += STILL_STEP
            }
        }
        // Воду с нижней кромки принимает карточка ниже; у неподвижного (уже мокрого) стекла анимации нет, и вода не перетекает.
        if (rain.animate) fresh.spill = { x, r -> rain.stack.spill(pane, x, r) }
        pane.glass = fresh
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
            val squash = bead.squash.toFloat()
            if (bead.pooled) {
                // Вода у нижней кромки: низкий длинный валик, после слияния на миг выпячивается вверх.
                val ry = glass.halfHeight(bead).toFloat() * (1f + SQUASH_TALL * squash)
                val heavy = (ry / GlassRain.POOL_HEIGHT_LIMIT.toFloat()).coerceIn(0f, 1f)
                lensDrop(canvas, x, y, glass.halfWidth(bead).toFloat(), ry, 0f, heavy, 0f, px, sky)
            } else if (bead.r < SMALL_RADIUS) {
                canvas.drawCircle(x + 0.3f, y + 0.5f, r * 0.9f, smallShadow)
                canvas.drawCircle(x - 0.3f, y - 0.4f, r * 0.38f, smallLight)
            } else {
                // Чем крупнее капля, тем сильнее она оседает: снизу тяжёлая и круглая, сверху уже. У каждой своя форма
                // (хвост, растяжение вширь, наклон); после слияния на миг сплющена вдоль линии слияния.
                val heavy = ((bead.r - SMALL_RADIUS) / SAG_SPAN).toFloat().coerceIn(0f, 1f)
                var rx = r * 0.95f * bead.wide.toFloat()
                var ry = r * (1.05f + 0.15f * heavy)
                if (bead.squashSideways) {
                    rx *= 1f + SQUASH_WIDE * squash
                    ry *= 1f - SQUASH_LOW * squash
                } else {
                    ry *= 1f + SQUASH_TALL * squash
                    rx *= 1f - SQUASH_LOW * squash
                }
                lensDrop(canvas, x, y, rx, ry, (BEAD_TAIL + BEAD_TAIL_SAG * heavy) * bead.form.toFloat(), heavy, bead.tilt.toFloat(), px, sky)
            }
        }
        for (runner in glass.runners) {
            // Стекающая капля «комета»: круглая голова впереди, хвост вверх по мокрому следу; на разгоне хвост длиннее.
            val r = runner.r.toFloat()
            val stretch = glass.stretch(runner).toFloat()
            val ry = r * (RUNNER_LENGTH + RUNNER_LENGTH_GAIN * stretch)
            lensDrop(canvas, runner.x.toFloat(), runner.y.toFloat() + r * HEAD_FORWARD, r * 0.92f, ry, RUNNER_TAIL + RUNNER_TAIL_GAIN * stretch, 1f, 0f, px, sky)
        }
        canvas.restore()
    }

    /**
     * Капля-линза: центр ([x], [y]), полуоси [rx] и [ry] (dp), хвост [tail] (0 круг, больше вытянутее кверху, см.
     * [DropOutline]) и наклон [tilt] (градусы). В ней перевёрнутое небо из круга в [LENS_WIDTH] раз шире, сверху светлее, снизу тёмная кромка
     * (у тяжёлой капли [heavy] гуще), в верхней части блик.
     */
    private fun lensDrop(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, tail: Float, heavy: Float, tilt: Float, px: Float, sky: LensSky?) {
        val level = (tail * TAIL_STEPS).roundToInt().coerceIn(0, MAX_TAIL_LEVEL)
        val shape = shapes[level] ?: DropShape(DropOutline.points(level / TAIL_STEPS.toDouble())).also { shapes[level] = it }
        shapeMatrix.setScale(rx, ry)
        shapeMatrix.postRotate(tilt)
        shapeMatrix.postTranslate(x, y)
        shape.body.transform(shapeMatrix, body)
        shape.bottom.transform(shapeMatrix, rimBottom)
        shape.top.transform(shapeMatrix, rimTop)
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
            canvas.drawPath(body, lensPaint)
        }
        matrix.setScale(1f, 2 * ry)
        matrix.postTranslate(0f, y - ry)
        shadePaint.shader?.setLocalMatrix(matrix)
        canvas.drawPath(body, shadePaint)
        rimDark.alpha = ((RIM_DARK_ALPHA + RIM_DARK_HEAVY * heavy) * 255).roundToInt()
        canvas.drawPath(rimBottom, rimDark)
        canvas.drawPath(rimTop, rimLight)
        if (rx > 2f) {
            // У длинного валика блик не уезжает к краю: ширина блика считается от высоты.
            val hw = min(rx, ry * 1.6f)
            val lift = min(level / TAIL_STEPS, 1.4f) / 1.4f
            canvas.save()
            canvas.translate(x - hw * 0.35f, y + ry * (HIGHLIGHT_Y + HIGHLIGHT_Y_TAIL * lift))
            canvas.rotate(HIGHLIGHT_TURN)
            oval.set(-hw * 0.22f, -ry * 0.15f, hw * 0.22f, ry * 0.15f)
            canvas.drawOval(oval, highlight)
            canvas.restore()
        }
    }
}

/** Контур капли с хвостом одного уровня в единичных координатах: вся капля и две дуги кромки (нижняя тёмная, верхняя светлая). */
private class DropShape(points: DoubleArray) {
    val body = Path()
    val bottom = Path()
    val top = Path()

    init {
        val count = points.size / 2
        fun x(i: Int) = points[2 * (i % count)].toFloat()
        fun y(i: Int) = points[2 * (i % count) + 1].toFloat()
        body.moveTo(x(0), y(0))
        for (i in 1 until count) body.lineTo(x(i), y(i))
        body.close()
        // Углы дуг как у круглой капли; «по часовой от трёх часов» превращается в долю обхода контура от вершины.
        arc(bottom, count, BOTTOM_START, BOTTOM_SWEEP, ::x, ::y)
        arc(top, count, TOP_START, TOP_SWEEP, ::x, ::y)
    }

    private fun arc(path: Path, count: Int, startDegrees: Float, sweepDegrees: Float, x: (Int) -> Float, y: (Int) -> Float) {
        val from = ceil(((startDegrees + 90f) / 360f) * count).toInt()
        val to = floor(((startDegrees + sweepDegrees + 90f) / 360f) * count).toInt()
        path.moveTo(x(from), y(from))
        for (i in from + 1..to) path.lineTo(x(i), y(i))
    }
}

/** Тёмная кромка по нижней половине капли и светлая сверху слева (градусы, по часовой от «трёх часов»). */
private const val BOTTOM_START = 5.7f
private const val BOTTOM_SWEEP = 168.6f
private const val TOP_START = 208.6f
private const val TOP_SWEEP = 106.4f
private const val HIGHLIGHT_TURN = -28.6f
private const val RIM_WIDTH_DP = 0.7f
private const val RIM_DARK_ALPHA = 0.26f

/** Тяжёлая капля темнее снизу: добавка к прозрачности нижней кромки. */
private const val RIM_DARK_HEAVY = 0.2f

/** Блик в верхней части капли: высота от центра в долях полуоси, у вытянутой капли ниже (над круглой головой). */
private const val HIGHLIGHT_Y = -0.45f
private const val HIGHLIGHT_Y_TAIL = 0.4f

/** Форма капли по гравитации: хвост задаётся числом от 0 (круг) в шагах [TAIL_STEPS], готовых контуров столько, сколько уровней. */
private const val TAIL_STEPS = 10f
private const val MAX_TAIL_LEVEL = 16

/** Неподвижная капля оседает, пока растёт от [SMALL_RADIUS] на [SAG_SPAN] dp: хвост от [BEAD_TAIL] до плюс [BEAD_TAIL_SAG]. */
private const val SAG_SPAN = 2.6
private const val BEAD_TAIL = 0.15f
private const val BEAD_TAIL_SAG = 0.5f

/** После слияния капля на миг сплющена: растёт вдоль линии слияния (вширь или вверх) и убавляет поперёк. */
private const val SQUASH_WIDE = 0.35f
private const val SQUASH_TALL = 0.3f
private const val SQUASH_LOW = 0.15f

/** Стекающая капля: длина в радиусах (стоит и на разгоне), хвост, и насколько голова выдвинута вперёд от центра в радиусах. */
private const val RUNNER_LENGTH = 1.3f
private const val RUNNER_LENGTH_GAIN = 0.35f
private const val RUNNER_TAIL = 0.8f
private const val RUNNER_TAIL_GAIN = 0.6f
private const val HEAD_FORWARD = 0.25f

private fun argb(alpha: Float, gray: Int): Int = android.graphics.Color.argb((alpha * 255).roundToInt(), gray, gray, gray)

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = RIM_WIDTH_DP
}
