package io.github.dumahuch.pogodaapp.ui.map

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import io.github.dumahuch.pogodaapp.map.ScreenView
import io.github.dumahuch.pogodaapp.map.WindFlow
import io.github.dumahuch.pogodaapp.map.WindParticles
import io.github.dumahuch.pogodaapp.map.WindSegments

/**
 * Линии, бегущие по ветру, поверх карты ([WindParticles]): только пока есть поле ветра [flow] и карта стоит ([view] не
 * null). Новый час поля частицы не сбрасывает, они просто несутся дальше по новому ветру. Пока экран не рисуется
 * (приложение свёрнуто, вкладка закрыта), часы кадров стоят и частицы не считаются.
 */
@Composable
internal fun WindOverlay(flow: WindFlow?, view: ScreenView?, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val particles = remember(view, dp) {
        view?.let { WindParticles(WindParticles.countFor(it.width / dp, it.height / dp), dp) }
    }
    val currentFlow by rememberUpdatedState(flow)
    var frame by remember { mutableLongStateOf(0L) }
    val active = flow != null && particles != null && view != null

    LaunchedEffect(active, particles) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val field = currentFlow
                if (last != 0L && field != null) particles.advance((now - last) / NANOS_PER_SECOND, field, view)
                last = now
                frame = now
            }
        }
    }

    val segments = remember { WindSegments() }
    val paints = remember(dp) {
        Array(segments.bucketCount) { bucket ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LINE_COLOR
                alpha = (WindParticles.alphaOf(segments.level(bucket)) * 255).toInt()
                strokeWidth = WindParticles.WidthsDp[segments.width(bucket)] * dp
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }
        }
    }

    Canvas(modifier) {
        // Чтение кадра здесь перерисовывает только холст, без пересборки экрана.
        if (frame == 0L || !active) return@Canvas
        particles.collect(segments)
        drawIntoCanvas { canvas ->
            for (bucket in 0 until segments.bucketCount) {
                val size = segments.size(bucket)
                if (size > 0) canvas.nativeCanvas.drawLines(segments.data(bucket), 0, size, paints[bucket])
            }
        }
    }
}

private const val LINE_COLOR = 0xFFF5F8FA.toInt()
private const val NANOS_PER_SECOND = 1_000_000_000f
