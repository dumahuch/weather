package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.github.dumahuch.pogodaapp.sky.Flake
import io.github.dumahuch.pogodaapp.sky.Gusts
import io.github.dumahuch.pogodaapp.sky.Pose
import io.github.dumahuch.pogodaapp.sky.PrecipClock
import io.github.dumahuch.pogodaapp.sky.PrecipKind
import io.github.dumahuch.pogodaapp.sky.PrecipSpec
import io.github.dumahuch.pogodaapp.sky.RainField
import io.github.dumahuch.pogodaapp.sky.SnowField
import io.github.dumahuch.pogodaapp.sky.strengthStep
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Не чаще: слой лежит под размытием стекла (Haze), и каждое его обновление заставляет пересчитать размытие под карточками.
 * 30 мс: на 60 Гц обновление через кадр, на 120 Гц через четыре. Размытые штрихи и мягкие снежинки при 30 кадрах в секунду
 * выглядят плавно; если на Pixel время кадра не уложится, это число единственная ручка (см. claude/topics/weather-fx.md).
 */
private const val FRAME_INTERVAL_NANOS = 30_000_000L

/** Момент, на котором стоит слой, пока анимации выключены (энергосбережение, «убрать анимацию»): кадр как на фотографии. */
private const val STILL_SECONDS = 3.0

/** Поле частиц пересоздаётся, только когда размер изменился больше чем на эту величину (dp). */
private const val SIZE_STEP_DP = 10

/** Ближние слои дождя рисуются ещё и широким бледным штрихом: так передаётся расфокус. */
private const val HALO_DEPTH = 0.65
private const val HALO_WIDTH = 2.2f
private const val HALO_ALPHA = 0.35f

/** Дальние слои дождя достаточно одного штриха, у ближних хвост плавно гаснет тремя отрезками. */
private const val TAIL_DEPTH = 0.4
private val TAIL_ALPHA = floatArrayOf(0.2f, 0.6f, 1.0f)

/** Цвет струй: на светлом небе темнее его, на тёмном светлее; множитель прозрачности подобран по макету. */
private val LIGHT_SKY_RAIN = Color(0xFF465564)
private val DARK_SKY_RAIN = Color(0xFFE2EAF2)
private const val LIGHT_SKY_GAIN = 0.75f
private const val DARK_SKY_GAIN = 0.9f

private val SNOW_TINT = Color(0xFFF6FAFF)
private val SNOW_RIM = Color(0xFF5A6C84).copy(alpha = 0.14f)
private const val SPRITE_PX = 64

/** На светлом небе у снежинки есть серо-голубая кромка: диск занимает такую долю картинки, остальное кромка. */
private const val RIM_DISC_SHARE = 0.8f
private const val NEAR_DEPTH = 0.75
private const val MID_DEPTH = 0.5
private const val NEAR_ALPHA = 0.55f
private const val MID_ALPHA = 0.92f
private const val FAR_ALPHA = 0.85f
private const val CRYSTAL_ALPHA = 0.35f
private const val CRYSTAL_ARMS = 3
private const val CRYSTAL_ARM_LENGTH = 0.85f
private const val CRYSTAL_WIDTH_DP = 0.8f

/**
 * Дождь или снег поверх неба. Свои часы, небо при этом не перерисовывается; когда анимации выключены системой,
 * стоит один кадр. Лежит внутри источника размытия, поэтому стекло карточек размывает и осадки.
 */
@Composable
internal fun PrecipLayer(
    spec: PrecipSpec?,
    animate: Boolean,
    started: Boolean,
    lightSky: Boolean,
    modifier: Modifier = Modifier,
) {
    if (spec == null) return
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val clock = remember { mutableDoubleStateOf(STILL_SECONDS) }
    LaunchedEffect(animate, started) {
        if (!animate || !started) return@LaunchedEffect
        val timer = PrecipClock(FRAME_INTERVAL_NANOS, STILL_SECONDS)
        while (true) withFrameNanos { now -> timer.seconds(now)?.let { clock.doubleValue = it } }
    }
    val widthDp = size.width / density / SIZE_STEP_DP
    val heightDp = size.height / density / SIZE_STEP_DP
    val stepWidth = widthDp.roundToInt() * SIZE_STEP_DP.toDouble()
    val stepHeight = heightDp.roundToInt() * SIZE_STEP_DP.toDouble()
    val step = strengthStep(spec.strength)
    val rain = remember(spec.kind, step, stepWidth, stepHeight) {
        if (spec.kind == PrecipKind.Rain && stepWidth > 0) RainField.create(step, stepWidth, stepHeight) else null
    }
    val snow = remember(spec.kind, step, stepWidth, stepHeight) {
        if (spec.kind == PrecipKind.Snow && stepWidth > 0) SnowField.create(step, stepWidth, stepHeight) else null
    }
    val sprites = remember(spec.kind, lightSky) { if (spec.kind == PrecipKind.Snow) Sprites(lightSky) else null }
    val pose = remember { Pose() }
    val paint = remember { Paint().apply { isAntiAlias = true; filterQuality = FilterQuality.Low } }
    Box(
        modifier
            .onSizeChanged { size = it }
            .drawBehind {
                val t = clock.doubleValue
                if (rain != null) {
                    drawRain(rain, spec, t, lightSky, pose)
                } else if (snow != null && sprites != null) {
                    drawSnow(snow, sprites, spec, t, pose, paint)
                }
            },
    )
}

private fun DrawScope.drawRain(field: RainField, spec: PrecipSpec, t: Double, lightSky: Boolean, pose: Pose) {
    val px = density
    // Наклон в этот миг (с порывом): голова падает по прямой, а хвост тянется назад вдоль неё.
    val tilt = spec.rainTilt * Gusts.rainFactor(t)
    val unit = 1.0 / sqrt(1.0 + tilt * tilt)
    val dirX = (tilt * unit).toFloat()
    val dirY = unit.toFloat()
    val base = if (lightSky) LIGHT_SKY_RAIN else DARK_SKY_RAIN
    val gain = if (lightSky) LIGHT_SKY_GAIN else DARK_SKY_GAIN
    for (drop in field.drops) {
        field.head(drop, t, spec.rainTilt, pose)
        val hx = pose.x.toFloat() * px
        val hy = pose.y.toFloat() * px
        val tx = hx - dirX * drop.length.toFloat() * px
        val ty = hy - dirY * drop.length.toFloat() * px
        val width = drop.width.toFloat() * px
        val alpha = drop.alpha.toFloat() * gain
        if (drop.depth >= HALO_DEPTH) {
            drawLine(base.copy(alpha = alpha * HALO_ALPHA), Offset(tx, ty), Offset(hx, hy), width * HALO_WIDTH)
        }
        if (drop.depth < TAIL_DEPTH) {
            drawLine(base.copy(alpha = alpha * TAIL_ALPHA[1]), Offset(tx, ty), Offset(hx, hy), width)
        } else {
            val parts = TAIL_ALPHA.size
            for (i in 0 until parts) {
                val from = Offset(tx + (hx - tx) * i / parts, ty + (hy - ty) * i / parts)
                val to = Offset(tx + (hx - tx) * (i + 1) / parts, ty + (hy - ty) * (i + 1) / parts)
                drawLine(base.copy(alpha = alpha * TAIL_ALPHA[i]), from, to, width)
            }
        }
    }
}

private fun DrawScope.drawSnow(field: SnowField, sprites: Sprites, spec: PrecipSpec, t: Double, pose: Pose, paint: Paint) {
    val px = density
    val drift = spec.snowDrift
    drawIntoCanvas { canvas ->
        for (flake in field.flakes) {
            field.centre(flake, t, drift, pose)
            val outer = flake.radius.toFloat() * px * sprites.outerScale
            val near = flake.depth >= NEAR_DEPTH
            paint.alpha = when {
                near -> NEAR_ALPHA
                flake.depth >= MID_DEPTH -> MID_ALPHA
                else -> FAR_ALPHA
            }
            canvas.save()
            canvas.translate(pose.x.toFloat() * px - outer, pose.y.toFloat() * px - outer)
            val scale = 2 * outer / SPRITE_PX
            canvas.scale(scale, scale)
            canvas.drawImage(if (near) sprites.bokeh else sprites.soft, Offset.Zero, paint)
            canvas.restore()
        }
    }
    // Лучи у самых крупных.
    for (flake in field.flakes) if (flake.crystal) drawCrystal(field, flake, t, drift, pose)
}

private fun DrawScope.drawCrystal(field: SnowField, flake: Flake, t: Double, drift: Double, pose: Pose) {
    field.centre(flake, t, drift, pose)
    val px = density
    val cx = pose.x.toFloat() * px
    val cy = pose.y.toFloat() * px
    val arm = flake.radius.toFloat() * px * CRYSTAL_ARM_LENGTH
    val color = SNOW_TINT.copy(alpha = CRYSTAL_ALPHA)
    val angle = flake.phase + flake.spin * t
    for (i in 0 until CRYSTAL_ARMS) {
        val a = angle + Math.PI * i / CRYSTAL_ARMS
        val dx = (cos(a) * arm).toFloat()
        val dy = (sin(a) * arm).toFloat()
        drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), CRYSTAL_WIDTH_DP * px)
    }
}

/** Картинки снежинок: мягкая (дальние и средние) и расфокусированная с колечком (ближние), на светлом небе с серо-голубой кромкой. */
private class Sprites(lightSky: Boolean) {
    val soft: ImageBitmap = disc(near = false, rim = lightSky)
    val bokeh: ImageBitmap = disc(near = true, rim = lightSky)

    /** Во сколько раз картинка больше самого диска (диск плюс кромка). */
    val outerScale: Float = if (lightSky) 1f / RIM_DISC_SHARE else 1f
}

private fun disc(near: Boolean, rim: Boolean): ImageBitmap {
    val bitmap = ImageBitmap(SPRITE_PX, SPRITE_PX)
    val canvas = Canvas(bitmap)
    val centre = Offset(SPRITE_PX / 2f, SPRITE_PX / 2f)
    val outer = SPRITE_PX / 2f
    val paint = Paint().apply { isAntiAlias = true }
    if (rim) {
        paint.color = SNOW_RIM
        canvas.drawCircle(centre, outer, paint)
    }
    val radius = if (rim) outer * RIM_DISC_SHARE else outer
    val colors: List<Color>
    val stops: List<Float>
    if (near) {
        colors = listOf(SNOW_TINT.copy(alpha = 0.55f), SNOW_TINT.copy(alpha = 0.5f), SNOW_TINT.copy(alpha = 0.68f), SNOW_TINT.copy(alpha = 0f))
        stops = listOf(0f, 0.55f, 0.8f, 1f)
    } else {
        colors = listOf(SNOW_TINT, SNOW_TINT.copy(alpha = 0.9f), SNOW_TINT.copy(alpha = 0f))
        stops = listOf(0f, 0.55f, 1f)
    }
    paint.shader = RadialGradientShader(centre, radius, colors, stops)
    canvas.drawCircle(centre, radius, paint)
    return bitmap
}
