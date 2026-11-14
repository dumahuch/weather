package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.FogWipe
import io.github.dumahuch.pogodaapp.sky.FrostComposer
import io.github.dumahuch.pogodaapp.sky.GlassFrost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Непрозрачность плёнки при густоте 1; ниже густоты [MIN_COVER] плёнки нет совсем. */
private const val FOG_ALPHA = 0.4f
private const val MIN_COVER = 0.02f

/** Плёнка молочно-белая с долей цвета стекла (ночью не светится серым пятном). */
private val FOG_WHITE = Color(0xFFF1F5F9)
private const val FOG_TINT = 0.3f

/** Иней белый с долей цвета стекла (подхватывает свет неба). */
private val FROST_WHITE = Color(0xFFF7FBFF)
private const val FROST_TINT = 0.15f

/** Иней собирается не мельче стольких пикселей на dp (на Pixel 6 Pro 3,5): разницы на глаз нет, памяти меньше. */
private const val FROST_PX_PER_DP = 3.0

/** Готовый иней проявляется за столько наносекунд, а не появляется рывком. */
private const val FROST_APPEAR_NANOS = 400_000_000f

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
 * Иней на стекле из картинок ([FrostComposer], рисует [FrostLook]), [frost] доля стекла под ним (0…1). Тёплый палец протапливает
 * след, через несколько секунд он снова замерзает (около минуты). С [patch] касания ловит [frostWiping] на внешней коробке.
 */
internal fun Modifier.glassFrost(frost: State<Float>?, tint: Color, patch: FogPatch? = null): Modifier =
    if (frost == null) this else this then GlassFogElement(frost, tint, patch, draws = true, frosty = true)

/** Только ловит палец для инея, который рисует [glassFrost] с тем же [patch]. */
internal fun Modifier.frostWiping(frost: State<Float>?, patch: FogPatch): Modifier =
    if (frost == null) this else this then GlassFogElement(frost, Color.Unspecified, patch, draws = false, frosty = true)

private class GlassFogElement(
    val fog: State<Float>,
    val tint: Color,
    val patch: FogPatch?,
    val draws: Boolean,
    val frosty: Boolean = false,
) : ModifierNodeElement<GlassFogNode>() {
    override fun create() = GlassFogNode(fog, tint, patch ?: FogPatch(), draws, listens = patch == null || !draws, frosty)

    override fun update(node: GlassFogNode) = node.update(fog, tint)

    override fun equals(other: Any?): Boolean =
        other is GlassFogElement && other.fog === fog && other.tint == tint && other.patch === patch && other.draws == draws &&
            other.frosty == frosty

    override fun hashCode(): Int =
        (((System.identityHashCode(fog) * 31 + tint.hashCode()) * 31 + System.identityHashCode(patch)) * 31 + draws.hashCode()) * 31 +
            frosty.hashCode()

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
) : Modifier.Node(), DrawModifierNode, PointerInputModifierNode, CompositionLocalConsumerModifierNode {

    /** Иней для текущего размера стекла, его ключ, сборка в фоне и проявление готового (только у инея). */
    private var look: FrostLook? = null
    private var sheet: Bitmap? = null
    private var sheetKey = 0L
    private var making: Job? = null
    private var appear = 1f

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
        making = null
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
            val glass = glassFor(size.width / density, size.height / density)
            if (dirty || (!frosty && cover != drawnCover)) render(glass, cover)
            val made = bitmap
            if (made != null && frosty) {
                drawFrost(made, cover)
            } else if (made != null) {
                // Сглаживание при растяжке маленькой картинки даёт ровную плёнку и мягкий край следа.
                target.set(0f, 0f, size.width, size.height)
                drawIntoCanvas { it.nativeCanvas.drawBitmap(made, null, target, paint) }
            }
        }
        drawContent()
    }

    /** Иней рисует видеокарта по готовому узору; пока узор собирается (доля секунды), инея нет. */
    private fun ContentDrawScope.drawFrost(wipe: Bitmap, level: Float) {
        val pxPerDp = min(density.toDouble(), FROST_PX_PER_DP)
        val widthDp = size.width / density
        val heightDp = size.height / density
        var w = (widthDp * pxPerDp).roundToInt().coerceAtLeast(1)
        var h = (heightDp * pxPerDp).roundToInt().coerceAtLeast(1)
        var scale = pxPerDp
        if (w.toLong() * h > FrostComposer.MAX_PIXELS) {
            val shrink = sqrt(FrostComposer.MAX_PIXELS.toDouble() / (w.toLong() * h))
            w = (w * shrink).roundToInt()
            h = (h * shrink).roundToInt()
            scale *= shrink
        }
        // Узор зависит от размера в dp: одно и то же стекло всегда замерзает одинаково.
        val seed = (widthDp.roundToInt() * 7919L) xor heightDp.roundToInt().toLong()
        val key = ((w.toLong() shl 20) xor h.toLong()) * 31 + seed
        val ready = sheet.takeIf { sheetKey == key } ?: FrostSheets.cached(key)?.also {
            sheet = it
            sheetKey = key
            appear = 1f
        }
        if (ready == null) {
            makeSheet(key, w, h, scale, seed)
            return
        }
        val ice = lerp(FROST_WHITE, (if (tint.isSpecified) tint else FROST_WHITE).copy(alpha = 1f), FROST_TINT)
        val painter = look ?: FrostLook().also { look = it }
        drawIntoCanvas { painter.draw(it.nativeCanvas, size.width, size.height, ready, wipe, level, appear, ice) }
    }

    private fun makeSheet(key: Long, width: Int, height: Int, pxPerDp: Double, seed: Long) {
        if (making?.isActive == true) return
        val assets = currentValueOf(LocalContext).assets
        making = coroutineScope.launch {
            val made = withContext(Dispatchers.Default) {
                // Нет картинки или памяти: стекло просто без инея, приложение работает дальше.
                runCatching { FrostSheets.make(key, width, height, pxPerDp, assets, seed) }.getOrNull()
            } ?: return@launch
            sheet = made
            sheetKey = key
            appear = 0f
            var start = -1L
            while (appear < 1f) {
                val now = withFrameNanos { it }
                if (start < 0) start = now
                appear = ((now - start) / FROST_APPEAR_NANOS).coerceAtMost(1f)
                invalidateDraw()
            }
        }
    }

    /** Стекло этого размера; новое (сухое от пальца) при заметной смене размера. */
    private fun glassFor(widthDp: Float, heightDp: Float): FogWipe {
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
        val count = fresh.cols * fresh.rows
        pixels = IntArray(count)
        bitmap = Bitmap.createBitmap(fresh.cols, fresh.rows, Bitmap.Config.ARGB_8888)
        dirty = true
        return fresh
    }

    /** Клетки плёнки в маленькую картинку; у инея в ней только след пальца (прозрачность: сколько инея осталось). */
    private fun render(glass: FogWipe, cover: Float) {
        if (frosty) {
            for (i in pixels.indices) pixels[i] = ((glass.cover(i) * 255).roundToInt() shl 24) or 0xFFFFFF
            bitmap?.setPixels(pixels, 0, glass.cols, 0, 0, glass.cols, glass.rows)
            dirty = false
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
