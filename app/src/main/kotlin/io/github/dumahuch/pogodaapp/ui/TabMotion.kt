package io.github.dumahuch.pogodaapp.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /**
     * Картинка того, что сейчас на экране в слое [layer]: ждёт кадра, в котором содержимое записано в слой, и снимает его.
     * Не вышло за [CAPTURE_TIMEOUT_MS] или снимок упал: пусто, вкладка сменится без картинки.
     */
    suspend fun capture(layer: GraphicsLayer): ImageBitmap? {
        val drawn = CompletableDeferred<Unit>()
        request = drawn
        return try {
            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                drawn.await()
                layer.toImageBitmap()
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
    suspend fun run(old: ImageBitmap?, startShiftPx: Int, switch: () -> Unit) {
        try {
            picture = old
            cover.snapTo(if (old != null) 1f else 0f)
            shift.snapTo(startShiftPx.toFloat())
            switch()
            coroutineScope {
                launch {
                    cover.animateTo(0f, tween(PUSH_COVER_MS, easing = LinearEasing))
                    picture = null
                }
                shift.animateTo(0f, tween(PUSH_MS))
            }
        } finally {
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
    }
}
