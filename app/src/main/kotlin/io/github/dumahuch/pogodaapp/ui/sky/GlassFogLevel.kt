package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.dumahuch.pogodaapp.sky.GlassFog
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import kotlin.math.roundToInt

/** За сколько плёнка тумана проступает или сходит целиком (мс). */
private const val FADE_MILLIS = 5_000

/** Шагов густоты: ступень 0,02, проступание видно плавным, а карточки перерисовываются не каждый кадр. */
private const val STEPS = 50f

/**
 * Густота запотевания стекла по погоде (на экране проверки по подставленной погоде, без влажности и видимости). Плёнка
 * проступает и сходит за [FADE_MILLIS]; пусто, пока её нет совсем, чтобы стекло не держало лишних узлов.
 */
@Composable
internal fun rememberGlassFog(input: SkyInput?, override: SkyOverride, animationsOn: Boolean, started: Boolean): State<Float>? {
    val target = remember(input, override) { fogTarget(input, override) }
    val level = remember { Animatable(0f) }
    LaunchedEffect(target, animationsOn, started) {
        if (!started) return@LaunchedEffect
        if (!animationsOn) {
            level.snapTo(target)
            return@LaunchedEffect
        }
        level.animateTo(target, tween(FADE_MILLIS, easing = LinearEasing))
    }
    val stepped = remember(level) { derivedStateOf { (level.value * STEPS).roundToInt() / STEPS } }
    val shown by remember(level) { derivedStateOf { (level.value * STEPS).roundToInt() > 0 } }
    return if (shown) stepped else null
}

private fun fogTarget(input: SkyInput?, override: SkyOverride): Float {
    if (input == null) return 0f
    val measured = override.condition == null
    return GlassFog.density(
        condition = override.condition ?: input.condition,
        precipitationMm = if (measured) input.precipitationMm else null,
        humidity = if (measured) input.humidity else null,
        visibilityM = if (measured) input.visibilityM else null,
    ).toFloat()
}
