package io.github.dumahuch.pogodaapp.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Смена вкладки «через небо» (0.8.55). По замерам стенда (claude/audit/15-stand-results.md) первые кадры новой вкладки
 * стоят 75–260 мс, и пока она собирается, двигать нечего. Поэтому смена в три части, и двух вкладок на экране нет никогда:
 * 1) уход: прежняя вкладка уже картинка, она гаснет и чуть уменьшается за [TAB_EXIT_MS];
 * 2) небо: живое содержимое спрятано ([hidden]), под ним собирается новая вкладка, пока кадры не станут короткими
 *    ([newTabSettled]);
 * 3) приход: новая вкладка снимается в картинку и проявляется, вырастая до полного размера, за [TAB_ENTER_MS];
 *    потом картинку в тот же кадр сменяет живая вкладка.
 * С 0.8.56 вкладки с небом собраны заранее ([TabDeck]); с 0.8.57 сборка и снимок новой идут под уходящей картинкой (см. [run]),
 * и небо остаётся только если новая вкладка не успела за время ухода.
 * С 0.8.90 «картинка» это записанный слой ([GraphicsLayer]), а не снимок в растр: снимок `toImageBitmap()` прогонял кадр
 * через видеокарту и ждал её на главном потоке (стенд 22.11: 16–35 мс дважды на смену, два тяжёлых кадра). Слоёв два:
 * уходящая вкладка рисуется из одного, пока в другой записывается приходящая.
 */
@Stable
internal class TabMotion {
    /** Просьба записать ближайший кадр содержимого в слой [Capture.layer]; рисующий код отмечает её выполненной. */
    var request by mutableStateOf<Capture?>(null)
        private set

    /** Живое содержимое не рисуется и не принимает нажатий: на экране небо и картинка. */
    var hidden by mutableStateOf(false)
        private set

    /** Записанный слой уходящей или приходящей вкладки; иначе пусто. */
    var picture by mutableStateOf<GraphicsLayer?>(null)
        private set

    /** Непрозрачность картинки. */
    val alpha = Animatable(0f)

    /** Размер картинки относительно экрана. */
    val scale = Animatable(1f)

    /** Идущая смена вкладки: новое нажатие отменяет её. */
    var job: Job? = null

    /** Сколько заняли снимки, для строки в журнале. */
    private val captureNotes = ArrayList<String>(2)

    /** Куда записать кадр и знак, что он записан. */
    class Capture(val layer: GraphicsLayer, val drawn: CompletableDeferred<Unit>)

    /**
     * Записывает ближайший кадр содержимого в слой [layer] и отдаёт его. Не вышло за [timeoutMs] или запись упала: пусто.
     */
    suspend fun capture(layer: GraphicsLayer, timeoutMs: Long = CAPTURE_TIMEOUT_MS): GraphicsLayer? {
        val drawn = CompletableDeferred<Unit>()
        request = Capture(layer, drawn)
        val asked = System.nanoTime()
        return try {
            withTimeoutOrNull(timeoutMs) {
                drawn.await()
                captureNotes += "слой ${millis(asked, System.nanoTime())} мс"
                layer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Слой вкладки не записан", e)
            null
        } finally {
            request = null
        }
    }

    /**
     * Сама смена: [old] уходит, [switch] меняет вкладку, новая записывается в [layer] (не в тот же слой, что [old]) и приходит картинкой
     * (если [arriveAsPicture]; карта рисуется отдельной поверхностью и в картинку не попадает, она просто появляется).
     * С 0.8.57 новая вкладка ставится и снимается прямо под уходящей картинкой, а не после неё: к концу ухода она
     * обычно уже готова, и пустого неба между вкладками нет. Приход всё равно начинается не раньше конца ухода.
     * Отмена (новое нажатие) сразу показывает живое содержимое.
     */
    suspend fun run(
        old: GraphicsLayer?,
        layer: GraphicsLayer,
        arriveAsPicture: Boolean,
        name: String,
        switch: () -> Unit,
    ) {
        val stamps = ArrayList<Long>(FRAMES_KEPT)
        var buildMs = 0L
        var skyMs = 0L
        var arrived = "живой"
        try {
            stamps += System.nanoTime()
            coroutineScope {
                // Замер для журнала: когда приходили кадры, пока шла смена. На саму смену не влияет.
                val watch = launch {
                    while (stamps.size < FRAMES_KEPT) withFrameNanos { stamps += System.nanoTime() }
                }
                hidden = true
                var exitEnd = System.nanoTime()
                val exit = if (old != null) {
                    picture = old
                    alpha.snapTo(1f)
                    scale.snapTo(1f)
                    launch {
                        animatePicture(to = 0f, toScale = TAB_FAR_SCALE, ms = TAB_EXIT_MS, leaving = true)
                        exitEnd = System.nanoTime()
                    }
                } else {
                    null
                }
                // Карта сразу видна сквозь уходящую картинку, поэтому на неё переходим только после ухода.
                if (!arriveAsPicture) exit?.join()
                val start = System.nanoTime()
                switch()
                var last = start
                var seen = 0
                while (true) {
                    val now = withFrameNanos { System.nanoTime() }
                    seen++
                    if (newTabSettled(millis(last, now), seen, millis(start, now))) break
                    last = now
                }
                val new = if (arriveAsPicture) capture(layer, ARRIVE_CAPTURE_TIMEOUT_MS) else null
                buildMs = millis(start, System.nanoTime())
                exit?.join()
                picture = null
                skyMs = skyMillis(exitEnd, System.nanoTime())
                if (new != null) {
                    arrived = "картинкой"
                    alpha.snapTo(0f)
                    scale.snapTo(TAB_FAR_SCALE)
                    picture = new
                    animatePicture(to = 1f, toScale = 1f, ms = TAB_ENTER_MS, leaving = false)
                }
                // Картинку сменяет живая вкладка в одном кадре.
                hidden = false
                picture = null
                watch.cancel()
            }
        } finally {
            Log.i(
                TAG,
                "Смена $name: снимки ${captureNotes.joinToString(", ").ifEmpty { "нет" }}; сборка $buildMs мс; " +
                    "небо $skyMs мс; пришла $arrived; кадры, мс: ${frameGaps(stamps).joinToString(" ")}",
            )
            captureNotes.clear()
            hidden = false
            picture = null
            withContext(NonCancellable) {
                alpha.snapTo(0f)
                scale.snapTo(1f)
            }
        }
    }

    private suspend fun animatePicture(to: Float, toScale: Float, ms: Int, leaving: Boolean) = coroutineScope {
        val easing = if (leaving) FastOutLinearInEasing else LinearOutSlowInEasing
        launch { scale.animateTo(toScale, tween(ms, easing = easing)) }
        alpha.animateTo(to, tween(ms, easing = easing))
    }

    private companion object {
        const val TAG = "PogodaTabs"

        /** Сколько ждать картинку прежней вкладки: дольше нажатие казалось бы пропущенным. */
        const val CAPTURE_TIMEOUT_MS = 150L

        /** Новая вкладка рисуется в слой впервые, это дольше; не успели: она просто появляется. */
        const val ARRIVE_CAPTURE_TIMEOUT_MS = 300L

        /** Больше кадров за одну смену в журнал не пишем. */
        const val FRAMES_KEPT = 100

        fun millis(fromNanos: Long, toNanos: Long): Long = (toNanos - fromNanos) / 1_000_000
    }
}

/** Как быстро уходит прежняя вкладка: сразу по нажатию; под ней тем временем ставится новая. */
internal const val TAB_EXIT_MS = 150

/** Как быстро проявляется новая вкладка, когда собрана. */
internal const val TAB_ENTER_MS = 220

/** Размер, до которого уходящая вкладка уменьшается и с которого вырастает приходящая. */
internal const val TAB_FAR_SCALE = 0.96f

/** Кадр не дольше этого значит, что новая вкладка собрана (на «Подробно» обычный кадр 20–42 мс). */
internal const val SETTLE_FRAME_MS = 45L

/** Самый первый кадр после смены ещё старый: судить можно не раньше второго. */
internal const val SETTLE_MIN_FRAMES = 2

/** Дольше не ждём, даже если кадры всё ещё длинные: иначе нажатие казалось бы зависшим. */
internal const val SETTLE_MAX_MS = 450L

/**
 * Собрана ли новая вкладка: прошло [framesSeen] кадров, последний длился [gapMs], всего ждём [waitedMs].
 */
internal fun newTabSettled(gapMs: Long, framesSeen: Int, waitedMs: Long): Boolean =
    waitedMs >= SETTLE_MAX_MS || (framesSeen >= SETTLE_MIN_FRAMES && gapMs <= SETTLE_FRAME_MS)

/**
 * Сколько миллисекунд было видно одно небо: от конца ухода ([exitEndNanos]) до начала прихода ([arriveNanos]).
 * Если новая вкладка была готова раньше конца ухода, неба нет: ноль.
 */
internal fun skyMillis(exitEndNanos: Long, arriveNanos: Long): Long =
    ((arriveNanos - exitEndNanos) / 1_000_000).coerceAtLeast(0)

/** Промежутки между соседними отметками времени в миллисекундах: первый — от нажатия до первого кадра. */
internal fun frameGaps(stampsNanos: List<Long>): List<Long> =
    stampsNanos.zipWithNext { a, b -> (b - a) / 1_000_000 }
