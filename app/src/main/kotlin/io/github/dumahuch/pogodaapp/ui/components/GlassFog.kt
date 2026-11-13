package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.FogWipe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/** Непрозрачность плёнки при густоте 1; ниже густоты [MIN_COVER] плёнки нет совсем. */
private const val FOG_ALPHA = 0.4f
private const val MIN_COVER = 0.02f

/** Плёнка молочно-белая с долей цвета стекла (ночью не светится серым пятном). */
private val FOG_WHITE = Color(0xFFF1F5F9)
private const val FOG_TINT = 0.3f

/** Неровность плёнки по клеткам и сгущение к низу стекла (там оседает больше влаги). */
private const val GRAIN = 0.3f
private const val BOTTOM_EXTRA = 0.3f

/** Плёнка возвращается с шагом не чаще 30 мс, как дождь и снег. */
private const val STEP_NANOS = 30_000_000L

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

private class GlassFogElement(val fog: State<Float>, val tint: Color, val patch: FogPatch?, val draws: Boolean) :
    ModifierNodeElement<GlassFogNode>() {
    override fun create() = GlassFogNode(fog, tint, patch ?: FogPatch(), draws, listens = patch == null || !draws)

    override fun update(node: GlassFogNode) = node.update(fog, tint)

    override fun equals(other: Any?): Boolean =
        other is GlassFogElement && other.fog === fog && other.tint == tint && other.patch === patch && other.draws == draws

    override fun hashCode(): Int =
        ((System.identityHashCode(fog) * 31 + tint.hashCode()) * 31 + System.identityHashCode(patch)) * 31 + draws.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "glassFog"
    }
}

private class GlassFogNode(
    private var fog: State<Float>,
    private var tint: Color,
    private val patch: FogPatch,
    private val draws: Boolean,
    private val listens: Boolean,
) : Modifier.Node(), DrawModifierNode, PointerInputModifierNode {

    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null
    private var pixels = IntArray(0)
    private var grain = FloatArray(0)
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
                if (now - last < STEP_NANOS) continue
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
            val glass = glassFor(size.width / density, size.height / density)
            if (dirty || cover != drawnCover) render(glass, cover)
            image?.let {
                drawImage(
                    it,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(glass.cols, glass.rows),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        drawContent()
    }

    /** Стекло этого размера; новое (сухое от пальца) при заметной смене размера. */
    private fun glassFor(widthDp: Float, heightDp: Float): FogWipe {
        val current = patch.wipe?.takeIf { abs(it.widthDp - widthDp) < 1 && abs(it.heightDp - heightDp) < 1 }
        if (current != null && bitmap != null) return current
        val fresh = current ?: FogWipe(widthDp.toDouble(), heightDp.toDouble())
        patch.wipe = fresh
        val count = fresh.cols * fresh.rows
        val random = Random(count.toLong() * 31 + fresh.cols)
        grain = FloatArray(count) { 1f - GRAIN / 2 + GRAIN * random.nextFloat() }
        pixels = IntArray(count)
        val made = Bitmap.createBitmap(fresh.cols, fresh.rows, Bitmap.Config.ARGB_8888)
        bitmap = made
        image = made.asImageBitmap()
        dirty = true
        return fresh
    }

    /** Клетки плёнки в маленькую картинку: растянутая с мягкой фильтрацией, она даёт ровную плёнку с мягким краем следа. */
    private fun render(glass: FogWipe, cover: Float) {
        val base = lerp(FOG_WHITE, (if (tint.isSpecified) tint else FOG_WHITE).copy(alpha = 1f), FOG_TINT).toArgb() and 0x00FFFFFF
        for (row in 0 until glass.rows) {
            val lower = 1f - BOTTOM_EXTRA / 2 + BOTTOM_EXTRA * (row + 0.5f) / glass.rows
            for (col in 0 until glass.cols) {
                val i = row * glass.cols + col
                val alpha = (FOG_ALPHA * cover * grain[i] * lower * glass.cover(i)).coerceIn(0f, 1f)
                pixels[i] = ((alpha * 255).roundToInt() shl 24) or base
            }
        }
        bitmap?.setPixels(pixels, 0, glass.cols, 0, 0, glass.cols, glass.rows)
        drawnCover = cover
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
