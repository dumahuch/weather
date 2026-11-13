package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import io.github.dumahuch.pogodaapp.sky.SnowDriftShape
import java.util.Random

/** Ниже этой высоты (dp) сугроб не рисуется совсем: он ещё полностью прозрачный. */
private const val MIN_VISIBLE_DP = 0.05f

/** Шаг по ширине карточки, на котором строится верх сугроба, и отступ от края, где он начинается (dp). */
private const val STEP_DP = 2.0
private const val EDGE_DP = 3.0

/** Насколько основание уходит вниз за кромку карточки, чтобы не было щели (dp). */
private const val SINK_DP = 1.5f

private const val RIM_WIDTH_DP = 1.1f
private const val RIM_LIFT_DP = 0.4
private const val GRAINS = 90
private const val GRAIN_EDGE_DP = 14.0
private const val SPECKS = 10

/** Верх тела сугроба берёт оттенок неба (доля цвета стекла), низ голубоватый. */
private const val TOP_TINT = 0.12f
private const val BOTTOM_TINT = 0.15f
private val TOP_SNOW = Color.White
private val BOTTOM_SNOW = Color(0xFFB8C8E0)

private var nextSeed = 1

/**
 * Сугроб на верхней кромке карточки, растёт пока идёт снег ([level] высота в dp, 0 без снега). Рисуется позади карточки
 * и вне её границ: только над верхней кромкой. У кнопок и нижней панели [maxShare] ограничивает высоту долей высоты
 * элемента (у карточек без ограничения), чтобы маленькая кнопка не тонула в снегу.
 */
internal fun Modifier.snowDrift(shape: Shape, level: State<Float>?, tint: Color, maxShare: Float = Float.MAX_VALUE): Modifier =
    if (level == null) this else this then SnowDriftElement(shape, level, tint, maxShare)

private class SnowDriftElement(val shape: Shape, val level: State<Float>, val tint: Color, val maxShare: Float) :
    ModifierNodeElement<SnowDriftNode>() {
    override fun create() = SnowDriftNode(shape, level, tint, maxShare)

    override fun update(node: SnowDriftNode) = node.update(shape, level, tint, maxShare)

    override fun equals(other: Any?): Boolean =
        other is SnowDriftElement && other.shape == shape && other.level === level && other.tint == tint && other.maxShare == maxShare

    override fun hashCode(): Int =
        ((shape.hashCode() * 31 + System.identityHashCode(level)) * 31 + tint.hashCode()) * 31 + maxShare.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "snowDrift"
    }
}

private class SnowDriftNode(
    private var shape: Shape,
    private var level: State<Float>,
    private var tint: Color,
    private var maxShare: Float,
) : Modifier.Node(), DrawModifierNode {

    /** Свой у каждой карточки: соседние сугробы не должны быть одинаковыми. */
    private val seed = nextSeed++

    fun update(shape: Shape, level: State<Float>, tint: Color, maxShare: Float) {
        this.shape = shape
        this.level = level
        this.tint = tint
        this.maxShare = maxShare
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val height = minOf(level.value, size.height / density * maxShare)
        if (height >= MIN_VISIBLE_DP && size.width > 0f) drawDrift(height)
        drawContent()
    }

    private fun ContentDrawScope.drawDrift(heightDp: Float) {
        val px = density
        val width = size.width / px.toDouble()
        val radius = topRadiusDp(shape.createOutline(size, layoutDirection, this), px)
        val h = heightDp.toDouble()
        fun base(x: Double) = SnowDriftShape.edgeDrop(x, width, radius)
        fun top(x: Double) = base(x) - h * SnowDriftShape.profile(x, seed) * SnowDriftShape.taper(x, width)
        val last = width - EDGE_DP

        val body = Path()
        val rim = Path()
        var x = EDGE_DP
        body.moveTo((x * px).toFloat(), ((base(x) + SINK_DP) * px).toFloat())
        while (x <= last) {
            val y = top(x)
            body.lineTo((x * px).toFloat(), (y * px).toFloat())
            val lifted = Offset((x * px).toFloat(), ((y - RIM_LIFT_DP) * px).toFloat())
            if (x == EDGE_DP) rim.moveTo(lifted.x, lifted.y) else rim.lineTo(lifted.x, lifted.y)
            x += STEP_DP
        }
        x = last
        while (x >= EDGE_DP) {
            body.lineTo((x * px).toFloat(), ((base(x) + SINK_DP) * px).toFloat())
            x -= STEP_DP
        }
        body.close()

        val sky = tint.copy(alpha = 1f)
        val brush = Brush.verticalGradient(
            0f to lerp(TOP_SNOW, sky, TOP_TINT),
            1f to lerp(BOTTOM_SNOW, sky, BOTTOM_TINT),
            startY = (-h * px).toFloat(),
            endY = SINK_DP * px,
        )
        // Молодой сугроб проявляется из прозрачного, кромка тоньше самого сугроба.
        val fade = SnowDriftShape.visibility(h).toFloat()
        drawPath(body, brush, alpha = fade)
        val rimWidth = minOf(RIM_WIDTH_DP, heightDp) * px
        drawPath(rim, Color.White.copy(alpha = 0.9f * fade), style = Stroke(width = rimWidth, cap = StrokeCap.Round))
        drawGrain(h, width, px, fade, ::top)
    }

    /** Зерно: тёмные и светлые крупинки внутри сугроба и несколько ярких искорок. */
    private fun ContentDrawScope.drawGrain(h: Double, width: Double, px: Float, fade: Float, top: (Double) -> Double) {
        val random = Random(seed * GRAIN_SEED.toLong() + 1)
        val edge = minOf(GRAIN_EDGE_DP, width / 4)
        repeat(GRAINS) {
            val x = edge + random.nextDouble() * (width - 2 * edge)
            val y = top(x) + random.nextDouble() * h * SnowDriftShape.taper(x, width)
            val alpha = (0.12f + random.nextFloat() * 0.18f) * fade
            val color = if (random.nextBoolean()) Color(0xFF96ACCC).copy(alpha = alpha) else Color.White.copy(alpha = alpha * 2)
            drawCircle(color, (0.5f + random.nextFloat() * 0.5f) * px, Offset((x * px).toFloat(), (y * px).toFloat()))
        }
        if (h <= SPECK_MIN_DP) return
        // Искорки тоже проявляются постепенно, начиная с SPECK_MIN_DP.
        val speck = 0.8f * SnowDriftShape.visibility(h - SPECK_MIN_DP).toFloat()
        repeat(SPECKS) {
            val x = edge + random.nextDouble() * (width - 2 * edge)
            val y = top(x) + random.nextDouble() * h * SPECK_DEPTH
            drawCircle(Color.White.copy(alpha = speck), 0.9f * px, Offset((x * px).toFloat(), (y * px).toFloat()))
        }
    }

    private fun topRadiusDp(outline: Outline, px: Float): Double = when (outline) {
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x / px.toDouble()
        else -> 0.0
    }
}

private const val GRAIN_SEED = 7919
private const val SPECK_MIN_DP = 3.0
private const val SPECK_DEPTH = 0.8
