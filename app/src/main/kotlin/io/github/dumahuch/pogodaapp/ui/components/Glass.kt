package io.github.dumahuch.pogodaapp.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
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
    /** Тень карточек «Выраженная» (настройка): вторая тень, светлая кромка и вуаль у всех стеклянных карточек, см. [CardLift]. */
    internal val lifted: Boolean = false,
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
fun rememberGlass(palette: PogodaPalette, haze: HazeState?, lifted: Boolean = false): Glass {
    val step = (palette.smoke.coerceIn(0f, 1f) * SMOKE_STEPS).roundToInt()
    val cardBlur = remember(step) { GlassBlur.card(step / SMOKE_STEPS.toFloat()) }
    return remember(palette, haze, cardBlur, lifted) { Glass(palette, haze, cardBlur, lifted) }
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
internal data class ShadowLayer(val dx: Float, val dy: Float, val blur: Float, val spread: Float, val alpha: Float)

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

/**
 * Добавка, которая отделяет карточки от серого неба сильнее обычного: вторая тень под обычной, тонкая светлая кромка и
 * лёгкая вуаль, отодвигающая заливку от цвета неба. С 0.5.3 она была только у плиток «Подробно», с 0.5.4 это ступень
 * «Выраженная» настройки «Тень карточек» и действует на все стеклянные карточки ([Glass.lifted]).
 * Обычная тень и блик остаются как в макете, добавка лежит поверх них.
 */
internal object CardLift {
    val Shadow = listOf(
        ShadowLayer(0f, 1f, 2.5f, 0f, 0.30f),
        ShadowLayer(0f, 4f, 10f, -1f, 0.30f),
        ShadowLayer(0f, 10f, 22f, -4f, 0.30f),
    )

    /** Прозрачность светлой кромки: заметна на любом небе, но не превращается в рамку. */
    const val RIM_ALPHA = 0.30f

    /** Вуаль над заливкой: у светлого стекла белая, у дымчатого чёрная, между ними плавно; она всегда повышает контраст текста. */
    const val VEIL_LIGHT = 0.10f
    const val VEIL_SMOKY = 0.08f

    fun veil(smoke: Float): Color {
        val k = smoke.coerceIn(0f, 1f)
        val gray = 1f - k
        return Color(gray, gray, gray, VEIL_LIGHT + (VEIL_SMOKY - VEIL_LIGHT) * k)
    }
}

/** Тень кнопок и нижней панели: `0 8px 20px -10px`. */
internal val ButtonShadow = listOf(ShadowLayer(0f, 8f, 20f, -10f, 1f))

/**
 * Стекло карточек: размытый фон, заливка цвета неба, блик по верхней левой кромке, тонкая линия, семислойная
 * тень снаружи и блик по кромке внутри. Всё по макету (`.glass`). Без размытия ([Glass.flat]) рисуется
 * то же самое, кроме самого размытия. Если у стекла включена ступень «Выраженная» ([Glass.lifted]), добавляются вторая
 * тень, светлая кромка и вуаль ([CardLift]).
 */
fun Modifier.glassCard(shape: Shape, glass: Glass): Modifier {
    val palette = glass.palette
    val lifted = glass.lifted
    return this
        .then(if (lifted) Modifier.rasterShadow(shape, CardLift.Shadow, inset = false, color = CardShadowColor) else Modifier)
        .rasterShadow(shape, CardShadow, inset = false, color = CardShadowColor)
        .clip(shape)
        .rasterShadow(shape, CardRim, inset = true, color = CardRimColor)
        .topHighlight(shape, palette.glassHighlight)
        .border(1.dp, palette.glassLine, shape)
        .hazeGlass(glass, glass.cardBlur)
        .background(palette.glass, shape)
        .then(if (lifted) Modifier.background(CardLift.veil(palette.smoke), shape) else Modifier)
        .background(
            Brush.linearGradient(0f to palette.sheen, 0.55f to Color.Transparent),
            shape,
        )
        .then(if (lifted) Modifier.border(1.dp, Color.White.copy(alpha = CardLift.RIM_ALPHA), shape) else Modifier)
}

/** Подложка кнопок и нижней панели: молочнее карточек, своя тень, размытие с насыщением 1,6. */
fun Modifier.glassButton(shape: Shape, glass: Glass): Modifier {
    val palette = glass.palette
    return this
        .rasterShadow(shape, ButtonShadow, inset = false, color = palette.shadow)
        .clip(shape)
        .topHighlight(shape, palette.buttonHighlight)
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
 * Светлая линия в один dp по верхней кромке формы (CSS `inset 0 1px 0`): форма без самой себя, сдвинутой вниз.
 * Чёткая заливка без размытия, поэтому дешёвая.
 */
private fun Modifier.topHighlight(shape: Shape, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val band = Path.combine(PathOperation.Difference, outline.toPath(), outline.shifted(0f, 1.dp.toPx()))
    onDrawWithContent {
        drawContent()
        drawPath(band, color)
    }
}

/**
 * Тень по слоям, как CSS `box-shadow` (при [inset] как `box-shadow: inset`). Размытые слои один раз рисуются в
 * растр на фоновом потоке, а на экран выводятся картинкой с цветом: так видеокарта не размывает тени каждый
 * кадр, и они не зависят от того, как часто пересобирается экран. Пока растр не готов, тени нет.
 * Наружная тень видна только вне формы (под прозрачным стеклом её нет). Размытие CSS радиуса B это гауссова сигма B / 2.
 */
private fun Modifier.rasterShadow(shape: Shape, layers: List<ShadowLayer>, inset: Boolean, color: Color): Modifier =
    this then RasterShadowElement(shape, layers, inset, color)

private class RasterShadowElement(
    val shape: Shape,
    val layers: List<ShadowLayer>,
    val inset: Boolean,
    val color: Color,
) : ModifierNodeElement<RasterShadowNode>() {
    override fun create() = RasterShadowNode(shape, layers, inset, color)

    override fun update(node: RasterShadowNode) = node.update(shape, layers, inset, color)

    override fun equals(other: Any?): Boolean =
        other is RasterShadowElement && other.shape == shape && other.layers == layers && other.inset == inset && other.color == color

    override fun hashCode(): Int = ((shape.hashCode() * 31 + layers.hashCode()) * 31 + inset.hashCode()) * 31 + color.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "rasterShadow"
    }
}

/** От чего зависит растр тени: размер, плотность, форма и слои. Цвет в растр не входит, он накладывается при выводе. */
private data class ShadowKey(
    val size: Size,
    val density: Float,
    val direction: LayoutDirection,
    val shape: Shape,
    val layers: List<ShadowLayer>,
    val inset: Boolean,
)

private class RasterShadowNode(
    private var shape: Shape,
    private var layers: List<ShadowLayer>,
    private var inset: Boolean,
    color: Color,
) : Modifier.Node(), DrawModifierNode {

    private var tint = ColorFilter.tint(color, BlendMode.SrcIn)
    private var image: ImageBitmap? = null
    private var imageKey: ShadowKey? = null
    private var margin = 0
    private var pendingKey: ShadowKey? = null
    private var pending: Job? = null

    fun update(shape: Shape, layers: List<ShadowLayer>, inset: Boolean, color: Color) {
        this.shape = shape
        this.layers = layers
        this.inset = inset
        tint = ColorFilter.tint(color, BlendMode.SrcIn)
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (size.minDimension > 0f) ensureImage(ShadowKey(size, density, layoutDirection, shape, layers, inset))
        if (!inset) drawShadowImage()
        drawContent()
        if (inset) drawShadowImage()
    }

    private fun ContentDrawScope.drawShadowImage() {
        val ready = image ?: return
        drawImage(ready, topLeft = Offset(-margin.toFloat(), -margin.toFloat()), colorFilter = tint)
    }

    private fun ContentDrawScope.ensureImage(key: ShadowKey) {
        if (key == imageKey || key == pendingKey) return
        pending?.cancel()
        pendingKey = key
        val outline = key.shape.createOutline(key.size, key.direction, this)
        val width = ceil(key.size.width).toInt()
        val height = ceil(key.size.height).toInt()
        val marginPx = if (key.inset) 0 else ceil(key.layers.maxOf { layer -> layer.reach() } * key.density).toInt()
        pending = coroutineScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                renderShadow(outline, key.layers, key.inset, key.density, width, height, marginPx)
            }
            image = bitmap.asImageBitmap()
            imageKey = key
            margin = marginPx
            pendingKey = null
            invalidateDraw()
        }
    }
}

/** Насколько слой выходит за форму (dp): хвост размытия (три сигмы), расширение и сдвиг. */
private fun ShadowLayer.reach(): Float = blur * 1.5f + abs(spread) + maxOf(abs(dx), abs(dy))

private fun alphaPaint(alpha: Float, sigma: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255f).roundToInt(), 255, 255, 255)
        val radius = maskRadiusForSigma(sigma)
        if (radius > 0f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }

/**
 * Растр тени в виде карты прозрачности (один канал). Наружная тень: слои, форма, увеличенная на расширение и
 * сдвинутая, размытые; потом вырезана сама форма. Вставная тень: «снаружи» формы, сжатой на расширение и сдвинутой,
 * размытое и обрезанное по форме. Слои, полоса которых целиком вне формы, не рисуются: они дают меньше 2 %.
 */
private fun renderShadow(
    outline: Outline,
    layers: List<ShadowLayer>,
    inset: Boolean,
    density: Float,
    width: Int,
    height: Int,
    margin: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ALPHA_8)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.translate(margin.toFloat(), margin.toFloat())
    val inside = outline.toPath().asAndroidPath()
    if (inset) canvas.clipPath(inside)
    for (layer in layers) {
        val spread = layer.spread * density
        val dx = layer.dx * density
        val dy = layer.dy * density
        val paint = alphaPaint(layer.alpha, layer.blur / 2f * density)
        if (inset) {
            if (layer.spread + maxOf(abs(layer.dx), abs(layer.dy)) + layer.blur <= 0f) continue
            val hole = outline.grown(-spread).shifted(dx, dy)
            val reach = layer.reach() * density + 2f * density
            val complement = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-reach, -reach, width + reach, height + reach))
                addPath(hole)
            }
            canvas.drawPath(complement.asAndroidPath(), paint)
        } else {
            canvas.drawPath(outline.grown(spread).shifted(dx, dy).asAndroidPath(), paint)
        }
    }
    if (!inset) {
        val clear = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawPath(inside, clear)
    }
    return bitmap
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
