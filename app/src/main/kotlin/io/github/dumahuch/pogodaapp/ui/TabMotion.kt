package io.github.dumahuch.pogodaapp.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
 * Движение при смене вкладки (0.8.52). Прежние попытки (0.8.45–0.8.50) рвались, потому что на экране жили две вкладки с
 * размытым стеклом сразу и кадр стоил 80–100 мс. Здесь прежняя вкладка в момент нажатия снимается в картинку ([capture])
 * и убирается; картинка гаснет за [PUSH_COVER_MS], как уходящее окно при переходе вглубь, а новая вкладка въезжает сбоку
 * за [PUSH_MS] той же кривой. Размытие считает только новая вкладка.
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

    /** Сдвиг новой вкладки по горизонтали в пикселях. */
    val shift = Animatable(0f)

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
     * Сама смена: картинка [old] закрывает экран, [switch] меняет вкладку под ней, картинка гаснет, новая вкладка
     * въезжает со сдвига [startShiftPx]. Отмена (новое нажатие) сразу ставит всё на место.
     */
    suspend fun run(old: ImageBitmap?, startShiftPx: Int, name: String, switch: () -> Unit) {
        val stamps = ArrayList<Long>(FRAMES_KEPT)
        try {
            picture = old
            cover.snapTo(if (old != null) 1f else 0f)
            shift.snapTo(startShiftPx.toFloat())
            stamps += System.nanoTime()
            switch()
            coroutineScope {
                // Замер для журнала: когда приходили кадры, пока шла смена. На само движение не влияет.
                val watch = launch {
                    while (stamps.size < FRAMES_KEPT) withFrameNanos { stamps += System.nanoTime() }
                }
                launch {
                    cover.animateTo(0f, tween(PUSH_COVER_MS, easing = LinearEasing))
                    picture = null
                }
                shift.animateTo(0f, tween(PUSH_MS))
                watch.cancel()
            }
        } finally {
            val shot = if (old == null) "без картинки" else captureNote
            Log.i(TAG, "Смена $name: $shot; кадры, мс: ${frameGaps(stamps).joinToString(" ")}")
            picture = null
            withContext(NonCancellable) {
                cover.snapTo(0f)
                shift.snapTo(0f)
            }
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

/** Промежутки между соседними отметками времени в миллисекундах: первый — от смены вкладки до первого кадра. */
internal fun frameGaps(stampsNanos: List<Long>): List<Long> =
    stampsNanos.zipWithNext { a, b -> (b - a) / 1_000_000 }
