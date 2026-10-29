package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import kotlin.math.roundToInt

/**
 * Всё, что нужно стеклу: цвета интерфейса и (когда есть небо) состояние размытия. Стекло не создаёт своё
 * размытие, а берёт то, что ему отдаёт хозяин неба ([LocalGlass]).
 */
@Stable
class Glass internal constructor(
    val palette: PogodaPalette,
    internal val haze: HazeState?,
    internal val cardBlur: HazeBlurStyle,
) {
    companion object {
        /** Стекло без размытия: пока неба нет и в проверках. */
        fun flat(palette: PogodaPalette): Glass = Glass(palette, null, GlassBlur.card(0f))
    }
}

val LocalGlass = compositionLocalOf { Glass.flat(PogodaPalette.Neutral) }

/** Шаги, на которые делится «дымчатость» стекла, чтобы не пересоздавать стиль размытия при каждом сдвиге цвета. */
private const val SMOKE_STEPS = 20

/** Стекло для палитры [palette] и состояния размытия [haze] (пустое, пока размывать нечего). */
@Composable
fun rememberGlass(palette: PogodaPalette, haze: HazeState?): Glass {
    val step = (palette.smoke.coerceIn(0f, 1f) * SMOKE_STEPS).roundToInt()
    val cardBlur = remember(step) { GlassBlur.card(step / SMOKE_STEPS.toFloat()) }
    return remember(palette, haze, cardBlur) { Glass(palette, haze, cardBlur) }
}

/**
 * Размытие под стеклом (Haze). Значения из макета: размытие 10 px (в приложении 10 dp), у карточек яркость
 * и насыщенность фона зависят от того, насколько стекло дымчатое («Авто»: днём лёгкое, при светлом тексте
 * дымчатое), у кнопок и нижней панели всегда насыщенность 1,6 и зерно 0,08.
 */
internal object GlassBlur {
    val Radius: Dp = 10.dp

    /** Зерно карточек: 0,10 у лёгкого стекла, 0,16 у дымчатого. */
    fun noise(smoke: Float): Float = 0.10f + 0.06f * smoke

    /** Яркость фона под карточкой: от 1 до 0,85. */
    fun brightness(smoke: Float): Float = 1f - 0.15f * smoke

    /** Насыщенность фона под карточкой: от 1,6 до 2. */
    fun saturation(smoke: Float): Float = 1.6f + 0.4f * smoke

    /** Яркость и насыщенность одной матрицей: оба действия линейны и от порядка не зависят. */
    fun colorMatrix(saturation: Float, brightness: Float): ColorMatrix {
        val matrix = ColorMatrix().apply { setToSaturation(saturation) }
        val values = matrix.values
        for (row in 0 until 3) {
            for (column in 0 until 5) values[row * 5 + column] *= brightness
        }
        return matrix
    }

    fun card(smoke: Float): HazeBlurStyle = HazeBlurStyle {
        blurRadius(Radius)
        noiseFactor(noise(smoke))
        colorEffects(listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(colorMatrix(saturation(smoke), brightness(smoke))))))
    }

    val Button: HazeBlurStyle = HazeBlurStyle {
        blurRadius(Radius)
        noiseFactor(0.08f)
        colorEffects(listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(colorMatrix(1.6f, 1f)))))
    }
}

/**
 * Один слой тени в единицах макета (CSS px = dp): сдвиг, размытие, расширение и непрозрачность.
 * Для вставных теней (блик по кромке) расширение сжимает «дыру», как в CSS.
 */
internal class ShadowLayer(val dx: Float, val dy: Float, val blur: Float, val spread: Float, val alpha: Float)

/** Тень карточек, `--sh3d` макета: семь слоёв цвета hsl(205 20% 10%). Порядок как в макете, сверху вниз. */
internal val CardShadow = listOf(
    ShadowLayer(0f, 0f, 0.75f, 0f, 0.2f),
    ShadowLayer(0.7f, 0.8f, 1.2f, -0.4f, 0.1f),
    ShadowLayer(1.3f, 1.5f, 2.2f, -0.8f, 0.1f),
    ShadowLayer(2.3f, 2.6f, 3.9f, -1.2f, 0.1f),
    ShadowLayer(3.9f, 4.4f, 6.6f, -1.7f, 0.1f),
    ShadowLayer(6.5f, 7.2f, 10.9f, -2.1f, 0.1f),
    ShadowLayer(8f, 9f, 14f, -2.5f, 0.2f),
)
internal val CardShadowColor = Color(20, 26, 31)

/** Блик по верхней левой кромке карточки, `--rim` макета (вставные тени цвета hsl(205 20% 90%)). */
internal val CardRim = listOf(
    ShadowLayer(2f, 2f, 1f, -3f, 0.8f),
    ShadowLayer(4f, 4f, 2f, -6f, 0.3f),
    ShadowLayer(1.5f, 1.5f, 1.5f, -0.75f, 0.15f),
    ShadowLayer(1.5f, 1.5f, 0.25f, 0f, 0.03f),
    ShadowLayer(0f, 0f, 0.25f, 0.5f, 0.03f),
)
internal val CardRimColor = Color(224, 230, 235)

/** Тень кнопок и нижней панели: `0 8px 20px -10px`. */
internal val ButtonShadow = listOf(ShadowLayer(0f, 8f, 20f, -10f, 1f))

/**
 * Стекло карточек: размытый фон, заливка цвета неба, блик по верхней левой кромке, тонкая линия, семислойная
 * тень снаружи и блик по кромке внутри. Всё по макету (`.glass`). Без размытия ([Glass.flat]) рисуется
 * то же самое, кроме самого размытия.
 */
fun Modifier.glassCard(shape: Shape, glass: Glass): Modifier {
    val palette = glass.palette
    return this
        .outerShadow(shape, CardShadow, CardShadowColor)
        .clip(shape)
        .insetShadow(shape, CardRim, CardRimColor)
        .insetShadow(shape, listOf(ShadowLayer(0f, 1f, 0f, 0f, 1f)), palette.glassHighlight)
        .border(1.dp, palette.glassLine, shape)
        .hazeGlass(glass, glass.cardBlur)
        .background(palette.glass, shape)
        .background(
            Brush.linearGradient(0f to palette.sheen, 0.55f to Color.Transparent),
            shape,
        )
}

/** Подложка кнопок и нижней панели: молочнее карточек, своя тень, размытие с насыщением 1,6. */
fun Modifier.glassButton(shape: Shape, glass: Glass): Modifier {
    val palette = glass.palette
    return this
        .outerShadow(shape, ButtonShadow, palette.shadow)
        .clip(shape)
        .insetShadow(shape, listOf(ShadowLayer(0f, 1f, 0f, 0f, 1f)), palette.buttonHighlight)
        .border(1.dp, palette.glassLine, shape)
        .hazeGlass(glass, GlassBlur.Button)
        .background(palette.buttonBackground, shape)
}

private fun Modifier.hazeGlass(glass: Glass, style: HazeBlurStyle): Modifier {
    val haze = glass.haze ?: return this
    return hazeBlur(input = HazeInput.Sources(haze), style = style)
}

/** Радиус маски размытия Android (`BlurMaskFilter`) для размытия с сигмой [sigma] пикселей (Skia: сигма = 0,57735 · радиус + 0,5). */
internal fun maskRadiusForSigma(sigma: Float): Float = if (sigma <= 0.5f) 0f else (sigma - 0.5f) / 0.57735f

/**
 * Наружная тень по слоям, как CSS `box-shadow` без `inset`: тень видна только вне формы (под прозрачным стеклом
 * её нет). Размытие CSS с радиусом B даёт гауссову сигму B / 2.
 */
private fun Modifier.outerShadow(shape: Shape, layers: List<ShadowLayer>, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val hole = outline.toPath()
    val paths = layers.map { layer -> outline.grown(layer.spread.dp.toPx()).shifted(layer.dx.dp.toPx(), layer.dy.dp.toPx()) }
    val paints = layers.map { layer -> shadowPaint(color.copy(alpha = color.alpha * layer.alpha), layer.blur.dp.toPx() / 2f) }
    onDrawBehind {
        clipPath(hole, ClipOp.Difference) {
            drawIntoCanvas { canvas ->
                // Нижние слои первыми: так сверху остаётся самый резкий.
                for (index in layers.indices.reversed()) canvas.nativeCanvas.drawPath(paths[index].asAndroidPath(), paints[index])
            }
        }
    }
}

/**
 * Вставная тень по слоям, как CSS `inset`: тень «снаружи» формы, сдвинутой на (dx, dy) и сжатой на spread,
 * рисуется только внутри формы. Так получается блик вдоль верхней и левой кромки.
 */
private fun Modifier.insetShadow(shape: Shape, layers: List<ShadowLayer>, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val inside = outline.toPath()
    val margin = 8.dp.toPx() + layers.maxOf { it.blur + kotlin.math.abs(it.dx) + kotlin.math.abs(it.dy) + kotlin.math.abs(it.spread) }.dp.toPx() * 2f
    val paths = layers.map { layer ->
        val hole = outline.grown(-layer.spread.dp.toPx()).shifted(layer.dx.dp.toPx(), layer.dy.dp.toPx())
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-margin, -margin, size.width + margin, size.height + margin))
            addPath(hole)
        }
    }
    val paints = layers.map { layer -> shadowPaint(color.copy(alpha = color.alpha * layer.alpha), layer.blur.dp.toPx() / 2f) }
    onDrawWithContent {
        drawContent()
        clipPath(inside) {
            drawIntoCanvas { canvas ->
                for (index in layers.indices) canvas.nativeCanvas.drawPath(paths[index].asAndroidPath(), paints[index])
            }
        }
    }
}

private fun shadowPaint(color: Color, sigma: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(
            (color.alpha * 255f).roundToInt(),
            (color.red * 255f).roundToInt(),
            (color.green * 255f).roundToInt(),
            (color.blue * 255f).roundToInt(),
        )
        val radius = maskRadiusForSigma(sigma)
        if (radius > 0f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }

/** Контур, увеличенный на [by] со всех сторон (отрицательное число сжимает); углы растут вместе с ним. */
internal fun Outline.grown(by: Float): Outline = when (this) {
    is Outline.Rectangle -> Outline.Rectangle(Rect(rect.left - by, rect.top - by, rect.right + by, rect.bottom + by))
    is Outline.Rounded -> Outline.Rounded(
        RoundRect(
            left = roundRect.left - by,
            top = roundRect.top - by,
            right = roundRect.right + by,
            bottom = roundRect.bottom + by,
            topLeftCornerRadius = roundRect.topLeftCornerRadius.grown(by),
            topRightCornerRadius = roundRect.topRightCornerRadius.grown(by),
            bottomRightCornerRadius = roundRect.bottomRightCornerRadius.grown(by),
            bottomLeftCornerRadius = roundRect.bottomLeftCornerRadius.grown(by),
        ),
    )
    // Для произвольной формы расширение не считается: форма остаётся прежней.
    is Outline.Generic -> this
}

private fun CornerRadius.grown(by: Float) = CornerRadius((x + by).coerceAtLeast(0f), (y + by).coerceAtLeast(0f))

/** Контур как путь. Путь произвольной формы отдаётся как есть: менять его нельзя. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/** Контур, сдвинутый на ([dx], [dy]), как новый путь. */
private fun Outline.shifted(dx: Float, dy: Float): Path = Path().apply { addPath(toPath(), Offset(dx, dy)) }
