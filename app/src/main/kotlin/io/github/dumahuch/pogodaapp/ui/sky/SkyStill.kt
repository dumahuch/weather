package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * Когда рисовать неподвижную часть неба готовой картинкой (step-122). Ключ описывает всё, от чего она зависит (время суток,
 * погода, солнце и луна, подъём прокруткой, размер, загруженные картины). Новый ключ: кадр рисуется как раньше, напрямую
 * (во время прокрутки ключ меняется каждый кадр, и записывать картинку впустую незачем). Тот же ключ второй кадр подряд:
 * картинка записывается. Дальше, пока ключ тот же, берётся готовая.
 */
internal class StillGate {

    enum class Step { Direct, Record, Reuse }

    private var recorded: Any? = null
    private var seen: Any? = null

    fun step(key: Any): Step = when (key) {
        recorded -> Step.Reuse
        seen -> {
            recorded = key
            Step.Record
        }
        else -> {
            seen = key
            Step.Direct
        }
    }

    fun reset() {
        recorded = null
        seen = null
    }
}

/**
 * Готовые картинки неподвижной части неба: [back] небо с вуалью, затемнением и солнцем с луной, [ridges] гряды без
 * погодных накладок (они ложатся на гряды вместе с облаками, как раньше). Каждая картинка держится в памяти видеокарты
 * целиком (около 18 МБ на экран 1440×3120) и заново не рисуется, пока её не перезаписали; на каждом кадре заново
 * рисуются только облака. [drop] освобождает память (когда картины неба выгружаются).
 */
internal class SkyStill(private val graphics: GraphicsContext) {

    private val gate = StillGate()
    private var backLayer: GraphicsLayer? = null
    private var ridgesLayer: GraphicsLayer? = null

    val back: GraphicsLayer get() = backLayer ?: create().also { backLayer = it }
    val ridges: GraphicsLayer get() = ridgesLayer ?: create().also { ridgesLayer = it }

    fun step(key: Any): StillGate.Step = gate.step(key)

    /** Записывает обе картинки размером с [scope]. */
    fun record(scope: DrawScope, drawBack: DrawScope.() -> Unit, drawRidges: DrawScope.() -> Unit) {
        val size = IntSize(ceil(scope.size.width).toInt(), ceil(scope.size.height).toInt())
        back.record(scope, scope.layoutDirection, size, drawBack)
        ridges.record(scope, scope.layoutDirection, size, drawRidges)
    }

    fun drop() {
        backLayer?.let(graphics::releaseGraphicsLayer)
        ridgesLayer?.let(graphics::releaseGraphicsLayer)
        backLayer = null
        ridgesLayer = null
        gate.reset()
    }

    /** Картинка в отдельном буфере: видеокарта хранит готовые пиксели и перерисовывает их только после новой записи. */
    private fun create(): GraphicsLayer = graphics.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
}
