package io.github.dumahuch.pogodaapp.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
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
 * Смена вкладки (0.8.53). Прежняя вкладка в момент нажатия снимается в картинку ([capture]) и закрывает экран, пока под
 * ней собирается новая: по замерам стенда (claude/audit/15-stand-results.md) первые кадры новой вкладки стоят 70–440 мс.
 * Когда кадры снова короткие ([newTabSettled]), картинка гаснет за [TAB_FADE_MS]. Новая вкладка не сдвигается: при сдвиге
 * размытие карточек пересчитывалось в каждом кадре (решение Димы, вариант «б»).
 */
@Stable
internal class TabMotion {
    /** Просьба записать ближайший кадр содержимого в слой; рисующий код отмечает её выполненной. */
    var request by mutableStateOf<CompletableDeferred<Unit>?>(null)
        private set

    /** Картинка прежней вкладки, пока она гаснет; иначе пусто. */
    var picture by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Непрозрачность картинки прежней вкладки. */
    val cover = Animatable(0f)

    /** Идущая смена вкладки: новое нажатие отменяет её. */
    var job: Job? = null

    /** Сколько занял последний снимок прежней вкладки, для строки в журнале. */
    private var captureNote = ""

    /**
     * Картинка того, что сейчас на экране в слое [layer]: ждёт кадра, в котором содержимое записано в слой, и снимает его.
     * Не вышло за [CAPTURE_TIMEOUT_MS] или снимок упал: пусто, вкладка сменится без картинки.
     */
    suspend fun capture(layer: GraphicsLayer): ImageBitmap? {
        val drawn = CompletableDeferred<Unit>()
        request = drawn
        val asked = System.nanoTime()
        return try {
            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                drawn.await()
                val recorded = System.nanoTime()
                layer.toImageBitmap().also {
                    captureNote = "кадр в слой ${millis(asked, recorded)} мс, картинка ${millis(recorded, System.nanoTime())} мс"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Картинка прежней вкладки не снята", e)
            null
        } finally {
            request = null
        }
    }

    /**
     * Сама смена: картинка [old] закрывает экран, [switch] меняет вкладку под ней; картинка держится, пока новая вкладка
     * не соберётся, и гаснет. Отмена (новое нажатие) сразу убирает картинку.
     */
    suspend fun run(old: ImageBitmap?, name: String, switch: () -> Unit) {
        val stamps = ArrayList<Long>(FRAMES_KEPT)
        var heldMs = 0L
        try {
            picture = old
            cover.snapTo(if (old != null) 1f else 0f)
            val start = System.nanoTime()
            stamps += start
            switch()
            if (old == null) return
            coroutineScope {
                // Замер для журнала: когда приходили кадры, пока шла смена. На саму смену не влияет.
                val watch = launch {
                    while (stamps.size < FRAMES_KEPT) withFrameNanos { stamps += System.nanoTime() }
                }
                var last = start
                var seen = 0
                while (true) {
                    val now = withFrameNanos { System.nanoTime() }
                    seen++
                    if (newTabSettled(millis(last, now), seen, millis(start, now))) break
                    last = now
                }
                heldMs = millis(start, System.nanoTime())
                cover.animateTo(0f, tween(TAB_FADE_MS, easing = FastOutSlowInEasing))
                watch.cancel()
            }
        } finally {
            val shot = if (old == null) "без картинки" else "$captureNote, держали $heldMs мс"
            Log.i(TAG, "Смена $name: $shot; кадры, мс: ${frameGaps(stamps).joinToString(" ")}")
            picture = null
            withContext(NonCancellable) { cover.snapTo(0f) }
        }
    }

    private companion object {
        const val TAG = "PogodaTabs"

        /** Сколько ждать картинку: дольше нажатие казалось бы пропущенным. */
        const val CAPTURE_TIMEOUT_MS = 150L

        /** Больше кадров за одну смену в журнал не пишем. */
        const val FRAMES_KEPT = 80

        fun millis(fromNanos: Long, toNanos: Long): Long = (toNanos - fromNanos) / 1_000_000
    }
}

/** Как быстро гаснет картинка прежней вкладки, когда новая готова. */
internal const val TAB_FADE_MS = 320

/** Кадр не дольше этого значит, что новая вкладка собрана (на «Подробно» обычный кадр 20–42 мс). */
internal const val SETTLE_FRAME_MS = 45L

/** Самый первый кадр после смены ещё старый: судить можно не раньше второго. */
internal const val SETTLE_MIN_FRAMES = 2

/** Дольше картинку не держим, даже если кадры всё ещё длинные: иначе нажатие казалось бы зависшим. */
internal const val SETTLE_MAX_MS = 450L

/**
 * Можно ли гасить картинку прежней вкладки: прошло [framesSeen] кадров, последний длился [gapMs], всего ждём [waitedMs].
 */
internal fun newTabSettled(gapMs: Long, framesSeen: Int, waitedMs: Long): Boolean =
    waitedMs >= SETTLE_MAX_MS || (framesSeen >= SETTLE_MIN_FRAMES && gapMs <= SETTLE_FRAME_MS)

/** Промежутки между соседними отметками времени в миллисекундах: первый — от смены вкладки до первого кадра. */
internal fun frameGaps(stampsNanos: List<Long>): List<Long> =
    stampsNanos.zipWithNext { a, b -> (b - a) / 1_000_000 }
