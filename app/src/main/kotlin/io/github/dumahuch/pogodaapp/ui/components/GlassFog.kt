package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.FogWipe
import io.github.dumahuch.pogodaapp.sky.FrostPattern
import io.github.dumahuch.pogodaapp.sky.GlassFrost
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Непрозрачность плёнки при густоте 1; ниже густоты [MIN_COVER] плёнки нет совсем. */
private const val FOG_ALPHA = 0.4f
private const val MIN_COVER = 0.02f

/** Плёнка молочно-белая с долей цвета стекла (ночью не светится серым пятном). */
private val FOG_WHITE = Color(0xFFF1F5F9)
private const val FOG_TINT = 0.3f

/** Иней: матовая плёнка полупрозрачная (гуще у кромки), перья плотнее и белее; край растущего инея мягкий (доля [GlassFrost.coverage]). */
private const val FROST_ALPHA = 0.45f
private const val FROST_LINE_ALPHA = 0.38f
private const val FROST_EDGE = 0.05f
private val FROST_ICE = Color(0xFFDCE8F4)
private val FROST_WHITE = Color(0xFFF7FBFF)
private const val FROST_TINT = 0.2f

/** Плёнка ровная (пятна по клеткам в 0.8.19 читались квадратами), к низу стекла чуть гуще: там оседает больше влаги. */
private const val BOTTOM_EXTRA = 0.3f

/**
 * Общее у двух половин высокой карточки ([GlassCardBox]): палец ловит внешняя коробка ([fogWiping]), а плёнку рисует
 * подложка под содержимым ([glassFog] с этим [FogPatch]).
 */
@Stable
class FogPatch {
    internal var wipe: FogWipe? = null
    internal var onWiped: (() -> Unit)? = null
}

/**
 * Запотевшее стекло: молочная плёнка густотой [fog] (0…1) под содержимым карточки. Палец, прошедший по стеклу, стирает
 * плёнку, через несколько секунд она медленно возвращается. Касания только читаются, прокрутка и нажатия работают как
 * раньше. С [patch] касания ловит [fogWiping] на внешней коробке.
 */
internal fun Modifier.glassFog(fog: State<Float>?, tint: Color, patch: FogPatch? = null): Modifier =
    if (fog == null) this else this then GlassFogElement(fog, tint, patch, draws = true)

/** Только ловит палец для плёнки, которую рисует [glassFog] с тем же [patch]. */
internal fun Modifier.fogWiping(fog: State<Float>?, patch: FogPatch): Modifier =
    if (fog == null) this else this then GlassFogElement(fog, Color.Unspecified, patch, draws = false)

/**
 * Иней на стекле: растёт от краёв ветками ([FrostPattern]), [frost] доля стекла под ним (0…1). Тёплый палец протапливает
 * след, через несколько секунд он снова замерзает (около минуты). С [patch] касания ловит [frostWiping] на внешней коробке.
 */
internal fun Modifier.glassFrost(frost: State<Float>?, tint: Color, shape: Shape, patch: FogPatch? = null): Modifier =
    if (frost == null) this else this then GlassFogElement(frost, tint, patch, draws = true, frosty = true, shape = shape)

/** Только ловит палец для инея, который рисует [glassFrost] с тем же [patch]. */
internal fun Modifier.frostWiping(frost: State<Float>?, patch: FogPatch): Modifier =
    if (frost == null) this else this then GlassFogElement(frost, Color.Unspecified, patch, draws = false, frosty = true)

private class GlassFogElement(
    val fog: State<Float>,
    val tint: Color,
    val patch: FogPatch?,
    val draws: Boolean,
    val frosty: Boolean = false,
    /** Форма стекла: иней идёт от её кромки (у круглой кнопки кольцом). */
    val shape: Shape? = null,
) : ModifierNodeElement<GlassFogNode>() {
    override fun create() = GlassFogNode(fog, tint, patch ?: FogPatch(), draws, listens = patch == null || !draws, frosty, shape)

    override fun update(node: GlassFogNode) = node.update(fog, tint)

    override fun equals(other: Any?): Boolean =
        other is GlassFogElement && other.fog === fog && other.tint == tint && other.patch === patch && other.draws == draws &&
            other.frosty == frosty && other.shape == shape

    override fun hashCode(): Int =
        ((((System.identityHashCode(fog) * 31 + tint.hashCode()) * 31 + System.identityHashCode(patch)) * 31 + draws.hashCode()) * 31 +
            frosty.hashCode()) * 31 + shape.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = if (frosty) "glassFrost" else "glassFog"
    }
}

private class GlassFogNode(
    private var fog: State<Float>,
    private var tint: Color,
    private val patch: FogPatch,
    private val draws: Boolean,
    private val listens: Boolean,
    private val frosty: Boolean,
    private val shape: Shape?,
) : Modifier.Node(), DrawModifierNode, PointerInputModifierNode {

    /** Узор инея для текущего размера стекла (только у инея). */
    private var pattern: FrostPattern? = null

    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private var drawnCover = -1f
    private var dirty = true
    private var recovery: Job? = null
    private val listener: () -> Unit = ::wiped

    fun update(fog: State<Float>, tint: Color) {
        this.fog = fog
        if (tint != this.tint) {
            this.tint = tint
            dirty = true
        }
        invalidateDraw()
    }

    override fun onAttach() {
        if (draws) {
            patch.onWiped = listener
            if (patch.wipe?.active == true) wiped()
        }
    }

    override fun onDetach() {
        if (patch.onWiped === listener) patch.onWiped = null
        recovery = null
    }

    private fun wiped() {
        dirty = true
        invalidateDraw()
        if (recovery?.isActive == true) return
        recovery = coroutineScope.launch {
            var last = -1L
            while (true) {
                val now = withFrameNanos { it }
                if (last < 0) {
                    last = now
                    continue
                }
                val glass = patch.wipe ?: break
                val still = glass.step((now - last) / 1e9)
                last = now
                dirty = true
                invalidateDraw()
                if (!still) break
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val cover = fog.value
        if (draws && cover >= MIN_COVER && size.width >= 1f && size.height >= 1f) {
            val glass = glassFor(size.width / density, size.height / density, cornerRadiusDp())
            if (dirty || cover != drawnCover) render(glass, cover)
            val made = bitmap
            if (made != null) {
                // Сглаживание при растяжке маленькой картинки даёт ровную плёнку и мягкий край следа.
                target.set(0f, 0f, size.width, size.height)
                drawIntoCanvas { it.nativeCanvas.drawBitmap(made, null, target, paint) }
            }
        }
        drawContent()
    }

    /** Стекло этого размера; новое (сухое от пальца) при заметной смене размера. */
    /** Скругление верхнего левого угла формы в dp (0 у прямоугольной или неизвестной формы). */
    private fun ContentDrawScope.cornerRadiusDp(): Double = when (val outline = shape?.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x / density.toDouble()
        else -> 0.0
    }

    private fun glassFor(widthDp: Float, heightDp: Float, radiusDp: Double): FogWipe {
        val current = patch.wipe?.takeIf { abs(it.widthDp - widthDp) < 1 && abs(it.heightDp - heightDp) < 1 }
        if (current != null && bitmap != null) return current
        val fresh = current ?: if (frosty) {
            FogWipe(
                widthDp.toDouble(),
                heightDp.toDouble(),
                GlassFrost.MELT_HOLD_SECONDS,
                GlassFrost.REFREEZE_SECONDS,
                GlassFrost.CELL_DP,
                GlassFrost.MAX_CELLS,
            )
        } else {
            FogWipe(widthDp.toDouble(), heightDp.toDouble())
        }
        patch.wipe = fresh
        if (frosty) pattern = FrostPattern(fresh.cols, fresh.rows, fresh.cell, radiusDp, (fresh.cols * 7919L) xor fresh.rows.toLong())
        val count = fresh.cols * fresh.rows
        pixels = IntArray(count)
        bitmap = Bitmap.createBitmap(fresh.cols, fresh.rows, Bitmap.Config.ARGB_8888)
        dirty = true
        return fresh
    }

    /** Клетки плёнки в маленькую картинку. */
    private fun render(glass: FogWipe, cover: Float) {
        val frost = pattern
        if (frosty && frost != null) {
            renderFrost(glass, frost, cover)
            return
        }
        val base = lerp(FOG_WHITE, (if (tint.isSpecified) tint else FOG_WHITE).copy(alpha = 1f), FOG_TINT).toArgb() and 0x00FFFFFF
        for (row in 0 until glass.rows) {
            val lower = 1f - BOTTOM_EXTRA / 2 + BOTTOM_EXTRA * (row + 0.5f) / glass.rows
            for (col in 0 until glass.cols) {
                val i = row * glass.cols + col
                val alpha = (FOG_ALPHA * cover * lower * glass.cover(i)).coerceIn(0f, 1f)
                pixels[i] = ((alpha * 255).roundToInt() shl 24) or base
            }
        }
        bitmap?.setPixels(pixels, 0, glass.cols, 0, 0, glass.cols, glass.rows)
        drawnCover = cover
        dirty = false
    }

    /**
     * Иней: клетка замерзает, когда доля [level] дошла до её порога (с мягким краем [FROST_EDGE]); ровный иней полупрозрачный,
     * ветки белее и плотнее. Цвет ледяной голубоватый с долей цвета стекла.
     */
    private fun renderFrost(glass: FogWipe, frost: FrostPattern, level: Float) {
        val ice = lerp(FROST_ICE, (if (tint.isSpecified) tint else FROST_ICE).copy(alpha = 1f), FROST_TINT)
        val white = FROST_WHITE
        for (i in pixels.indices) {
            val grown = ((level - frost.threshold[i]) / FROST_EDGE).coerceIn(0f, 1f)
            if (grown <= 0f) {
                pixels[i] = 0
                continue
            }
            val line = frost.crystal[i]
            val alpha = (grown * (FROST_ALPHA * frost.matte[i] + FROST_LINE_ALPHA * line) * glass.cover(i)).coerceIn(0f, 1f)
            val r = (ice.red + (white.red - ice.red) * line) * 255
            val g = (ice.green + (white.green - ice.green) * line) * 255
            val b = (ice.blue + (white.blue - ice.blue) * line) * 255
            pixels[i] = ((alpha * 255).roundToInt() shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
        }
        bitmap?.setPixels(pixels, 0, glass.cols, 0, 0, glass.cols, glass.rows)
        drawnCover = level
        dirty = false
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (!listens || pass != PointerEventPass.Initial || fog.value < MIN_COVER) return
        val glass = patch.wipe ?: return
        val px = requireDensity().density
        var touched = false
        for (change in pointerEvent.changes) {
            if (!change.pressed) continue
            val from = if (change.previousPressed) change.previousPosition else change.position
            glass.wipe((from.x / px).toDouble(), (from.y / px).toDouble(), (change.position.x / px).toDouble(), (change.position.y / px).toDouble())
            touched = true
        }
        if (touched && glass.active) patch.onWiped?.invoke()
    }

    override fun onCancelPointerInput() = Unit
}
