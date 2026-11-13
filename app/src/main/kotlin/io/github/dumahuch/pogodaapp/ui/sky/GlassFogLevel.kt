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
import io.github.dumahuch.pogodaapp.sky.GlassFrost
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import kotlin.math.abs
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
    return rememberGlassLevel(target, FADE_MILLIS, FADE_MILLIS, STEPS, animationsOn, started)
}

/** Иней нарастает от краёв за [FROST_GROW_MILLIS] (как сугроб), в оттепель сходит за [FROST_MELT_MILLIS]. */
private const val FROST_GROW_MILLIS = 25_000
private const val FROST_MELT_MILLIS = 10_000

/** Ступень доли инея: фронт двигается мелкими шагами, без рывков. */
private const val FROST_STEPS = 120f

/** Доля стекла под инеем по температуре (на экране проверки по кнопке мороза); пусто, пока инея нет совсем. */
@Composable
internal fun rememberGlassFrost(input: SkyInput?, override: SkyOverride, animationsOn: Boolean, started: Boolean): State<Float>? {
    val target = remember(input, override) { frostTarget(input, override) }
    return rememberGlassLevel(target, FROST_GROW_MILLIS, FROST_MELT_MILLIS, FROST_STEPS, animationsOn, started)
}

/** Иней на стекле: по подставленной температуре экрана проверки или по настоящей. */
internal fun frostTarget(input: SkyInput?, override: SkyOverride): Float {
    if (input == null) return 0f
    return GlassFrost.coverage(override.frostC ?: input.temperatureC).toFloat()
}

/** Уровень, плавно идущий к [target]: вверх за [upMillis], вниз за [downMillis] (полный ход), ступенями 1 / [steps]. */
@Composable
private fun rememberGlassLevel(target: Float, upMillis: Int, downMillis: Int, steps: Float, animationsOn: Boolean, started: Boolean): State<Float>? {
    val level = remember { Animatable(0f) }
    LaunchedEffect(target, animationsOn, started) {
        if (!started) return@LaunchedEffect
        if (!animationsOn) {
            level.snapTo(target)
            return@LaunchedEffect
        }
        val distance = abs(target - level.value)
        val full = if (target > level.value) upMillis else downMillis
        level.animateTo(target, tween((distance * full).roundToInt().coerceAtLeast(1), easing = LinearEasing))
    }
    val stepped = remember(level, steps) { derivedStateOf { (level.value * steps).roundToInt() / steps } }
    val shown by remember(level, steps) { derivedStateOf { (level.value * steps).roundToInt() > 0 } }
    return if (shown) stepped else null
}

private fun fogTarget(input: SkyInput?, override: SkyOverride): Float {
    // В мороз вместо запотевания иней.
    if (input == null || frostTarget(input, override) > 0f) return 0f
    val measured = override.condition == null
    return GlassFog.density(
        condition = override.condition ?: input.condition,
        precipitationMm = if (measured) input.precipitationMm else null,
        humidity = if (measured) input.humidity else null,
        visibilityM = if (measured) input.visibilityM else null,
    ).toFloat()
}
