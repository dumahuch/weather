package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * Когда рисовать неподвижную часть неба готовой картинкой (step-122). Ключ описывает всё, от чего она зависит (время суток,
 * погода, солнце и луна, размер, загруженные картины, у гряд ещё подъём прокруткой). Новый ключ: кадр рисуется как раньше,
 * напрямую (во время прокрутки ключ гряд меняется каждый кадр, и записывать картинку впустую незачем). Тот же ключ второй
 * кадр подряд: картинка записывается. Дальше, пока ключ тот же, берётся готовая.
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
 * Шаги двух картинок: задней ([back]) и гряд ([ridges]) по отдельности (0.8.89). Заднее небо от прокрутки не зависит (у слоя
 * неба глубина 0), поэтому при прокрутке оно берётся готовым, а напрямую рисуются только гряды. Раньше ключ был общий и с
 * подъёмом: на каждом кадре прокрутки всё небо с вуалью, затемнением, солнцем и луной рисовалось заново.
 */
internal class StillGates {
    private val backGate = StillGate()
    private val ridgesGate = StillGate()

    fun back(key: Any): StillGate.Step = backGate.step(key)
    fun ridges(key: Any): StillGate.Step = ridgesGate.step(key)

    fun reset() {
        backGate.reset()
        ridgesGate.reset()
    }
}

/**
 * Готовые картинки неподвижной части неба: [back] небо с вуалью, затемнением и солнцем с луной, [ridges] гряды без
 * погодных накладок (они ложатся на гряды вместе с облаками, как раньше). Каждая картинка держится в памяти видеокарты
 * целиком (около 18 МБ на экран 1440×3120) и заново не рисуется, пока её не перезаписали; на каждом кадре заново
 * рисуются только облака (и гряды, пока идёт прокрутка). [drop] освобождает память (когда картины неба выгружаются).
 */
internal class SkyStill(private val graphics: GraphicsContext) {

    private val gates = StillGates()
    private var backLayer: GraphicsLayer? = null
    private var ridgesLayer: GraphicsLayer? = null

    val back: GraphicsLayer get() = backLayer ?: create().also { backLayer = it }
    val ridges: GraphicsLayer get() = ridgesLayer ?: create().also { ridgesLayer = it }

    fun backStep(key: Any): StillGate.Step = gates.back(key)
    fun ridgesStep(key: Any): StillGate.Step = gates.ridges(key)

    /** Записывает картинку [layer] размером с [scope]. */
    fun record(layer: GraphicsLayer, scope: DrawScope, draw: DrawScope.() -> Unit) {
        val size = IntSize(ceil(scope.size.width).toInt(), ceil(scope.size.height).toInt())
        layer.record(scope, scope.layoutDirection, size, draw)
    }

    fun drop() {
        backLayer?.let(graphics::releaseGraphicsLayer)
        ridgesLayer?.let(graphics::releaseGraphicsLayer)
        backLayer = null
        ridgesLayer = null
        gates.reset()
    }

    /** Картинка в отдельном буфере: видеокарта хранит готовые пиксели и перерисовывает их только после новой записи. */
    private fun create(): GraphicsLayer = graphics.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
}
